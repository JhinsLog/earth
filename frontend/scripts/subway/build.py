# OSM 노선 관계 → 지도용 GeoJSON(frontend/public/data/subway-kr.geojson).
#
# 만드는 법:
#   curl -s --data-urlencode data@query.overpassql https://overpass-api.de/api/interpreter -o geom.json
#   python3 -I build.py geom.json ../../public/data/subway-kr.geojson
#
# 결과물은 OpenStreetMap에서 파생한 데이터베이스라 ODbL을 따른다(저장소 LICENSE의 THIRD-PARTY 항목).
# 노선이 새로 열리면 다시 만든다. 지하철로 분류된 노선(subway·light_rail·monorail)과, 수도권 전철처럼
# OSM에서 train으로 분류된 광역전철(network에 전철·도시철도·GTX·광역)을 함께 받는다.
import json, sys, re, math

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

# 같은 노선인데 관계 일부에만 colour가 적힌 경우가 있다(실측: 광주 도시철도 1호선). 이름이 같은 관계의 색을 쓴다.
# stations.py도 같은 규칙을 써야 역과 노선의 색이 같아진다.
colour_by_name = {}
for r in rels:
    c = r['tags'].get('colour')
    if c:
        colour_by_name.setdefault(re.split(r'[:(]', r['tags'].get('name', ''))[0].strip(),
                                  '#' + c if re.fullmatch(r'[0-9A-Fa-f]{6}', c) else c)
# 공항 터미널 안 셔틀은 지하철 노선이 아니다.
rels = [r for r in rels if '셔틀트레인' not in r['tags'].get('name', '')]

# 노선 관계는 보통 방향마다 하나씩(상행·하행) 있고, 각자 자기 선로를 따라간다. 둘 다 그리면 복선 구간이
# 확대했을 때 두 줄(선로 간격 10~20m)로 보인다. 노선도처럼 한 줄로 보이도록 반대 방향 짝 중 하나를 먼저 그리고,
# 다른 하나는 뒤로 미룬다. 버리지는 않는다 — 한 방향 관계에 선로가 빠져 있거나 두 방향 선로가 따로 떨어진 곳이
# 있어서다(실측: 버리면 GTX-A 수서~성남 1km, 대구 3호선 540m 등이 사라짐). 뒤로 미룬 쪽은 아래 겹침 자르기에서
# 먼저 그린 선로 곁 구간이 잘리고 혼자 지나는 구간만 남는다.
# 짝은 같은 노선(색)에서 출발·도착이 서로 뒤바뀐 관계다. 2호선 순환선처럼 출발·도착이 없는 순환 관계는 내선을 미룬다.
def norm_stop(name):
    return re.sub(r'역$', '', (name or '').strip())

def colour_key(t):
    return (t.get('colour') or '').lstrip('#').lower()

seen_dirs, primary = set(), set()
for r in sorted(rels, key=lambda r: r['id']):
    t = r['tags']
    if '내선' in t.get('name', '') and not t.get('from'):
        continue
    a, b = norm_stop(t.get('from')), norm_stop(t.get('to'))
    if a and b and (colour_key(t), b, a) in seen_dirs:
        continue
    seen_dirs.add((colour_key(t), a, b))
    primary.add(r['id'])
print('relations', len(rels), 'drawn first', len(primary))
for r in sorted(rels, key=lambda r: (r['id'] not in primary, r['id'])):
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
        way_line[m['ref']] = {'line': name, 'colour': colour or colour_by_name.get(name, '#9aa4b2'), 'key': key,
                              'first': r['id'] in primary}

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
    # 차량기지 선로(yard)와 선로를 건너가는 짧은 연결선(crossover)은 노선이 아니다. 측선(siding)은 남긴다 — 노선
    # 관계에 든 측선은 대부분 열차가 실제로 서는 역 선로다(GTX-A는 성남역에서 본선을 벗어나 측선으로 정차해,
    # 빼면 성남역 앞뒤 약 1.4km가 끊겼다). 본선 곁 측선은 아래 겹침 자르기에서 본선에 합쳐진다.
    if t.get('railway') not in KEEP or t.get('service') in ('yard', 'spur', 'crossover'):
        skipped += 1
        continue
    pts = [(round(p['lon'], 5), round(p['lat'], 5)) for p in w['geometry']]
    pts = rdp(pts, 0.00005)
    features.append({
        'type': 'Feature',
        'properties': {'line': info['line'], 'colour': info['colour']},
        'first': info['first'],
        'src': wid,
        'siding': t.get('service') == 'siding',
        'geometry': {'type': 'LineString', 'coordinates': [list(p) for p in pts]},
    })

# 복선 구간은 OSM에 상행·하행 선로가 따로 그려져 있어, 확대하면 같은 노선이 두 줄(약 10~20m 간격)로 보인다.
# 노선도처럼 한 줄로 보이도록, 같은 노선(같은 색)에서 이미 남긴 선로 곁을 지나는 구간은 잘라 낸다.
# 선로를 통째로 빼지 않고 구간 단위로 자르므로, 곁에 다른 선로가 없는 구간(지선·분기·단선)은 그대로 남아 틈이 생기지 않는다.
# 먼저 그릴 방향의 선로부터, 그 안에서는 긴 선로부터 남기므로 본선이 남는다.
TWIN_METERS = 40       # 이 거리 안이면 같은 선로 묶음으로 본다(복선 간격 + 여유). 60m로 넓히면 GTX-A 수서 부근(상하행 터널 약 50m)도
                       # 한 줄이 되지만, 분기마다 이음선이 길어져 네모난 꺾임이 생긴다(실측: 신도림·구로)
SAMPLE_METERS = 20     # 선로를 이 간격으로 잘라 본다(단순화로 점이 듬성듬성해서)
CELL_METERS = 100      # 가까운 선분만 찾으려고 쓰는 격자 한 칸
KX = 111_320 * math.cos(math.radians(36))  # 국내 위도에서 경도 1도의 길이(m). 이 용도에는 충분히 정확하다
KY = 110_540

def densify(coords):
    pts = [(lon * KX, lat * KY) for lon, lat in coords]
    out = [pts[0]]
    for (x1, y1), (x2, y2) in zip(pts, pts[1:]):
        n = max(1, int(math.hypot(x2 - x1, y2 - y1) // SAMPLE_METERS))
        out += [(x1 + (x2 - x1) * k / n, y1 + (y2 - y1) * k / n) for k in range(1, n + 1)]
    return out

def seg_dist(p, seg):
    # 남긴 선로의 끝점 너머는 덮인 것으로 치지 않는다. 치면 한쪽 선로가 짧게 끊긴 곳(OSM에서 빠진 구간 등)에서
    # 반대쪽 선로도 끝점 반경만큼 잘려 노선에 틈이 생긴다(실측: 구로·금정 분기에서 약 40m 틈).
    (px, py), (ax, ay), (bx, by), a_end, b_end = p, *seg
    dx, dy = bx - ax, by - ay
    t = 0 if dx == dy == 0 else ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)
    if (t < 0 and a_end) or (t > 1 and b_end):
        return math.inf
    t = max(0, min(1, t))
    return math.hypot(px - ax - t * dx, py - ay - t * dy)

def cell(p):
    return int(p[0] // CELL_METERS), int(p[1] // CELL_METERS)

# 역 측선은 본선보다 넓게 벌어진다(GTX-A 성남역: 상·하행 측선 약 50m 간격). 측선은 이 거리까지 겹침으로 본다.
SIDING_TWIN_METERS = 80

def near_kept(grid, p, limit=TWIN_METERS):
    cx, cy = cell(p)
    # 칸(100m)보다 먼 거리는 보지 않으므로 limit은 CELL_METERS를 넘지 않아야 한다
    return any(seg_dist(p, seg) < limit
               for i in (-1, 0, 1) for j in (-1, 0, 1) for seg in grid.get((cx + i, cy + j), ()))

def register(grid, pts):
    # 점 간격(SAMPLE_METERS)이 칸보다 작으므로 각 선분은 양 끝 점의 칸에만 넣어도 빠지는 칸이 없다.
    last = len(pts) - 2
    for k, (a, b) in enumerate(zip(pts, pts[1:])):
        for c in {cell(a), cell(b)}:
            grid.setdefault(c, []).append((a, b, k == 0, k == last))

def length(coords):
    return sum(math.hypot((a[0] - b[0]) * KX, (a[1] - b[1]) * KY) for a, b in zip(coords, coords[1:]))

originals = list(features)
kept, grids, cut = [], {}, 0
for f in sorted(features, key=lambda f: (not f['first'], -length(f['geometry']['coordinates']))):
    grid = grids.setdefault(f['properties']['colour'], {})
    pts = densify(f['geometry']['coordinates'])
    limit = SIDING_TWIN_METERS if f['siding'] else TWIN_METERS
    free = [not near_kept(grid, p, limit) for p in pts]
    if not all(free):
        cut += 1
    # 곁에 남긴 선로가 없는 점이 이어진 구간마다 한 조각. 앞뒤로 한 점씩 늘려 남긴 선로와 이어 붙인다.
    i = 0
    while i < len(pts):
        if not free[i]:
            i += 1
            continue
        j = i
        while j + 1 < len(pts) and free[j + 1]:
            j += 1
        piece = pts[max(0, i - 1):min(len(pts), j + 2)]
        if len(piece) >= 2:
            coords = rdp([(round(x / KX, 5), round(y / KY, 5)) for x, y in piece], 0.00005)
            kept.append({**f, 'geometry': {'type': 'LineString', 'coordinates': [list(c) for c in coords]}})
            register(grid, piece)
        i = j + 1
# 승강장처럼 선로 간격이 TWIN_METERS보다 잠깐 넓어지는 곳에서는 짧은 조각이 남아, 이어 붙이면 좁은 고리가 된다.
# 다른 선로 곁(SIDE_METERS 안)에만 있는 짧은 조각은 뺀다. 짧은 것부터 보고, 뺀 조각은 다음 판정에서 제외한다.
SIDE_METERS = 80
SHORT_METERS = 500

def piece_grid(pieces):
    g = {}
    for f in pieces:
        register(g, [(lon * KX, lat * KY) for lon, lat in f['geometry']['coordinates']])
    return g

removed = set()
by_colour = {}
for k, f in enumerate(kept):
    by_colour.setdefault(f['properties']['colour'], []).append(k)
for colour, idxs in by_colour.items():
    for k in sorted(idxs, key=lambda k: length(kept[k]['geometry']['coordinates'])):
        coords = kept[k]['geometry']['coordinates']
        if length(coords) >= SHORT_METERS:
            continue
        others = piece_grid(kept[m] for m in idxs if m != k and m not in removed)
        pts = densify(coords)
        def side(p):
            cx, cy = cell(p)
            # 끝점 너머를 빼는 규칙(seg_dist)을 여기서도 지킨다. 안 지키면 두 조각 사이를 잇는 짧은 조각이
            # 양 끝 조각 "곁"으로 잡혀 지워지고, 그 자리에 틈이 생긴다.
            return any(seg_dist(p, seg) < SIDE_METERS
                       for i in (-1, 0, 1) for j in (-1, 0, 1) for seg in others.get((cx + i, cy + j), ()))
        if all(side(p) for p in pts):
            removed.add(k)
kept = [f for k, f in enumerate(kept) if k not in removed]
print('short side pieces removed', len(removed))

# 한 선로만 남기면 노선이 두 선로의 한가운데가 아니라 한쪽(선로 간격의 절반, 5~10m)으로 비켜 있다. 역 점은
# 상·하행 정차 위치의 평균이라 한가운데에 있으므로, 확대하면 역과 노선이 어긋나 보인다. 남긴 선로의 각 점을
# 곁의 다른 선로(잘라 낸 쪽 포함, 원래 선로 전부)와의 중간으로 옮긴다. 선로가 갈라지는 곳에서 갑자기 꺾이지 않도록
# CENTER_FULL_METERS~TWIN_METERS 사이에서는 옮기는 양을 거리에 따라 줄인다.
CENTER_FULL_METERS = 25

def nearest_point(grid, p, skip_src):
    best, best_q = TWIN_METERS, None
    cx, cy = cell(p)
    for i in (-1, 0, 1):
        for j in (-1, 0, 1):
            for (a, b, a_end, b_end, src) in grid.get((cx + i, cy + j), ()):
                if src == skip_src:
                    continue
                dx, dy = b[0] - a[0], b[1] - a[1]
                t = 0 if dx == dy == 0 else ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / (dx * dx + dy * dy)
                if (t < 0 and a_end) or (t > 1 and b_end):
                    continue
                t = max(0, min(1, t))
                q = (a[0] + t * dx, a[1] + t * dy)
                d = math.hypot(p[0] - q[0], p[1] - q[1])
                if d < best:
                    best, best_q = d, q
    return best, best_q

all_tracks = {}
for f in originals:
    pts = densify(f['geometry']['coordinates'])
    grid = all_tracks.setdefault(f['properties']['colour'], {})
    last = len(pts) - 2
    for k, (a, b) in enumerate(zip(pts, pts[1:])):
        for c in {cell(a), cell(b)}:
            grid.setdefault(c, []).append((a, b, k == 0, k == last, f['src']))

moved = 0
for f in kept:
    grid = all_tracks[f['properties']['colour']]
    out = []
    for p in densify(f['geometry']['coordinates']):
        d, q = nearest_point(grid, p, f['src'])
        if q is not None:
            w = 1 if d <= CENTER_FULL_METERS else (TWIN_METERS - d) / (TWIN_METERS - CENTER_FULL_METERS)
            p = (p[0] + (q[0] - p[0]) * 0.5 * w, p[1] + (q[1] - p[1]) * 0.5 * w)
            moved += 1
        out.append((round(p[0] / KX, 5), round(p[1] / KY, 5)))
    f['geometry']['coordinates'] = [list(c) for c in rdp(out, 0.00005)]
print('points centred', moved)

# 조각끼리 갈아타는 곳(분기·복복선 끝)에서는 자른 조각 끝이 이웃 조각과 10~20m 떨어져 틈으로 보일 수 있다.
# 끝점에서 가장 가까운 같은 노선 선로까지 GAP_METERS 안이면 짧은 선을 그어 잇는다.
GAP_METERS = 60
grids = {}
for f in kept:
    register(grids.setdefault(f['properties']['colour'], {}),
             [(lon * KX, lat * KY) for lon, lat in f['geometry']['coordinates']])

def nearest_on_others(grid, p, own):
    best, best_q = GAP_METERS, None
    cx, cy = cell(p)
    r = int(GAP_METERS // CELL_METERS) + 1
    for i in range(-r, r + 1):
        for j in range(-r, r + 1):
            for seg in grid.get((cx + i, cy + j), ()):
                if seg[:2] in own:
                    continue
                (ax, ay), (bx, by) = seg[0], seg[1]
                dx, dy = bx - ax, by - ay
                t = 0 if dx == dy == 0 else max(0, min(1, ((p[0] - ax) * dx + (p[1] - ay) * dy) / (dx * dx + dy * dy)))
                q = (ax + t * dx, ay + t * dy)
                dist = math.hypot(p[0] - q[0], p[1] - q[1])
                if dist < best:
                    best, best_q = dist, q
    return best, best_q

bridged = 0
for f in kept:
    grid = grids[f['properties']['colour']]
    coords = f['geometry']['coordinates']
    xy = [(lon * KX, lat * KY) for lon, lat in coords]
    own = set(zip(xy, xy[1:]))
    for end in (0, -1):
        dist, q = nearest_on_others(grid, xy[end], own)
        if q is None or dist < 2:  # 이미 붙어 있다
            continue
        joint = [round(q[0] / KX, 5), round(q[1] / KY, 5)]
        if end == 0:
            coords.insert(0, joint)
        else:
            coords.append(joint)
        bridged += 1
for f in kept:
    f.pop('first', None)
    f.pop('src', None)
    f.pop('siding', None)
features = kept
print('tracks cut', cut, 'gaps bridged', bridged)

out = {'type': 'FeatureCollection', 'features': features}
json.dump(out, open(dst, 'w'), ensure_ascii=False, separators=(',', ':'))
lines = sorted({f['properties']['line'] for f in features})
print('features', len(features), 'skipped', skipped, 'lines', len(lines))
print(lines)
