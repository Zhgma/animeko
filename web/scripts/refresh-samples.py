"""Refresh the opt-in, dated public Bangumi sample (no account data)."""
import datetime
import json
import os
from pathlib import Path
import urllib.request

root = Path(__file__).resolve().parents[1] / 'src/wasmJsMain/resources/samples'
root.mkdir(parents=True, exist_ok=True)
(root / 'covers').mkdir(exist_ok=True)
proxy = os.environ.get('HTTPS_PROXY')
opener = urllib.request.build_opener(urllib.request.ProxyHandler({'https': proxy} if proxy else {}))

def get(url):
    return opener.open(urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 AnimeComicWeb/0.1'}), timeout=40).read()

sources = []
for kind in (1, 2):
    url = f'https://api.bgm.tv/v0/subjects?type={kind}&sort=rank&limit=12' + ('&cat=1001&series=true' if kind == 1 else '')
    data = json.loads(get(url))
    items = []
    for s in data['data']:
        cover = s.get('images', {}).get('common', '')
        local_cover = ''
        if cover.startswith('https://lain.bgm.tv/'):
            filename = f'covers/{s["id"]}.jpg'
            (root / filename).write_bytes(get(cover))
            local_cover = 'samples/' + filename
            sources.append({'file': filename, 'url': cover})
        items.append(dict(id=s['id'], type=kind, title=s.get('name_cn') or s['name'], original=s['name'],
            cover=local_cover, score=str(s.get('rating', {}).get('score', '')), rank=s.get('rating', {}).get('rank', 0),
            date=s.get('date') or '', summary=s.get('summary') or '', count=s.get('total_episodes') or s.get('eps') or 0,
            tags=[t['name'] for t in s.get('tags', [])[:10]], sample=True))
    (root / f'{kind}.json').write_text(json.dumps({'items': items}, ensure_ascii=False, indent=2), encoding='utf-8')
    sources.append({'file': f'{kind}.json', 'url': url})
(root / 'sources.json').write_text(json.dumps({'retrieved': str(datetime.date.today()), 'description': 'Public metadata and cover thumbnails from Bangumi; opt-in preview only, not live ranking.', 'sources': sources}, ensure_ascii=False, indent=2), encoding='utf-8')
print(f'Saved {len(sources)} sample assets to {root}')
