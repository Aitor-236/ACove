"""Live regression against a disposable ACove database and Nginx deployment.

Requires QA_ENV_FILE (Docker-style env file), QA_DB_PORT and QA_SITE_URL.
Refuses databases whose name does not end in _qa. Credentials stay in /tmp.
"""
import base64
import hashlib
import hmac
import json
import os
from pathlib import Path
import secrets
import subprocess
import time
import urllib.error
import urllib.request
from html.parser import HTMLParser

import bcrypt

ENV = dict(line.split('=', 1) for line in Path(os.environ['QA_ENV_FILE']).read_text().splitlines() if '=' in line)
DB = ENV['MYSQL_DATABASE']
assert DB.endswith('_qa'), 'Only disposable QA databases are allowed'
BASE = os.environ.get('QA_SITE_URL', 'http://127.0.0.1:18081').rstrip('/')
DB_PORT = os.environ.get('QA_DB_PORT', '13316')
results = []


def sql(statement):
    env = {**os.environ, 'MYSQL_PWD': ENV['MYSQL_ROOT_PASSWORD']}
    return subprocess.check_output(['mysql', '--host=127.0.0.1', f'--port={DB_PORT}', '--user=root',
                                    '--default-character-set=utf8mb4', '--batch', '--skip-column-names', DB,
                                    '--execute', statement], env=env, text=True).strip()


def check(label, condition):
    assert condition, label
    results.append(label)
    print('PASS', label, flush=True)


def call(path, payload=None, token=None, expected=200, http=200, raw=None, content_type=None):
    headers = {}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    data = raw
    if payload is not None:
        data = json.dumps(payload).encode()
        headers['Content-Type'] = 'application/json'
    if content_type:
        headers['Content-Type'] = content_type
    request = urllib.request.Request(BASE + '/api' + path, data=data, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            status, body = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, body = error.code, error.read()
    result = json.loads(body)
    assert (status, result['code']) == (http, expected), (path, status, result)
    return result.get('data')


class Head(HTMLParser):
    def __init__(self, text):
        super().__init__(convert_charrefs=True)
        self.titles = []
        self.meta = {}
        self.canonical = None
        self.in_title = False
        self.feed(text)

    def handle_starttag(self, tag, attributes):
        attrs = dict(attributes)
        if tag == 'title':
            self.in_title = True
            self.titles.append('')
        if tag == 'meta':
            self.meta[attrs.get('property', attrs.get('name'))] = attrs.get('content')
        if tag == 'link' and attrs.get('rel') == 'canonical':
            self.canonical = attrs.get('href')

    def handle_endtag(self, tag):
        if tag == 'title':
            self.in_title = False

    def handle_data(self, data):
        if self.in_title:
            self.titles[-1] += data


def page(path):
    with urllib.request.urlopen(BASE + path, timeout=10) as response:
        text = response.read().decode()
        assert 'no-cache' in response.headers.get('Cache-Control', ''), response.headers
    assert '<!--#' not in text, 'SSI instructions must be consumed by Nginx'
    parsed = Head(text)
    assert len(parsed.titles) == 1, parsed.titles
    return parsed, text


def login(username, password, expected=200):
    result = call('/auth/login', {'username': username, 'password': password}, expected=expected)
    return result['token'] if result else None


def signed(payload):
    encode = lambda value: base64.urlsafe_b64encode(json.dumps(value, separators=(',', ':')).encode()).rstrip(b'=')
    body = encode({'alg': 'HS256', 'typ': 'JWT'}) + b'.' + encode(payload)
    signature = base64.urlsafe_b64encode(hmac.new(ENV['BLOG_JWT_SECRET'].encode(), body, hashlib.sha256).digest()).rstrip(b'=')
    return (body + b'.' + signature).decode()


def main():
    check('Flyway V1–V10 applied', sql("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL") == '10')
    owner, other = 'qa_owner_20261027', 'qa_other_20261027'
    old_password, new_password = secrets.token_hex(18), secrets.token_hex(18)
    other_password = secrets.token_hex(18)
    for username, password, role in [(owner, old_password, 'owner'), (other, other_password, 'admin')]:
        hashed = bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode()
        sql(f"INSERT INTO sys_user(username,email,password,role) VALUES('{username}','{username}@example.test','{hashed}','{role}')")
    owner_id = int(sql(f"SELECT id FROM sys_user WHERE username='{owner}'"))
    owner_token, other_token = login(owner, old_password), login(other, other_password)
    call('/admin/user/profile', token=owner_token)
    call('/admin/user/password', {'currentPassword': 'wrong-password', 'newPassword': new_password}, owner_token, expected=400)
    call('/admin/user/profile', token=owner_token)
    check('failed password change preserves sessions', sql(f'SELECT token_version FROM sys_user WHERE id={owner_id}') == '0')
    call('/admin/user/password', {'currentPassword': old_password, 'newPassword': new_password}, owner_token)
    call('/admin/user/profile', token=owner_token, expected=401, http=401)
    login(owner, old_password, expected=401)
    owner_token = login(owner, new_password)
    call('/admin/user/profile', token=owner_token)
    check('password change revokes old token and allows fresh login', sql(f'SELECT token_version FROM sys_user WHERE id={owner_id}') == '1')
    call('/admin/user/profile', token=other_token)
    check('another user session remains valid', True)
    for payload in [
        {'userId': owner_id, 'username': owner, 'exp': int(time.time()) + 300},
        {'userId': 99999999, 'tokenVersion': 0, 'exp': int(time.time()) + 300},
        {'userId': owner_id, 'tokenVersion': 1},
    ]:
        call('/admin/article/list', token=signed(payload), expected=401, http=401)
    check('legacy, deleted-user and expiry-free tokens are rejected', True)

    first = call('/admin/category/create', {'categoryName': 'QA同名分类', 'categoryIdentifier': 'qa-category-first'}, owner_token)
    second = call('/admin/category/create', {'categoryName': 'QA同名分类', 'categoryIdentifier': 'qa-category-second'}, owner_token)
    article = call('/admin/article/create', {'title': 'QA身份检查', 'summary': '摘要', 'content': '正文', 'categoryId': second['id']}, owner_token)
    aid = article['id']
    check('duplicate-name category creation persists the selected ID', sql(f'SELECT category_id FROM article WHERE id={aid}') == str(second['id']))
    check('article author comes from authenticated user', sql(f'SELECT author_id FROM article WHERE id={aid}') == str(owner_id))
    call('/admin/article/update', {'id': aid, 'content': '局部更新'}, owner_token)
    check('partial article update preserves category', sql(f'SELECT category_id FROM article WHERE id={aid}') == str(second['id']))
    call('/admin/article/update', {'id': aid, 'categoryId': first['id']}, owner_token)
    check('category ID switches precisely', sql(f'SELECT category_id FROM article WHERE id={aid}') == str(first['id']))
    for data in [{'categoryId': 99999999, 'categoryName': 'QA同名分类'}, {'categoryName': 'QA同名分类'}]:
        call('/admin/article/update', {'id': aid, **data}, owner_token, expected=400)
    check('invalid ID and ambiguous legacy category name are rejected', True)
    call('/admin/article/update', {'id': aid, 'categoryName': 'qa-category-second'}, owner_token)
    check('legacy slug input remains compatible', sql(f'SELECT category_id FROM article WHERE id={aid}') == str(second['id']))

    site_name = 'QA <星> & "自定义站点"'
    call('/admin/site/settings/update', {'siteName': site_name, 'heroText': 'QA 首页摘要', 'heroImage': '/uploads/qa-cover.png'}, owner_token)
    title, summary = 'QA 文章 <标题> & "引号"', 'QA 摘要 <内容> & "引号"'
    call('/admin/article/update', {'id': aid, 'title': title, 'summary': summary}, owner_token)
    call(f'/admin/article/publish?id={aid}', {}, owner_token)
    public = call('/article/list?category=qa-category-second')
    check('public article cards expose stable category slug', public['records'][0]['categorySlug'] == 'qa-category-second')
    home, _ = page('/')
    check('no-JS homepage uses custom site name and summary', home.titles == [site_name] and home.meta['description'] == 'QA 首页摘要')
    detail, text = page(f'/articles/{aid}?from=share&keyword=a%26b')
    check('no-JS article title and description are escaped correctly', detail.titles == [title + ' | ' + site_name] and detail.meta['description'] == summary and '<标题>' not in text)
    check('article canonical retains its original path and drops tracking query', detail.canonical == BASE + f'/articles/{aid}')
    check('Open Graph metadata and absolute share image are present', detail.meta['og:type'] == 'article' and detail.meta['og:site_name'] == site_name and detail.meta['og:image'] == BASE + '/api/uploads/qa-cover.png')
    gallery = call('/admin/gallery/create', {'name': 'QA 图库 <名字>', 'description': 'QA 图库摘要 & 照片'}, owner_token)
    gallery_head, _ = page(f"/gallery/{gallery['id']}")
    check('no-JS gallery uses its title and description', gallery_head.titles == ['QA 图库 <名字> | ' + site_name] and gallery_head.meta['description'] == 'QA 图库摘要 & 照片')
    draft = call('/admin/article/create', {'title': 'QA_SECRET_DRAFT_TITLE', 'summary': 'QA_SECRET_DRAFT_SUMMARY', 'content': '正文', 'categoryId': second['id']}, owner_token)
    for path in [f"/articles/{draft['id']}", '/articles/99999999', '/gallery/99999999', '/unknown-qa-route', '/login', '/admin/articles']:
        head, text = page(path)
        check('private/missing route is noindex: ' + path, head.meta['robots'].startswith('noindex') and 'QA_SECRET_DRAFT' not in text and head.canonical is None)
    direct = urllib.request.Request(BASE + '/_page-head')
    try:
        urllib.request.urlopen(direct)
        raise AssertionError('internal SSI endpoint was publicly accessible')
    except urllib.error.HTTPError as error:
        check('internal SSI location is not publicly accessible', error.code == 404)

    # Seed enough real published records for page-three UI/back-navigation checks.
    for index in range(1, 19):
        record = call('/admin/article/create', {'title': f'QA状态恢复 {index:02}', 'summary': 'QA状态恢复列表摘要', 'content': '# QA 正文', 'categoryId': second['id'], 'tags': ['QA恢复']}, owner_token)
        call(f"/admin/article/publish?id={record['id']}", {}, owner_token)
    credentials = {'username': owner, 'password': new_password, 'token': owner_token, 'articleId': aid, 'galleryId': gallery['id'], 'siteName': site_name}
    path = Path('/tmp/acove-defect-users.json')
    path.write_text(json.dumps(credentials, ensure_ascii=False))
    path.chmod(0o600)
    check('page-three dataset and temporary browser credentials prepared', call('/article/list?keyword=QA%E7%8A%B6%E6%80%81%E6%81%A2%E5%A4%8D&page=3&size=6')['total'] == 18)
    print(json.dumps({'passed': len(results), 'checks': results}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
