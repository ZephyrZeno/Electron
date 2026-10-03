"""Distinct solid luminaire models; vanilla light levels and optional emissive material maps."""
from generate_processing_assets import A,D,ROOT,write,cube,model,transform,recipe
import json
from PIL import Image,ImageDraw

for lit in (False,True):
 im=Image.new('RGB',(128,128),'#fff1ce' if lit else '#81949d');d=ImageDraw.Draw(im)
 for x in range(3,128,5):d.line((x,0,x,127),fill='#e6d6b0' if lit else '#657c88',width=1)
 for y in (5,121):d.line((0,y,127,y),fill='#d4c6a4' if lit else '#546a79',width=2)
 name='fixture_on' if lit else 'fixture_off';im.save(A/'textures/block'/f'{name}.png')
 Image.new('RGBA',(128,128),(90,10,0,254 if lit else 255)).save(A/'textures/block'/f'{name}_s.png')

names={'workshop_lamp':('工坊防护灯','Workshop Bulkhead Lamp'),'yard_lamp':('庭院柱灯','Yard Post Lamp')}
for name in names:
 variants={}
 for appearance in ('off','on','broken'):
  tx={'lens':'electron:block/'+('scorched' if appearance=='broken' else 'fixture_'+appearance)}
  els=[cube((0,0,0),(16,1,16),'cast_base'),cube((1.6,1,2),(2.4,14.6,3),'galvanized'),cube((13.6,1,2),(14.4,14.6,3),'galvanized'),cube((1,14.6,2),(15,15,4),'control_case')]
  for x in (3,11):
   els += [cube((x-.5,1,6.5),(x+.5,14.5,7.5),'galvanized'),cube((x-.8,14.5,6.2),(x+.8,15,7.8),'porcelain'),cube((x-.4,15,6.6),(x+.4,15.2,7.4),'copper_bus')]
  if name=='workshop_lamp':
   els += [cube((2,1,9),(3,12,15),'galvanized'),cube((13,1,9),(14,12,15),'galvanized'),cube((2,10,9),(14,11.5,15),'cast_base'),cube((3,11.5,9.4),(13,13.7,14.6),'lens')]
   for x in (2.9,5.2,7.5,9.8,12.1):els.append(cube((x,13.7,9.2),(x+.35,14.2,14.8),'galvanized'))
   els += [cube((2.7,13.7,9.1),(13.3,14.2,9.45),'galvanized'),cube((2.7,13.7,14.55),(13.3,14.2,14.9),'galvanized')]
  else:
   els += [cube((5.5,1,9),(10.5,2.3,14),'cast_base'),cube((7,2.3,10.5),(9,8.5,12.5),'galvanized'),cube((4.5,8.2,8.7),(11.5,9,14.8),'cast_base'),cube((5,9,9.2),(11,13.5,14.3),'lens'),cube((4.2,13.5,8.5),(11.8,14.2,15),'galvanized'),cube((5,14.2,9.3),(11,14.7,14.2),'cast_base')]
   for x in (4.8,10.8):
    for z in (9,14.1):els.append(cube((x,9,z),(x+.4,13.5,z+.4),'cast_base'))
  for face in ('up','down','north','south','west','east'):
   for turn in range(4):
    ref=f'{name}_{appearance}_{face}_{turn}';write(A/'models/block'/f'{ref}.json',model(transform(els,face,turn),tx))
    for power in range(16):
     if appearance=='broken' or (power>0)==(appearance=='on'):
      variants[f'facing={face},turn={turn},broken={str(appearance=="broken").lower()},power={power}']={'model':'electron:block/'+ref}
  if appearance=='off':write(A/'models/item'/f'{name}.json',model(els,tx))
 write(A/'blockstates'/f'{name}.json',{'variants':variants})
 loot=json.loads((D/'loot_table/blocks/dc_motor.json').read_text());loot=json.loads(json.dumps(loot).replace('electron:dc_motor','electron:'+name));write(D/'loot_table/blocks'/f'{name}.json',loot)
recipe('workshop_lamp',['minecraft:iron_ingot','minecraft:iron_ingot','minecraft:glass','electron:indicator_led','electron:indicator_led','minecraft:copper_ingot'])
recipe('yard_lamp',['electron:iron_plate','minecraft:iron_ingot','minecraft:glass','minecraft:glass','electron:indicator_led','electron:indicator_led','electron:indicator_led','minecraft:copper_ingot'])
for locale,i in [('zh_cn',0),('en_us',1)]:
 p=A/'lang'/f'{locale}.json';data=json.loads(p.read_text());data.update({'block.electron.'+n:v[i] for n,v in names.items()})
 data['tooltip.electron.fixture_rating']=('额定 24 V / %s W · 建议 18–26 V · 最高 28 V · 原版光照' if i==0 else 'Rated 24 V / %s W; recommended 18–26 V, maximum 28 V; vanilla lighting')
 data.update({k:v[i] for k,v in {
  'map.electron.processing':('12 用电加工线','12 Powered workshop'),
  'map.electron.processing_1':('粉碎→分离→电炉→封装','Crush > separate > kiln > seal'),
  'map.electron.processing_2':('材料箱在台边，空槽再加料','Supplies beside bench'),
  'map.electron.processing_3':('手册：用电加工与核燃料','Handbook: powered processing'),
  'guide.electron.distribution.p3':('工坊防护灯为 24 V / 2.4 W，庭院柱灯为 24 V / 6 W；两者均可安装在不同朝向，通电提供原版光照，断电熄灭。高于 28 V 可能烧毁，应使用固定降压模块和保护器。工作坊的粉碎机、离心机、电炉和封装机也使用 24 V，详见“用电加工与核燃料”。','The bulkhead lamp uses 24 V / 2.4 W; the yard lamp uses 24 V / 6 W. Both mount in any direction and produce vanilla light only when powered. Above 28 V they can fail; use suitable step-down modules and protection. The crusher, centrifuge, kiln and fabricator also use 24 V; see Powered processing.')
 }.items()});write(p,data)
for rel in ('mineable/pickaxe','needs_iron_tool'):
 p=ROOT/'src/main/resources/data/minecraft/tags/block'/f'{rel}.json';data=json.loads(p.read_text());data['values']=sorted(set(data['values']+['electron:'+n for n in names]));write(p,data)
print('Workshop bulkhead and yard post lights generated')
