#!/usr/bin/env python3
"""真实 MySQL 8.4 回归；只创建临时 Compose 项目，退出时销毁容器及测试数据。

运行：python3 sql/tests/test_email_uniqueness.py
依赖：Docker Compose、本地 mysql 客户端、Python bcrypt、Java 21 和 Maven。
Maven 默认离线；如需下载依赖，设置 ACOVE_QA_MAVEN_ONLINE=1。
"""

import concurrent.futures
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import threading
import time
import uuid


ROOT = Path(__file__).resolve().parents[2]
PASSWORD = "isolated-test-only-password"


def run(args, *, env, sql=None, check=True, timeout=180):
    result = subprocess.run(args, cwd=ROOT, env=env, input=sql, text=True,
                            capture_output=True, timeout=timeout)
    if check and result.returncode:
        raise AssertionError(f"Command failed: {args[0]}\n{result.stdout}\n{result.stderr}")
    return result


def main():
    for executable in ("docker", "mysql", "mvn"):
        if not shutil.which(executable):
            raise SystemExit(f"Required executable: {executable}")
    import bcrypt

    with tempfile.TemporaryDirectory(prefix="acove-email-qa-") as directory:
        compose_file = Path(directory) / "compose.yml"
        compose_file.write_text("""services:
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_ROOT_PASSWORD: isolated-test-only-password
      MYSQL_ROOT_HOST: '%'
    ports:
      - '127.0.0.1::3306'
    tmpfs:
      - /var/lib/mysql
""", encoding="utf-8")
        env = dict(os.environ, COMPOSE_FILE=str(compose_file),
                   COMPOSE_PROJECT_NAME="acove-email-qa-" + uuid.uuid4().hex[:12])
        compose = ["docker", "compose"]

        def mysql(sql, database=None, check=True):
            args = compose + ["exec", "-T", "-e", f"MYSQL_PWD={PASSWORD}", "mysql", "mysql",
                              "--host=127.0.0.1", "--protocol=TCP", "--user=root",
                              "--default-character-set=utf8mb4", "--batch", "--raw", "--skip-column-names"]
            if database:
                args += ["--database=" + database]
            return run(args, env=env, sql=sql, check=check).stdout.strip()

        try:
            print("Starting isolated MySQL 8.4 (no existing Compose project or database is used)", flush=True)
            run(compose + ["up", "-d", "--pull", "never"], env=env)
            deadline = time.monotonic() + 90
            while True:
                try:
                    if mysql("SELECT 1;") == "1":
                        break
                except AssertionError:
                    if time.monotonic() >= deadline:
                        raise
                time.sleep(1)
            port = run(compose + ["port", "mysql", "3306"], env=env).stdout.strip().rsplit(":", 1)[1]
            env.update(ACOVE_QA_MYSQL_URL=f"jdbc:mysql://127.0.0.1:{port}/",
                       ACOVE_QA_MYSQL_PASSWORD=PASSWORD)

            print("Running backend tests and actual Flyway V1–V10 / V6 upgrade scenarios", flush=True)
            maven = ["mvn"]
            if env.get("ACOVE_QA_MAVEN_ONLINE") != "1":
                maven.append("-o")
            maven += ["-f", "backend/pom.xml", "test", "-Dmaven-surefire-plugin.version=3.5.5"]
            result = run(maven, env=env)
            for line in result.stdout.splitlines():
                if "Tests run:" in line or "BUILD SUCCESS" in line:
                    print(line, flush=True)

            for entry in ("standalone", "deploy"):
                database = "acove_script_qa_" + entry
                mysql(f"CREATE DATABASE {database} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;")
                for migration in sorted((ROOT / "backend/src/main/resources/db/migration").glob("V*.sql"),
                                        key=lambda path: int(path.name.split("__", 1)[0][1:])):
                    mysql(migration.read_text(encoding="utf-8"), database)
                entry_env = dict(env, BLOG_DB_HOST="127.0.0.1", BLOG_DB_PORT=port,
                                 BLOG_DB_NAME=database, BLOG_DB_USER="root", BLOG_DB_PASSWORD=PASSWORD)

                def account(username, email, password="account-test-password"):
                    call_env = dict(entry_env, BLOG_ACCOUNT_USERNAME=username,
                                    BLOG_ACCOUNT_EMAIL=email, BLOG_ACCOUNT_PASSWORD=password)
                    args = (["bash", "sql/init_account.sh", username, email] if entry == "standalone"
                            else ["bash", "deploy.sh", "account"])
                    return run(args, env=call_env, check=False)

                def snapshot():
                    return mysql("SELECT id,username,email,password,role,token_version,create_time FROM sys_user ORDER BY id;", database)

                def success(result):
                    assert result.returncode == 0, result.stderr
                    assert "账号已写入" in result.stdout, result.stdout

                def conflict(result):
                    assert result.returncode != 0, "Conflicting write unexpectedly succeeded"
                    assert "邮箱已被占用" in result.stderr, result.stderr
                    assert "账号已写入" not in result.stdout, result.stdout

                def check_password(username, password):
                    # Test usernames below contain no quotes.
                    stored = mysql(f"SELECT password FROM sys_user WHERE username='{username}';", database)
                    assert bcrypt.checkpw(password.encode(), stored.encode())

                success(account("alice", "alice@example.com"))
                assert mysql("SELECT role FROM sys_user WHERE username='alice';", database) == "owner"
                success(account("bob", "bob@example.com"))
                assert mysql("SELECT token_version FROM sys_user WHERE username='alice';", database) == "0"
                success(account("alice", "alice@example.com", "reset-password"))
                assert mysql("SELECT token_version FROM sys_user WHERE username='alice';", database) == "1"
                assert mysql("SELECT token_version FROM sys_user WHERE username='bob';", database) == "0"
                check_password("alice", "reset-password")
                success(account("bob", "bob-new@example.com", "bob-new-password"))
                check_password("bob", "bob-new-password")
                assert mysql("SELECT token_version FROM sys_user WHERE username='bob';", database) == "1"
                for name in ("intruder", "bob"):
                    before = snapshot()
                    conflict(account(name, "ALICE@example.com", "must-not-change-any-password"))
                    assert snapshot() == before, "Conflict changed existing accounts"

                # A failed account write must not promote an owner as a partial side effect.
                mysql("UPDATE sys_user SET role='user';", database)
                before = snapshot()
                conflict(account("intruder", "alice@example.com"))
                assert snapshot() == before
                mysql("UPDATE sys_user SET role='owner' WHERE username='alice';", database)

                def race(names, email):
                    barrier = threading.Barrier(2)

                    def contender(name):
                        barrier.wait(timeout=10)
                        return account(name, email, name + "-race-password")

                    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
                        results = list(pool.map(contender, names))
                    assert sum(result.returncode == 0 for result in results) == 1, [
                        result.stderr for result in results]
                    for result in results:
                        if result.returncode != 0:
                            assert "账号已写入" not in result.stdout
                    assert mysql(f"SELECT COUNT(*) FROM sys_user WHERE email='{email}';", database) == "1"
                    return names[next(i for i, result in enumerate(results) if result.returncode != 0)]

                before = snapshot()
                race(("new-racer-a", "new-racer-b"), "new-race@example.com")
                assert mysql("SELECT COUNT(*) FROM sys_user WHERE username LIKE 'new-racer-%';", database) == "1"
                assert all(row in snapshot().splitlines() for row in before.splitlines())

                previous = {name: mysql(f"SELECT email,password,token_version FROM sys_user WHERE username='{name}';", database)
                            for name in ("alice", "bob")}
                loser = race(("alice", "bob"), "existing-race@example.com")
                assert mysql(f"SELECT email,password,token_version FROM sys_user WHERE username='{loser}';", database) == previous[loser]

                # Both callers use the same SQL-mode-independent literal encoder.
                mysql("SET GLOBAL sql_mode=CONCAT(@@GLOBAL.sql_mode,',NO_BACKSLASH_ESCAPES');")
                success(account("quote'\\name", "quoted@example.com"))
                assert mysql("SELECT COUNT(*) FROM sys_user WHERE email='quoted@example.com';", database) == "1"

                # Missing V10 must fail before any password/email/role mutation.
                mysql("ALTER TABLE sys_user RENAME COLUMN token_version TO previous_token_version;", database)
                before = mysql("SELECT id,username,email,password,role,previous_token_version FROM sys_user ORDER BY id;", database)
                missing = account("alice", "v10-missing@example.com", "must-not-change-password")
                assert missing.returncode != 0 and "V10" in missing.stderr, missing.stderr
                assert mysql("SELECT id,username,email,password,role,previous_token_version FROM sys_user ORDER BY id;", database) == before
                mysql("ALTER TABLE sys_user RENAME COLUMN previous_token_version TO token_version;", database)

                # Missing V7 is rejected before any account/role mutation.
                mysql("ALTER TABLE sys_user DROP INDEX uk_email;", database)
                before = snapshot()
                missing = account("missing-migration", "unused@example.com")
                assert missing.returncode != 0 and "V7" in missing.stderr, missing.stderr
                assert snapshot() == before
                print(f"PASS {entry}: create/reset/change/conflict/rollback/concurrency/SQL-mode/schema guard", flush=True)
            print("PASS all email uniqueness regression checks", flush=True)
        finally:
            run(compose + ["down", "--volumes", "--remove-orphans"], env=env)
            print("Removed isolated MySQL test project", flush=True)


if __name__ == "__main__":
    main()
