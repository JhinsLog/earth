# OSM 노선 관계의 정차 위치 → 지도용 역 GeoJSON(frontend/public/data/subway-stations-kr.geojson).
#
# 만드는 법:
#   curl -s --data-urlencode data@stations.overpassql https://overpass-api.de/api/interpreter -o stops.json
#   (overpass-api.de가 붐비거나 406을 내면 같은 쿼리를 https://maps.mail.ru/osm/tools/overpass/api/interpreter 로)
#   python3 -I stations.py stops.json ../../public/data/subway-stations-kr.geojson
#
# 역 노드(railway=station)가 아니라 노선 관계에 정차 위치(role=stop)로 들어 있는 노드를 쓴다. 역 노드는
# 어느 노선의 역인지 적혀 있지 않은 경우가 많고, 정차 위치는 노선 관계에 직접 매여 있어 노선 색을 알 수 있다.
# 한 역에는 방향·노선마다 정차 위치가 여러 개 있으므로, 이름이 같고 가까운 것을 역 하나로 묶는다.
# 묶인 노선 색이 둘 이상이면 환승역이다. 노선 이름이 아니라 색으로 세는 이유는 1호선처럼 한 노선이 관계 여러 개로
# 나뉘어 있어서다(이름으로 세면 1호선 일반역이 환승역이 된다). 결과물은 ODbL을 따른다(저장소 LICENSE의 THIRD-PARTY 항목).
import json, sys, re, math

src, dst = sys.argv[1], sys.argv[2]
els = json.load(open(src))['elements']
nodes = {e['id']: e for e in els if e['type'] == 'node'}
rels = [e for e in els if e['type'] == 'relation' and '셔틀트레인' not in e['tags'].get('name', '')]

# 같은 이름이라도 이만큼 떨어져 있으면 다른 역이다(예: 서울 2호선 '신촌'과 경의선 '신촌'은 약 400m).
# 환승 통로로 이어진 역은 정차 위치끼리 보통 수백 m 안이다.
SAME_STATION_METERS = 700

def line_name(t):
    return re.split(r'[:(]', t.get('name', ''))[0].strip()

def colour_of(t):
    c = t.get('colour')
    if c and re.fullmatch(r'[0-9A-Fa-f]{6}', c):
        c = '#' + c
    return c or '#9aa4b2'

def station_key(name):
    # '강남역'과 '강남'을 같은 역으로 묶는 비교용 키. 표시는 원래 이름으로 한다('서울역'은 이름 자체가 '서울역').
    name = re.sub(r'\s*\(.*\)$', '', name).strip()
    return name[:-1] if name.endswith('역') and len(name) > 2 else name

stops = []  # (비교 키, 이름, 영문 이름, 경도, 위도, 노선, 색)
for r in rels:
    t = r['tags']
    for m in r.get('members', []):
        if m['type'] != 'node' or not m['role'].startswith('stop'):
            continue
        n = nodes.get(m['ref'])
        if not n:
            continue
        nt = n.get('tags', {})
        # 공사 중인 역은 아직 탈 수 없다.
        if nt.get('railway') == 'construction' or 'construction' in nt.get('public_transport', ''):
            continue
        name = nt.get('name')
        if not name:
            continue
        stops.append((station_key(name), name.strip(), nt.get('name:en'), n['lon'], n['lat'], line_name(t), colour_of(t)))

def meters(a, b):
    lat = math.radians((a[1] + b[1]) / 2)
    dx = (a[0] - b[0]) * 111_320 * math.cos(lat)
    dy = (a[1] - b[1]) * 110_540
    return math.hypot(dx, dy)

clusters = []  # {'key', 'names', 'name_en', 'pts', 'lines': {노선: 색}}
for key, name, name_en, lon, lat, line, colour in stops:
    for c in clusters:
        if c['key'] == key and any(meters((lon, lat), p) < SAME_STATION_METERS for p in c['pts']):
            break
    else:
        c = {'key': key, 'names': [], 'name_en': None, 'pts': [], 'lines': {}}
        clusters.append(c)
    c['names'].append(name)
    c['pts'].append((lon, lat))
    c['lines'].setdefault(line, colour)
    c['name_en'] = c['name_en'] or name_en

features = []
for c in clusters:
    lon = sum(p[0] for p in c['pts']) / len(c['pts'])
    lat = sum(p[1] for p in c['pts']) / len(c['pts'])
    colours = list(dict.fromkeys(c['lines'].values()))
    # 노선마다 표기가 조금씩 다르면('강남'·'강남역') 가장 많이 쓰인 것을 고른다.
    name = max(set(c['names']), key=lambda n: (c['names'].count(n), -len(n)))
    props = {'name': name, 'colour': colours[0], 'transfer': len(colours) > 1}
    if c['name_en']:
        # 영문 이름에도 'Station'이 붙은 경우가 있어 뗀다.
        props['name_en'] = re.sub(r'\s+Station$', '', c['name_en'])
    features.append({
        'type': 'Feature',
        'properties': props,
        'geometry': {'type': 'Point', 'coordinates': [round(lon, 5), round(lat, 5)]},
    })

json.dump({'type': 'FeatureCollection', 'features': features}, open(dst, 'w'),
          ensure_ascii=False, separators=(',', ':'))
transfers = [f['properties']['name'] for f in features if f['properties']['transfer']]
print('stops', len(stops), 'stations', len(features), 'transfers', len(transfers))
print(sorted(transfers)[:80])
