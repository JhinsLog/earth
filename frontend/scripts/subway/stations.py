# OSM 노선 관계의 정차 위치 → 지도용 역 GeoJSON(frontend/public/data/subway-stations-kr.geojson).
#
# 만드는 법:
#   curl -s --data-urlencode data@stations.overpassql https://overpass-api.de/api/interpreter -o stops.json
#   (overpass-api.de가 붐비거나 406을 내면 같은 쿼리를 https://maps.mail.ru/osm/tools/overpass/api/interpreter 로)
#   python3 -I stations.py stops.json ../../public/data/subway-stations-kr.geojson ../../public/data/subway-kr.geojson
#   (마지막 인자는 build.py로 먼저 만든 노선 파일. 일반역을 노선 위로 붙이는 데 쓴다)
#
# 역 노드(railway=station)가 아니라 노선 관계에 정차 위치(role=stop)로 들어 있는 노드를 쓴다. 역 노드는
# 어느 노선의 역인지 적혀 있지 않은 경우가 많고, 정차 위치는 노선 관계에 직접 매여 있어 노선 색을 알 수 있다.
# 한 역에는 방향·노선마다 정차 위치가 여러 개 있으므로, 이름이 같고 가까운 것을 역 하나로 묶는다.
# 묶인 노선 색이 둘 이상이면 환승역이다. 노선 이름이 아니라 색으로 세는 이유는 1호선처럼 한 노선이 관계 여러 개로
# 나뉘어 있어서다(이름으로 세면 1호선 일반역이 환승역이 된다). 결과물은 ODbL을 따른다(저장소 LICENSE의 THIRD-PARTY 항목).
import json, sys, re, math

src, dst = sys.argv[1], sys.argv[2]
lines_path = sys.argv[3] if len(sys.argv) > 3 else None
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

# 역 점은 상·하행 정차 위치의 평균이라 선로 한가운데지만, 노선은 단순화·가운데 맞추기를 거친 선이라 몇 m씩
# 비킬 수 있다. 확대하면 역과 노선이 어긋나 보이므로, 한 노선만 지나는 역은 가장 가까운 같은 색 노선 위로 붙인다.
# 환승역은 정차 위치 평균에 두면 노선 사이 빈 곳에 뜬다. 정차 위치는 노선끼리 만나는 곳이 아니라 각 노선의 승강장
# 자리라서다(실측: 당산 2·9호선, 샛강, 동작). 노선도처럼 '갈아타는 지점'을 보여 주려고, 역 주변 노선 위의 점 중
# 그 역의 다른 노선들까지 거리 합이 가장 작은 점에 둔다. 노선이 교차하면 교차점, 나란히 달리면 한 노선 위
# 다른 노선에 가장 가까운 자리가 된다. 실제 승강장 위치는 포기한다.
SNAP_METERS = 60
KX = 111_320 * math.cos(math.radians(36))
KY = 110_540
line_segs = {}
if lines_path:
    for f in json.load(open(lines_path))['features']:
        pts = [(lon * KX, lat * KY) for lon, lat in f['geometry']['coordinates']]
        line_segs.setdefault(f['properties']['colour'].lower(), []).extend(zip(pts, pts[1:]))

def snap(lon, lat, colour):
    p = (lon * KX, lat * KY)
    best, best_q = SNAP_METERS, None
    for (ax, ay), (bx, by) in line_segs.get(colour.lower(), ()):
        dx, dy = bx - ax, by - ay
        t = 0 if dx == dy == 0 else max(0, min(1, ((p[0] - ax) * dx + (p[1] - ay) * dy) / (dx * dx + dy * dy)))
        q = (ax + t * dx, ay + t * dy)
        d = math.hypot(p[0] - q[0], p[1] - q[1])
        if d < best:
            best, best_q = d, q
    return (best_q[0] / KX, best_q[1] / KY) if best_q else (lon, lat)

TRANSFER_SEARCH_METERS = 300
CANDIDATE_STEP_METERS = 5

def seg_point_dist(p, a, b):
    dx, dy = b[0] - a[0], b[1] - a[1]
    t = 0 if dx == dy == 0 else max(0, min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / (dx * dx + dy * dy)))
    return math.hypot(p[0] - a[0] - t * dx, p[1] - a[1] - t * dy)

def transfer_point(lon, lat, colours):
    centre = (lon * KX, lat * KY)
    r = TRANSFER_SEARCH_METERS
    near = {}
    for colour in colours:
        segs = [(a, b) for a, b in line_segs.get(colour.lower(), ())
                if seg_point_dist(centre, a, b) < r + 200]
        if segs:
            near[colour] = segs
    if len(near) < 2:  # 주변에 노선이 하나뿐이면 일반역처럼 그 노선 위로
        return snap(lon, lat, next(iter(near), colours[0]))
    best, best_p = math.inf, None
    for colour, segs in near.items():
        for a, b in segs:
            n = max(1, int(math.hypot(b[0] - a[0], b[1] - a[1]) // CANDIDATE_STEP_METERS))
            for k in range(n + 1):
                p = (a[0] + (b[0] - a[0]) * k / n, a[1] + (b[1] - a[1]) * k / n)
                if math.hypot(p[0] - centre[0], p[1] - centre[1]) > r:
                    continue
                cost = sum(min(seg_point_dist(p, sa, sb) for sa, sb in other)
                           for oc, other in near.items() if oc != colour)
                if cost < best:
                    best, best_p = cost, p
    return (best_p[0] / KX, best_p[1] / KY) if best_p else (lon, lat)

features, snapped = [], 0
for c in clusters:
    lon = sum(p[0] for p in c['pts']) / len(c['pts'])
    lat = sum(p[1] for p in c['pts']) / len(c['pts'])
    colours = list(dict.fromkeys(c['lines'].values()))
    # 노선마다 표기가 조금씩 다르면('강남'·'강남역') 가장 많이 쓰인 것을 고른다.
    name = max(set(c['names']), key=lambda n: (c['names'].count(n), -len(n)))
    props = {'name': name, 'colour': colours[0], 'transfer': len(colours) > 1}
    if line_segs:
        new = transfer_point(lon, lat, colours) if props['transfer'] else snap(lon, lat, colours[0])
        snapped += new != (lon, lat)
        lon, lat = new
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
print('stops', len(stops), 'stations', len(features), 'transfers', len(transfers), 'snapped', snapped)
print(sorted(transfers)[:80])
