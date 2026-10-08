"""Generate the analog PCB and environmental-sensor additions without touching older models."""
from pathlib import Path
import argparse,copy,json,math
from generate_pcb_sequential_assets import cube,lead,DISPLAY,ASSETS,DATA,TEXTURES
from refine_model_surfaces import refine

TX={**TEXTURES,**{n:'electron:block/pcb/'+n for n in ('laminate','gold_trace','resistor','led_off','terminal_red','display_glass')}}
PARTS=('r220','r100k','diode','cap100uf','pot10k','comparator','schmitt','mosfet','buck5')
INGREDIENTS={
 'smd_r220':['resistor_220','copper_foil'], 'smd_r100k':['smd_r10k','minecraft:quartz','copper_foil'],
 'smd_diode':['silicon_diode','copper_foil'], 'smd_cap100uf':['minecraft:iron_nugget','copper_foil','minecraft:paper','solder_alloy'],
 'smd_pot10k':['smd_r10k','minecraft:iron_nugget','copper_foil'],
 'smd_comparator':['smd_not','smd_not','smd_r10k','minecraft:quartz','solder_alloy'],
 'smd_schmitt':['smd_comparator','smd_sr_latch','smd_r10k','solder_alloy'],
 'smd_mosfet':['power_mosfet','copper_foil','solder_alloy'],
 'smd_buck5':['smd_mosfet','smd_diode','smd_cap100uf','smd_r10k','copper_coil','solder_alloy'],
 'light_sensor':['electrical_relay','smd_comparator','smd_pot10k','minecraft:glass','minecraft:quartz','smd_led'],
 'rain_sensor':['electrical_relay','smd_schmitt','smd_r100k','copper_foil','blank_pcb']}
def c(a,b,t,n='detail'):return cube(a,b,t,n)
def obj(elements,display=DISPLAY):
    value={'parent':'minecraft:block/block','ambientocclusion':True,'textures':TX,'elements':elements,'display':display}
    refine(value);return value
def cylinder(x,y,z,r,h,tex):
    out=[]
    for j in range(12):
        a=-r+2*r*j/12;b=-r+2*r*(j+1)/12;d=math.sqrt(max(0,r*r-((a+b)/2)**2))
        out.append(c((x+a,y,z-d),(x+b,y+h,z+d),tex,'round_'+str(j)))
    return out
def item(part):
    if part in ('r220','r100k','diode'):
        e=[c((4,6,6),(12,7.4,10),'rubber' if part=='diode' else 'resistor','body')]
        for x in (3.3,11.3):e.append(c((x,5.8,6),(x+1.4,7.55,10),'solder_joint','cap'))
        if part=='diode':e.append(c((10.3,7.4,6),(10.9,7.48,10),'enamel','cathode'))
        else:
            for x in ((5.6,7.5) if part=='r220' else (5.6,7,8.4,9.8)):e.append(c((x,7.4,6.1),(x+.4,7.47,9.9),'rubber','band'))
        return obj(e)
    if part=='cap100uf':
        e=[c((4,5.4,5),(12,5.9,11),'rubber','base')]+cylinder(8,5.9,8,3.1,4.3,'brushed_steel')
        e += [c((5.2,10.2,7.85),(10.8,10.26,8.15),'rubber','vent_x'),c((7.85,10.2,5.2),(8.15,10.26,10.8),'rubber','vent_z'),c((4.6,6,7.5),(4.9,9.7,8.5),'terminal_red','polarity')]
        for x in (2.8,11.2):e.append(c((x,5.65,7.4),(x+2,6.05,8.6),'solder_joint','lead'))
        return obj(e)
    if part=='pot10k':
        e=[c((4.2,6,4.2),(11.8,8.4,11.8),'enamel','case')]+cylinder(8,8.4,8,2.8,.6,'brushed_steel')
        e.append(c((5.6,9,7.8),(10.4,9.08,8.2),'rubber','adjuster'))
        for k,(side,v) in enumerate(((0,5.1),(0,10.9),(2,8))):e+=lead(side,v,k)
        return obj(e)
    if part=='buck5':
        e=[c((2.5,5.7,2.5),(13.5,6.05,13.5),'laminate','substrate'),c((4,6.05,4),(9.4,7.8,9.4),'rubber','inductor')]
        for x in (4.6,5.5,6.4,7.3,8.2):e.append(c((x,7.8,4.2),(x+.5,8.05,9.2),'gold_trace','winding'))
        e += cylinder(11,6.1,5,1.5,2.8,'brushed_steel')+[c((5,6.1,10.3),(9,6.9,12.3),'ic_package','controller')]
        for k,(side,v) in enumerate(((0,5.1),(0,10.9),(2,5.1),(2,10.9))):e+=lead(side,v,k)
        return obj(e)
    e=[c((4.3,6.1,4.3),(11.7,6.65,11.7),'rubber','lower'),c((4.3,6.65,4.3),(11.7,7.8,11.7),'ic_package','mold'),c((4.9,7.8,5),(5.6,7.9,5.7),'enamel','pin_one')]
    pins=((0,5.1),(0,10.9),(2,8)) if part=='mosfet' else ((0,5.1),(0,8),(0,10.9),(2,5.1),(2,10.9))
    for k,(side,v) in enumerate(pins):e+=lead(side,v,k)
    marks={'comparator':[(6,6,6.5,10),(6.5,6.5,7.5,9.5),(7.5,7,8.5,9),(8.5,7.6,10,8.4)],
           'schmitt':[(6,6.5,8.5,7),(8,7,8.5,9.5),(8.5,9,10.5,9.5)],
           'mosfet':[(6,6,6.5,10),(7.5,6,8,10),(8,6,10,6.5),(8,9.5,10,10)]}[part]
    for x,z,xx,zz in marks:e.append(c((x,7.8,z),(xx,7.88,zz),'enamel','symbol'))
    return obj(e)
def sensor(name,on=False):
    e=[c((0,0,0),(16,2.6,16),'rubber','base'),c((.1,2.6,.1),(15.9,2.8,15.9),'laminate','board')]
    for x in (3,11):
        for z in (3,13):e += [c((x-.7,2.8,z-.7),(x+.7,3.1,z+.7),'enamel','terminal'),c((x-.3,3.1,z-.3),(x+.3,3.22,z+.3),'solder_joint','screw')]
    e += [c((5,2.8,5),(9,3.35,11),'ic_package','relay'),c((5.4,3.35,8),(8.6,3.45,10.5),'enamel','seal')]
    if name=='light_sensor':
        e += cylinder(7,3.35,7,1.6,.25,'brushed_steel')+cylinder(7,3.6,7,1.35,.25,'display_glass')
        for z in (6.2,6.7,7.2,7.7):e.append(c((6.1,3.85,z),(7.9,3.89,z+.12),'gold_trace','photosurface'))
    else:
        e += [c((5.1,3.45,5.2),(8.9,3.65,10.7),'enamel','sensing_plate')]
        for j in range(7):
            x=5.35+j*.5;e.append(c((x,3.65,5.6 if j%2==0 else 6.2),(x+.18,3.73,10 if j%2==0 else 10.4),'gold_trace','comb'))
    e += [c((5.3,3.35,10.72),(6.1,3.5,10.99),'terminal_red' if on else 'rubber','status')]
    return e
def transform(elements,face,turn):
    # Models start on the UP face. Rotate geometry and face normals together.
    def vec(p,center=True):
        x,y,z=p
        if center:x-=8;y-=8;z-=8
        for _ in range(turn):x,z=-z,x
        q={'up':(x,y,z),'down':(x,-y,z),'north':(x,z,-y),'south':(x,z,y),'west':(-y,z,x),'east':(y,z,x)}[face]
        return tuple(v+8 if center else v for v in q)
    dirs={'up':(0,1,0),'down':(0,-1,0),'north':(0,0,-1),'south':(0,0,1),'east':(1,0,0),'west':(-1,0,0)};inverse={v:k for k,v in dirs.items()}
    result=[]
    for el in elements:
        a,b=vec(el['from']),vec(el['to']);out=copy.deepcopy(el);out['from']=[min(x,y) for x,y in zip(a,b)];out['to']=[max(x,y) for x,y in zip(a,b)]
        out['faces']={inverse[vec(dirs[k],False)]:v for k,v in out['faces'].items()};result.append(out)
    return result
def files():
    out={ASSETS/'models/item'/f'smd_{p}.json':item(p) for p in PARTS}
    block_display=copy.deepcopy(DISPLAY);block_display['gui']['scale']=[.65]*3
    for name in ('light_sensor','rain_sensor'):
        variants={}
        for look in ('off','on','broken'):
            for face in ('up','down','north','south','west','east'):
                for turn in range(4):
                    ref=f'{name}_{look}_{face}_{turn}';model=obj(transform(sensor(name,look=='on'),face,turn),block_display)
                    if look=='broken':model['textures']={k:'electron:block/scorched' for k in model['textures']}
                    out[ASSETS/'models/block'/f'{ref}.json']=model
                    for power in range(16):
                        if look=='broken' or (power>0)==(look=='on'):variants[f'facing={face},turn={turn},broken={str(look=="broken").lower()},power={power}']={'model':'electron:block/'+ref}
        out[ASSETS/'blockstates'/f'{name}.json']={'variants':variants};out[ASSETS/'models/item'/f'{name}.json']=obj(sensor(name),block_display)
        out[DATA/'loot_table/blocks'/f'{name}.json']=json.loads((DATA/'loot_table/blocks/temperature_sensor.json').read_text().replace('temperature_sensor',name))
    for name,ingredients in INGREDIENTS.items():
        ids=[v if ':' in v else 'electron:'+v for v in ingredients]
        out[DATA/'recipe'/f'{name}.json']={'type':'minecraft:crafting_shapeless','category':'misc','ingredients':[{'item':v} for v in ids],'result':{'id':'electron:'+name,'count':1}}
        out[DATA/'advancement/recipes'/f'{name}.json']={'criteria':{'material':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':[ids[0]]}]}}},'requirements':[['material']],'rewards':{'recipes':['electron:'+name]}}
    return out
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--check',action='store_true');args=parser.parse_args()
    for path,value in files().items():
        if 'elements' in value:
            for el in value['elements']:
                assert el['faces'] and all(0<=a<b<=16 for a,b in zip(el['from'],el['to'])),path
            again=copy.deepcopy(value);refine(again);assert again==value,path
        data=json.dumps(value,ensure_ascii=False,indent=2)+'\n'
        if args.check:assert path.read_text(encoding='utf-8')==data,path
        else:path.parent.mkdir(parents=True,exist_ok=True);path.write_text(data,encoding='utf-8')
    print('Verified 9 miniature components and 2 mounted environmental sensors.' if args.check else 'Generated analog and sensor assets.')
