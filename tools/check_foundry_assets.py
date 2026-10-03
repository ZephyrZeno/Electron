"""Resolve local model/texture references, exhaust block states and render a QA sheet.

The sheet is an offline orthographic inspection of actual model JSON and textures;
it is not a Minecraft screenshot. It uses only the existing Pillow dependency.
"""
from pathlib import Path
import json
import itertools
import math
import sys
from functools import lru_cache
from PIL import Image, ImageDraw, ImageFont, ImageEnhance

ROOT=Path(__file__).resolve().parents[1]
A=ROOT/'src/main/resources/assets/electron'
OUT=ROOT/'build/asset-review'
OUT.mkdir(parents=True,exist_ok=True)

@lru_cache(maxsize=None)
def read(path): return json.loads(path.read_text(encoding='utf-8'))
@lru_cache(maxsize=None)
def model(ref, seen=()):
    if not ref.startswith('electron:'): return {}
    assert ref not in seen, f'Parent cycle: {ref}'
    obj=read(A/'models'/(ref.split(':')[1]+'.json'))
    parent=model(obj.get('parent',''),seen+(ref,))
    return {**parent,**obj,'textures':{**parent.get('textures',{}),**obj.get('textures',{})}}
@lru_cache(maxsize=None)
def bitmap(p):return Image.open(p).convert('RGBA')
def texture(ref,tx):
    seen=set()
    while ref.startswith('#'):
        assert ref not in seen,f'Texture cycle: {ref}'
        seen.add(ref);ref=tx[ref[1:]]
    if ref.startswith('electron:'):
        p=A/'textures'/(ref.split(':')[1]+'.png');assert p.exists(),f'Missing {p}'
        return bitmap(p)
    raise AssertionError('Preview needs local texture: '+ref)

models=list((A/'models').rglob('*.json'))
for p in models:
    resolved=model('electron:'+p.relative_to(A/'models').with_suffix('').as_posix())
    for value in resolved.get('textures',{}).values():
        if value.startswith(('electron:','#')):texture(value,resolved['textures'])
    for element in resolved.get('elements',[]):
        assert all(a<b for a,b in zip(element['from'],element['to'])),f'Degenerate cube: {p}'
        assert all(-16<=v<=32 for v in element['from']+element['to']),f'Bounds: {p}'
        for face in element['faces'].values():texture(face['texture'],resolved['textures'])
    for override in resolved.get('overrides',[]):model(override['model'])

# The full mounted assembly, including the static crank preview, fits the 4/16 envelope.
for path in (A/'blockstates').glob('*.json'):
    if 'multipart' not in read(path) or path.stem in ('iron_pipe','glass_pipe','pipe_valve'):continue
    inventory=model('electron:block/'+path.stem+'_inventory')
    large=path.stem.startswith('substation_') or path.stem in ('dc_generator','distribution_box','dc_breaker','engineering_lamp','dc_motor','tower_terminal','thermal_generator','hydro_generator','wind_generator','lightning_collector','nuclear_reactor','substation','mains_inlet')
    assert min(e['from'][2] for e in inventory['elements'])>=(0 if large else 12),path
    up=model('electron:block/'+path.stem+'_off_up')
    assert max(e['to'][1] for e in up['elements'])<=(16 if large else 4),path
    if path.stem=='tower_terminal':continue
    plate=up['elements'][1]
    assert abs(plate['to'][1]-(15 if large else 3))<1e-9,(path,'Panel height must match device scale')

state_count=0
for path in (A/'blockstates').glob('*.json'):
    obj=read(path)
    if 'variants' in obj:
        for variant in obj['variants'].values():model(variant['model'])
    elif path.stem in ('iron_pipe','glass_pipe','pipe_valve'):
        for flags in itertools.product(['true','false'],repeat=6):
            state=dict(zip(('down','up','north','south','east','west'),flags))
            matches=[p for p in obj['multipart'] if all(state[k]==v for k,v in p.get('when',{}).items())]
            assert len(matches)==1+flags.count('true'),(path,state)
            for part in matches:model(part['apply']['model'])
    else:
        for facing,turn,broken,enabled,power in itertools.product(['up','down','north','south','east','west'],map(str,range(4)),['true','false'],['true','false'],map(str,range(16))):
            state=dict(facing=facing,turn=turn,broken=broken,enabled=enabled,power=power)
            matches=[p for p in obj['multipart'] if all(state[k] in v.split('|') for k,v in p['when'].items())]
            assert len(matches)==1,(path,state,len(matches))
            model(matches[0]['apply']['model']);state_count+=1

zh=read(A/'lang/zh_cn.json')
for name in ('ore_crusher','centrifuge','electric_kiln','fuel_fabricator','workshop_lamp','yard_lamp'):
    variants=read(A/'blockstates'/f'{name}.json')['variants']
    fixture=name in ('workshop_lamp','yard_lamp')
    assert len(variants)==24*(32 if fixture else 2),(name,'Unexpected mounted variants')
    for face,turn,broken,power in itertools.product(('up','down','north','south','west','east'),range(4),('false','true'),range(16)):
        key=f'facing={face},turn={turn},broken={broken}'+(f',power={power}' if fixture else '')
        assert key in variants,(name,key)
    for variant in variants.values():
        for element in model(variant['model'])['elements']:
            assert all(0<=v<=16 for v in element['from']+element['to']),(name,'Model escapes occupied block')
    display=model('electron:item/'+name)['display']
    assert all(hand in display for hand in ('firstperson_righthand','firstperson_lefthand','thirdperson_righthand','thirdperson_lefthand'))
for p in (A/'models/item').glob('*.json'):
    if p.stem.startswith(('wire_spool_','cable_spool_')) or p.stem.endswith('_item'):continue
    assert 'item.electron.'+p.stem in zh or 'block.electron.'+p.stem in zh,p
for path in (ROOT/'src/main/resources/data/electron/recipe').glob('*.json'):
    obj=read(path);name=obj['result']['id'].split(':')[1]
    assert (A/'models/item'/f'{name}.json').exists(),path
    assert (ROOT/'src/main/resources/data/electron/advancement/recipes'/path.name).exists(),path

font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',15)
small=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',11)

def project(p,scale=7.0):
    x,y,z=p
    return ((16-x-z)*scale+128,(x-z)*scale*.45-y*scale+163)

def face_image(canvas,im,points,shade,depths,zbuffer):
    tl,tr,br,bl=points
    ux,uy=tr[0]-tl[0],tr[1]-tl[1];vx,vy=bl[0]-tl[0],bl[1]-tl[1]
    det=ux*vy-vx*uy
    if abs(det)<.001:return
    iw,ih=im.size
    coeff=(iw*vy/det,-iw*vx/det,iw*(vx*tl[1]-vy*tl[0])/det,
           -ih*uy/det,ih*ux/det,ih*(uy*tl[0]-ux*tl[1])/det)
    im=ImageEnhance.Brightness(im).enhance(shade)
    warped=im.transform(canvas.size,Image.Transform.AFFINE,coeff,Image.Resampling.NEAREST)
    mask=Image.new('L',canvas.size);ImageDraw.Draw(mask).polygon(points,fill=255)
    target=canvas.load();pixels=warped.load();covered=mask.load()
    left=max(0,int(min(p[0] for p in points)));right=min(canvas.width,int(max(p[0] for p in points))+1)
    top=max(0,int(min(p[1] for p in points)));bottom=min(canvas.height,int(max(p[1] for p in points))+1)
    for y in range(top,bottom):
        for x in range(left,right):
            if not covered[x,y]:continue
            u=(coeff[0]*x+coeff[1]*y+coeff[2])/iw;v=(coeff[3]*x+coeff[4]*y+coeff[5])/ih
            depth=depths[0]+u*(depths[1]-depths[0])+v*(depths[3]-depths[0])
            index=y*canvas.width+x
            if depth>=zbuffer[index] and pixels[x,y][3]>0:
                zbuffer[index]=depth;target[x,y]=pixels[x,y]

def render(ref):
    obj=model(ref);tx=obj['textures'];quads=[]
    for e in obj.get('elements',[]):
        x0,y0,z0=e['from'];x1,y1,z1=e['to']
        faces={'north':([(x1,y1,z0),(x0,y1,z0),(x0,y0,z0),(x1,y0,z0)],.78),
               'east':([(x1,y1,z1),(x1,y1,z0),(x1,y0,z0),(x1,y0,z1)],.90),
               'up':([(x0,y1,z0),(x1,y1,z0),(x1,y1,z1),(x0,y1,z1)],1.0)}
        for name,(verts,shade) in faces.items():
            if name not in e['faces']:continue
            f=e['faces'][name];im=texture(f['texture'],tx)
            u0,v0,u1,v1=f.get('uv',[0,0,16,16]);w,h=im.size
            im=im.crop((min(u0,u1)*w/16,min(v0,v1)*h/16,max(u0,u1)*w/16,max(v0,v1)*h/16))
            if u1<u0:im=im.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
            if v1<v0:im=im.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
            if 'rotation' in e:
                rot=e['rotation'];ox,oy,oz=rot['origin'];angle=math.radians(rot['angle']);c,si=math.cos(angle),math.sin(angle)
                points=[]
                for x,y,z in verts:
                    x,y,z=x-ox,y-oy,z-oz
                    if rot['axis']=='x':y,z=y*c-z*si,y*si+z*c
                    elif rot['axis']=='y':x,z=x*c+z*si,-x*si+z*c
                    else:x,y=x*c-y*si,x*si+y*c
                    points.append((x+ox,y+oy,z+oz))
                verts=points
            depths=[x-z+y*.9 for x,y,z in verts]
            quads.append((im,list(map(project,verts)),shade,depths))
    canvas=Image.new('RGBA',(256,220),(23,33,43,255))
    zbuffer=[float('-inf')]*(canvas.width*canvas.height)
    for im,verts,shade,depths in quads:face_image(canvas,im,verts,shade,depths,zbuffer)
    if not quads:
        im=texture(tx['layer0'],tx).resize((128,128),Image.Resampling.NEAREST);canvas.alpha_composite(im,(64,35))
    return canvas

def sheet(filename,title,names):
    rows=(len(names)+3)//4
    canvas=Image.new('RGB',(1056,62+rows*252),'#0e1722');d=ImageDraw.Draw(canvas)
    d.text((18,14),title,font=font,fill='#a6e5cc');d.text((18,38),'实际 JSON 模型与像素贴图 · 离线几何预览（非游戏截图）',font=small,fill='#8ca1b2')
    for i,(name,variant) in enumerate(names):
        x=8+(i%4)*262;y=62+(i//4)*252
        canvas.paste(render('electron:'+variant),(x,y));key=('block.electron.' if 'block.electron.'+name in zh else 'item.electron.')+name
        d.text((x+10,y+222),zh[key],font=font,fill='#e5edf3')
    canvas.save(OUT/filename)

if '--processing' in sys.argv:
    sheet('processing.png','ELECTRON / 用电加工、立体材料与照明',[(n,'item/'+n) for n in ['ore_crusher','centrifuge','electric_kiln','fuel_fabricator','crushed_uranium','fuel_concentrate','uranium_tailings','fuel_cladding','iron_plate','copper_coil','workshop_lamp','yard_lamp']])
    (OUT/'validation.json').write_text(json.dumps({'models':len(models),'electrical_block_states':state_count},indent=2),encoding='utf-8')
    sys.exit(0)
if '--industrial' in sys.argv:
    sheet('industrial.png','ELECTRON / 机组部件、供水与接线',[(n,'item/'+n) for n in ['boiler_module','turbine_module','alternator_module','condenser_module','plant_control_module','reactor_vessel_module','control_rod_drive','safety_valve_module','water_pump','wind_mast','wind_base','cable_breakout','iron_pipe','glass_pipe','pipe_valve','boiler_water_tank','reactor_coolant_tank','industrial_assembly_table','low_profile_socket','plant_fuel_port']])
    sheet('service.png','ELECTRON / 加料与六模块工作台',[(n,'item/'+n) for n in ['plant_control_module','plant_fuel_port','industrial_assembly_table','uranium_ore','raw_uranium','uranium_pellet','fuel_rod','spent_fuel_rod']])
    print(OUT/'industrial.png')
    (OUT/'validation.json').write_text(json.dumps({'models':len(models),'electrical_block_states':state_count},indent=2),encoding='utf-8')
    sys.exit(0)
gates=['and','or','not','xor','nand','nor','xnor','buffer']
sheet('gates.png','ELECTRON / 薄板逻辑门 · 红石版与电线版',[(g+'_gate','block/'+g+'_gate_on') for g in gates]+[('wire_'+g+'_gate','block/wire_'+g+'_gate_inventory') for g in gates])
names=['hand_crank','resistor_100','resistor_220','resistor_1000','indicator_led','circuit_switch','assembly_table','binary_memory','wire_spool','wire_cutters','multimeter','terminal','wall_port_2','wire_bridge','engineering_handbook','burnt_component']
names += ['calibration_wrench','silicon_diode','fuse_25ma','magnifying_visor']
names += ['momentary_button','capacitor_100mf','resistor_10000']
names += ['rechargeable_battery','terminal_strip','electrical_redstone_switch']
names += ['power_mosfet','electrical_relay','boost_converter','buck_converter','dc_generator','distribution_box','dc_breaker','engineering_lamp','dc_motor','cable_spool']
names += ['boost_converter_12','buck_converter_3v3','buck_converter_12']
sheet('equipment.png','ELECTRON / 电路工坊 · 首批设备与工具',[(name,'item/'+name) for name in names])
sheet('towers.png','ELECTRON / 玩家搭建塔架与绝缘接线横担',[('tower_section','item/tower_section'),('tower_terminal','item/tower_terminal'),('tower_terminal','block/tower_terminal_off_up')])
sheet('grid.png','ELECTRON / 大型供电与传动',[(n,'item/'+n) for n in ['thermal_generator','hydro_generator','wind_generator','lightning_collector','nuclear_reactor','substation','mains_inlet','plant_casing','reactor_casing','transmission_shaft','rotating_platform','uranium_ore']])
print(f'Validated {len(models)} models, {state_count} electrical block states, recipe models and Chinese names.')
print(OUT)
(OUT/'validation.json').write_text(json.dumps({'models':len(models),'electrical_block_states':state_count},indent=2),encoding='utf-8')
