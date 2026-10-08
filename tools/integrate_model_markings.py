"""Make markings part of a solid surface, never depth-biased overlay geometry.

This postprocessor reuses the existing high-resolution label materials. It cuts
the receiving housing face into non-overlapping UV patches, removing the old
plate's side/back faces. Chip package bodies remain solid. No raster resampling,
runtime text renderer, endpoint movement, or collision change is involved.
"""
from pathlib import Path
import argparse
import copy
import json
import re
import subprocess
import refine_model_surfaces as surfaces

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / 'src/main/resources/assets/electron'
EPS = 1e-6
MATERIALS = {'#case', '#trim', '#silver', '#copper', '#rubber', '#ceramic', '#chip', '#wood'}
RATING_FREE = {'engineering_lamp', 'workshop_lamp', 'yard_lamp', 'emergency_lamp',
               'dc_motor', 'dc_generator', 'hand_crank', 'dc_breaker', 'electrical_relay',
               'boost_converter', 'boost_converter_12', 'buck_converter', 'buck_converter_12',
               'buck_converter_3v3', 'rechargeable_battery', 'electrical_redstone_switch',
               'capacitor_100mf', 'thermal_generator', 'hydro_generator', 'wind_generator',
               'lightning_collector', 'nuclear_reactor', 'mains_inlet', 'substation'}
VARIANT = re.compile(r'^(.*)_(off|on|unknown|broken|open)_(up|down|north|south|east|west)(?:_r([123]))?$')


def model_family(path):
    match = VARIANT.fullmatch(path.stem)
    return path.stem[:-10] if path.stem.endswith('_inventory') else match[1] if match else ''


def compare_baseline(families, ref):
    """Count the same variants from a fixed Git commit, without checking files out."""
    commit = subprocess.check_output(['git', 'rev-parse', '--verify', '--end-of-options', ref+'^{commit}'], cwd=ROOT, text=True).strip()
    rows = subprocess.check_output(['git', 'ls-tree', '-r', commit, '--', 'src/main/resources/assets/electron/models/block'], cwd=ROOT, text=True).splitlines()
    result = dict(baseline_commit=commit, families=len(families), models=0, before_quads=0, after_quads=0, plate_boxes_removed=0)
    process = subprocess.Popen(['git', 'cat-file', '--batch'], cwd=ROOT, stdin=subprocess.PIPE, stdout=subprocess.PIPE)
    try:
        for row in rows:
            info, relative = row.split('\t')
            path = ROOT/relative
            if model_family(path) not in families:
                continue
            process.stdin.write((info.split()[2]+'\n').encode())
            process.stdin.flush()
            header = process.stdout.readline().split()
            original = json.loads(process.stdout.read(int(header[2])))
            process.stdout.read(1)
            current = json.loads(path.read_text(encoding='utf-8'))
            result['models'] += 1
            result['before_quads'] += sum(len(e['faces']) for e in original['elements'])
            result['after_quads'] += sum(len(e['faces']) for e in current['elements'])
            result['plate_boxes_removed'] += sum(e.get('name', '').startswith('surface_mark:') for e in current['elements'])
    finally:
        process.stdin.close()
        process.stdout.close()
        process.wait()
    result['quad_reduction_percent'] = round((1-result['after_quads']/result['before_quads'])*100, 2)
    return result


def marking(ref):
    return ref == '#label' or re.fullmatch(r'#pin\d+', ref) is not None


def area(rect):
    return max(0, rect[2] - rect[0]) * max(0, rect[3] - rect[1])


def intersection(a, b):
    return max(a[0], b[0]), max(a[1], b[1]), min(a[2], b[2]), min(a[3], b[3])


def rectangle(e, side):
    _, _, u, _, v, _ = surfaces.FACES[side]
    return e['from'][u], e['from'][v], e['to'][u], e['to'][v]


def plane(e, side):
    axis, sign, *_ = surfaces.FACES[side]
    return e['to' if sign > 0 else 'from'][axis]


def face_patch(e, side, rect, face, name=None):
    patch = {k: copy.deepcopy(value) for k, value in e.items() if k != 'faces'}
    _, _, u, _, v, _ = surfaces.FACES[side]
    patch['from'][u], patch['from'][v], patch['to'][u], patch['to'][v] = rect
    patch['faces'] = {side: copy.deepcopy(face)}
    if name:
        patch['name'] = name
    return patch


def embed(obj, plate, side, tower=False):
    """Replace one host face region; the mark has exactly the host's depth."""
    axis, sign, *_ = surfaces.FACES[side]
    target = rectangle(plate, side)
    choices = []
    planes = {}
    for host in obj['elements']:
        if host is plate or 'rotation' in host or side not in host['faces']:
            continue
        face = host['faces'][side]
        if marking(face['texture']):
            continue
        gap = sign * (plane(plate, side) - plane(host, side))
        if gap < -EPS or gap > (2 if tower else .4) + EPS:
            continue
        cut = intersection(target, rectangle(host, side))
        if tower:
            # Old tower tags were suspended in air above the porcelain discs.
            # Print on the actual nearest disc, clipping the tag to its rim.
            if face['texture'] not in ('#ceramic', '#scorched') or area(cut) < .4:
                continue
        elif area(cut) < EPS:
            continue
        if tower:
            choices.append((gap, -area(cut), [host], cut))
        else:
            planes.setdefault(round(plane(host, side), 8), []).append(host)
    if not tower:
        for depth, hosts in planes.items():
            remaining = [target]
            for host in hosts:
                remaining = [part for rect in remaining for part in surfaces.subtract(rect, rectangle(host, side))]
            if not remaining:
                choices.append((sign * (plane(plate, side) - depth), 0, hosts, target))
    if not choices:
        raise ValueError(f'No solid backing for {plate["faces"][side]["texture"]}: {plate["from"]}')
    _, _, hosts, cut = min(choices, key=lambda c: c[:2])
    for host in hosts:
        old_face = host['faces'].pop(side)
        for rect in surfaces.subtract(rectangle(host, side), cut):
            face = surfaces.crop_uv(old_face, host, surfaces.FACES[side], rect)
            obj['elements'].append(face_patch(host, side, rect, face))
    mark = plate['faces'][side]
    obj['elements'].append(face_patch(hosts[0], side, cut, mark, 'surface_mark:' + mark['texture'][1:]))
    obj['elements'].remove(plate)


def remap_materials(obj):
    """Keep brushed metal/grain at a common physical scale on tiny parts."""
    for e in obj['elements']:
        if 'rotation' in e:
            continue
        for side, face in e['faces'].items():
            if face['texture'] not in MATERIALS:
                continue
            _, _, u, us, v, vs = surfaces.FACES[side]
            face['uv'] = [round(e['from'][u] if us > 0 else 16-e['to'][u], 9),
                          round(e['from'][v] if vs > 0 else 16-e['to'][v], 9),
                          round(e['to'][u] if us > 0 else 16-e['from'][u], 9),
                          round(e['to'][v] if vs > 0 else 16-e['from'][v], 9)]


def integrate(obj, family, turn=0, broken=False):
    if family in RATING_FREE:
        # Match the existing no-rating-plate design even after a legacy generator runs.
        obj['elements'] = [e for e in obj.get('elements', [])
                           if not any(f['texture'] in ('#label', '#rating_plate') for f in e['faces'].values())]
    plates = []
    for e in obj.get('elements', []):
        for side, face in e['faces'].items():
            if not marking(face['texture']):
                continue
            key = face['texture'][1:]
            # World variants used blank labels with a separate TextRenderer pass.
            suffix = '_label' if key == 'label' else '_pin_' + key[3:]
            ref = 'electron:block/' + family + suffix
            if not (A / 'textures/block' / (family + suffix + '.png')).exists():
                raise ValueError('Missing printed material: ' + ref)
            obj['textures'][key] = 'electron:block/scorched' if broken else ref
            rotation = turn * (90 if side in ('north', 'east', 'up') else -90) % 360
            if rotation:
                face['rotation'] = rotation
            else:
                face.pop('rotation', None)
            axis = surfaces.FACES[side][0]
            thickness = e['to'][axis] - e['from'][axis]
            package = key == 'label' and (family.startswith('wire_') or family == 'power_mosfet')
            if len(e['faces']) > 1 and thickness <= .26 and not package:
                plates.append((e, side))
    if not plates and not any(marking(f['texture']) for e in obj.get('elements', []) for f in e['faces'].values()):
        return 0, 0
    remap_materials(obj)
    for e, side in plates:
        embed(obj, e, side, tower=family == 'tower_terminal')
    obj['elements'] = [e for e in obj['elements'] if e['faces']]
    removed, _ = surfaces.refine(obj)
    return len(plates), removed


def audit(obj):
    """A surface inlay must have no coplanar face behind it, in any orientation."""
    checked = 0
    for e in obj.get('elements', []):
        if not e.get('name', '').startswith('surface_mark:'):
            continue
        assert len(e['faces']) == 1
        side = next(iter(e['faces']))
        for other in obj['elements']:
            if other is e or side not in other['faces'] or 'rotation' in other:
                continue
            if abs(plane(e, side) - plane(other, side)) < EPS:
                assert area(intersection(rectangle(e, side), rectangle(other, side))) < EPS, (side, e, other)
        checked += 1
    return checked


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true', help='Validate committed models without rewriting them')
    parser.add_argument('--baseline-ref', help='Optional Git revision for a reproducible before/after mesh count')
    args = parser.parse_args()
    families = sorted(p.stem[:-10] for p in (A/'models/block').glob('*_inventory.json')
                      if any(marking(f['texture']) for e in json.loads(p.read_text()).get('elements', []) for f in e['faces'].values()))
    report = dict(models=0, models_changed=0, plates_removed=0, hidden_faces_removed=0,
                  before_quads=0, after_quads=0, inlaid_faces=0, families=families)
    for path in sorted((A/'models/block').glob('*.json')):
        match = VARIANT.fullmatch(path.stem)
        family = model_family(path)
        if family not in families:
            continue
        obj = json.loads(path.read_text(encoding='utf-8'))
        before = copy.deepcopy(obj)
        try:
            plates, hidden = integrate(obj, family, int(match[4] or 0) if match else 0, bool(match and match[2] == 'broken'))
        except Exception as error:
            raise ValueError(f'{path.name}: {error}') from error
        report['models'] += 1
        report['plates_removed'] += plates
        report['hidden_faces_removed'] += hidden
        report['before_quads'] += sum(len(e['faces']) for e in before['elements'])
        report['after_quads'] += sum(len(e['faces']) for e in obj['elements'])
        report['inlaid_faces'] += audit(obj)
        if obj != before:
            if args.check:
                raise AssertionError('Model needs surface integration: ' + str(path))
            report['models_changed'] += 1
            path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    target = ROOT/'build/asset-review/integrated-surfaces.json'
    target.parent.mkdir(parents=True, exist_ok=True)
    if args.check:
        (target.parent/'integrated-surfaces-audit.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
    else:
        target.write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
    if args.baseline_ref:
        comparison = compare_baseline(families, args.baseline_ref)
        (target.parent/'surface-baseline-comparison.json').write_text(json.dumps(comparison, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(report))


if __name__ == '__main__':
    main()
