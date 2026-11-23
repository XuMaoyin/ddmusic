#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把本地 byd-voice 分支相对 origin/main 的变更，通过 GitHub Git Data API 提交并快进 main。"""
import json, base64, subprocess, urllib.request, urllib.error, os, sys

TOKEN = os.environ['TOKEN']
API = 'https://api.github.com/repos/XuMaoyin/ddmusic'
BASE_COMMIT = ''   # 运行时从 main ref 解析
HEAD_REF = 'byd-voice'
H = {'Authorization': 'Bearer ' + TOKEN, 'User-Agent': 'dsh',
     'Accept': 'application/vnd.github+json', 'Content-Type': 'application/json'}

def api(method, url, data=None, retries=3):
    for i in range(retries):
        try:
            body = json.dumps(data).encode() if data is not None else None
            req = urllib.request.Request(url, data=body, headers=H, method=method)
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            print('HTTP', e.code, url, e.read()[:300]); raise
        except Exception as e:
            print('retry', i, type(e).__name__, e)
            if i == retries - 1: raise

def git(*args):
    return subprocess.run(['git'] + list(args), capture_output=True, check=True).stdout

# 解析远端 main 完整 sha
if not BASE_COMMIT:
    BASE_COMMIT = api('GET', f'{API}/git/ref/heads/main')['object']['sha']
print('base sha:', BASE_COMMIT[:7])

# 变更列表
out = git('diff', '--name-status', '-M', BASE_COMMIT, HEAD_REF).decode()
if not out.strip(): out = ''
changes = []  # (status, path, old_path)
for line in out.strip().split('\n'):
    parts = line.split('\t')
    st = parts[0][0]
    if st == 'R':
        changes.append(('D', parts[1]))
        changes.append(('A', parts[2]))
    elif st in 'AMD':
        changes.append((st, parts[1]))

# 模式变化（gradlew 644->755）：git diff --raw 找
raw = git('diff', '--raw', BASE_COMMIT, HEAD_REF).decode()
modes = {}
for line in raw.strip().split('\n'):
    f = line.split()
    if len(f) >= 5 and f[0].startswith(':'):
        old_mode = f[0][1:]          # 例 :100644
        new_mode = f[1]              # 新 mode 在第 2 列
        path = f[-1]
        if new_mode != '100644':
            modes[path] = new_mode

# base commit / tree
commit = api('GET', f'{API}/git/commits/{BASE_COMMIT}')
base_tree = commit['tree']['sha']
print('base commit:', commit['sha'][:7], 'tree:', base_tree[:7])

# 上传 blobs（A/M）
tree_items = []
for st, path in changes:
    if st == 'D':
        tree_items.append({'path': path, 'mode': '100644', 'type': 'blob', 'sha': None})
        continue
    content = git('show', HEAD_REF + ':' + path)
    blob = api('POST', f'{API}/git/blobs', {
        'content': base64.b64encode(content).decode(), 'encoding': 'base64'})
    mode = modes.get(path, '100644')
    tree_items.append({'path': path, 'mode': mode, 'type': 'blob', 'sha': blob['sha']})
    print('blob', st, path, blob['sha'][:7])

import collections
bad=[t for t in tree_items if t.get('mode') not in ('100644','100755','040000','120000','160000')]
print('bad mode entries:', bad[:5])
print('mode counts:', collections.Counter(str(t.get('mode')) for t in tree_items))
print('null-sha entries:', sum(1 for t in tree_items if t.get('sha') is None))
tree = api('POST', f'{API}/git/trees', {'base_tree': base_tree, 'tree': tree_items})
print('tree:', tree['sha'][:7])

msg = git('log', '-1', '--format=%B', HEAD_REF).decode().strip()
author = {'name': 'XuMaoyin', 'email': 'xumaoyin@users.noreply.github.com'}
new_commit = api('POST', f'{API}/git/commits', {
    'message': msg, 'tree': tree['sha'], 'parents': [commit['sha']], 'author': author,
    'committer': author})
print('commit:', new_commit['sha'][:7])

ref = api('PATCH', f'{API}/git/refs/heads/main', {'sha': new_commit['sha'], 'force': False})
print('main ->', ref['object']['sha'][:7])
