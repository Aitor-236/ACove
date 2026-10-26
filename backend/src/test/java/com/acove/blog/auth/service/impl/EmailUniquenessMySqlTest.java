package com.acove.blog.auth.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import com.acove.blog.auth.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 由 sql/tests/test_email_uniqueness.py 提供独立 MySQL；默认单测不连接任何业务库。 */
@EnabledIfEnvironmentVariable(named = "ACOVE_QA_MYSQL_URL", matches = ".+")
class EmailUniquenessMySqlTest {
    private String database;
    private String url;
    private Connection connection;

    @BeforeEach
    void createIsolatedDatabase() throws SQLException {
        database = "acove_email_qa_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection server = serverConnection(); var statement = server.createStatement()) {
            assertTrue(server.getMetaData().getDatabaseProductVersion().startsWith("8.4."));
            statement.execute("CREATE DATABASE " + database + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        url = System.getenv("ACOVE_QA_MYSQL_URL") + database + "?characterEncoding=UTF-8";
        connection = DriverManager.getConnection(url, "root", password());
    }

    @AfterEach
    void dropOnlyTestDatabase() throws SQLException {
        if (connection != null) {
            connection.close();
        }
        if (database != null) {
            try (Connection server = serverConnection(); var statement = server.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS " + database);
            }
        }
    }

    @Test
    void fullMigrationChainCreatesUniqueEmailIndex() throws SQLException {
        flyway("latest").migrate();

        assertEquals(List.of("10"), rows("SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"));
        assertEquals(List.of("uk_email\t0\temail"), rows("SELECT INDEX_NAME, NON_UNIQUE, COLUMN_NAME "
                + "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_email'"));
        execute("INSERT INTO sys_user(username,email,password) VALUES ('alice','Same@example.com','hash')");
        SQLException error = assertThrows(SQLException.class, () -> execute(
                "INSERT INTO sys_user(username,email,password) VALUES ('bob','same@example.com','other-hash')"));
        assertEquals(1062, error.getErrorCode());
    }

    @Test
    void cleanV6UpgradePreservesAccountsAndArticleAuthor() throws SQLException {
        flyway("6").migrate();
        seed("bob@example.com");
        List<String> users = rows("SELECT id,username,email,password,avatar,role,create_time FROM sys_user ORDER BY id");
        List<String> articles = rows("SELECT * FROM article ORDER BY id");

        flyway("latest").migrate();

        assertEquals(users, rows("SELECT id,username,email,password,avatar,role,create_time FROM sys_user ORDER BY id"));
        assertEquals(List.of("0", "0"), rows("SELECT token_version FROM sys_user ORDER BY id"));
        assertEquals(articles, rows("SELECT * FROM article ORDER BY id"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"shared@example.com", "SHARED@example.com"})
    void duplicateV6DataBlocksMigrationUntilAdminAssignsDistinctEmail(String duplicate) throws SQLException {
        flyway("6").migrate();
        seed(duplicate);
        List<String> users = rows("SELECT id,username,email,password,avatar,role,create_time FROM sys_user ORDER BY id");
        List<String> articles = rows("SELECT * FROM article ORDER BY id");
        List<String> identities = rows("SELECT id, username, password, role, create_time FROM sys_user ORDER BY id");

        assertEquals(2, rows("SELECT u.email, u.id, u.username FROM sys_user u JOIN "
                + "(SELECT email FROM sys_user GROUP BY email HAVING COUNT(*) > 1) d "
                + "ON u.email = d.email ORDER BY u.email, u.id").size());
        assertThrows(FlywayException.class, () -> flyway("latest").migrate());
        assertEquals(users, rows("SELECT id,username,email,password,avatar,role,create_time FROM sys_user ORDER BY id"));
        assertEquals(List.of("0"), rows("SELECT COUNT(*) FROM information_schema.STATISTICS "
                + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_user' AND INDEX_NAME='uk_email'"));

        execute("UPDATE sys_user SET email='confirmed-bob@example.com' WHERE id=22");
        flyway("latest").repair();
        flyway("latest").migrate();

        assertEquals(identities, rows("SELECT id, username, password, role, create_time FROM sys_user ORDER BY id"));
        assertEquals(articles, rows("SELECT * FROM article ORDER BY id"));
    }

    @Test
    void passwordMapperAtomicallyIncrementsVersionAndRejectsStaleCredentials() throws SQLException {
        flyway("latest").migrate();
        seed("bob@example.com");
        var config = new MybatisConfiguration();
        config.setEnvironment(new Environment("session-test", new JdbcTransactionFactory(),
                new UnpooledDataSource("com.mysql.cj.jdbc.Driver", url, "root", password())));
        config.addMapper(SysUserMapper.class);
        try (var session = new MybatisSqlSessionFactoryBuilder().build(config).openSession(true)) {
            var mapper = session.getMapper(SysUserMapper.class);
            assertEquals(1, mapper.changePasswordAndRevokeTokens(11L, "alice-hash", "new-hash", 0L));
            assertEquals(0, mapper.changePasswordAndRevokeTokens(11L, "alice-hash", "stale-hash", 0L));
            assertEquals(0, mapper.changePasswordAndRevokeTokens(11L, "new-hash", "stale-version", 0L));
            assertEquals(0, mapper.changePasswordAndRevokeTokens(99L, "missing", "unused", 0L));
        }
        assertEquals(List.of("11\tnew-hash\t1", "22\tbob-hash\t0"),
                rows("SELECT id,password,token_version FROM sys_user ORDER BY id"));
    }

    private void seed(String secondEmail) throws SQLException {
        try (var insert = connection.prepareStatement("INSERT INTO sys_user(id,username,email,password,role) "
                + "VALUES(11,'alice','shared@example.com','alice-hash','owner'),(22,'bob',?,'bob-hash','admin')")) {
            insert.setString(1, secondEmail);
            insert.executeUpdate();
        }
        execute("INSERT INTO article(author_id,category_id,title,summary,content_markdown) "
                + "SELECT 22,MIN(id),'title','summary','content' FROM article_category");
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(url, "root", password())
                .locations("classpath:db/migration").target(target).cleanDisabled(true).load();
    }

    private Connection serverConnection() throws SQLException {
        return DriverManager.getConnection(System.getenv("ACOVE_QA_MYSQL_URL"), "root", password());
    }

    private String password() {
        return System.getenv("ACOVE_QA_MYSQL_PASSWORD");
    }

    private void execute(String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private List<String> rows(String sql) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            while (result.next()) {
                List<String> values = new ArrayList<>();
                for (int column = 1; column <= result.getMetaData().getColumnCount(); column++) {
                    values.add(String.valueOf(result.getObject(column)));
                }
                rows.add(String.join("\t", values));
            }
        }
        return rows;
    }
}
