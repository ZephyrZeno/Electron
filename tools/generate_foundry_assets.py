"""Deterministic pixel art and cuboid models for the Circuit Foundry P0 release.

No downloaded artwork. Run after generate_assets.py, which owns legacy recipes.
All device faces use the same physical pin coordinates as CircuitParts.java.
"""
from pathlib import Path
from copy import deepcopy
import json
import random
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
A = RES / 'assets/electron'
GATES = {'and': ('AND', '与门'), 'or': ('OR', '或门'), 'not': ('NOT', '非门'),
         'xor': ('XOR', '异或门'), 'nand': ('NAND', '与非门'), 'nor': ('NOR', '或非门'),
         'xnor': ('XNOR', '同或门'), 'buffer': ('BUF', '缓冲器')}
COLORS = ['#df4949', '#323941', '#45cddd', '#e7c64a', '#67ce82', '#b485db', '#e5e8e8', '#8394a3']
FONT = ImageFont.load_default(size=8)
SMALL = ImageFont.load_default(size=6)
FACES = ['north', 'south', 'east', 'west', 'up', 'down']


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def texture(name, color, style='metal', label=None):
    im = Image.new('RGB', (64, 64), color)
    d = ImageDraw.Draw(im)
    rng = random.Random(name)
    for _ in range(150):
        x, y = rng.randrange(64), rng.randrange(64)
        r, g, b = im.getpixel((x, y))
        n = rng.choice([-6, -3, 3, 6])
        d.point((x, y), fill=tuple(max(0, min(255, c+n)) for c in (r, g, b)))
    if style == 'metal':
        for y in range(4, 64, 8):
            d.line((0, y, 63, y), fill='#435564')
    elif style == 'board':
        for y in (6, 14, 50, 58):
            d.line((4, y, 22, y, 28, y+3, 58, y+3), fill='#6f8876', width=1)
            for x in (4, 58):
                d.ellipse((x-1, y-1, x+1, y+1), fill='#d3b774')
    if label:
        im=im.resize((256,256),Image.Resampling.NEAREST);d=ImageDraw.Draw(im)
        face=ImageFont.truetype('C:/Windows/Fonts/consolab.ttf',min(112,max(30,int(360/len(label)))))
        d.text((128,128),label,anchor='mm',font=face,fill='#ffffff',stroke_width=1)
    p = A / 'textures/block' / f'{name}.png'
    p.parent.mkdir(parents=True, exist_ok=True)
    im.save(p)
    return im


def cube(lo, hi, tex='case', face_tex=None, uv=None):
    return {'from': list(lo), 'to': list(hi), 'faces': {
        f: {'texture': '#'+(face_tex or {}).get(f, tex), 'uv': (uv or {}).get(f, [0, 0, 16, 16])}
        for f in FACES}}


def model(name, elements, textures, item=False):
    write(A / 'models' / ('item' if item else 'block') / f'{name}.json', {
        'parent': 'minecraft:block/block', 'ambientocclusion': True,
        'textures': {'particle': textures.get('case', next(iter(textures.values()))), **textures},
        'elements': elements,
        'display': {'gui': {'rotation': [25, 225, 0], 'translation': [0, 0, 0], 'scale': [.68, .68, .68]},
                    'ground': {'translation': [0, 3, 0], 'scale': [.28, .28, .28]},
                    'fixed': {'rotation': [0, 180, 0], 'scale': [.65, .65, .65]},
                    'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [.45, .45, .45]},
                    'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [.375, .375, .375]}}
    })


BASE = {key: 'electron:block/'+key for key in ['case', 'trim', 'copper', 'silver', 'ceramic', 'chip', 'red_board', 'green_board', 'lamp_off', 'lamp_on', 'lamp_x', 'scorched', 'rubber', 'wood']}
for name, color, style in [('case', '#283441', 'metal'), ('trim', '#748494', 'metal'),
                         ('copper', '#bc7e48', ''), ('silver', '#c4cfda', ''), ('ceramic', '#d9cda6', ''),
                         ('chip', '#171f28', ''), ('red_board', '#752d3a', 'board'), ('green_board', '#174e42', 'board'),
                         ('lamp_off', '#543840', ''), ('lamp_on', '#8ef5bd', ''), ('lamp_x', '#ffc64c', ''),
                         ('scorched', '#241e1c', ''), ('rubber', '#202a35', ''), ('wood', '#625043', 'metal')]:
    texture(name, color, style)
for n, c in enumerate(COLORS):
    texture('wire_'+str(n), c, '')


def redstone_gate(fn, label):
    name = fn+'_gate' if fn != 'binary_memory' else fn
    for on in (False, True):
        suffix = '_on' if on else ''
        im = texture(name+suffix, '#702638', 'board')
        d = ImageDraw.Draw(im)
        # The north edge of the UP face is the output; blockstate rotates it with FACING.
        d.line((32, 12, 32, 23), fill='#ff866f' if on else '#b64e53', width=3)
        d.polygon([(26, 19), (32, 12), (38, 19)], fill='#fff1c7')
        d.rounded_rectangle((14, 25, 49, 41), radius=2, fill='#17202c', outline='#d6c1b5')
        d.text((32, 33), label, anchor='mm', font=FONT, fill='#ffffff')
        d.text((32, 55), 'RS / '+label, anchor='mm', font=SMALL, fill='#f3bda7')
        unary = fn in ('not', 'buffer', 'binary_memory')
        if unary:
            d.line((32, 42, 32, 63), fill='#ceac6e', width=2)
        else:
            d.line((0, 32, 13, 32), fill='#ceac6e', width=2)
            d.line((50, 32, 63, 32), fill='#ceac6e', width=2)
        im.save(A/'textures/block'/f'{name+suffix}.png')
        tx = {**BASE, 'panel': 'electron:block/'+name+suffix, 'status': BASE['lamp_on' if on else 'lamp_off']}
        els = [cube((0, 0, 0), (16, 10, 16), 'case'), cube((.5, 10, .5), (15.5, 11, 15.5), 'red_board', {'up': 'panel'}),
               cube((5, 11, 6), (11, 12.3, 10), 'chip'), cube((7, 11, 1), (9, 11.6, 3), 'status')]
        # Chip's independent silkscreen, visible on its raised top.
        chip_im = texture('rs_chip_'+fn, '#182330', '', label)
        tx['die'] = 'electron:block/rs_chip_'+fn
        els[2]['faces']['up']['texture'] = '#die'
        for x in (4.4, 11):
            for z in (6.5, 7.5, 8.5, 9.5):
                els.append(cube((x, 10.8, z), (x+.6, 11.6, z+.45), 'silver'))
        for x, z in ((1, 1), (14, 1), (1, 14), (14, 14)):
            els.append(cube((x, 11, z), (x+.65, 11.4, z+.65), 'silver'))
        pads = [(6, 0, 10, 2)] + ([(6, 14, 10, 16)] if unary else [(0, 6, 2, 10), (14, 6, 16, 10)])
        if fn == 'binary_memory':
            pads += [(0, 6, 2, 10), (14, 6, 16, 10)]
        for x0, z0, x1, z1 in pads:
            els.append(cube((x0-.12 if x0==0 else x0, 8, z0-.12 if z0==0 else z0),
                            (x1+.12 if x1==16 else x1, 10.8, z1+.12 if z1==16 else z1), 'copper'))
        for element in els:
            for key in ('from','to'):
                y=element[key][1];element[key][1]=y*.25 if y<=10 else 2.5+(y-10)*.5
        model(name+suffix, els, tx)
    variants = {f'facing={f},powered={str(on).lower()}': {'model': 'electron:block/'+name+('_on' if on else ''), 'y': rot}
                for f, rot in [('north', 0), ('east', 90), ('south', 180), ('west', 270)] for on in (False, True)}
    write(A/'blockstates'/f'{name}.json', {'variants': variants})
    write(A/'models/item'/f'{name}.json', {'parent': 'electron:block/'+name})


for fn, (label, _) in GATES.items():
    redstone_gate(fn, label)
redstone_gate('binary_memory', 'D-FF')


def local_cube(lo, hi, tex='case', front_tex=None):
    # Local coordinates: u (canonical +axis), v (+axis), d (into the face).
    return (lo, hi, tex, front_tex)


def orient(elements, face,turn=0,inset=0):
    def point(p):
        u, v, d = p
        if turn==1:u,v=16-v,u
        elif turn==2:u,v=16-u,16-v
        elif turn==3:u,v=v,16-u
        d=16 if d==16 else d+inset
        return {'north': (u, v, d), 'south': (u, v, 16-d), 'west': (d, v, u),
                'east': (16-d, v, u), 'up': (u, 16-d, v), 'down': (u, d, v)}[face]
    result = []
    for lo, hi, tex, front in elements:
        p, q = point(lo), point(hi)
        result.append(cube(tuple(min(p[i], q[i]) for i in range(3)), tuple(max(p[i], q[i]) for i in range(3)), tex, {face: front} if front else None))
    return result


DEVICES = {'hand_crank': ('5 V / DC', '手摇电源'), 'resistor_100': ('100 OHM', '100 Ω 电阻'),
           'resistor_220': ('220 OHM', '220 Ω 电阻'), 'resistor_1000': ('1K OHM', '1 kΩ 电阻'),
           'indicator_led': ('LED / + -', '指示 LED'), 'circuit_switch': ('SWITCH', '电路开关')}
DEVICES.update({'silicon_diode':('A >| K','硅二极管'),'fuse_25ma':('25 mA','25 mA 保险丝')})
DEVICES.update({'momentary_button':('1 s / NO','自复位按钮'),'capacitor_100mf':('100 mF / 10 V','100 mF 极性电容'),'resistor_10000':('10K OHM','10 kΩ 电阻')})
DEVICES.update({'rechargeable_battery':('5 V / 200 J','可充电电池模块'),'terminal_strip':('A / B','双母排接线排'),'electrical_redstone_switch':('5 V COIL','电控红石开关')})
DEVICES.update({'wire_'+fn+'_gate': (label, zh+'（电线）') for fn, (label, zh) in GATES.items()})
DEVICES.update({'power_mosfet':('N-MOS','功率 MOSFET'),'electrical_relay':('5 V / NO','电磁继电器'),'boost_converter':('24V','24 V 升压模块'),'buck_converter':('5V','5 V 降压模块'),
                'dc_generator':('24 V DC','手摇直流发电机'),'distribution_box':('DC BUS','低压分线箱'),'dc_breaker':('1 A DC','直流保护断路器'),'engineering_lamp':('24 V / 2.4 W','工程照明灯'),'dc_motor':('24 V MOTOR','直流电机')})
DEVICES.update({'boost_converter_12':('12V','12 V 升压模块'),'buck_converter_3v3':('3.3V','3.3 V 降压模块'),'buck_converter_12':('12V','12 V 降压模块')})
LARGE={'dc_generator','distribution_box','dc_breaker','engineering_lamp','dc_motor'}


def device(name, label):
    gate = name.startswith('wire_');large=name in LARGE
    tx = {**BASE, 'panel': 'electron:block/'+name+'_panel', 'label': 'electron:block/'+name+'_label'}
    im = texture(name+'_panel', '#164f43' if gate else '#354954', 'board')
    d = ImageDraw.Draw(im)
    d.rectangle((1, 1, 62, 62), outline='#9ab7ab', width=2)
    d.text((32, 5), 'ECF / '+('POWER' if large else 'LOGIC' if gate else 'DC'), anchor='mt', font=SMALL, fill='#bdd7cc')
    if large:
        for x in range(5,60,5):d.rectangle((x,58,x+2,61),fill='#d1a94c')
    im.save(A/'textures/block'/f'{name}_panel.png')
    texture(name+'_label', '#192731', '', label)
    pins = [(3, 13, 'VCC'), (11, 13, 'GND'), (3, 3, 'A'), (11, 3, 'NC' if name in ('wire_not_gate','wire_buffer_gate') else 'B'), (7, 7, 'Y')] if gate else [(3, 7, '+'), (11, 7, '-')]
    if name.startswith('resistor_') or name in ('circuit_switch','momentary_button','fuse_25ma'):pins=[(3,7,'A'),(11,7,'B')]
    if name=='silicon_diode':pins=[(3,7,'A'),(11,7,'K')]
    if name=='rechargeable_battery' or name.startswith(('boost_converter','buck_converter')):pins=[(3,13,'IN+'),(11,13,'IN-'),(3,3,'OUT+'),(11,3,'OUT-')]
    if name=='electrical_relay':pins=[(3,13,'COIL+'),(11,13,'COIL-'),(3,3,'COM'),(11,3,'NO')]
    if name=='power_mosfet':pins=[(3,13,'G'),(11,13,'D'),(7,3,'S')]
    if name=='dc_breaker':pins=[(3,7,'A'),(11,7,'B')]
    if name in ('terminal_strip','distribution_box'):pins=[(u,v,f'{bus}{i+1}') for bus,u in [('A',3),('B',11)] for i,v in enumerate((3,7,11,15))]
    # Normal, lit/high, unknown, broken and switch-open variants.
    for state in ('off', 'on', 'unknown', 'broken', 'open'):
        t = {**tx, 'status': BASE['scorched' if state == 'broken' else 'lamp_on' if state == 'on' else 'lamp_x' if state == 'unknown' else 'lamp_off']}
        board = 'scorched' if state == 'broken' else 'panel'
        els = [local_cube((0, 0, .5), (16, 16, 16), 'case'), local_cube((.35, .35, 0), (15.65, 15.65, .6), 'trim', board)]
        if large:els[0]=local_cube((.8,.8,.5),(15.2,15.2,16),'case')
        for u, v in ((.8,.8), (14.4,.8), (.8,14.4), (14.4,14.4)):
            els.append(local_cube((u, v, -.1), (u+.8, v+.8, .1), 'silver'))
        for i, (u, v, mark) in enumerate(pins):
            texture(name+'_pin_'+str(i), '#1b2a32', '', mark)
            t['pin'+str(i)] = 'electron:block/'+name+'_pin_'+str(i)
            els.extend([local_cube((u-.75, v-.75, -.25), (u+.75, v+.75, .1), 'rubber'),
                        local_cube((u-.38, v-.38, -.65), (u+.38, v+.38, -.25), 'silver' if mark in ('GND','-','IN-','OUT-') or mark.startswith('B') else 'copper'),
                        local_cube((u-1.7, v-2.3, -.08), (u+1.7, v-.8, .08), 'chip', 'pin'+str(i))])
        if gate:
            els += [local_cube((5, 9, -.8), (9, 12, .05), 'chip', 'label'), local_cube((11.9, 9.5, -.3), (13, 10.6, 0), 'status')]
            for u in (4.5, 9):
                for v in (9.2, 10, 10.8):
                    els.append(local_cube((u, v, -.35), (u+.5, v+.35, 0), 'silver'))
        elif name=='rechargeable_battery':
            t['battery_cell']='electron:block/battery_cell'
            els += [local_cube((5,5,-.4),(9,11,.1),'rubber'),local_cube((5.2,5.2,-.85),(8.8,10.8,-.35),'scorched' if state=='broken' else 'battery_cell'),
                    local_cube((5.7,5,-.9),(8.3,5.6,-.3),'silver'),local_cube((5.7,10.4,-.9),(8.3,11,-.3),'silver'),
                    local_cube((5.3,7,-.95),(8.7,9.5,-.85),'chip','label')]
            for v in (6,7,8,9,10):els.append(local_cube((9.5,v,-.2),(10,v+.55,0),'scorched' if state=='broken' else 'lamp_off'))
        elif name in ('terminal_strip','distribution_box'):
            for u,tex in [(3,'copper'),(11,'silver')]:
                els += [local_cube((u-.65,2.2,-.12),(u+.65,15.8,.08),'rubber'),local_cube((u-.22,2.3,-.2),(u+.22,15.7,-.12),tex)]
            for v in (3,7,11,15):
                for u in (3,11):els.append(local_cube((u-.28,v-.045,-.68),(u+.28,v+.045,-.64),'chip'))
        elif name=='power_mosfet':
            els += [local_cube((4.8,8.8,-.65),(9.2,12.2,.1),'trim'),local_cube((5.2,9.1,-.9),(8.8,11.8,-.65),'chip','label'),local_cube((11.5,9.5,-.3),(12.4,10.4,0),'status')]
            for u in (5.2,6.3,7.4,8.5):els.append(local_cube((u,8.8,-1),(u+.35,9.25,-.55),'silver'))
        elif name.startswith(('boost_converter','buck_converter')):
            els += [local_cube((4.8,5,-.45),(9.2,11.2,.05),'rubber'),local_cube((5.2,5.4,-.8),(8.8,7.6,-.4),'copper'),local_cube((5.2,8,-.9),(8.8,10.8,-.4),'chip','label'),local_cube((10.1,8.2,-.3),(11,9.1,0),'status')]
            for v in (5.5,6.1,6.7,7.3):els.append(local_cube((5.15,v,-.91),(8.85,v+.15,-.79),'wood'))
            for u in (5.5,8):els.append(local_cube((u,10.9,-.6),(u+.5,11.5,.05),'silver'))
        elif name=='electrical_relay':
            els += [local_cube((4.8,5,-.4),(9.2,11.4,.05),'rubber'),local_cube((5.1,7.8,-.75),(8.9,10.9,-.3),'copper'),local_cube((5.5,8.1,-.88),(8.5,10.6,-.7),'chip','label'),local_cube((5.2,5.5,-.6),(8.8,6.7,-.3),'silver'),local_cube((10,8.8,-.3),(11,9.8,0),'status')]
        elif name=='engineering_lamp':
            els += [local_cube((4.5,8.5,-.45),(9.5,13.5,.05),'trim'),local_cube((5,9,-.9),(9,13,-.45),'scorched' if state=='broken' else 'lamp_on' if state=='on' else 'ceramic'),local_cube((3,2,-.1),(11,4.2,.05),'chip','label')]
            for u in (4.7,9.1):els.append(local_cube((u,8.7,-1),(u+.2,13.3,-.4),'silver'))
        elif name=='dc_motor':
            els += [local_cube((4.7,8.7,-.5),(9.3,13.3,.05),'trim'),local_cube((5.2,9.2,-.7),(8.8,12.8,-.5),'rubber'),local_cube((6.5,10.8,-.95),(7.5,12.2,-.6),'silver'),local_cube((3,2,-.1),(11,4.2,.05),'chip','label')]
            for u in (4.8,8.8):
                for v in (9,10,11,12):els.append(local_cube((u,v,-.65),(u+.35,v+.5,-.45),'chip'))
        elif name=='dc_breaker':
            els += [local_cube((5,9,-.5),(9,13,.1),'rubber'),local_cube((3,2,-.1),(11,4.2,.05),'chip','label'),local_cube((10,10,-.3),(11,11,0),'status')]
        elif name=='electrical_redstone_switch':
            els += [local_cube((5,9,-.6),(9,12,.05),'copper'),local_cube((5.4,9.4,-.85),(8.6,11.6,-.6),'chip','label'),local_cube((10,8.8,-.3),(11,9.8,0),'status')]
            for v in (0,14):
                els += [local_cube((6.7,v,-.2),(7.3,v+1.1,0),'red_board'),local_cube((6.1,v+1.1,-.2),(7.9,v+1.5,0),'red_board'),local_cube((6.6,v+1.5,-.2),(7.4,v+2,0),'copper')]
            for v in (9.3,10.1,10.9):els.append(local_cube((4.7,v,-.5),(5.3,v+.3,.05),'silver'))
        elif name.startswith('resistor'):
            els += [local_cube((5, 6, -.7), (9, 8, 0), 'ceramic'), local_cube((4, 6.7, -.3), (10, 7.3, 0), 'copper'),
                    local_cube((3, 11, -.2), (13, 13.5, 0), 'chip', 'label')]
            bands = {'resistor_100': ['wood', 'rubber', 'wood'], 'resistor_220': ['red_board', 'red_board', 'wood'], 'resistor_1000': ['wood', 'rubber', 'wire_0'], 'resistor_10000':['wood','rubber','resistor_orange']}[name]
            for u, band in zip((5.4, 6.4, 7.8), bands):
                t[band] = 'electron:block/'+band
                els.append(local_cube((u, 5.95, -.85), (u+.35, 8.05, -.7), band))
        elif name == 'indicator_led':
            els += [local_cube((5.3, 9.3, -.5), (8.7, 12.7, .1), 'trim'), local_cube((5.8, 9.8, -1), (8.2, 12.2, -.5), 'status')]
        elif name == 'circuit_switch':
            els += [local_cube((5.5, 9, -.4), (8.5, 13, .1), 'rubber')]
        elif name=='momentary_button':
            t['button_cap']='electron:block/button_cap'
            els += [local_cube((5.1,9.1,-.4),(8.9,12.9,.05),'rubber'),local_cube((3,2,-.1),(11,4.2,.05),'chip','label')]
            for u in (5.1,8.5):els.append(local_cube((u,9.1,-.5),(u+.4,12.9,-.15),'silver'))
            for v in (9.1,12.5):els.append(local_cube((5.5,v,-.5),(8.5,v+.4,-.15),'silver'))
            if state=='broken':els.append(local_cube((5.8,9.8,-.45),(8.2,12.2,0),'scorched'))
        elif name=='capacitor_100mf':
            t['capacitor_can']='electron:block/capacitor_can'
            els += [local_cube((4.8,9.3,-.35),(9.2,13.7,.05),'rubber'),local_cube((5,9.5,-.9),(9,13.5,-.25),'scorched' if state=='broken' else 'capacitor_can'),
                    local_cube((5.35,9.85,-.98),(8.65,13.15,-.9),'scorched' if state=='broken' else 'silver'),
                    local_cube((8.55,9.5,-.94),(9,13.5,-.25),'ceramic'),local_cube((3,2,-.1),(11,4.2,.05),'chip','label')]
            for v in (10.3,11.4,12.5):els.append(local_cube((8.6,v,-.96),(8.95,v+.14,-.94),'chip'))
            els += [local_cube((6.85,10.25,-1),(7.05,12.75,-.97),'chip'),local_cube((5.75,11.4,-1),(8.25,11.6,-.97),'chip')]
        elif name in ('silicon_diode','fuse_25ma'):
            els += [local_cube((4,6.7,-.3),(10,7.3,0),'copper'),local_cube((5,6,-.75),(9,8,.05),'chip' if name=='silicon_diode' else 'ceramic'),
                    local_cube((3,11,-.2),(13,13.5,0),'chip','label')]
            if name=='silicon_diode':els.append(local_cube((8.2,5.96,-.82),(8.7,8.04,-.75),'silver'))
            else:
                for u in (5,8.4):els.append(local_cube((u,5.95,-.83),(u+.6,8.05,-.7),'silver'))
                els.append(local_cube((5.7,6.85,-.81),(8.3,7.15,-.76),'copper' if state!='broken' else 'scorched'))
        else:
            els += [local_cube((5.5, 10, -.4), (8.5, 13, .1), 'trim'),local_cube((3, 2, -.1), (11, 4.2, .1), 'chip', 'label')]
        depth=1 if large else 13
        if large:
            for u in (.2,15.2):
                for v in (3,5,7,9,11):els.append(local_cube((u,v,2),(u+.5,v+.45,14),'rubber'))
        if name=='indicator_led':
            t['status']='electron:block/'+('led_red_on' if state=='on' else 'scorched' if state=='broken' else 'led_red_off')
        live={**t,'label':BASE['chip']}
        if gate or name.startswith(('boost_converter','buck_converter')) or name in ('rechargeable_battery','terminal_strip','distribution_box','electrical_relay','power_mosfet'):
            for i in range(len(pins)):live['pin'+str(i)]=BASE['chip']
        for face in FACES:
            for turn in range(4):
                # Keep old model names as the turn-zero alias for existing external references.
                suffix='' if turn==0 else '_r'+str(turn)
                model(name+'_'+state+'_'+face+suffix, orient(els, face,turn,depth),live)
        if state=='off':
            inventory=deepcopy(els)
            if name in ('hand_crank','dc_generator'):inventory += [local_cube((6.5,11,-.7),(9.5,12,-.4),'silver'),local_cube((9,10.8,-1),(10,12.2,-.7),'wood')]
            if name in ('circuit_switch','dc_breaker'):inventory += [local_cube((6,11,-1),(8,13,-.4),'silver')]
            if name=='electrical_relay':inventory += [local_cube((5.5,6,-.9),(8.5,6.4,-.6),'silver')]
            if name=='dc_motor':inventory += [local_cube((5.5,11.25,-1),(8.5,11.75,-.75),'copper')]
            if name=='momentary_button':inventory += [local_cube((5.8,9.8,-1),(8.2,12.2,-.55),'button_cap')]
            if name=='electrical_redstone_switch':inventory += [local_cube((6,12.5,-.9),(8,12.9,-.55),'silver')]
            model(name+'_inventory',orient(inventory,'north',0,depth),t)
    parts = []
    for face,turn in ((f,r) for f in FACES for r in range(4)):
        def part(when, state):
            suffix='' if turn==0 else '_r'+str(turn)
            parts.append({'when': {'facing': face,'turn':str(turn), **when}, 'apply': {'model': 'electron:block/'+name+'_'+state+'_'+face+suffix}})
        part({'broken': 'true'}, 'broken')
        if name in ('circuit_switch','momentary_button','dc_breaker'):
            part({'broken': 'false', 'enabled': 'false'}, 'open')
            part({'broken': 'false', 'enabled': 'true'}, 'on')
        elif gate:
            part({'broken': 'false', 'power': '0|1|2|3|4|5|6'}, 'off')
            part({'broken': 'false', 'power': '7|8|9|10|11|12|13|14'}, 'unknown')
            part({'broken': 'false', 'power': '15'}, 'on')
        else:
            part({'broken': 'false', 'power': '0'}, 'off')
            part({'broken': 'false', 'power': '|'.join(map(str, range(1,16)))}, 'on')
    write(A/'blockstates'/f'{name}.json', {'multipart': parts})
    write(A/'models/item'/f'{name}.json', {'parent': 'electron:block/'+name+'_inventory'})


texture('led_red_on','#ff6a42','');texture('led_red_off','#602b2e','')
button_cap=texture('button_cap','#287eac','');d=ImageDraw.Draw(button_cap);d.rounded_rectangle((6,6,57,57),radius=12,outline='#87cbe4',width=3);d.ellipse((21,21,42,42),outline='#f1f4e9',width=3);button_cap.save(A/'textures/block/button_cap.png')
texture('capacitor_can','#214973','metal')
texture('resistor_orange','#ce702a','')
cell=texture('battery_cell','#37725b','');d=ImageDraw.Draw(cell);d.line((8,4,8,59),fill='#91b49d',width=3);d.line((56,4,56,59),fill='#214c41',width=3);cell.save(A/'textures/block/battery_cell.png')

glow=Image.new('RGBA',(64,64),(255,255,255,0))
for y in range(64):
    for x in range(64):
        r=((x-31.5)**2+(y-31.5)**2)**.5/31.5
        glow.putpixel((x,y),(255,255,255,round(max(0,1-r)**2*220)))
glow.save(A/'textures/block/led_glow.png')
for name, (label, _) in DEVICES.items():
    device(name, label)

# A small workbench with raised instrument rail, vise, drawers and a cutting mat.
bench = [cube((0, 11, 0), (16, 13, 16), 'wood'), cube((1, 13, 2), (12, 13.2, 13), 'green_board'),
         cube((1, 0, 1), (3, 11, 3), 'trim'), cube((13, 0, 1), (15, 11, 3), 'trim'),
         cube((1, 0, 13), (3, 11, 15), 'trim'), cube((13, 0, 13), (15, 11, 15), 'trim'),
         cube((4, 4, 2), (12, 11, 14), 'case'), cube((4.2, 5, 1.7), (11.8, 7.5, 2), 'trim'),
         cube((4.2, 8, 1.7), (11.8, 10.5, 2), 'trim'), cube((6.75, 6, 1.2), (9.25, 6.6, 1.7), 'rubber'),
         cube((6.75, 9, 1.2), (9.25, 9.6, 1.7), 'rubber'), cube((1, 13, 14), (15, 16, 16), 'case'),
         cube((12, 13, 5), (15, 14, 10), 'trim'), cube((12, 14, 5), (15, 15.5, 6), 'silver'),
         cube((12, 14, 9), (15, 15.5, 10), 'silver')]
model('assembly_table', bench, BASE)
write(A/'blockstates/assembly_table.json', {'variants': {'facing='+face: {'model': 'electron:block/assembly_table', 'y': turn*90} for turn,face in enumerate(('north','east','south','west'))}})
write(A/'models/item/assembly_table.json', {'parent': 'electron:block/assembly_table'})

# Standalone 3D tools and attachment inventory models.
for color in range(8):
    tx = {**BASE, 'coil': 'electron:block/wire_'+str(color)}
    elements = [cube((3, 2, 2), (13, 4, 14), 'trim'), cube((3, 12, 2), (13, 14, 14), 'trim'),
                cube((4, 4, 3), (12, 12, 13), 'coil'), cube((7, 1, 7), (9, 15, 9), 'wood')]
    for y in range(5, 12, 2):
        elements.append(cube((3.8, y, 2.8), (12.2, y+.35, 13.2), 'coil'))
    model('wire_spool_'+str(color), elements, tx, True)
write(A/'models/item/wire_spool.json', {'parent': 'electron:item/wire_spool_0', 'overrides': [
    {'predicate': {'custom_model_data': i}, 'model': 'electron:item/wire_spool_'+str(i)} for i in range(1,8)]})
for color in range(8):
    tx={**BASE,'coil':'electron:block/wire_'+str(color)}
    elements=[cube((2,1,1),(14,3,15),'trim'),cube((2,13,1),(14,15,15),'trim'),cube((3,3,2),(13,13,14),'rubber'),cube((7,0,7),(9,16,9),'silver')]
    for y in (4,7,10):elements += [cube((2.7,y,1.7),(13.3,y+1.5,14.3),'coil'),cube((2.65,y+.55,1.65),(13.35,y+.9,14.35),'rubber')]
    model('cable_spool_'+str(color),elements,tx,True)
write(A/'models/item/cable_spool.json',{'parent':'electron:item/cable_spool_0','overrides':[{'predicate':{'custom_model_data':i},'model':'electron:item/cable_spool_'+str(i)} for i in range(1,8)]})
model('wire_cutters', [cube((3, 0, 7), (6, 9, 9), 'wire_0'), cube((10, 0, 7), (13, 9, 9), 'wire_0'),
      cube((5, 7, 7), (11, 11, 9), 'trim'), cube((5, 11, 7), (7.8, 15, 9), 'silver'),
      cube((8.2, 11, 7), (11, 15, 9), 'silver'), cube((7, 8, 6.5), (9, 10, 9.5), 'copper')], {**BASE, 'wire_0': 'electron:block/wire_0'}, True)
texture('meter_screen', '#6a9280', '', '5.00 V')
model('multimeter', [cube((3, 1, 5), (13, 15, 10), 'copper'), cube((4, 2, 4.7), (12, 14, 5), 'rubber'),
      cube((4.5, 10, 4.5), (11.5, 13.5, 4.7), 'chip', {'north':'display'}), cube((6, 5, 4), (10, 9, 4.7), 'trim'),
      cube((7.5, 4.5, 3.7), (8.5, 9.5, 4), 'rubber'), cube((5, 2.5, 4), (6, 3.5, 4.7), 'wire_0'),
      cube((10, 2.5, 4), (11, 3.5, 4.7), 'silver')], {**BASE, 'wire_0':'electron:block/wire_0', 'display':'electron:block/meter_screen'}, True)
meter_path=A/'models/item/multimeter.json';meter=json.loads(meter_path.read_text(encoding='utf-8'))
for hand in ('righthand','lefthand'):
    meter['display']['firstperson_'+hand]={'rotation':[0,180,0],'translation':[0,1.4,-.6],'scale':[.68,.68,.68]}
    meter['display']['thirdperson_'+hand]={'rotation':[0,180,0],'translation':[0,2.5,0],'scale':[.45,.45,.45]}
write(meter_path,meter)
model('calibration_wrench',[cube((6,1,6),(10,11,8),'rubber'),cube((5,10,6),(11,13,8),'silver'),cube((4,12,6),(6,16,8),'silver'),cube((10,12,6),(12,16,8),'silver'),cube((7,2,5.8),(9,4,6),'copper')],BASE,True)
model('terminal', [cube((4, 6, 4),(12, 8, 12),'rubber'),cube((6, 8, 6),(10, 11, 10),'copper'),cube((7.7,11,6),(8.3,11.15,10),'chip')], BASE, True)
model('wall_port_2', [cube((3,2,3),(13,4,13),'case'),cube((3,12,3),(13,14,13),'case'),
      cube((5,1,6),(7,15,8),'copper'),cube((9,1,6),(11,15,8),'silver'),cube((4,4,5),(12,12,9),'ceramic')], BASE, True)
bridge_kit=[]
for x in (3,11):
    for z in (3,11):
        bridge_kit += [cube((x-1,2,z-1),(x+3,3,z+3),'trim'),cube((x,3,z),(x+2,7,z+2),'ceramic'),cube((x-.5,7,z-.5),(x+2.5,8,z+2.5),'ceramic'),cube((x+.6,8,z+.6),(x+1.4,8.5,z+1.4),'rubber')]
model('wire_bridge',bridge_kit,BASE,True)
texture('optical_glass','#97d2ce','')
visor=[cube((1,10,2),(15,12,3),'rubber'),cube((.5,8,2),(2,12,14),'rubber'),cube((14,8,2),(15.5,12,14),'rubber'),cube((1,8,13),(15,11,14),'rubber'),
       cube((6,7.2,.9),(10,8.3,2),'copper')]
for x in (2,9):
    visor += [cube((x,4,1),(x+5,4.8,2),'trim'),cube((x,8.2,1),(x+5,9,2),'trim'),cube((x,4,1),(x+.8,9,2),'trim'),cube((x+4.2,4,1),(x+5,9,2),'trim'),cube((x+.8,4.8,1.35),(x+4.2,8.2,1.6),'glass'),cube((x+1,7.5,1.2),(x+2.5,7.85,1.35),'silver')]
model('magnifying_visor',visor,{**BASE,'glass':'electron:block/optical_glass'},True)
visor_path=A/'models/item/magnifying_visor.json';visor_model=json.loads(visor_path.read_text(encoding='utf-8'))
visor_model['display']['head']={'rotation':[0,0,0],'translation':[0,1.5,0],'scale':[1.2,1.2,1.2]}
write(visor_path,visor_model)
for name, base in [('engineering_handbook' ,'#174c43'), ('burnt_component','#45302c')]:
    im=Image.new('RGBA',(32,32),(0,0,0,0));d=ImageDraw.Draw(im)
    if name=='engineering_handbook':
        d.rounded_rectangle((5,2,26,29),radius=2,fill=base,outline='#9cbaa0',width=1)
        d.rectangle((7,4,9,27),fill='#b19563');d.text((18,10),'ECF',anchor='mm',font=FONT,fill='#e7e9ca')
        d.line((12,17,17,17,17,24,23,24),fill='#c9b272',width=1)
        for x,y in [(12,17),(23,24)]:d.rectangle((x-1,y-1,x+1,y+1),fill='#e4cf94')
    else:
        d.polygon([(7,8),(22,5),(27,19),(20,26),(9,23)],fill=base)
        d.rectangle((11,9,22,20),fill='#171b21');d.line((11,9,16,15,13,18,22,20),fill='#9e542e',width=2)
        for y in range(9,23,4):d.line((6,y,10,y+1),fill='#93979a',width=2)
    p=A/'textures/item'/f'{name}.png';p.parent.mkdir(parents=True,exist_ok=True);im.save(p)
    write(A/'models/item'/f'{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':'electron:item/'+name}})


def recipe(name, ingredients, count=1):
    ing=[{'tag': x[1:]} if x.startswith('#') else {'item': x if ':' in x else 'minecraft:'+x} for x in ingredients]
    write(RES/'data/electron/recipe'/f'{name}.json',{'type':'minecraft:crafting_shapeless','category':'redstone','ingredients':ing,'result':{'id':'electron:'+name,'count':count}})


recipe('wire_spool',['copper_ingot']*2+['paper']*2+['stick']+['#minecraft:planks']*2)
recipe('hand_crank',['copper_ingot']*2+['iron_ingot']*2+['redstone','lever'])
for n,material in [('100','coal'),('220','charcoal'),('1000','quartz')]:
    recipe('resistor_'+n,['copper_ingot']*2+['brick']*2+[material])
recipe('indicator_led',['copper_ingot']*2+['redstone','glass','red_dye'])
recipe('silicon_diode',['copper_ingot','copper_ingot','quartz','redstone'])
recipe('fuse_25ma',['copper_ingot','iron_nugget','glass'],2)
recipe('circuit_switch',['copper_ingot']*2+['lever','stone'])
recipe('momentary_button',['copper_ingot']*2+['stone_button','iron_nugget','stone'])
recipe('capacitor_100mf',['copper_ingot']*2+['iron_ingot','paper','paper','slime_ball'])
recipe('resistor_10000',['copper_ingot']*2+['brick']*2+['quartz','quartz'])
recipe('rechargeable_battery',['copper_ingot']*2+['iron_ingot']*2+['redstone']*2+['slime_ball','paper','quartz'])
recipe('terminal_strip',['copper_ingot']*2+['iron_ingot','brick','brick'])
recipe('electrical_redstone_switch',['repeater','copper_ingot','copper_ingot','iron_ingot','lever','paper'])
recipe('power_mosfet',['copper_ingot','copper_ingot','iron_ingot','quartz','quartz','paper'])
recipe('electrical_relay',['copper_ingot','copper_ingot','iron_ingot','iron_ingot','lever','paper'])
recipe('boost_converter',['copper_ingot','copper_ingot','iron_ingot','quartz','slime_ball','paper','redstone'])
recipe('buck_converter',['copper_ingot','copper_ingot','iron_ingot','quartz','paper','paper','redstone'])
recipe('boost_converter_12',['copper_ingot','copper_ingot','iron_ingot','quartz','slime_ball','paper','redstone','brick'])
recipe('buck_converter_3v3',['copper_ingot','copper_ingot','iron_ingot','quartz','paper','paper','redstone','iron_nugget'])
recipe('buck_converter_12',['copper_ingot','copper_ingot','iron_ingot','quartz','paper','paper','redstone','quartz'])
recipe('dc_generator',['iron_ingot']*3+['copper_ingot']*3+['lever','redstone','piston'])
recipe('distribution_box',['iron_ingot']*3+['copper_ingot']*3+['brick','paper'])
recipe('dc_breaker',['iron_ingot']*3+['copper_ingot']*2+['lever','quartz','paper'])
recipe('engineering_lamp',['iron_ingot']*2+['copper_ingot']*2+['glass']*3+['glowstone_dust'])
recipe('dc_motor',['iron_ingot']*3+['copper_ingot']*3+['piston','quartz','redstone'])
recipe('cable_spool',['copper_ingot']*4+['paper']*2+['string','iron_ingot'])
recipe('assembly_table',['crafting_table','iron_ingot','iron_ingot','copper_ingot']+['#minecraft:planks']*2)
recipe('wire_cutters',['iron_ingot','iron_ingot','stick'])
recipe('multimeter',['iron_ingot']*2+['copper_ingot']*2+['redstone','glass'])
recipe('calibration_wrench',['iron_ingot','iron_ingot','copper_ingot','stick'])
recipe('magnifying_visor',['glass','glass','copper_ingot','iron_ingot','leather'])
recipe('terminal',['copper_ingot','copper_ingot','brick'],4)
recipe('wall_port_2',['copper_ingot','copper_ingot','paper','paper','brick'])
recipe('wire_bridge',['paper','paper','brick'],8)
recipe('engineering_handbook',['book','redstone','paper'])
for fn in GATES:
    recipe('wire_'+fn+'_gate',['electron:'+fn+'_gate','copper_ingot','paper','quartz'])

for name in list(DEVICES)+['assembly_table']:
    entries=[{'type':'minecraft:item','name':'electron:'+name}]
    if name in DEVICES:
        entries=[{'type':'minecraft:alternatives','children':[
            {'type':'minecraft:item','name':'electron:burnt_component','conditions':[{'condition':'minecraft:block_state_property','block':'electron:'+name,'properties':{'broken':'true'}}]},
            {'type':'minecraft:item','name':'electron:'+name}]}]
        if name=='rechargeable_battery':entries[0]['children'][1]['functions']=[{'function':'minecraft:copy_components','source':'block_entity','include':['electron:battery_energy']}]
    write(RES/'data/electron/loot_table/blocks'/f'{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':entries,'conditions':[{'condition':'minecraft:survives_explosion'}]}]})

for path in (RES/'data/electron/recipe').glob('*.json'):
    # Unlock the complete starter catalog after obtaining copper or redstone.
    write(RES/'data/electron/advancement/recipes'/f'{path.stem}.json',{
        'parent':'minecraft:recipes/root','criteria':{'has_material':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['minecraft:copper_ingot','minecraft:redstone']}]}}},
        'requirements':[['has_material']],'rewards':{'recipes':['electron:'+path.stem]}})

items={'cable_spool':('Single-core Power Cable Spool','单芯低压电缆卷'),'wire_spool':('Insulated Wire Spool','绝缘导线线轴'),'wire_cutters':('Wire Cutters','剪线钳'),'multimeter':('Multimeter','万用表'),
       'calibration_wrench':('Calibration Wrench','校准扳手'),'magnifying_visor':('Precision Magnifying Visor','精密头戴放大镜'),
       'terminal':('Screw Terminal','接线端子'),'wall_port_2':('Two-core Wall Port','双芯穿墙端口'),'wire_bridge':('Wire Bridge','跨线桥'),
       'engineering_handbook':('Engineering Handbook','工程手册'),'burnt_component':('Burnt Component','损坏元件')}
for lang in ('en_us','zh_cn'):
    zh=lang=='zh_cn'
    data={'itemGroup.electron.components':'Electron · 小型电路' if zh else 'Electron · Small circuits',
          'itemGroup.electron.redstone':'Electron · 红石兼容' if zh else 'Electron · Redstone',
          'itemGroup.electron.workshop':'Electron · 工程工具' if zh else 'Electron · Workshop'}
    data.update({'block.electron.'+fn+'_gate':label[1]+'（红石）' if zh else label[0]+' Gate (Redstone)' for fn,label in GATES.items()})
    data['block.electron.binary_memory']='二进制存储器（红石）' if zh else 'Binary Memory (Redstone)'
    for name,(label,title) in DEVICES.items():
        en={'hand_crank':'Hand-crank Supply','indicator_led':'Indicator LED','circuit_switch':'Circuit Switch','silicon_diode':'Silicon Diode','fuse_25ma':'25 mA Fuse','momentary_button':'Momentary Button','capacitor_100mf':'100 mF Polarized Capacitor','rechargeable_battery':'Rechargeable Battery Module','terminal_strip':'Dual-bus Terminal Strip','electrical_redstone_switch':'Electrically Actuated Redstone Switch'}.get(name,label+' Gate (Wire)' if name.startswith('wire_') else label+' Resistor')
        data['block.electron.'+name]=title if zh else en
    data['block.electron.assembly_table']='手工装配台' if zh else 'Assembly Table'
    data.update({'item.electron.'+name:labels[1 if zh else 0] for name,labels in items.items()})
    data['key.categories.electron']='Electron 电路工坊' if zh else 'Electron Circuit Foundry'
    data['key.electron.magnify']='按住放大镜观察' if zh else 'Hold to magnify'
    data['tooltip.electron.magnifier']='右键戴上或放入头部槽；按住放大键（默认 Z）3×，潜行时 6×' if zh else 'Equip in head slot; hold magnify (default Z) for 3x, sneak for 6x'
    data['hud.electron.magnify']='精密观察 ×%s' if zh else 'Precision view x%s'
    specs={
        'small_component':('小型电路 · 方块器件 · 细导线端子','Small circuit · Block component · Fine-wire terminals'),
        'redstone_component':('红石兼容 · 原版信号接口','Redstone · Vanilla signal interface'),
        'redstone_rating':('信号 0–15；箭头侧输出，不直接接电学导线','Signal 0–15; arrow is output; no direct electrical connection'),
        'panel_height':('安装板面高度 3/16 格；凸起为功能部件','Mounting surface: 3/16 block; functional parts project above it'),
        'crank_voltage':('输出 5 V DC · 内阻 5 Ω','Output 5 V DC · Internal resistance 5 ohm'),
        'crank_energy':('储能 200 J · 每秒可摇一次增加 5 J','200 J capacity · One crank per second adds 5 J'),
        'resistor_rating':('%s Ω · 额定功率 0.25 W · 无极性','%s ohm · Rated power 0.25 W · Non-polar'),
        'resistor_voltage':('连续两端电压参考上限 %s V（由额定功率计算）','Continuous voltage reference limit %s V (power rating)'),
        'led_voltage':('正向工作压降约 2.1–2.4 V，必须限流','Forward operating drop about 2.1–2.4 V; current limiting required'),
        'led_current':('推荐 5–20 mA · 额定上限 30 mA · 有极性','Recommended 5–20 mA · Rated limit 30 mA · Polarized'),
        'led_series':('5 V 电源串 220 Ω；禁止直接接 5 V','Use 220 ohm in series at 5 V; do not connect directly'),
        'switch_rating':('闭合电阻 0.02 Ω；断开时高阻','Closed resistance 0.02 ohm; high impedance when open'),
        'gate_voltage':('供电 4.5–5.5 V · VCC / GND；A、B 输入，Y 输出','Supply 4.5–5.5 V · VCC/GND; A/B inputs, Y output'),
        'gate_current':('额定输出 20 mA · 输出内阻 100 Ω · 延迟 2 刻','Rated output 20 mA · Output resistance 100 ohm · 2 tick delay'),
        'gate_threshold':('低 ≤30% / 高 ≥70%；悬空 X，掉电 Z','Low <=30% / high >=70%; floating X, unpowered Z'),
        'diode_voltage':('正向压降约 0.7–0.8 V · A → K（银环）','Forward drop about 0.7–0.8 V · A to K (silver band)'),
        'diode_current':('额定电流 50 mA；反向阻断','Rated current 50 mA; blocks reverse current'),
        'fuse_rating':('额定电流 25 mA · 导通电阻 0.2 Ω','Rated current 25 mA · Closed resistance 0.2 ohm'),
        'fuse_replace':('持续过载后熔断，需更换；不能代替限流电阻','Blows under sustained overload; replace it; not a current limiter')}
    data.update({'tooltip.electron.'+key:value[0 if zh else 1] for key,value in specs.items()})
    for key,label in {'hand_crank':('手摇电源转动','Crank turning'),'switch_click':('开关切换','Switch clicks'),'led_on':('LED 接通','LED powers on'),'electrical_arc':('器件过载放电','Electrical arcing'),'component_fail':('器件烧毁','Component burns out'),'component_pop':('器件爆裂','Component pops'),'wrench_turn':('器件校准','Device rotated')}.items():data['subtitles.electron.'+key]=label[0 if zh else 1]
    language_path=A/'lang'/f'{lang}.json'
    localized=json.loads(language_path.read_text(encoding='utf-8')) if language_path.exists() else {}
    for key,value in data.items():localized.setdefault(key,value)
    write(language_path,localized)
write(RES/'data/minecraft/tags/block/mineable/pickaxe.json',{'replace':False,'values':['electron:'+n for n in list(DEVICES)+[fn+'_gate' for fn in GATES]+['binary_memory']]})
write(RES/'data/minecraft/tags/block/mineable/axe.json',{'replace':False,'values':['electron:assembly_table']})
# Keep the working face readable in either hand instead of showing a board edge.
for name in list(DEVICES)+list(items)+[fn+'_gate' for fn in GATES]+['binary_memory']:
    p=A/'models/item'/f'{name}.json';data=json.loads(p.read_text(encoding='utf-8'));display=data.setdefault('display',{})
    if name in DEVICES or name in ('multimeter','magnifying_visor','calibration_wrench','wire_cutters'):
        rotation=[0,180,0]
    elif name.endswith('_gate') or name=='binary_memory':rotation=[70,180,0]
    else:continue
    for hand in ('righthand','lefthand'):
        display['firstperson_'+hand]={'rotation':rotation,'translation':[0,1.4,-.6],'scale':[.58,.58,.58]}
        display['thirdperson_'+hand]={'rotation':rotation,'translation':[0,2.5,0],'scale':[.4,.4,.4]}
    write(p,data)
print(f'Generated {len(DEVICES)} electrical devices, redstone gates, {len(items)} tools/parts and resources.')
write(A/'sounds.json',{name:{'subtitle':'subtitles.electron.'+name,'sounds':[{'name':'minecraft:'+event,'type':'event','pitch':pitch,'volume':volume}]} for name,event,pitch,volume in [
    ('hand_crank','block.grindstone.use',.9,.7),('switch_click','block.lever.click',1,1),('led_on','block.note_block.hat',1.6,.6),
    ('electrical_arc','block.copper_bulb.turn_off',1.4,.8),('component_fail','block.fire.extinguish',1.4,.8),
    ('component_pop','entity.firework_rocket.blast',1.4,.8),('wrench_turn','block.tripwire.attach',.8,.8)]})
