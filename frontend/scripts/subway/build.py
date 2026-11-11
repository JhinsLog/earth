# OSM 노선 관계 → 지도용 GeoJSON(frontend/public/data/subway-kr.geojson).
#
# 만드는 법:
#   curl -s --data-urlencode data@query.overpassql https://overpass-api.de/api/interpreter -o geom.json
#   python3 -I build.py geom.json ../../public/data/subway-kr.geojson
#
# 결과물은 OpenStreetMap에서 파생한 데이터베이스라 ODbL을 따른다(저장소 LICENSE의 THIRD-PARTY 항목).
# 노선이 새로 열리면 다시 만든다. 지하철로 분류된 노선(subway·light_rail·monorail)과, 수도권 전철처럼
# OSM에서 train으로 분류된 광역전철(network에 전철·도시철도·GTX·광역)을 함께 받는다.
import json, sys, re

src, dst = sys.argv[1], sys.argv[2]
els = json.load(open(src))['elements']
rels = [e for e in els if e['type'] == 'relation']
ways = {e['id']: e for e in els if e['type'] == 'way'}

def line_key(t):
    # 방향·운행 계통별로 나뉜 관계를 노선 하나로 묶는다. ref가 있으면 그것, 없으면 이름에서 방향 표기를 뗀다.
    name = t.get('name', '')
    base = re.split(r'[:(]', name)[0].strip()
    return (t.get('network', ''), t.get('ref') or base)

way_line = {}
# 공항 터미널 안 셔틀은 지하철 노선이 아니다.
rels = [r for r in rels if '셔틀트레인' not in r['tags'].get('name', '')]
for r in sorted(rels, key=lambda r: r['id']):
    t = r['tags']
    colour = t.get('colour')
    # OSM의 colour는 '#00A23F'도 있고 'yellow' 같은 색 이름도 있다. '#' 없는 16진수만 고친다.
    if colour and re.fullmatch(r'[0-9A-Fa-f]{6}', colour):
        colour = '#' + colour
    key = line_key(t)
    name = re.split(r'[:(]', t.get('name', ''))[0].strip()
    for m in r.get('members', []):
        if m['type'] != 'way' or m['ref'] in way_line:
            continue
        way_line[m['ref']] = {'line': name, 'colour': colour or '#9aa4b2', 'key': key}

KEEP = {'subway', 'light_rail', 'monorail', 'rail', 'narrow_gauge'}

def rdp(pts, eps):
    if len(pts) < 3:
        return pts
    (x1, y1), (x2, y2) = pts[0], pts[-1]
    dx, dy = x2 - x1, y2 - y1
    norm = (dx * dx + dy * dy) ** 0.5 or 1e-12
    best, idx = 0, 0
    for i in range(1, len(pts) - 1):
        x, y = pts[i]
        d = abs(dy * x - dx * y + x2 * y1 - y2 * x1) / norm
        if d > best:
            best, idx = d, i
    if best <= eps:
        return [pts[0], pts[-1]]
    return rdp(pts[:idx + 1], eps)[:-1] + rdp(pts[idx:], eps)

features, skipped = [], 0
for wid, info in way_line.items():
    w = ways.get(wid)
    if not w or 'geometry' not in w:
        skipped += 1
        continue
    t = w.get('tags', {})
    if t.get('railway') not in KEEP or t.get('service') in ('yard', 'siding', 'spur', 'crossover'):
        skipped += 1
        continue
    pts = [(round(p['lon'], 5), round(p['lat'], 5)) for p in w['geometry']]
    pts = rdp(pts, 0.00005)
    features.append({
        'type': 'Feature',
        'properties': {'line': info['line'], 'colour': info['colour']},
        'geometry': {'type': 'LineString', 'coordinates': [list(p) for p in pts]},
    })

out = {'type': 'FeatureCollection', 'features': features}
json.dump(out, open(dst, 'w'), ensure_ascii=False, separators=(',', ':'))
lines = sorted({f['properties']['line'] for f in features})
print('features', len(features), 'skipped', skipped, 'lines', len(lines))
print(lines)
