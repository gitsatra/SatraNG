#!/usr/bin/env python3
"""Generates res/values/sx_strings.xml, values-fa and values-ru from strings_src/*.tsv.
Each tsv line: name<TAB>english<TAB>persian   (use \\n for newlines). Russian lives in strings_src/ru.txt
(name<TAB>russian) and plurals_ru.txt (name|quantity|russian); every string needs a Russian entry.
Run from repo root: python3 tools/gen_sx_strings.py
"""
import glob, os, sys
from xml.sax.saxutils import escape

root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
src = os.path.join(root, 'tools', 'strings_src')
res = os.path.join(root, 'V2rayNG', 'app', 'src', 'main', 'res')

def esc(s):
    s = escape(s).replace("'", "\\'").replace('"', '\\"')
    s = s.replace('\\\\n', '\\n')
    if s.startswith('@') or s.startswith('?'):
        s = '\\' + s
    return s

entries = []
seen = set()
for f in sorted(glob.glob(os.path.join(src, '*.tsv'))):
    for ln, line in enumerate(open(f, encoding='utf-8'), 1):
        line = line.rstrip('\n')
        if not line.strip() or line.startswith('#'):
            continue
        parts = line.split('\t')
        if len(parts) != 3:
            sys.exit(f'{f}:{ln}: expected 3 tab-separated columns, got {len(parts)}: {line[:60]}')
        name, en, fa = parts
        if name in seen:
            sys.exit(f'duplicate string {name}')
        seen.add(name)
        entries.append((name, en, fa))

ru = {}
rf = os.path.join(src, 'ru.txt')
for ln, line in enumerate(open(rf, encoding='utf-8'), 1):
    line = line.rstrip('\n')
    if not line.strip() or line.startswith('#'): continue
    k, v = line.split('\t')
    ru[k] = v
missing = [e[0] for e in entries if e[0] not in ru]
if missing:
    sys.exit('missing Russian strings: ' + ', '.join(missing))
extra = [k for k in ru if k not in seen]
if extra:
    sys.exit('Russian strings without source: ' + ', '.join(extra))
entries = [(n, e, f, ru[n]) for n, e, f in entries]

def write(path, col):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as out:
        out.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n')
        for e in entries:
            val = e[col]
            fmt = ' formatted="false"' if False else ''
            out.write(f'    <string name="{e[0]}"{fmt}>{esc(val)}</string>\n')
        out.write('</resources>\n')

plurals = []
pf = os.path.join(src, 'plurals.txt')
if os.path.exists(pf):
    # format: name|quantity|english|persian
    for line in open(pf, encoding='utf-8'):
        line = line.rstrip('\n')
        if not line.strip() or line.startswith('#'): continue
        n, q, e, f = line.split('|')
        plurals.append((n, q, e, f))

ru_plurals = {}
rpf = os.path.join(src, 'plurals_ru.txt')
if os.path.exists(rpf):
    for line in open(rpf, encoding='utf-8'):
        line = line.rstrip('\n')
        if not line.strip() or line.startswith('#'): continue
        n, q, v = line.split('|')
        ru_plurals.setdefault(n, []).append((q, v))
for n in {p[0] for p in plurals}:
    if n not in ru_plurals:
        sys.exit('missing Russian plural: ' + n)

def write_plurals(path, col):
    # append plurals before </resources>
    txt = open(path, encoding='utf-8').read()
    names = []
    for p in plurals:
        if p[0] not in names: names.append(p[0])
    block = ''
    for n in names:
        block += f'    <plurals name="{n}">\n'
        if col == 3:
            for q, v in ru_plurals[n]:
                block += f'        <item quantity="{q}">{esc(v)}</item>\n'
        else:
          for p in plurals:
            if p[0] == n:
                block += f'        <item quantity="{p[1]}">{esc(p[2 if col == 1 else 3])}</item>\n'
        block += '    </plurals>\n'
    open(path, 'w', encoding='utf-8').write(txt.replace('</resources>', block + '</resources>'))

write(os.path.join(res, 'values', 'sx_strings.xml'), 1)
write(os.path.join(res, 'values-fa', 'sx_strings.xml'), 2)
write(os.path.join(res, 'values-ru', 'sx_strings.xml'), 3)
write_plurals(os.path.join(res, 'values', 'sx_strings.xml'), 1)
write_plurals(os.path.join(res, 'values-fa', 'sx_strings.xml'), 2)
write_plurals(os.path.join(res, 'values-ru', 'sx_strings.xml'), 3)
print(len(entries), 'strings')
