"""Deterministic workshop models, industrial materials, localized instructions and original sound samples."""
from pathlib import Path
import json,math,random,sys
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1];A=ROOT/'src/main/resources/assets/electron';D=ROOT/'src/main/resources/data/electron'
def write(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
materials={'steel_pipe':'#697f86','cooling_concrete':'#b9b6a8','roof_panels':'#41606e','instrument_glass':'#326578','safety_yellow':'#dbb64a'}
for n,c in materials.items():
 im=Image.new('RGB',(64,64),c);d=ImageDraw.Draw(im);rng=random.Random(n)
 for _ in range(250):
  x,y=rng.randrange(64),rng.randrange(64);rgb=im.getpixel((x,y));d.point((x,y),fill=tuple(max(0,min(255,k+rng.randint(-12,12))) for k in rgb))
 for y in range(0,64,16):d.line((0,y,63,y),fill='#66767a',width=1)
 if n=='instrument_glass':d.line((8,5,38,55),fill='#a0d3cf',width=2)
 if n=='safety_yellow':
  for x in range(-64,96,16):d.line((x,0,x+64,64),fill='#30383b',width=7)
 im.save(A/'textures/block/assembled'/f'{n}.png')
P=A/'textures/block/industrial';P.mkdir(parents=True,exist_ok=True)
for n,c in [('water',(44,123,190,180)),('water_flow',(145,221,239,140)),('glass',(180,221,227,0))]:
 im=Image.new('RGBA',(64,64),c);d=ImageDraw.Draw(im)
 if n=='glass':d.rectangle((0,0,63,63),outline=(168,211,215,255),width=3);d.line((5,45,46,4),fill=(186,229,230,170),width=2)
 else:
  for y in range(3,64,12):d.arc((2,y-6,64,y+8),0,180,fill=(155,222,240,180),width=2)
 im.save(P/f'{n}.png')
TX={n:'electron:block/assembled/'+n for n in list(materials)+['galvanized','cast_base','control_case','service_panel','copper_bus','turbine_white','boiler','hydro_blue','reactor_shell','porcelain']}
TX['glass']='electron:block/industrial/glass';TX['particle']=TX['galvanized']
def cube(a,b,t):return {'from':list(a),'to':list(b),'faces':{f:{'texture':'#'+t,'uv':[0,0,16,16]} for f in ('north','south','east','west','up','down')}}
def model(n,els):write(A/'models/block'/f'{n}.json',{'parent':'minecraft:block/block','ambientocclusion':True,'textures':TX,'elements':els})
def plain(n,els):model(n,els);write(A/'blockstates'/f'{n}.json',{'variants':{'':{'model':'electron:block/'+n}}});write(A/'models/item'/f'{n}.json',{'parent':'electron:block/'+n})
def loot(n):write(D/'loot_table/blocks'/f'{n}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'electron:'+n}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
def craft(n,items,count=1):
 write(D/'recipe'/f'{n}.json',{'type':'minecraft:crafting_shapeless','category':'misc','ingredients':[{'item':x if ':' in x else 'minecraft:'+x} for x in items],'result':{'id':'electron:'+n,'count':count}})
 write(D/'advancement/recipes'/f'{n}.json',{'criteria':{'has_item':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':'minecraft:iron_ingot'}]}}},'requirements':[['has_item']],'rewards':{'recipes':['electron:'+n]}})
names={
 'wind_mast':('风机锥形塔筒','Tubular Wind Mast'),'boiler_module':('锅炉机组部件','Boiler Module'),'turbine_module':('汽轮机部件','Steam Turbine Module'),'alternator_module':('发电机部件','Alternator Module'),'condenser_module':('冷凝器部件','Condenser Module'),'plant_control_module':('机组控制柜部件','Plant Control Module'),'reactor_vessel_module':('反应堆压力容器部件','Reactor Vessel Module'),'control_rod_drive':('控制棒驱动部件','Control Rod Drive'),'safety_valve_module':('安全阀部件','Safety Valve Module'),'water_pump':('24 V 电动水泵','24 V Water Pump'),'wind_base':('风机底部接线柜','Wind Turbine Base Cabinet'),'cable_breakout':('电缆扩展接线座','Cable Breakout Socket'),'iron_pipe':('铁皮水管','Steel Water Pipe'),'glass_pipe':('透明水管','Transparent Water Pipe'),'pipe_valve':('管道截止阀','Pipe Shutoff Valve'),'boiler_water_tank':('锅炉补水槽','Boiler Feedwater Tank'),'reactor_coolant_tank':('核电冷却水槽','Reactor Cooling Reservoir'),'industrial_assembly_table':('大型机组装配台','Industrial Assembly Workbench')}
for n in names:loot(n)
# Each module has a recognisable silhouette and visible fasteners/ports.
for i,n in enumerate(['boiler_module','turbine_module','alternator_module','condenser_module','plant_control_module','reactor_vessel_module','control_rod_drive','safety_valve_module']):
 els=[cube((0,0,0),(16,2,16),'cast_base')]
 if n in ('boiler_module','reactor_vessel_module'):
  for x,z,X,Z in [(3,2,13,14),(2,3,14,13),(1,5,15,11)]:els.append(cube((x,2,z),(X,13,Z),'boiler' if i==0 else 'reactor_shell'))
  els+=[cube((4,13,4),(12,15,12),'galvanized'),cube((7,15,7),(9,16,9),'copper_bus')]
 elif n=='plant_control_module':
  els+=[cube((2,2,3),(14,15,13),'control_case'),cube((3,8,2.8),(13,13,3),'instrument_glass')]
  for x in (4,7,10):els.append(cube((x,5,2.5),(x+1.2,6,3),'safety_yellow'))
 elif n=='condenser_module':
  els.append(cube((2,2,3),(14,13,13),'control_case'))
  for x in range(2,15,2):els.append(cube((x,3,1),(x+.6,12,15),'copper_bus'))
 elif n=='control_rod_drive':
  for x in (3,7,11):els.append(cube((x,2,4),(x+2,15,12),'galvanized'))
  els.append(cube((2,10,3),(14,13,13),'control_case'))
 elif n=='safety_valve_module':
  els+=[cube((6,2,1),(10,7,15),'steel_pipe'),cube((7,6,7),(9,14,9),'copper_bus')]
  for a,b in [((3,13,3),(13,14.5,4)),((3,13,12),(13,14.5,13)),((3,13,4),(4,14.5,12)),((12,13,4),(13,14.5,12)),((7.5,13,4),(8.5,14.5,12))]:els.append(cube(a,b,'safety_yellow'))
 elif n=='turbine_module':
  els+=[cube((4,3,2),(12,13,14),'steel_pipe'),cube((2,5,2.2),(14,11,13.8),'hydro_blue'),cube((6.5,6.5,0),(9.5,9.5,16),'galvanized'),cube((6,13.05,4),(10,16,8),'copper_bus')]
  for z in (1.8,5,9,13.7):els.append(cube((2.5,4,z),(13.5,12,z+.45),'galvanized'))
 elif n=='alternator_module':
  els += [cube((3,3,3),(13,12,13),'cast_base'),cube((6,6,0),(10,9,16),'galvanized')]
  for z in range(3,13,2):
   els += [cube((2,4,z),(3,11,z+1),'copper_bus'),cube((13,4,z),(14,11,z+1),'copper_bus'),cube((3,12,z),(13,13,z+1),'copper_bus')]
  for z in (1,13):els.append(cube((2,3,z),(14,12,z+1),'control_case'))
  els.append(cube((5,13.1,5),(11,15,11),'service_panel'))
 plain(n,els)
plain('wind_mast',[cube((3,0,3),(13,16,13),'turbine_white'),cube((2.5,0,2.5),(13.5,1,13.5),'galvanized')])
def transform(els,face,turn):
 def pos(p):
  x,y,z=p
  for _ in range(turn):x,z=16-z,x
  return {'up':(x,y,z),'down':(x,16-y,z),'north':(x,z,16-y),'south':(x,z,y),'west':(16-y,z,x),'east':(y,z,x)}[face]
 out=[]
 for e in els:
  a,b=pos(e['from']),pos(e['to']);out.append({**e,'from':[min(x,y) for x,y in zip(a,b)],'to':[max(x,y) for x,y in zip(a,b)]})
 return out
for n in ('water_pump','wind_base','cable_breakout'):
 els=[cube((0,0,0),(16,2,16),'cast_base'),cube((.4,2,.4),(15.6,15,15.6),'control_case')]
 if n=='water_pump':
  els=[cube((0,0,0),(16,2,16),'cast_base'),cube((4,2,3),(12,13,13),'hydro_blue'),cube((2.5,4,4),(13.5,11,12),'hydro_blue'),cube((1,14,5),(15,15,9),'control_case')]
 pins=[(3,7),(11,7)] if n=='water_pump' else [(3,3),(11,3)] if n=='wind_base' else [(x,z) for x in (3,11) for z in (3,7,11,15)]
 if n!='water_pump':els.append(cube((.7,14.7,.7),(15.3,15,15.3),'service_panel'))
 for x,z in pins:els += [cube((x-1,15,z-1),(x+1,15.25,z+1),'porcelain'),cube((x-.4,15.25,z-.4),(x+.4,15.55,z+.4),'copper_bus')]
 if n=='water_pump':
  els += [cube((5,3,.2),(11,9,15.8),'steel_pipe'),cube((6,15.1,5),(10,15.8,10),'hydro_blue')]
  for z in (0,14):els.append(cube((4,2,z),(12,10,z+2),'galvanized'))
  for z in (4,6,8,10):els.append(cube((2,4,z),(14,12,z+.45),'galvanized'))
 elif n=='wind_base':els += [cube((6,2,0),(10,12,.5),'safety_yellow'),cube((6,15.05,6),(10,15.5,10),'instrument_glass')]
 else:
  els += [cube((6,15.05,1),(8,15.35,15),'safety_yellow')]
 variants={}
 for f in ('up','down','north','south','east','west'):
  for t in range(4):
   ident=f'{n}_{f}_{t}';model(ident,transform(els,f,t));variants[f'facing={f},turn={t}']={'model':'electron:block/'+ident}
 write(A/'blockstates'/f'{n}.json',{'variants':variants});write(A/'models/item'/f'{n}.json',{'parent':'electron:block/'+n+'_up_0'})
# Pipe arms are real connected geometry, with transparent sleeves over a visible water core.
for n,glass in [('iron_pipe',False),('glass_pipe',True),('pipe_valve',False)]:
 core=[cube((4.8,4.8,4.8),(11.2,11.2,11.2),'glass' if glass else 'steel_pipe')]
 if n=='pipe_valve':
  core.append(cube((7,11,7),(9,14,9),'copper_bus'))
  for a,b in [((3,13,3),(13,14.5,4)),((3,13,12),(13,14.5,13)),((3,13,4),(4,14.5,12)),((12,13,4),(13,14.5,12)),((7.5,13,4),(8.5,14.5,12))]:core.append(cube(a,b,'safety_yellow'))
 model(n+'_core',core)
 multipart=[{'apply':{'model':'electron:block/'+n+'_core'}}]
 coords={'down':((5.4,0,5.4),(10.6,8,10.6)),'up':((5.4,8,5.4),(10.6,16,10.6)),'north':((5.4,5.4,0),(10.6,10.6,8)),'south':((5.4,5.4,8),(10.6,10.6,16)),'west':((0,5.4,5.4),(8,10.6,10.6)),'east':((8,5.4,5.4),(16,10.6,10.6))}
 for face,(a,b) in coords.items():
  arm=[cube(a,b,'glass' if glass else 'steel_pipe')]
  # A narrow coupling ring identifies each socket without blocking the water window.
  axis=next(k for k in range(3) if (a[k],b[k]) in ((0,8),(8,16)))
  lo=[4.9,4.9,4.9];hi=[11.1,11.1,11.1];lo[axis]=.1 if a[axis]==0 else 14.8;hi[axis]=1.2 if a[axis]==0 else 15.9
  arm.append(cube(lo,hi,'galvanized'))
  model(n+'_'+face,arm);multipart.append({'when':{face:'true'},'apply':{'model':'electron:block/'+n+'_'+face}})
 write(A/'blockstates'/f'{n}.json',{'multipart':multipart})
 inventory=core.copy()
 for f in ('north','south'):
  a,b=coords[f];a,b=list(a),list(b);a[2]=max(.2,a[2]);b[2]=min(15.8,b[2]);inventory.append(cube(a,b,'glass' if glass else 'steel_pipe'))
 model(n+'_inventory',inventory+[cube((4.9,4.9,z),(11.1,11.1,z+1),'galvanized') for z in (0,15)])
 write(A/'models/item'/f'{n}.json',{'parent':'electron:block/'+n+'_inventory'})
for n,nuclear in [('boiler_water_tank',False),('reactor_coolant_tank',True)]:
 els=[cube((1,0,1),(15,1,15),'cast_base'),cube((1,15,1),(15,16,15),'safety_yellow' if nuclear else 'galvanized')]
 for x in (1,14):
  for z in (1,14):els.append(cube((x,1,z),(x+1,15,z+1),'galvanized'))
 for a,b in [((2,1,1),(14,15,1.15)),((2,1,14.85),(14,15,15)),((1,1,2),(1.15,15,14)),((14.85,1,2),(15,15,14))]:els.append(cube(a,b,'glass'))
 els += [cube((6,6,0),(10,10,1),'copper_bus'),cube((4,15.6,4),(12,15.8,12),'control_case')]
 # Give the high-capacity reservoir reinforced bands and an independent inspection panel.
 if nuclear:
  for y in (4,11):
   for a,b in [((1,y,1),(15,y+.65,1.35)),((1,y,14.65),(15,y+.65,15)),((1,y,1),(1.35,y+.65,15)),((14.65,y,1),(15,y+.65,15))]:els.append(cube(a,b,'galvanized'))
  els.append(cube((2.3,6,.7),(5.2,12,1.1),'instrument_glass'))
 plain(n,els)
for part in range(6):
 els=[cube((0,12,0),(16,16,16),'galvanized'),cube((2,0,2),(14,12,14),'control_case')]
 if part<3:
  for y in (3,7):els+=[cube((2,y,1.7),(14,y+3,2),'service_panel'),cube((6,y+1,1.35),(10,y+1.5,1.8),'copper_bus')]
 else:
  for z in (5,10):els.append(cube((2,15.7,z),(14,16,z+.25),'cast_base'))
 model('industrial_table_'+str(part),els)
write(A/'blockstates/industrial_assembly_table.json',{'variants':{f'part={p},facing={f}':{'model':f'electron:block/industrial_table_{p}','y':rot} for p in range(6) for f,rot in [('north',0),('east',90),('south',180),('west',270)]}})
write(A/'models/item/industrial_assembly_table.json',{'parent':'electron:block/industrial_table_0'})
craft('industrial_assembly_table',['electron:assembly_table','iron_block','iron_block','copper_block','crafting_table'])
craft('wind_mast',['iron_ingot']*5+['electron:plant_casing'],2);craft('wind_base',['electron:mains_inlet','iron_ingot','copper_ingot'])
craft('water_pump',['electron:dc_motor','bucket','electron:iron_pipe','iron_ingot','copper_ingot']);craft('cable_breakout',['electron:terminal_strip','iron_ingot','iron_ingot','copper_ingot'])
craft('iron_pipe',['iron_ingot']*3,8);craft('glass_pipe',['glass']*3+['iron_nugget']*2,8);craft('pipe_valve',['electron:iron_pipe','iron_ingot','copper_ingot'])
craft('boiler_water_tank',['iron_ingot']*4+['glass','bucket']);craft('reactor_coolant_tank',['electron:boiler_water_tank','electron:reactor_casing','iron_block','glass','glass'])
craft('thermal_generator',['electron:plant_control_module','electron:boiler_module','electron:alternator_module','electron:plant_casing'])
craft('nuclear_reactor',['electron:plant_control_module','electron:reactor_vessel_module','electron:control_rod_drive','electron:reactor_casing'])
# Original mono Ogg/Vorbis samples: no downloads, external packs or third-party recordings.
sys.path.insert(0,str(ROOT/'build/audio-tools'))
try:
 import numpy as np,soundfile as sf
except ModuleNotFoundError:
 np=sf=None  # Committed Ogg samples are sufficient for normal model/locale regeneration.
specs={'wire_connect':(.18,1200,.15),'cable_connect':(.3,170,.32),'wire_cut':(.22,2100,.30),'terminal_attach':(.26,550,.22),'machine_assemble':(1.0,120,.28),'machine_form':(.8,85,.18),'pump_run':(1.8,140,.17),'valve_turn':(.45,340,.22),'water_fill':(.9,310,.30),'motor_run':(1.8,95,.12),'wind_rotor':(1.8,62,.24),'hydro_run':(1.8,170,.34),'turbine_run':(1.8,220,.19),'plant_alarm':(1.1,720,.01),'pressure_burst':(1.6,55,.45)}
sound_dir=A/'sounds';sound_dir.mkdir(exist_ok=True);sounds=json.loads((A/'sounds.json').read_text())
for i,(name,(duration,freq,noise)) in enumerate(specs.items()):
 if np is not None:
  rate=22050;t=np.arange(round(duration*rate))/rate;rng=np.random.default_rng(1600+i)
  envelope=np.minimum(t/.025,1)*np.minimum((duration-t)/.09,1)
  if duration<1:envelope*=np.exp(-t*3/max(duration,.1))
  tone=.18*np.sin(math.tau*freq*t)+.09*np.sin(math.tau*freq*2.01*t);hiss=rng.normal(0,noise,len(t));hiss=np.convolve(hiss,np.ones(4)/4,mode='same')
  if name in ('wind_rotor','pump_run'):envelope*=.55+.45*np.sin(math.tau*3*t)**2
  if name=='plant_alarm':tone=.32*np.sin(math.tau*(freq*t+40*np.sin(math.tau*2*t)));envelope*=((t*4)%1<.65)
  if name=='pressure_burst':envelope*=np.exp(-t*2.8/duration)
  # Leave headroom for Vorbis reconstruction overshoot as well as simultaneous machines.
  audio=(np.clip((tone+hiss)*envelope,-.85,.85)*.7).astype('float32');sf.write(sound_dir/(name+'.ogg'),audio,rate,format='OGG',subtype='VORBIS')
 elif not (sound_dir/(name+'.ogg')).exists():
  raise RuntimeError('Recreating audio requires numpy and soundfile; see INDUSTRIAL_PLANTS_V6.md')
 sounds[name]={'subtitle':'subtitles.electron.'+name,'sounds':[{'name':'electron:'+name,'attenuation_distance':24 if duration>1 else 16}]}
write(A/'sounds.json',sounds)
entries={('block.electron.'+n):v for n,v in names.items()}
entries.update({
 'error.electron.drop_anchors':('垂直悬线必须上下对齐，并连接两端器件、端子或穿墙口','A vertical drop requires two aligned devices, terminals or wall ports'),
 'error.electron.drop_blocked':('垂直线段被方块、流体或未加载区块阻挡','Vertical drop blocked by a solid, fluid or unloaded chunk'),
 'message.electron.water_tank':('储水：%s / %s mB','Water: %s / %s mB'),
 'message.electron.assembly_materials':('材料不足或成品槽已满；配方显示在材料区下方','Missing materials or output full; ingredients appear below the input slots'),
 'message.electron.pump_ports':('24 V 水泵：后侧吸水，前侧接管道；用万用表测量供电','24 V pump: rear intake, front pipe outlet; use a multimeter for electrical readings'),
 'message.electron.wind_base_output':('风机底柜：OUT+/OUT− 输出；潜行右键开关，用万用表测量','Wind base: OUT+/OUT− output; sneak-use toggles the contact, use a multimeter to measure'),
 'assembly.electron.build':('装配所选部件','Assemble selected part'),'assembly.electron.inputs':('配方材料 → 右侧成品槽','Recipe materials → output slot'),
 'plant.electron.nuclear_foundation':('需要 30×30 平滑石基础，详见大型核电章节','Needs a 30×30 smooth-stone foundation; see the nuclear chapter'),
 'plant.electron.thermal_foundation':('需要 7×7 平滑石基础，详见火电装配章节','Needs a 7×7 smooth-stone foundation; see thermal assembly'),
 'plant.electron.modules':('机组部件未配齐或位置错误，查看工程手册','Missing or misplaced plant modules; consult the handbook'),
 'plant.electron.wind_mast':('需要 6–63 节风机塔筒与底部接线柜','Needs 6–63 wind mast sections and a base cabinet'),
 'plant.electron.flowing_water':('涡轮端需要接触流动的水，静水无法驱动','Turbine end requires flowing water; still water provides no drive'),
 'plant.electron.coolant_tank':('专用水槽缺水；通过管道水泵或水桶补水','Dedicated reservoir is low; refill with a powered pump or bucket'),
 'plant.electron.assembly_pending':('结构待自动成型，检查周围净空','Awaiting automatic formation; check surrounding clearance'),
 'plant.electron.plant_failure':('失冷导致压力事故，机组损坏','Cooling loss caused a pressure accident; plant damaged'),
 'plant.electron.scram':('缺水保护停机；余热仍需要冷却','Low-water trip; residual heat still requires cooling'),
 'tooltip.electron.pump':('额定输入 24 V · 2.4 W · 最大 1 L/s','Rated input 24 V / 2.4 W / up to 1 L/s'),
 'tooltip.electron.breakout':('两条独立母线 · 每条四个接点 · 5 A','Two isolated buses / four taps each / 5 A'),
})
for n,label in [('boiler','锅炉'),('turbine','汽轮机'),('alternator','发电机'),('condenser','冷凝器'),('control','控制柜'),('vessel','压力容器'),('control_rods','控制棒驱动'),('safety_valve','安全阀')]:entries['assembly.electron.'+n]=(label,n.replace('_',' ').title())
subtitles=['细导线接入','电缆插头锁紧','线钳剪断','端子固定','机组部件装配','多方块成型','水泵运转','阀门转动','水槽注水','电机运转','风机叶片掠过','水轮机运转','汽轮机运转','机组失冷报警','工业压力爆裂']
for (n,_),zh in zip(specs.items(),subtitles):entries['subtitles.electron.'+n]=(zh,n.replace('_',' ').capitalize())
for index,locale in enumerate(('zh_cn','en_us')):
 p=A/'lang'/f'{locale}.json';obj=json.loads(p.read_text(encoding='utf-8'));obj.update({k:v[index] for k,v in entries.items()});write(p,obj)
for rel in ('mineable/pickaxe','needs_iron_tool'):
 p=ROOT/'src/main/resources/data/minecraft/tags/block'/f'{rel}.json';obj=json.loads(p.read_text());obj['values']=sorted(set(obj['values']+['electron:'+n for n in names]));write(p,obj)
print('Industrial assets:',len(names),'blocks;',len(specs),'original sounds')
