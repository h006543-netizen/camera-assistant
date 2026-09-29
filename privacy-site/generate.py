"""Generate static policy pages and matching Android fallback content.

No network requests. Contact details must be supplied before publication.
"""
import json
from html import escape
from pathlib import Path
from urllib.parse import quote

ROOT = Path(__file__).resolve().parent
CONFIG = json.loads((ROOT / 'policy-config.json').read_text(encoding='utf-8'))
CONTENT = json.loads((ROOT / 'content.json').read_text(encoding='utf-8'))
READY = bool(CONFIG['operator_name'] and CONFIG['contact_email'])
if CONFIG['published'] and not READY:
    raise ValueError('Operator name and contact email are required before publication.')

links = {
    'google': 'https://policies.google.com/privacy',
    'ar': 'https://developers.google.com/ar/develop/play-safety-label',
}
svg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"><rect width="32" height="32" rx="6" fill="#15191f"/><rect x="5" y="9" width="22" height="16" rx="3" fill="#285ab8"/><circle cx="16" cy="17" r="5" fill="none" stroke="white" stroke-width="2"/><path d="M10 9V6h8v3" fill="none" stroke="white" stroke-width="2"/></svg>'
assets = ROOT.parent / 'app/src/main/assets/privacy'
assets.mkdir(parents=True, exist_ok=True)
for lang, data in CONTENT.items():
    brand = 'ShutterNote'
    body = f'<h1>{escape(data["title"])}</h1>'
    body += f'<p class="date">{escape(data["date_label"])}: {escape(CONFIG["effective_date"])}</p>'
    if not READY:
        body += f'<p class="draft"><strong>{escape(data["draft"])}</strong></p>'
    body += f'<p class="intro">{escape(data["intro"])}</p>'
    for heading, paragraphs in data['sections']:
        body += f'<section><h2>{escape(heading)}</h2>'
        body += ''.join(f'<p>{escape(p)}</p>' for p in paragraphs)
        body += '</section>'
    body += '<p>' + ' · '.join(f'<a href="{links[key]}">{escape(data[key + "_label"])}</a>' for key in links) + '</p>'
    body += f'<section><h2>{escape(data["contact_title"])}</h2><div class="contact">'
    if READY:
        name, email = escape(CONFIG['operator_name']), escape(CONFIG['contact_email'])
        body += f'<p>{escape(data["operator_label"])}: {name}</p><p>{escape(data["email_label"])}: <a href="mailto:{quote(CONFIG["contact_email"], safe="@.+")}">{email}</a></p>'
    else:
        body += f'<p>{escape(data["contact_pending"])}</p>'
    body += '</div></section>'
    nav = ''.join(f'<a href="{("../" if lang != "ko" else "") + ("index.html" if code == "ko" else code + "/index.html")}" lang="{code}" hreflang="{code}"' + (' aria-current="page"' if lang == code else '') + f'>{label}</a>' for code, label in [('ko','한국어'),('en','English'),('fr','Français'),('ja','日本語')])
    css_path = 'styles.css' if lang == 'ko' else '../styles.css'
    robots = '' if READY else '<meta name="robots" content="noindex">'
    page = f'''<!doctype html>
<html lang="{lang}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>{escape(data['title'])} | {brand}</title><meta name="description" content="{escape(data['intro'], quote=True)}">{robots}
<link rel="icon" type="image/svg+xml" href="data:image/svg+xml,{quote(svg)}"><link rel="stylesheet" href="{css_path}"></head>
<body><header class="masthead"><div><span class="brand">{brand}</span><nav aria-label="{escape(data['language_label'])}">{nav}</nav></div></header>
<main>{body}</main><footer>{brand} · {escape(data['title'])}</footer></body></html>
'''
    out = ROOT / 'dist' / ('' if lang == 'ko' else lang)
    out.mkdir(parents=True, exist_ok=True)
    (out / 'index.html').write_text(page, encoding='utf-8')
    (assets / f'{lang}.html').write_text(body, encoding='utf-8')

res = ROOT.parent / 'app/src/main/res/values/privacy_config.xml'
res.write_text('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
    + f'    <string name="privacy_policy_base_url" translatable="false">{escape(CONFIG["public_url"])}</string>\n'
    + f'    <bool name="privacy_policy_published">{str(CONFIG["published"]).lower()}</bool>\n</resources>\n', encoding='utf-8')
print('Generated 4 policy pages and Android fallback assets. Contact ready:', READY, 'Published:', CONFIG['published'])
