"""Typed industrial ports, restrained appliance housings and original seamless equipment audio."""
from pathlib import Path
import json,math,sys,re
from PIL import Image,ImageDraw,ImageFont
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/electron';D=R/'src/main/resources/data/electron'
sys.path.insert(0,str(R/'build/audio-tools'))
import numpy as np
import soundfile as sf
def write(p,v):
 p.parent.mkdir(parents=True,exist_ok=True);s=json.dumps(v,ensure_ascii=False,indent=2)+'\n'
 if not p.exists() or p.read_text(encoding='utf-8')!=s:p.write_text(s,encoding='utf-8')
def texture(name,color,style):
 im=Image.new('RGB',(128,128),color);d=ImageDraw.Draw(im)
 for y in range(128):
  d.line((0,y,127,y),fill=tuple(max(0,min(255,c+(y%3-1)*2)) for c in im.getpixel((0,y))))
 d.rectangle((3,3,124,124),outline='#859294',width=1)
 if style=='door':
  for y in range(79,116,6):d.rectangle((18,y,110,y+2),fill='#17242c');d.line((18,y+3,110,y+3),fill='#869498')
  for x in (8,119):
   for y in (8,119):d.ellipse((x-2,y-2,x+2,y+2),fill='#b5c3c0');d.line((x-1,y,x+1,y),fill='#26343c')
 elif style=='inside':
  for x in range(14,124,18):d.rectangle((x,15,x+8,110),fill='#1b2d33',outline='#617778')
 elif style=='contact':
  d.ellipse((26,26,101,101),fill='#a7b3ae',outline='#eff0d7',width=3);d.line((41,41,87,87),fill='#2b3436',width=8)
 im.save(A/'textures/block/assembled'/f'{name}.png')
for name,c,style in [('cabinet_skin','#60737a','door'),('cabinet_door','#465b65','door'),('cabinet_inside','#263d43','inside'),('cabinet_contact','#a98c57','contact')]:texture(name,c,style)
tx={n:'electron:block/assembled/'+n for n in ('cabinet_skin','cabinet_door','cabinet_inside','cabinet_contact','galvanized','cast_base','copper_bus','porcelain','safety_yellow')}
tx['particle']=tx['cabinet_skin']
def cube(a,b,t):
 return {'from':list(a),'to':list(b),'faces':{f:{'texture':'#'+t,'uv':[0,0,16,16]} for f in ('north','south','east','west','up','down')}}
def loot(n):
 write(D/'loot_table/blocks'/f'{n}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'electron:'+n}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
def recipe(n,items,count=1):
 write(D/'recipe'/f'{n}.json',{'type':'minecraft:crafting_shapeless','category':'misc','ingredients':[{'item':i} for i in items],'result':{'id':'electron:'+n,'count':count}})
 write(D/'advancement/recipes'/f'{n}.json',{'criteria':{'material':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':[items[0]]}]}}},'requirements':[['material']],'rewards':{'recipes':['electron:'+n]}})
def model(n,els,textures=tx):write(A/'models/block'/f'{n}.json',{'parent':'minecraft:block/block','ambientocclusion':True,'textures':textures,'elements':els})
ports={'plant_fuel_port':('solid',False),'reactor_fuel_intake':('rod',False),'plant_ash_outlet':('ash',True),'spent_rod_outlet':('spent',True)}
for n,(material,output) in ports.items():
 im=Image.new('RGB',(128,128),'#293c44');d=ImageDraw.Draw(im)
 color={'solid':'#b89a70','rod':'#61bdb0','ash':'#bac2bd','spent':'#e3b56d'}[material]
 d.rectangle((5,5,122,122),outline=color,width=3)
 if material in ('rod','spent'):
  for x in (48,61,74,87):d.rounded_rectangle((x,26,x+6,96),radius=2,fill=color)
  for y in (24,54,95):d.rectangle((44,y,98,y+5),fill='#acbdb7')
 else:
  for x,y in ((62,47),(84,53),(72,75)):d.polygon([(x-12,y-6),(x,y-16),(x+15,y),(x+8,y+13),(x-13,y+11)],fill=color)
 arrow=[(15,51),(27,51),(27,40),(41,64),(27,88),(27,76),(15,76)]
 if output:arrow=[(54-x,y) for x,y in arrow]
 d.polygon(arrow,fill=color);im.save(A/'textures/block/assembled'/f'{n}_symbol.png')
 nt={**tx,'symbol':'electron:block/assembled/'+n+'_symbol'}
 els=[cube((1,0,1),(15,1,15),'cast_base'),cube((1.4,1,1.4),(14.6,14.2,14.6),'cabinet_skin'),
      cube((2,2,14.6),(14,13,15.3),'cabinet_inside'),cube((3,3,15.3),(13,12,15.55),'symbol'),
      cube((1,14.2,1),(15,14.8,15.7),'galvanized'),cube((4.5,5,0),(11.5,10,1.4),'cast_base')]
 for x in (2.3,13): 
  for y in (2.5,12.3):els += [cube((x,y,15.3),(x+.6,y+.6,15.7),'galvanized'),cube((x+.12,y+.25,15.71),(x+.48,y+.35,15.76),'cast_base')]
 for y in (4,6,8,10):els.append(cube((1.12,y,4),(1.4,y+.5,11),'cabinet_inside'))
 if material in ('rod','spent'):
  for x in (3,5,7,9,11):els.append(cube((x,14.8,3),(x+.7,15.6,12),'galvanized'))
  els.append(cube((5,4,15.56),(5.4,11,15.88),'cabinet_contact'))
 elif output:
  for y in (3.2,4.1,5):els.append(cube((3,y,15.56),(13,y+.35,15.88),'galvanized'))
  els.append(cube((5.5,7,15.56),(10.5,7.6,15.95),'cast_base'))
 else:
  els.extend([cube((2,14.8,3),(14,15.8,13),'cast_base'),cube((3,15.8,4),(13,16,12),'cabinet_inside')])
 model(n,els,nt);write(A/'blockstates'/f'{n}.json',{'variants':{f'facing={f}':{'model':'electron:block/'+n,'y':r} for f,r in [('south',0),('west',90),('north',180),('east',270)]}})
 write(A/'models/item'/f'{n}.json',{'parent':'electron:block/'+n});loot(n)
 recipe(n,['minecraft:hopper','minecraft:iron_ingot','minecraft:copper_ingot']+(['electron:fuel_cladding'] if material in ('rod','spent') else []))
# Flanged water socket, with a distinct blue check-valve body. Fluid links share the pipe blockstate scheme.
els=[cube((3,3,3),(13,13,13),'cabinet_inside')]
for z in (0,13):els += [cube((2,2,z),(14,14,z+3),'galvanized'),cube((4,4,z+.1),(12,12,z+2.8),'cast_base'),cube((5,5,z+.05),(11,11,z+2.9),'porcelain')]
for x in (2.5,12.7):
 for y in (2.5,12.7):els.append(cube((x,y,.02),(x+.8,y+.8,.3),'cabinet_contact'))
model('plant_water_inlet',els)
model('plant_water_inlet_core',[cube((3,3,3),(13,13,13),'cabinet_inside')])
model('plant_water_inlet_arm',[e for e in els[1:] if e['to'][2]<=3])
write(A/'blockstates/plant_water_inlet.json',{'multipart':[{'apply':{'model':'electron:block/plant_water_inlet_core'}}]+[{'when':{face:'true'},'apply':{'model':'electron:block/plant_water_inlet_arm',**rotation}} for face,rotation in [('north',{}),('south',{'y':180}),('east',{'y':90}),('west',{'y':270}),('up',{'x':90}),('down',{'x':270})]]})
write(A/'models/item/plant_water_inlet.json',{'parent':'electron:block/plant_water_inlet'});loot('plant_water_inlet');recipe('plant_water_inlet',['electron:iron_pipe','electron:pipe_valve','minecraft:iron_ingot'])
model('combustion_ash_item',[cube((4,0,4),(12,3,12),'cast_base'),cube((6,3,5),(11,5,10),'cabinet_skin')]);write(A/'models/item/combustion_ash.json',{'parent':'electron:block/combustion_ash_item'})
# Appliances sit on a thin insulated chassis; independently modeled sockets, shutters, switch and strain relief.
def orient(point,face,turn):
 x,y,z=point;u,v=x,z
 for _ in range(turn):u,v=16-v,u
 d=16-y
 return {'up':(u,y,v),'down':(u,d,v),'north':(u,v,d),'south':(u,v,y),'west':(d,v,u),'east':(y,v,u)}[face]
for n in ('wall_outlet','power_strip','appliance_adapter'):
 els=[cube((1,10,1),(15,10.7,15),'cast_base'),cube((1.3,10.7,1.3),(14.7,14.7,14.7),'porcelain')]
 sockets=(4,8,12) if n=='power_strip' else (8,)
 for x in sockets:
  els += [cube((x-1.7,14.7,5.3),(x+1.7,15.0,10.7),'cabinet_skin'),cube((x-1.5,15.0,5.5),(x+1.5,15.15,10.5),'porcelain')]
  for dx in (-.65,.65):els.append(cube((x+dx-.20,15.15,7),(x+dx+.20,15.35,8.5),'cabinet_inside'))
  els.append(cube((x-.22,15.15,9.2),(x+.22,15.35,9.8),'cabinet_inside'))
 els += [cube((6,14.8,11.6),(10,15.3,13.5),'cast_base'),cube((6.4,15.3,12),(9.6,15.6,13.1),'safety_yellow')]
 for x in (2,13.5):
  for z in (2,13.5):els.append(cube((x,14.7,z),(x+.4,14.9,z+.4),'galvanized'))
 if n=='appliance_adapter':
  for x in range(3,14,2):els.append(cube((x,12,1.05),(x+.8,14,1.3),'cabinet_inside'))
 # Recessed hardwire inlet terminals follow existing MAINS pin coordinates.
 for x in (3,11):els += [cube((x-.7,14.75,12.5),(x+.7,15.1,13.7),'cabinet_inside'),cube((x-.2,15.1,12.8),(x+.2,15.4,13.2),'cabinet_contact')]
 variants={}
 for face in ('up','down','north','south','west','east'):
  for turn in range(4):
   name=f'{n}_{face}_{turn}';pose=[]
   for e in els:
    a=orient(e['from'],face,turn);b=orient(e['to'],face,turn);pose.append(cube(tuple(min(x,y) for x,y in zip(a,b)),tuple(max(x,y) for x,y in zip(a,b)),e['faces']['up']['texture'][1:]))
   model(name,pose);variants[f'facing={face},turn={turn}']={'model':'electron:block/'+name}
 write(A/'blockstates'/f'{n}.json',{'variants':variants});write(A/'models/item'/f'{n}.json',{'parent':f'electron:block/{n}_up_0'});loot(n)
recipe('wall_outlet',['minecraft:copper_ingot','minecraft:quartz','minecraft:iron_ingot'])
recipe('power_strip',['electron:wall_outlet']*3+['minecraft:iron_ingot'])
recipe('appliance_adapter',['electron:copper_coil','electron:power_mosfet','electron:capacitor_100mf','minecraft:iron_ingot','minecraft:quartz'])
recipe('appliance_power_cord',['minecraft:copper_ingot']*4+['minecraft:string']*2+['minecraft:dried_kelp'])
im=Image.new('RGB',(128,128),'#232a30');d=ImageDraw.Draw(im)
for y in range(0,128,8):d.line((0,y,127,y),fill='#343e43');d.line((0,y+1,127,y+1),fill='#161c21')
im.save(A/'textures/block/power_cord.png')
model('power_cord_item',[cube((2,6,2),(14,8,4),'cast_base'),cube((2,6,12),(14,8,14),'cast_base'),cube((2,6,4),(4,8,12),'cast_base'),cube((12,6,4),(14,8,12),'cast_base'),cube((5,5,5),(9,9,10),'cabinet_skin'),cube((5.7,6,10),(6.3,7,12),'galvanized'),cube((7.7,6,10),(8.3,7,12),'galvanized')])
write(A/'models/item/appliance_power_cord.json',{'parent':'electron:block/power_cord_item'})
# Remove voltage nameplates from existing equipment models, including held-item model parents.
rating_names={'engineering_lamp','workshop_lamp','yard_lamp','emergency_lamp','dc_motor','dc_generator','hand_crank','dc_breaker','electrical_relay','boost_converter','boost_converter_12','buck_converter','buck_converter_12','buck_converter_3v3','rechargeable_battery','electrical_redstone_switch','capacitor_100mf','thermal_generator','hydro_generator','wind_generator','lightning_collector','nuclear_reactor','mains_inlet','substation'}
for p in (A/'models/block').glob('*.json'):
 obj=json.loads(p.read_text(encoding='utf-8'));els=obj.get('elements')
 if not els:continue
 if any(p.stem.startswith(n+'_') for n in rating_names) or any('rating_plate' in f.get('texture','') for e in els for f in e.get('faces',{}).values()):
  kept=[e for e in els if not any(f.get('texture')=='#rating_plate' or (f.get('texture')=='#label' and any(p.stem.startswith(n+'_') for n in rating_names)) for f in e.get('faces',{}).values())]
  if len(kept)!=len(els):obj['elements']=kept;write(p,obj)
for name in rating_names:
 p=A/'textures/block'/f'{name}_label.png'
 if p.exists():
  im=Image.new('RGB',(128,128),'#283a40');d=ImageDraw.Draw(im)
  for y in range(0,128,16):d.line((10,y,118,y),fill='#3a4b50')
  im.save(p)
# Appliance inlet at the exact cord attachment point, including handheld model parents.
plug_names=('engineering_lamp','workshop_lamp','yard_lamp','emergency_lamp','dc_motor','water_pump','ore_crusher','centrifuge','electric_kiln','fuel_fabricator','precision_workbench','uv_exposure_unit','pcb_drill','pcb_etcher','solder_mask_applicator','immersion_gold_line')
for p in (A/'models/block').glob('*.json'):
 if not any(p.stem.startswith(n+'_') for n in plug_names):continue
 obj=json.loads(p.read_text(encoding='utf-8'))
 if not obj.get('elements'):continue
 obj['elements']=[e for e in obj['elements'] if not any(f['texture'] in ('#cord_inlet','#cord_contact') for f in e['faces'].values())]
 pose=re.search(r'_(up|down|north|south|east|west)(?:_r?([0-3]))?$',p.stem)
 face,turn=(pose.group(1),int(pose.group(2) or 0)) if pose else ('up',0)
 obj.setdefault('textures',{}).update({'cord_inlet':'electron:block/assembled/cast_base','cord_contact':'electron:block/assembled/galvanized'})
 for a,b,t in [((6.7,5.8,.04),(9.3,8.2,.24),'cord_inlet'),((7,6.1,.02),(9,7.9,.04),'cord_contact'),((7.25,6.35,.01),(8.75,7.65,.02),'cord_inlet')]:
  x,y=orient(a,face,turn),orient(b,face,turn);obj['elements'].append(cube(tuple(min(i,j) for i,j in zip(x,y)),tuple(max(i,j) for i,j in zip(x,y)),t))
 write(p,obj)
# Persistent mount rule and recipes for new utility blocks.
for tag in ('mineable/pickaxe','needs_iron_tool'):
 p=R/'src/main/resources/data/minecraft/tags/block'/f'{tag}.json';obj=json.loads(p.read_text());obj['values']=sorted(set(obj['values'])|{'electron:'+n for n in [*ports,'plant_water_inlet','wall_outlet','power_strip','appliance_adapter']});write(p,obj)
# Continuous loops: exact periodic oscillators and circular filtered noise, not faded short one-shots.
rate=22050;duration=4;n=rate*duration;t=np.arange(n)/rate;rng=np.random.default_rng(611)
def noise(low,high):
 spectrum=np.fft.rfft(rng.standard_normal(n));f=np.fft.rfftfreq(n,1/rate)
 spectrum*=np.minimum(1,f/max(1,low))**3/(1+(f/high)**6)
 v=np.fft.irfft(spectrum,n);return v/max(np.std(v),1e-9)
def sine(f):return np.sin(math.tau*f*t)
loops={
 'wind_rotor':.07*noise(45,420)*(.7+.3*np.cos(math.tau*.75*t))+.035*sine(62),
 'hydro_run':.08*noise(50,1800)+.035*noise(1200,4200)*(.8+.2*sine(2))+.025*sine(145),
 'pump_run':.08*sine(100)+.03*sine(200)+.025*noise(80,800)*(.8+.2*sine(5)),
 'turbine_run':.09*noise(25,350)+.055*sine(180)+.025*sine(540)+.02*noise(800,2200),
 'reactor_cooling':.065*sine(50)+.03*sine(100)+.055*noise(70,1600),
 'transformer_hum':.085*sine(100)+.022*sine(200)+.012*sine(300)+.006*noise(30,230),
 'motor_run':.075*sine(96)+.032*sine(288)+.02*noise(100,900),
 'centrifuge_run':.04*sine(700)+.065*sine(1400)+.025*noise(600,3000),
 'crusher_run':.075*noise(45,1400)*(.4+.6*np.cos(math.tau*3*t)**8)+.03*sine(80),
 'kiln_run':.035*sine(100)+.06*noise(70,500),
 'fabricator_run':.045*sine(190)+.055*noise(100,900)*(.25+.75*np.cos(math.tau*2*t)**12),
 'pcb_drill_run':.035*sine(2100)+.02*sine(4200)+.027*noise(1500,5000),
 'process_bath':.055*noise(70,1100)*(.8+.2*sine(1.5))+.014*sine(140),
}
sounds=json.loads((A/'sounds.json').read_text(encoding='utf-8'))
for name,data in loops.items():
 data=data-np.mean(data);sf.write(A/'sounds'/f'{name}.ogg',data.astype('float32'),rate,format='OGG',subtype='VORBIS')
 sounds[name]={'subtitle':'subtitles.electron.'+name,'sounds':[{'name':'electron:'+name,'attenuation_distance':32,'stream':False,'preload':True}]}
for name,seconds,freq in [('cabinet_open',.38,320),('cabinet_close',.24,95),('contact_spark',.16,2600)]:
 tt=np.arange(int(rate*seconds))/rate
 if name=='cabinet_open':v=.12*np.sin(math.tau*(320*tt+140*tt*tt))*np.exp(-tt*8)+.035*rng.standard_normal(len(tt))*np.sin(np.pi*tt/seconds)**2
 elif name=='cabinet_close':v=.24*np.sin(math.tau*95*tt)*np.exp(-tt*24)+.08*rng.standard_normal(len(tt))*np.exp(-tt*70)
 else:v=.13*rng.standard_normal(len(tt))*np.exp(-tt*55)+.035*np.sin(math.tau*2600*tt)*np.exp(-tt*75)
 v*=np.minimum(1,tt/.003)*np.minimum(1,(seconds-tt)/.01);sf.write(A/'sounds'/f'{name}.ogg',v.astype('float32'),rate,format='OGG',subtype='VORBIS')
 sounds[name]={'subtitle':'subtitles.electron.'+name,'sounds':[{'name':'electron:'+name,'attenuation_distance':16}]}
write(A/'sounds.json',sounds)
print('Generated typed plant interfaces, cabinet materials, plug-in appliances and 13 seamless loops.')
