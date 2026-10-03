"""Four distinct powered workshop machines, solid intermediate items and their localized recipes."""
from pathlib import Path
import json,copy,math
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1];A=ROOT/'src/main/resources/assets/electron';D=ROOT/'src/main/resources/data/electron'
def write(p,obj):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def cube(a,b,t):return {'from':list(a),'to':list(b),'faces':{f:{'texture':'#'+t,'uv':[0,0,16,16]} for f in ('up','down','north','south','east','west')}}
TX={k:'electron:block/assembled/'+k for k in ('cast_base','galvanized','control_case','service_panel','console_panel','copper_bus','porcelain','vent','firebrick','steel_pipe','hydro_blue','boiler')}
DISPLAY={'gui':{'rotation':[25,225,0],'scale':[.65,.65,.65]},'ground':{'translation':[0,3,0],'scale':[.35,.35,.35]},'fixed':{'rotation':[0,180,0],'scale':[.6,.6,.6]},'firstperson_righthand':{'rotation':[0,45,0],'scale':[.5,.5,.5]},'firstperson_lefthand':{'rotation':[0,-45,0],'scale':[.5,.5,.5]},'thirdperson_righthand':{'rotation':[65,45,0],'translation':[0,2,0],'scale':[.375,.375,.375]},'thirdperson_lefthand':{'rotation':[65,-45,0],'translation':[0,2,0],'scale':[.375,.375,.375]}}
def model(els,tx=None):return {'parent':'minecraft:block/block','ambientocclusion':True,'textures':{'particle':'electron:block/assembled/galvanized',**TX,**(tx or {})},'elements':els,'display':DISPLAY}
def round_vertical(x,y,z,r,h,t):
 # Stepped, non-overlapping strips approximate a round body in baked block models.
 return [cube((x-r+2*r*i/10,y,z-math.sqrt(max(0,r*r-(-r+2*r*(i+.5)/10)**2))),(x-r+2*r*(i+1)/10,y+h,z+math.sqrt(max(0,r*r-(-r+2*r*(i+.5)/10)**2))),t) for i in range(10)]
def transform(els,face,turn):
 def pos(p):
  x,y,z=p
  for _ in range(turn):x,z=16-z,x
  return {'up':(x,y,z),'down':(x,16-y,z),'north':(x,z,16-y),'south':(x,z,y),'west':(16-y,z,x),'east':(y,z,x)}[face]
 out=[]
 for el in els:
  a,b=pos(el['from']),pos(el['to']);out.append({**copy.deepcopy(el),'from':[min(x,y) for x,y in zip(a,b)],'to':[max(x,y) for x,y in zip(a,b)]})
 return out
names={'ore_crusher':('电动粉碎机','Electric Ore Crusher'),'centrifuge':('电动离心分离机','Powered Centrifuge'),'electric_kiln':('电炉','Electric Kiln'),'fuel_fabricator':('压制封装机','Press and Fuel Fabricator')}
for name in names:
 els=[cube((0,0,0),(16,1,16),'cast_base'),cube((1.6,1,2),(2.4,14.6,3),'galvanized'),cube((13.6,1,2),(14.4,14.6,3),'galvanized'),cube((1,14.6,2),(15,15,4),'control_case')]
 for x in (3,11):
  els += [cube((x-.5,1,6.5),(x+.5,13.5,7.5),'galvanized'),cube((x-.9,13.5,6.1),(x+.9,14.7,7.9),'porcelain'),cube((x-.5,14.7,6.5),(x+.5,15.2,7.5),'copper_bus')]
 if name=='ore_crusher':
  els += [cube((4.5,1,3),(11.5,7,14),'boiler'),cube((5,7,8),(6,14,15),'galvanized'),cube((10,7,8),(11,14,15),'galvanized'),cube((5,7,14),(11,14,15),'galvanized')]
  for x in (6.4,9):
   els+=round_vertical(x,7,11,1.25,5,'cast_base')
   for y in (7.2,8.4,9.6,10.8):els.append(cube((x-1.2,y,9.8),(x+1.2,y+.3,12.2),'galvanized'))
  els += [cube((4.7,3,2.2),(11.3,5.5,3),'cast_base'),cube((5,3.2,1.8),(11,3.7,3),'galvanized'),cube((1,2,9),(4.4,6,14.5),'hydro_blue')]
 elif name=='centrifuge':
  els+=round_vertical(8,1.1,11.3,3.7,11.8,'galvanized');els+=round_vertical(8,12.9,11.3,3.7,.8,'cast_base');els+=round_vertical(8,13.7,11.3,3.4,.5,'galvanized')
  for x,z in ((5.2,9),(10.8,9),(5.2,13.4),(10.8,13.4)):els.append(cube((x-.2,14.2,z-.2),(x+.2,14.6,z+.2),'cast_base'))
  els += [cube((6.7,14.2,10.7),(7.3,15.2,11.9),'cast_base'),cube((8.7,14.2,10.7),(9.3,15.2,11.9),'cast_base'),cube((6.7,15,10.7),(9.3,15.5,11.9),'galvanized'),cube((5.3,3,6.8),(10.7,7,7.2),'console_panel')]
 elif name=='electric_kiln':
  els += [cube((4.5,1,5),(11.5,12.5,15),'firebrick'),cube((4.3,1,4.8),(11.7,2,15.2),'galvanized'),cube((4.3,12.5,4.8),(11.7,13.1,15.2),'galvanized'),cube((5.2,3,4.2),(10.8,10.5,5),'cast_base'),cube((5.8,4,4.1),(10.2,9.5,4.2),'firebrick'),cube((10,5.5,3.6),(10.6,8,4.3),'galvanized'),cube((5,13.1,7),(11,13.5,13),'vent')]
 else:
  els += [cube((4.5,1,8),(11.5,3,15),'cast_base'),cube((4.7,3,12),(5.7,14,13),'galvanized'),cube((10.3,3,12),(11.3,14,13),'galvanized'),cube((4.3,13.5,9),(11.7,15,14.5),'control_case'),cube((7,8.5,10.5),(9,13.5,12.5),'steel_pipe'),cube((5.9,7.8,9.3),(10.1,8.5,13.5),'galvanized'),cube((6,3,9.5),(10,3.7,13.5),'galvanized')]
  for x in (6.5,8,9.5):els+=round_vertical(x,3.7,11.5,.4,.9,'cast_base')
 variants={}
 for face in ('up','down','north','south','west','east'):
  for turn in range(4):
   static=[e for e in els if name!='fuel_fabricator' or e['from'] not in ([7,8.5,10.5],[5.9,7.8,9.3])]
   for broken in (False,True):
    ref=f'{name}_{face}_{turn}'+('_broken' if broken else '')
    damaged={t:'electron:block/scorched' for t in ('boiler','hydro_blue','firebrick','porcelain','console_panel','galvanized')} if broken else {}
    write(A/'models/block'/f'{ref}.json',model(transform(static,face,turn),damaged));variants[f'facing={face},turn={turn},broken={str(broken).lower()}']={'model':'electron:block/'+ref}
 write(A/'blockstates'/f'{name}.json',{'variants':variants});write(A/'models/item'/f'{name}.json',model(els))
 loot=json.loads((D/'loot_table/blocks/dc_motor.json').read_text());loot=json.loads(json.dumps(loot).replace('electron:dc_motor','electron:'+name));write(D/'loot_table/blocks'/f'{name}.json',loot)

items={'crushed_uranium':('粉碎铀矿料','Crushed Uranium Ore'),'fuel_concentrate':('燃料级原料','Fuel Concentrate'),'uranium_tailings':('铀矿尾料','Uranium Tailings'),'iron_dust':('铁粉','Iron Dust'),'copper_dust':('铜粉','Copper Dust'),'fuel_cladding':('燃料包壳','Fuel Cladding'),'iron_plate':('压制铁板','Pressed Iron Plate'),'copper_coil':('绕制铜线圈','Wound Copper Coil')}
for name in items:
 extra={}
 if name=='crushed_uranium':
  extra={'ore':'electron:block/uranium_ore'};els=[cube((2,2,2),(14,3,14),'cast_base')]
  for x,y,z,size in ((3,3,4,4),(8,3,3,3),(8,3,8,4),(3,3,10,3),(5,6,7,3)):els.append(cube((x,y,z),(x+size,y+size*.8,z+size),'ore'))
 elif name=='uranium_tailings':
  els=[cube((3,2,3),(13,11,13),'cast_base'),cube((2.7,11,2.7),(13.3,11.7,13.3),'galvanized'),cube((6,11.7,6),(10,12.3,10),'control_case'),cube((4,4,2.9),(12,8.5,3),'porcelain')]
  for x in (5,8,11):els.append(cube((x,4.5,2.75),(x+.5,8,2.9),'cast_base'))
 elif name=='iron_plate':els=[cube((2,6,2),(14,7.2,14),'galvanized'),cube((3,7.25,3),(13,8.4,13),'galvanized')]
 elif name=='copper_coil':
  els=[]
  for i in range(12):
   x=3+i*.8;els += [cube((x,4,4),(x+.6,12,5),'copper_bus'),cube((x,4,11),(x+.6,12,12),'copper_bus'),cube((x,4,5),(x+.6,5,11),'copper_bus'),cube((x,11,5),(x+.6,12,11),'copper_bus')]
 elif name=='fuel_cladding':
  els=[]
  for x in (5,8,11):
   for z in (5,8,11):els += [cube((x-.5,2,z-.5),(x+.5,14,z+.5),'galvanized')]
  els += [cube((4,2,4),(12,2.5,12),'galvanized'),cube((4,13.5,4),(12,14,12),'galvanized')]
 else:
  material='copper_bus' if name=='copper_dust' else 'galvanized' if name=='iron_dust' else 'porcelain' if name=='fuel_concentrate' else 'cast_base'
  els=round_vertical(8,3,8,4,8,'control_case')+round_vertical(8,11,8,4.2,.7,'galvanized')+[cube((5,5,3.95),(11,9.5,4.05),material)]
 write(A/'models/item'/f'{name}.json',model(els,extra))
def recipe(name,ingredients,count=1):
 write(D/'recipe'/f'{name}.json',{'type':'minecraft:crafting_shapeless','category':'misc','ingredients':[{'item':v} for v in ingredients],'result':{'id':'electron:'+name,'count':count}})
 write(D/'advancement/recipes'/f'{name}.json',{'criteria':{'has_item':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':ingredients[0]}]}}},'requirements':[['has_item']],'rewards':{'recipes':['electron:'+name]}})
recipe('ore_crusher',['electron:dc_motor','minecraft:iron_block','minecraft:iron_block','minecraft:hopper','electron:electrical_relay','minecraft:copper_ingot'])
recipe('electric_kiln',['minecraft:furnace','minecraft:iron_block','minecraft:copper_block','electron:electrical_relay','electron:resistor_100'])
recipe('fuel_fabricator',['electron:dc_motor','minecraft:piston','minecraft:iron_block','minecraft:iron_block','electron:electrical_relay'])
recipe('centrifuge',['electron:dc_motor','electron:dc_motor','electron:iron_plate','electron:iron_plate','electron:iron_plate','electron:iron_plate','electron:copper_coil','electron:electrical_relay'])
recipe('fuel_cladding',['minecraft:iron_ingot','minecraft:iron_ingot','minecraft:copper_ingot'])
recipe('wind_generator',['electron:turbine_module','electron:alternator_module','electron:transmission_shaft','electron:iron_plate','electron:iron_plate','electron:iron_plate','electron:plant_casing'])
recipe('hydro_generator',['electron:turbine_module','electron:alternator_module','minecraft:bucket','electron:iron_plate','electron:iron_plate','electron:plant_casing'])
recipe('lightning_collector',['electron:capacitor_100mf','electron:capacitor_100mf','electron:capacitor_100mf','electron:capacitor_100mf','electron:iron_plate','electron:iron_plate','electron:copper_coil','electron:electrical_relay','minecraft:lightning_rod'])
for name in ('fuel_rod','uranium_pellet'):
 for folder in ('recipe','advancement/recipes'):
  old=D/folder/f'{name}.json'
  if old.exists():old.unlink()
for rel in ('mineable/pickaxe','needs_iron_tool'):
 p=ROOT/'src/main/resources/data/minecraft/tags/block'/f'{rel}.json';data=json.loads(p.read_text());data['values']=sorted(set(data['values']+[f'electron:{n}' for n in names]));write(p,data)
strings={**{'block.electron.'+k:v for k,v in names.items()},**{'item.electron.'+k:v for k,v in items.items()},
 'tooltip.electron.processing_rating':('额定 24 V / %s W · 工作 18–30 V · 实际耗电加工','Rated 24 V / %s W; operates at 18–30 V; work consumes real energy'),
 'processing_ui.electron.slot_0':('原料','Input'),'processing_ui.electron.slot_1':('辅料','Extra'),'processing_ui.electron.slot_2':('成品','Output'),'processing_ui.electron.slot_3':('副产物','Byprod'),
 'processing_ui.electron.state_0':('等待原料','Input needed'),'processing_ui.electron.state_1':('等待供电／启用','Await power'),'processing_ui.electron.state_2':('正在加工','Processing'),'processing_ui.electron.state_3':('输出已满','Output full'),'processing_ui.electron.state_4':('机器已损坏','Damaged'),
 'guide.electron.processing.title':('用电加工与核燃料','Powered processing'),
 'guide.electron.processing.subtitle':('实际供电 → 分离提纯 → 烧结 → 压制封装','Real power > separation > sintering > fabrication'),
 'guide.electron.processing.p0':('四台机器额定 24 V，工作范围 18–30 V。粉碎机 16 W、离心机 20 W、电炉 24 W、压制封装机 12 W。连接两个电源端子后在界面放入材料；断电和输出满都会暂停。早期可用手摇直流发电机启动加工，再扩建风电、水电和变电站。','All four machines use 24 V, operating at 18–30 V: crusher 16 W, centrifuge 20 W, kiln 24 W, fabricator 12 W. Connect both power terminals and load inputs. Power loss or full outputs pause work. Start with the hand-powered DC generator before expanding renewable supply.'),
 'guide.electron.processing.p1':('1 粗铀矿经粉碎得到 2 矿料；离心分离 2 矿料得到 1 燃料级原料及 1 尾料；电炉将 1 燃料级原料烧结成 1 芯块；封装机消耗 4 芯块和 1 包壳制成 1 燃料棒。包壳由 2 铁锭和 1 铜锭合成。旧的直接烧炼、工作台燃料棒配方已取消。','One raw uranium makes two crushed ore. The centrifuge separates two crushed ore into one concentrate plus one tailings. The kiln sinters one concentrate into one pellet. Fabricate four pellets plus one cladding into a fuel rod. Craft cladding with two iron and one copper ingot. Legacy shortcut recipes are removed.'),
 'guide.electron.processing.p2':('粉碎机也能把粗铁、粗铜加工成对应金属粉，电炉按 1:1 熔炼成锭。封装机还能将 2 铁锭压成 1 铁板，或将 2 铜锭绕成 1 铜线圈。铁板和线圈用于精密机组部件；这些机器在核电之外也有用途。','The crusher handles raw iron and copper; the kiln converts their dust into ingots at 1:1. The fabricator presses two iron ingots into one plate or winds two copper ingots into one coil. Plates and coils feed precision plant components beyond nuclear fuel.'),
 'guide.electron.processing.p3':('上方和侧面漏斗输入原料／辅料，底部漏斗只抽出成品与副产物。加工能量随存档保存；取走或换成不匹配原料会重置该批进度。完成时一次性核对并扣料，不复制物品。过压或接反可能损坏机器，请使用断路器和正确电源。','Top/side hoppers insert ingredients; bottom hoppers extract outputs only. Paid work is saved. Removing or changing incompatible ingredients resets the batch. Completion checks and consumes inputs atomically. Overvoltage or reversed polarity can damage the machine; use appropriate protection.'),
}
for locale,i in [('zh_cn',0),('en_us',1)]:
 p=A/'lang'/f'{locale}.json';data=json.loads(p.read_text());data.update({k:v[i] for k,v in strings.items()})
 data['guide.electron.nuclear_site.p2']=('专用水槽 (-14,0,-5) 可接水泵，也可通过控制面板加水桶。燃料棒须经过粉碎、离心、电炉烧结和封装，详见“用电加工与核燃料”。箱子／漏斗向外部输入口供料；底部抽出乏燃料与空桶。' if i==0 else 'Tank (-14,0,-5) accepts pumped water or buckets through the console. Fuel rods require crushing, centrifuging, sintering and fabrication; see Powered processing. Feed the outside port with chests/hoppers; extract spent rods and buckets below.')
 write(p,data)
 im=Image.new('RGB',(960,320),'#1b2430');d=ImageDraw.Draw(im);font=lambda n:ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',n)
 d.text((22,14),data['guide.electron.processing.title'],font=font(25),fill='#b5cdec')
 rows=[('粉碎机','Crusher','16 W'),('离心分离机','Centrifuge','20 W'),('电炉','Kiln','24 W'),('压制封装机','Fabricator','12 W')]
 for j,row in enumerate(rows):
  x=25+j*235;d.rounded_rectangle((x,76,x+205,224),radius=9,fill='#303f51',outline='#849dbd',width=2);d.text((x+13,94),row[i],font=font(19),fill='#edf1f7');d.text((x+13,141),'24 V / '+row[2],font=font(19),fill='#b8ccdc');
  if j<3:d.line((x+208,160,x+232,160),fill='#c5ad76',width=3)
 d.text((26,264),'缺电暂停 · 满槽暂停 · 漏斗输入输出' if i==0 else 'Power and output interlocks / hopper input and output',font=font(21),fill='#e0d4b9')
 im.save(A/f'textures/gui/tutorial/{locale}/processing.png')
print('Four machines and eight solid intermediate item models generated')
