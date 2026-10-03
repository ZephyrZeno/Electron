"""Plant service ports, six-module workshop, refined fuel textures and bilingual operating instructions."""
from pathlib import Path
import json, random, math
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1];A=ROOT/'src/main/resources/assets/electron';D=ROOT/'src/main/resources/data/electron'
def write(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def tex(n,draw,base='#72818a'):
 im=Image.new('RGBA',(128,128),base);d=ImageDraw.Draw(im);draw(d);im.save(A/'textures/block/assembled'/f'{n}.png')
tex('room_wall',lambda d:[d.line((0,y,128,y),fill='#65777c',width=1) for y in (0,127)],'#b5c0be')
tex('floor_tiles',lambda d:[d.rectangle((x,y,x+31,y+31),outline='#5e6d73',width=1) for x in range(0,128,32) for y in range(0,128,32)],'#8b9b9d')
def grille(d):
 for i in range(0,128,16):d.rectangle((i,0,i+3,127),fill='#b8c5c5');d.rectangle((0,i,127,i+3),fill='#6a848d')
tex('pool_grille',grille,(0,0,0,0))
def console(d):
 d.rounded_rectangle((3,3,124,124),radius=6,fill='#243941',outline='#a3babd',width=3)
 for x in (12,70):
  d.rectangle((x,14,x+45,58),fill='#08212a',outline='#6b9699',width=2)
  d.line([(x+3,46),(x+10,40),(x+18,42),(x+24,23),(x+32,35),(x+42,30)],fill='#61bda5',width=2)
 for x in range(13,118,17):
  d.ellipse((x,78,x+9,87),fill='#74bd9f' if x%2 else '#d4b565',outline='#b4c4c6')
  d.rectangle((x,102,x+10,108),fill='#bdc6c1')
tex('console_panel',console)
def pictogram(d,port=False):
 d.rectangle((3,3,124,124),outline='#c4cfba',width=3)
 if port:
  d.rectangle((36,16,90,93),outline='#c0d0d0',width=4)
  for x in (44,58,72,86):d.line((x,26,x,80),fill='#c8bb6c',width=4)
  d.polygon([(11,49),(31,49),(31,38),(46,63),(31,87),(31,76),(11,76)],fill='#6ec4ab')
 else:
  d.rectangle((18,28,110,85),outline='#8ec4ba',width=4);d.line((64,85,64,108),fill='#8ec4ba',width=4);d.line((36,108,92,108),fill='#8ec4ba',width=4)
  d.line([(29,67),(45,67),(54,45),(62,76),(79,53),(98,53)],fill='#d3dfbd',width=3)
tex('room_sign',pictogram,'#203e49');tex('intake_sign',lambda d:pictogram(d,True),'#233941')

TX={n:'electron:block/assembled/'+n for n in ('galvanized','cast_base','control_case','console_panel','intake_sign','service_panel','copper_bus','safety_yellow','grating')};TX['particle']=TX['galvanized']
def cube(a,b,t):return {'from':list(a),'to':list(b),'faces':{f:{'texture':'#'+t,'uv':[0,0,16,16]} for f in ('north','south','east','west','up','down')}}
def model(n,els):write(A/'models/block'/f'{n}.json',{'parent':'minecraft:block/block','ambientocclusion':True,'textures':TX,'elements':els})
for n,port in [('plant_control_module',False),('plant_fuel_port',True)]:
 els=[cube((.5,0,.5),(15.5,1,15.5),'cast_base'),cube((1,1,1),(15,14,15),'control_case')]
 if port:
  els += [cube((2,3,14.8),(14,12,15.8),'galvanized'),cube((3,4,15.7),(13,11,15.95),'intake_sign'),cube((5,14,5),(11,15,11),'cast_base')]
  for x in (2,13):
   for y in (3,11):els.append(cube((x,y,15.8),(x+.65,y+.65,16),'copper_bus'))
 else:
  els += [cube((2,7,14.8),(14,13.5,15.9),'console_panel'),cube((2,1,15),(14,5.5,15.8),'service_panel'),cube((6,3,15.7),(10,3.5,16),'galvanized')]
 model(n,els);write(A/'blockstates'/f'{n}.json',{'variants':{f'facing={f}':{'model':'electron:block/'+n,'y':r} for f,r in [('south',0),('west',90),('north',180),('east',270)]}})
 write(A/'models/item'/f'{n}.json',{'parent':'electron:block/'+n})
write(D/'loot_table/blocks/plant_fuel_port.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'electron:plant_fuel_port'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
write(D/'recipe/plant_fuel_port.json',{'type':'minecraft:crafting_shapeless','category':'misc','ingredients':[{'item':'minecraft:'+n} for n in ['hopper','iron_ingot','copper_ingot']],'result':{'id':'electron:plant_fuel_port','count':1}})
write(D/'advancement/recipes/plant_fuel_port.json',{'criteria':{'hopper':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['minecraft:hopper']}]}}},'requirements':[['hopper']],'rewards':{'recipes':['electron:plant_fuel_port']}})

# Six physically placed modules have distinct roles; a standalone module remains a plain cabinet.
for part in range(6):
 els=[cube((0,11.7,0),(16,14,16),'galvanized'),cube((.3,10.8,.3),(15.7,11.7,15.7),'cast_base')]
 for x in (1,13):
  for z in (1,13):els.append(cube((x,0,z),(x+2,11.7,z+2),'cast_base'))
 if part in (0,2):
  els.append(cube((2,1,2),(14,10.8,14),'control_case'))
  for y in (2,5,8):els += [cube((2,y,.6),(14,y+2.3,1.05),'service_panel'),cube((5.5,y+.8,.25),(10.5,y+1.2,.65),'galvanized')]
 elif part==1:
  els.append(cube((1,1,3),(15,2,15),'grating'))
 elif part==3:
  for x in (3,7,11):els.append(cube((x,14.01,3),(x+2,14.4,13),'grating'))
 elif part==4:
  els += [cube((3,14,4),(13,14.7,12),'cast_base'),cube((3,14.7,4),(5,16,12),'galvanized'),cube((10,14.7,4),(12,16,12),'galvanized'),cube((4,14.7,7),(14,15.3,8),'copper_bus')]
 else:
  els += [cube((2,14,2),(14,15.8,14),'control_case'),cube((3,15.81,3),(13,15.95,13),'console_panel')]
 model('industrial_table_'+str(part),els)
model('industrial_table_module',[cube((0,11.7,0),(16,14,16),'galvanized'),cube((1,0,1),(15,11.7,15),'control_case'),cube((2,2,.8),(14,10,1.05),'service_panel')])
variants={}
for f,r in [('north',0),('east',90),('south',180),('west',270)]:
 variants[f'formed=false,facing={f}']={'model':'electron:block/industrial_table_module','y':r}
 for p in range(6):variants[f'formed=true,part={p},facing={f}']={'model':f'electron:block/industrial_table_{p}','y':r}
write(A/'blockstates/industrial_assembly_table.json',{'variants':variants});write(A/'models/item/industrial_assembly_table.json',{'parent':'electron:block/industrial_table_module'})
recipe=json.loads((D/'recipe/industrial_assembly_table.json').read_text());recipe['result']['count']=6;write(D/'recipe/industrial_assembly_table.json',recipe)

# Fine-grained mineral inclusions in stone, without a flat fluorescent patch.
rng=random.Random(852);im=Image.new('RGB',(64,64));d=ImageDraw.Draw(im)
for y in range(64):
 for x in range(64):
  v=int(100+9*math.sin(x/5+y/8)+rng.randint(-14,14));im.putpixel((x,y),(v,v+2,v+1))
for _ in range(32):
 x,y=rng.randrange(64),rng.randrange(64);pts=[(x+rng.randint(-6,6),y+rng.randint(-5,5)) for i in range(7)]
 d.polygon(pts,fill=rng.choice(['#525a38','#707443','#887a47','#3c4840']));d.line((x-2,y-1,x+2,y-2),fill='#a39d69',width=1)
im.save(A/'textures/block/uranium_ore.png')
for n in ('raw_uranium','uranium_pellet','fuel_rod','spent_fuel_rod'):
 im=Image.new('RGBA',(64,64));d=ImageDraw.Draw(im)
 if n=='raw_uranium':
  poly=[(8,43),(12,23),(24,10),(43,14),(55,29),(50,48),(34,56),(16,52)];d.polygon(poly,fill='#626c55',outline='#343e3e')
  d.polygon([(13,24),(24,12),(33,25),(25,43),(10,42)],fill='#899478');d.polygon([(33,25),(43,15),(54,30),(47,46),(25,43)],fill='#475d4c')
  for _ in range(80):
   x,y=rng.randrange(16,47),rng.randrange(22,47);d.rectangle((x,y,x+1,y+2),fill=rng.choice(['#a49e6b','#677f50','#354a42']))
 elif n=='uranium_pellet':
  d.rounded_rectangle((18,15,47,53),radius=5,fill='#465752',outline='#a4ada0',width=2);d.ellipse((18,10,47,23),fill='#8e9988',outline='#c0c6ae',width=2);d.ellipse((24,13,41,20),fill='#6a755e');d.line((23,24,23,47),fill='#869a82',width=3);d.line((44,24,44,48),fill='#263d39',width=3)
 else:
  dark=n.startswith('spent');d.rounded_rectangle((15,4,49,61),radius=4,fill='#273941',outline='#a4b9bc',width=2)
  for x in (20,28,36,44):
   d.rectangle((x,11,x+3,53),fill='#8b9581' if dark else '#b9c8bd');d.line((x,11,x,53),fill='#dde2cf');d.line((x+3,11,x+3,53),fill='#566d69')
  for y in (8,26,46,55):d.rectangle((14,y,50,y+3),fill='#768f92',outline='#c7d1ce')
  d.rectangle((24,2,40,7),fill='#c5a95b' if not dark else '#957b5d');d.rectangle((17,57,47,61),fill='#c6a554' if not dark else '#966b51')
 im.save(A/'textures/item'/f'{n}.png')

entries={
'block.electron.plant_fuel_port':('机组燃料输入口','Plant Fuel Intake'),
'block.electron.industrial_assembly_table':('大型装配台模块','Industrial Workbench Module'),
'container.electron.plant_service':('机组控制面板','Plant Control Panel'),
'message.electron.service_unlinked':('先完成机组结构；控制柜与输入口会自动接入','Complete the plant structure to link the console and intake'),
'message.electron.use_control_room':('到控制面板操作：%s, %s, %s','Use the control panel at %s, %s, %s'),
'message.electron.table_six':('需要 6 块装配台模块，按同方向的 3×2 平台拼齐','Place six workbench modules facing the same way in a 3×2 rectangle'),
'plant_ui.electron.toggle':('启停','On / Off'),
'plant_ui.electron.slot_0':('燃料','Fuel'),'plant_ui.electron.slot_1':('水桶','Water'),'plant_ui.electron.slot_2':('乏燃料','Spent'),'plant_ui.electron.slot_3':('空桶','Buckets'),
'plant_ui.electron.nuclear':('核电：使用反应堆燃料棒','Nuclear: reactor fuel rods'),
'plant_ui.electron.thermal':('火电：煤、木炭或煤炭块','Thermal: coal / charcoal'),
'plant_ui.electron.stores':('燃料 %s%% · 水位 %s%%','Fuel %s%% / Water %s%%'),
'plant_ui.electron.status_0':('已停机','Stopped'),'plant_ui.electron.status_1':('正在发电','Generating'),'plant_ui.electron.status_2':('缺少燃料','Fuel needed'),'plant_ui.electron.status_3':('检查结构/供水','Check plant / water'),'plant_ui.electron.status_4':('机组已损坏','Plant damaged'),
'plant.electron.nuclear_foundation':('需要 36×36 平滑石基础，详见大型核电章节','Needs a 36×36 smooth-stone foundation; see the nuclear chapter'),
'tooltip.electron.plant_thermal':('11 kV · 120 W；控制面板加料；外部输入口支持漏斗和箱子','11 kV / 120 W; fuel through the control panel or hopper/chest intake'),
'tooltip.electron.plant_nuclear':('24 kV · 400 W；燃料棒供能；控制室加料，专用水槽冷却','24 kV / 400 W; fuel rods, control room and dedicated coolant tank'),
'map.electron.service':('机组加料与控制','Plant fuel and controls'),
'map.electron.service_1':('核电控制室 120,66,57','Nuclear room: 120,66,57'),
'map.electron.service_2':('箱子→输入口；下方回收','Chest feeds intake; outputs below'),
'map.electron.service_3':('6 块装配台拼成 3×2','Six bench modules form 3×2'),
'guide.electron.industrial_build.p0':('合成得到 6 块装配台模块，逐块放在平整支撑上，按同方向拼成 3×2。六块齐全才可打开；拆掉任意一块就停用，其余模块保留，材料会掉出。放入电路元件和金属，选择部件并装配。','Craft six workbench modules and place them individually on level supports in a 3×2 rectangle facing the same way. All six are required to open it. Removing one disables it, preserves the other modules and returns stored materials. Add circuit parts and metals, select a module and assemble.'),
'guide.electron.industrial_build.p2':('控制柜 (0,0,−1)，安全阀 (0,1,−4)，补水槽 (−2,0,−1)，燃料输入口 (−3,1,−2)。留出 8 格上方净空，拼齐自动成型。右键控制柜加入煤、木炭或煤炭块；输入口支持漏斗、相邻箱子或木桶，底面只输出空桶。','Control console (0,0,−1), safety valve (0,1,−4), tank (−2,0,−1), fuel intake (−3,1,−2). Leave eight cells overhead for automatic formation. Right-click the console for coal, charcoal or coal blocks. Intake accepts hoppers, adjacent chests/barrels; its bottom outputs empty buckets.'),
'guide.electron.nuclear_site.p0':('以前沿控制器为原点，平滑石基础范围 X=−15…20、Z=−35…0，共 36×36，留 21 格净高。坐标随朝向旋转。X=−8…2、Z=−25…−15 的边界放三层屏蔽机壳。冷却池有实体盖栅，禁止进入；东侧控制室可步行进入。','From the front controller, build a 36×36 smooth-stone foundation: X=−15…20, Z=−35…0, with 21 cells overhead. Offsets rotate with facing. Build a three-high shielding ring at X=−8…2, Z=−25…−15. The coolant basin has a solid grille; use the walk-in control room on the east.'),
'guide.electron.nuclear_site.p1':('压力容器 (−3,0,−20)、控制棒驱动 (−3,1,−20)、安全阀 (−3,3,−20)；汽轮机 (7,0,−7) 与 (10,0,−7)，发电机 (7,0,−3)，冷凝器 (6,0,−15)，控制柜 (16,0,−3)，外部输入口 (20,1,−3)。','Vessel (−3,0,−20), rod drive (−3,1,−20), valve (−3,3,−20); turbines (7,0,−7) and (10,0,−7), alternator (7,0,−3), condenser (6,0,−15), console (16,0,−3), outside intake (20,1,−3).'),
'guide.electron.nuclear_site.p2':('冷却水槽 (−14,0,−5) 可接泵；控制面板也接受水桶。粗铀矿烧炼为芯块，4 芯块＋2 铁锭＋1 铜锭合成燃料棒，放入燃料槽。箱子/漏斗可向外部口供料，下方漏斗提取乏燃料棒和空桶。乏燃料槽满会阻止装入下一根。','Tank (−14,0,−5) accepts a pump; the console also accepts water buckets. Smelt raw uranium into pellets; craft 4 pellets + 2 iron + 1 copper into a fuel rod and place it in Fuel. Chests/hoppers feed the outside port; a bottom hopper extracts spent rods and buckets. A full spent slot blocks the next rod.'),
}
for i,locale in enumerate(('zh_cn','en_us')):
 p=A/'lang'/f'{locale}.json';data=json.loads(p.read_text(encoding='utf-8'));data.update({k:v[i] for k,v in entries.items()})
 # Revise the existing chapter instead of adding conflicting 30x30 instructions.
 for key,value in list(data.items()):
  if key.startswith('guide.electron.'):data[key]=value.replace('30×30','36×36').replace('30 x 30','36 x 36').replace('30-by-30','36-by-36')
 write(p,data)
 font=lambda n:ImageFont.truetype('C:/Windows/Fonts/msyh.ttc' if i==0 else 'C:/Windows/Fonts/segoeui.ttf',n)
 for page in ('nuclear_site','industrial_build'):
  im=Image.new('RGB',(960,320),'#12242f');d=ImageDraw.Draw(im)
  title=('36×36 核电：控制室与外部输入' if i==0 else '36x36 nuclear site: control room and outside intake') if page=='nuclear_site' else ('六块模块，逐块放置，拼成 3×2' if i==0 else 'Place six separate modules to form a 3x2 workshop')
  d.text((20,14),title,font=font(25),fill='#a8e7d4')
  if page=='nuclear_site':
   d.rectangle((28,60,275,301),outline='#9fbabb',width=3)
   d.ellipse((68,99,141,181),fill='#7c9294');d.ellipse((180,81,220,125),outline='#bcc9c5',width=6)
   d.rectangle((34,209,71,257),fill='#417493');d.line((34,213,71,251),fill='#bfcfd1',width=2)
   d.rectangle((172,211,224,270),fill='#3d6863');d.rectangle((231,247,270,287),outline='#7fe1bd',width=3)
   d.rectangle((260,267,278,278),fill='#d9b764')
   rows=[('控制柜 (16,0,−3) → 打开加料界面','Console (16,0,-3) -> open fuel inventory'),('外部输入口 (20,1,−3) ← 箱子 / 漏斗','Outside intake (20,1,-3) <- chest / hopper'),('输入：燃料棒、水桶；底部：乏燃料、空桶','In: rods / water. Bottom: spent rods / buckets.'),('冷却池有实体盖栅；房间入口在前方','Covered coolant pool; room entrance at the front.'),('基础 X=−15…20 / Z=−35…0','Foundation X=-15..20 / Z=-35..0')]
   for j,pair in enumerate(rows):d.text((303,77+j*43),pair[i],font=font(19),fill='#d9e7df')
  else:
   for row in range(2):
    for col in range(3):
     x=37+col*76;y=78+row*89;d.rounded_rectangle((x,y,x+69,y+79),radius=5,fill='#465e66',outline='#9fbbbb',width=2);d.text((x+22,y+22),str(row*3+col+1),font=font(28),fill='#e1eacb')
   rows=[('每放一次，消耗一块；不会凭空展开','One placed item creates one block, not six.'),('六块同向、底部有支撑，拼齐自动启用','Align all six on supports for automatic assembly.'),('少一块就停用；拆开返还材料','One missing module disables work and returns items.'),('部件 → 建机组 → 控制柜与燃料输入口','Modules -> plant -> control console and intake')]
   for j,pair in enumerate(rows):d.text((295,81+j*49),pair[i],font=font(19),fill='#d9e7df')
  im.save(A/f'textures/gui/tutorial/{locale}/{page}.png')
for rel in ('mineable/pickaxe','needs_iron_tool'):
 p=ROOT/'src/main/resources/data/minecraft/tags/block'/f'{rel}.json';data=json.loads(p.read_text());data['values']=sorted(set(data['values']+['electron:plant_fuel_port']));write(p,data)
print('Plant service, modular workshop and refined fuel assets generated')
