"""Reproducible material detail, mono electrical Foley and the low-profile terminal hardware."""
from pathlib import Path
import json,math,random,sys
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parents[1];A=ROOT/'src/main/resources/assets/electron';D=ROOT/'src/main/resources/data/electron'
def write(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
materials={'boiler':'#626c71','reactor_shell':'#bcc3bc','reactor_rib':'#8d9c9a','cooling_concrete':'#b1b3ab','turbine_white':'#e0e4df','firebrick':'#976b50','grating':'#58666c','hatch':'#68787a','vent':'#465b66','roof_panels':'#4c6876','steel_pipe':'#74858c'}
for name,color in materials.items():
 im=Image.new('RGB',(128,128),color);d=ImageDraw.Draw(im);rng=random.Random(name)
 for y in range(128):
  for x in range(128):
   c=im.getpixel((x,y));noise=rng.randrange(-5,6);im.putpixel((x,y),tuple(max(0,min(255,k+noise)) for k in c))
 if name in ('reactor_shell','cooling_concrete'):
  for y in (0,63,127):d.line((0,y,127,y),fill='#87948e');d.line((0,y+2,127,y+2),fill='#ccd0c8')
  for x,y in ((12,12),(115,12),(12,115),(115,115)):d.ellipse((x-2,y-2,x+2,y+2),fill='#7c8986')
  for _ in range(18):
   x,y=rng.randrange(128),rng.randrange(20,125);d.line((x,y,x,y+rng.randrange(3,10)),fill='#a6b0a7')
 elif name=='firebrick':
  for row,y in enumerate(range(0,128,16)):
   d.line((0,y,127,y),fill='#3a3e3b',width=2)
   for x in range(-32+(row%2)*16,128,32):d.line((x,y,x,y+16),fill='#48413c',width=2)
 elif name=='grating':
  for x in range(0,128,8):d.line((x,0,x,127),fill='#a0b0b4',width=2);d.line((x+3,0,x+3,127),fill='#23323a',width=3)
  for y in range(0,128,24):d.line((0,y,127,y),fill='#81949a',width=2)
 elif name=='vent':
  d.rectangle((3,3,124,124),outline='#879da3',width=3)
  for y in range(12,120,9):d.line((12,y,116,y),fill='#192b35',width=4);d.line((12,y-3,116,y-3),fill='#80979e')
 elif name=='hatch':
  d.rounded_rectangle((7,5,120,122),radius=9,outline='#283a40',width=4);d.rectangle((92,53,102,78),fill='#b6c3bd',outline='#1f3135',width=2)
  for y in (24,92):d.rectangle((4,y,15,y+14),fill='#9facad')
  d.rectangle((38,16,86,26),fill='#d5bf62');d.ellipse((45,43,83,81),outline='#a9b7b5',width=3)
 else:
  d.rectangle((1,1,126,126),outline='#657677',width=2);d.line((4,4,122,4),fill='#d1dbd5')
  for x in (8,119):
   for y in (8,119):d.ellipse((x-3,y-3,x+3,y+3),fill='#9eafb0',outline='#3e5156');d.line((x-2,y,x+2,y),fill='#31474c')
  if name in ('boiler','steel_pipe','roof_panels'):
   for y in range(20,118,24):d.line((0,y,127,y),fill='#46575f');d.line((0,y+2,127,y+2),fill='#93a2a4')
 im.save(A/'textures/block/assembled'/f'{name}.png')

def cube(a,b,t):return {'from':list(a),'to':list(b),'faces':{f:{'texture':'#'+t,'uv':[0,0,16,16]} for f in ('north','south','east','west','up','down')}}
tx={n:'electron:block/assembled/'+n for n in ['cast_base','turbine_white','galvanized','porcelain','copper_bus','service_panel']};tx['particle']=tx['porcelain']
def transform(els,face,turn):
 def pos(p):
  x,y,z=p
  for _ in range(turn):x,z=16-z,x
  return {'up':(x,y,z),'down':(x,16-y,z),'north':(x,z,16-y),'south':(x,z,y),'west':(16-y,z,x),'east':(y,z,x)}[face]
 result=[]
 for e in els:
  a,b=pos(e['from']),pos(e['to']);result.append({**e,'from':[min(x,y) for x,y in zip(a,b)],'to':[max(x,y) for x,y in zip(a,b)]})
 return result
els=[cube((0,0,0),(16,1,16),'cast_base'),cube((1,1,1),(5,2.5,16),'porcelain'),cube((9,1,1),(13,2.5,16),'porcelain')]
for x in (3,11):
 for z in (3,7,11,15):
  els.extend([cube((x-.9,2.5,z-.9),(x+.9,2.8,z+.9),'cast_base'),cube((x-.35,2.8,z-.4),(x+.35,3.1,z+.4),'copper_bus')])
variants={}
for face in ('up','down','north','south','west','east'):
 for turn in range(4):
  name=f'low_profile_socket_{face}_{turn}';write(A/'models/block'/f'{name}.json',{'parent':'minecraft:block/block','textures':tx,'elements':transform(els,face,turn)});variants[f'facing={face},turn={turn}']={'model':'electron:block/'+name}
write(A/'blockstates/low_profile_socket.json',{'variants':variants});write(A/'models/item/low_profile_socket.json',{'parent':'electron:block/low_profile_socket_up_0'})
write(D/'recipe/low_profile_socket.json',{'type':'minecraft:crafting_shapeless','category':'redstone','ingredients':[{'item':'minecraft:copper_ingot'},{'item':'minecraft:quartz'},{'item':'minecraft:iron_nugget'}],'result':{'id':'electron:low_profile_socket','count':1}})
write(D/'advancement/recipes/low_profile_socket.json',{'criteria':{'has_item':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':'minecraft:copper_ingot'}]}}},'requirements':[['has_item']],'rewards':{'recipes':['electron:low_profile_socket']}})
write(D/'loot_table/blocks/low_profile_socket.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'electron:low_profile_socket'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
for rel in ('mineable/pickaxe','needs_iron_tool'):
 p=ROOT/'src/main/resources/data/minecraft/tags/block'/f'{rel}.json';obj=json.loads(p.read_text());obj['values']=sorted(set(obj['values']+['electron:low_profile_socket']));write(p,obj)
# Rounded tower foot; keep the existing electrical coordinates so placed wiring survives an upgrade.
body=[]
for a,b in [((1,0,4),(15,1.5,12)),((4,0,1),(12,1.5,15)),((2,0,2),(14,1.5,14)),((2,1.5,4),(14,16,12)),((4,1.5,2),(12,16,14)),((2.8,1.5,2.8),(13.2,16,13.2))]:body.append(cube(a,b,'turbine_white'))
for face in ('up','down','north','south','west','east'):
 for turn in range(4):
  taps=[]
  for x in (3,11):taps += [cube((x-.85,14.2,2.15),(x+.85,15.25,3.85),'porcelain'),cube((x-.35,15.25,2.65),(x+.35,15.6,3.35),'copper_bus'),cube((x-.45,12.8,3),(x+.45,14.4,6),'cast_base')]
  hatch=[cube((5,3,.1),(11,11,.35),'service_panel')]
  write(A/'models/block'/f'wind_base_{face}_{turn}.json',{'parent':'minecraft:block/block','textures':tx,'elements':body+transform(taps,face,turn)+hatch})

write(A/'blockstates/arc_flash.json',{'variants':{'':{'model':'electron:block/arc_flash'}}})
write(A/'models/block/arc_flash.json',{'textures':{'particle':'electron:block/silver'},'elements':[]})
p=A/'textures/effect';p.mkdir(exist_ok=True);im=Image.new('RGBA',(64,8))
for x in range(64):
 for y in range(8):im.putpixel((x,y),(255,255,255,int(255*max(0,1-abs(x-31.5)/31.5)**.5)))
im.save(p/'filament.png')

sys.path.insert(0,str(ROOT/'build/audio-tools'))
import numpy as np,soundfile as sf
sounds=json.loads((A/'sounds.json').read_text());samples={}
for index,name in enumerate(['electrical_arc_1','electrical_arc_2','electrical_arc_3','insulation_sizzle','wire_melt','breaker_trip','component_fail','component_pop']):
 rate=22050;duration={'insulation_sizzle':.65,'wire_melt':.48,'breaker_trip':.22,'component_pop':.45}.get(name,.24+index*.018)
 t=np.arange(int(rate*duration))/rate;rng=np.random.default_rng(1600+index);noise=rng.normal(0,1,len(t));high=noise-np.concatenate(([0],noise[:-1]))*.8
 signal=np.zeros(len(t))
 for start,decay,amp in [(0,.008,1),(.035,.004,.38),(.092,.012,.28)]:
  age=np.maximum(0,t-start);signal+=amp*(high*.38+np.sin(math.tau*(2300+index*130)*age)*.2)*np.exp(-age/decay)*(t>=start)
 if 'arc' in name:signal+=high*.12*np.exp(-t/.06)*(1+np.sin(math.tau*95*t))
 elif name=='insulation_sizzle':signal=high*.25*np.sin(math.pi*t/duration)**.5*(.55+.45*np.sin(math.tau*41*t)**2)
 elif name in ('wire_melt','component_fail'):signal+=noise*.25*np.exp(-t/.13)+np.sin(math.tau*160*t)*.12*np.exp(-t/.09)
 elif name=='component_pop':signal+=noise*.35*np.exp(-t/.08)+np.sin(math.tau*80*t)*.35*np.exp(-t/.05)
 else:signal+=np.sin(math.tau*630*t)*.3*np.exp(-t/.02)
 signal*=np.minimum(1,t/.0005)*np.minimum(1,(duration-t)/.02);signal=signal/np.max(np.abs(signal))*.70
 sf.write(A/'sounds'/f'{name}.ogg',signal.astype('float32'),rate,format='OGG',subtype='VORBIS');samples[name]=duration
labels={'electrical_arc':('电弧击穿','Electrical discharge'),'insulation_sizzle':('绝缘层过热嘶响','Insulation sizzling'),'wire_melt':('导线熔断','Conductor melting'),'breaker_trip':('断路器跳闸','Breaker tripping'),'component_fail':('器件烧毁','Component burning out'),'component_pop':('器件爆裂','Component rupture')}
for event in labels:
 ids=['electrical_arc_'+str(i) for i in (1,2,3)] if event=='electrical_arc' else [event]
 sounds[event]={'subtitle':'subtitles.electron.'+event,'sounds':[{'name':'electron:'+n,'attenuation_distance':24,'stream':False} for n in ids]}
write(A/'sounds.json',sounds)
entries={'block.electron.low_profile_socket':('矮型双母线插排','Low-profile Dual-bus Socket'),'block.electron.arc_flash':('放电闪光','Discharge Flash'),'block.electron.wind_base':('风机底部出线座','Wind Turbine Cable Foot'),'tooltip.electron.low_socket':('双独立母线 A/B · 每路四个接点 · 额定 5 A','Isolated A/B buses / four taps each / rated 5 A')}
for name,pair in labels.items():entries['subtitles.electron.'+name]=pair
for index,locale in enumerate(('zh_cn','en_us')):
 p=A/'lang'/f'{locale}.json';obj=json.loads(p.read_text(encoding='utf-8'));obj.update({k:v[index] for k,v in entries.items()});write(p,obj)
print('Detailed materials:',len(materials),'Electrical samples:',len(samples),'Low-profile socket: 24 orientations')
