"""Precision workbench, board fabrication line, miniature packages and local control fixtures."""
from pathlib import Path
import ast,copy,json,math,random
from PIL import Image,ImageDraw,ImageFont
from pcb_geometry import precision_geometry
ROOT=Path(__file__).resolve().parents[1]
A=ROOT/'src/main/resources/assets/electron';D=ROOT/'src/main/resources/data/electron'
# Reuse only geometry helpers, without executing the older generator's resource mutations.
source=ast.parse((ROOT/'tools/generate_processing_assets.py').read_text(encoding='utf-8'))
helpers=[n for n in source.body if isinstance(n,ast.FunctionDef) and n.name in ('write','cube','model','round_vertical','transform','recipe') or isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id in ('TX','DISPLAY') for t in n.targets)]
exec(compile(ast.Module(body=helpers,type_ignores=[]),'asset_helpers','exec'))
TEX=A/'textures/block/pcb';TEX.mkdir(parents=True,exist_ok=True)
palette={'laminate':(42,90,73),'gold_trace':(194,156,65),'solder_joint':(183,190,194),'ic_package':(34,40,48),'resistor':(117,110,97),'led_on':(248,108,47),'led_off':(93,48,42),'resin':(35,94,67),'mat':(58,95,112),'chemical':(116,134,117),'bath_gold':(181,147,69),'uv_lens':(44,50,94),'drill':(173,187,192)}
for name,color in palette.items():
 im=Image.new('RGB',(128,128),color);pix=im.load();rng=random.Random(name)
 for y in range(128):
  for x in range(128):
   grain=rng.choice((-3,-2,-1,0,0,1,2,3));pix[x,y]=tuple(max(0,min(255,c+grain)) for c in color)
 d=ImageDraw.Draw(im)
 if name=='ic_package':
  d.rounded_rectangle((5,5,122,122),radius=8,outline='#58616a',width=2);d.ellipse((12,12,26,26),fill='#979e9e')
  for y in (45,54,76):d.line((35,y,99 if y==76 else 88,y),fill='#a5abaa',width=2)
 elif name=='resistor':
  d.rectangle((0,0,18,127),fill='#c5cdcd');d.rectangle((109,0,127,127),fill='#c5cdcd')
  for x in (35,55,78):d.rectangle((x,0,x+5,127),fill='#343b3e')
 elif name in ('gold_trace','solder_joint','drill'):
  for y in range(5,128,9):d.line((0,y,127,y),fill=tuple(min(255,c+15) for c in color))
  d.line((2,2,125,2),fill='#e5dfc8',width=2)
 elif name=='laminate':
  for y in range(0,128,16):d.line((0,y,127,y),fill='#345f50')
 elif name.startswith('led'):
  d.rounded_rectangle((8,8,119,119),radius=22,outline='#ddba94',width=4);d.ellipse((28,22,55,48),fill='#f7d5b0' if name.endswith('on') else '#93716c')
 im.save(TEX/f'{name}.png')
TX.update({k:'electron:block/pcb/'+k for k in palette})
precision={'brushed_steel':(157,171,178),'enamel':(91,119,130),'rubber':(33,40,46),'panel_seam':(43,60,70),'terminal_red':(155,64,52),'terminal_black':(39,48,57),'rating_plate':(211,220,212),'mat_detail':(45,98,118),'display_glass':(30,47,56),'uv_detail':(47,54,100),'fluid_window':(95,133,137),'pump_paint':(150,123,74)}
for name,color in precision.items():
 im=Image.new('RGB',(256,256),color);pix=im.load();rng=random.Random(name)
 for y in range(256):
  stripe=rng.uniform(-5,5) if name=='brushed_steel' else 0
  for x in range(256):
   grain=rng.uniform(-1.8,1.8)+stripe;pix[x,y]=tuple(max(0,min(255,int(c+grain))) for c in color)
 d=ImageDraw.Draw(im)
 if name=='panel_seam':
  d.rounded_rectangle((7,7,248,248),radius=8,outline='#728994',width=3);d.line((13,13,242,13),fill='#9bafb5',width=2)
 elif name=='mat_detail':
  for x in range(12,256,16):d.line((x,0,x,255),fill='#356577')
  for y in range(12,256,16):d.line((0,y,255,y),fill='#356577')
  d.rounded_rectangle((3,3,252,252),radius=7,outline='#79a1ae',width=3)
  for x in range(16,250,16):d.line((x,4,x,12),fill='#aec4c9',width=2)
 elif name=='rating_plate':
  f=ImageFont.truetype('C:/Windows/Fonts/consola.ttf',44);d.text((18,40),'24 V',font=f,fill='#273a41');d.text((20,120),'+   -',font=f,fill='#273a41');d.rectangle((5,5,250,250),outline='#607379',width=3)
 elif name=='display_glass':
  d.rounded_rectangle((6,6,249,249),radius=14,outline='#9daeb0',width=3)
  for y in (42,68,94):d.line((27,y,153,y),fill='#7dcec8',width=5)
  for x in (42,104,166):d.ellipse((x,177,x+27,204),fill='#beab78')
 elif name=='uv_detail':
  for x in range(12,256,24):d.line((x,0,min(255,x+65),255),fill='#566398',width=3)
  d.rectangle((2,2,253,253),outline='#a9b6c2',width=4)
 elif name=='fluid_window':
  d.rectangle((5,5,250,250),outline='#b1c3c7',width=5)
  for y in range(28,235,22):d.line((12,y,46 if y%44==28 else 31,y),fill='#d4e4df',width=3)
  d.line((198,14,232,239),fill='#a2c4c3',width=3)
 im.save(TEX/f'{name}.png');TX[name]='electron:block/pcb/'+name

def board_geometry(stage='gold_finished_pcb'):
 face='gold_trace' if 'gold' in stage or stage=='assembled_pcb' else 'chemical' if stage in ('exposed_pcb','drilled_pcb') else 'resin'
 els=[cube((1.8,6.2,2),(14.2,6.6,14),'laminate')]
 if stage=='blank_pcb':return els+[cube((1.8,6.6,2),(14.2,6.68,14),'gold_trace')]
 for x in (2.5,4,5.5,7,8.5,10,11.5,13):
  els += [cube((x,6.6,2.4),(x+.35,6.68,4),face),cube((x,6.6,12),(x+.35,6.68,13.6),face)]
 for z in (4.7,7.2,10.8):els += [cube((3,6.6,z),(12.6,6.67,z+.18),face)]
 if stage=='assembled_pcb':
  els += [cube((5,6.7,6),(9,7.8,9),'ic_package'),cube((10,6.7,5),(12,7.25,6),'resistor'),cube((10.5,6.7,9),(11.6,7.3,10.1),'led_off')]
  for x in (5,6,7,8):
   for z in (5.7,9):els.append(cube((x,6.7,z),(x+.45,7.05,z+.5),'solder_joint'))
 return els

block_names={
 'precision_workbench':('精密电子工作台','Precision Electronics Workbench'),
 'pcb_socket':('PCB 接口底座','PCB Interface Socket'),
 'uv_exposure_unit':('紫外曝光机','UV Exposure Unit'),
 'pcb_drill':('PCB 钻孔机','PCB Drill'),
 'pcb_etcher':('PCB 蚀刻清洗机','PCB Etching and Rinse Unit'),
 'solder_mask_applicator':('阻焊涂覆固化机','Solder Mask and Curing Unit'),
 'immersion_gold_line':('沉金处理机','Immersion Gold Finishing Unit'),
 'water_level_sensor':('电动水位开关','Powered Level Switch'),
 'temperature_sensor':('电动温度联锁器','Powered Temperature Interlock'),
 'emergency_lamp':('蓄电应急灯','Rechargeable Emergency Lamp')}
machine_names=list(block_names)[2:7]
def screw(x,y,z):return [cube((x-.22,y,z-.22),(x+.22,y+.12,z+.22),'solder_joint'),cube((x-.15,y+.12,z-.035),(x+.15,y+.135,z+.035),'cast_base')]
for name in block_names:
 small=name in ('pcb_socket','water_level_sensor','temperature_sensor')
 if not small:
  els,dynamic=precision_geometry(name,cube,round_vertical)
 else:
  dynamic=[];els=[cube((0,0,0),(16,2.6,16),'cast_base')]
  if name=='pcb_socket':
   els += [cube((2,2.6,2),(14,2.85,14),'porcelain')]
   for x in (1,15):
    for z in (3,7,13):els += [cube((x-.65,2.6,z-.65),(x+.65,3,z+.65),'galvanized'),*screw(x,3,z)]
  elif name in ('water_level_sensor','temperature_sensor'):
   els += [cube((5,2.6,5),(9,3.45,11),'control_case'),cube((5.4,3.45,5.5),(8.6,3.55,7),'console_panel'),cube((5.4,3.45,8),(8.6,3.6,10.6),'ic_package')]
   if name=='water_level_sensor':
    els += [cube((12.3,2.7,5),(12.8,3.7,11),'galvanized'),cube((11.9,3,6),(13.2,3.6,8),'porcelain')]
   else:
    els += [cube((10,2.8,7),(14.8,3.3,7.8),'steel_pipe'),cube((14,2.9,7.1),(15.8,3.2,7.7),'copper_bus')]
   for x in (3,11):
    for z in (3,13):els += [cube((x-.7,2.6,z-.7),(x+.7,3,z+.7),'porcelain'),*screw(x,3,z)]
 # Keep fine-grain material scale consistent instead of stretching a full texture on every bolt.
 for el in els+dynamic:
  if el['faces']['up']['texture'][1:] in ('enamel','brushed_steel','rubber','laminate'):
   x0,y0,z0=el['from'];x1,y1,z1=el['to']
   uv={'up':[x0,z0,x1,z1],'down':[x0,16-z1,x1,16-z0],'north':[16-x1,16-y1,16-x0,16-y0],'south':[x0,16-y1,x1,16-y0],'east':[16-z1,16-y1,16-z0,16-y0],'west':[z0,16-y1,z1,16-y0]}
   for face in el['faces']:el['faces'][face]['uv']=uv[face]
 variants={}
 appearances=('off','on','broken') if name=='emergency_lamp' else ('off','broken')
 for look in appearances:
  tx={'lens':'electron:block/fixture_on' if look=='on' else 'electron:block/fixture_off'}
  if look=='broken':tx.update({k:'electron:block/scorched' for k in ('laminate','control_case','porcelain','lens','console_panel')})
  for face in ('up','down','north','south','west','east'):
   for turn in range(4):
    ref=f'{name}_{look}_{face}_{turn}';write(A/'models/block'/f'{ref}.json',model(transform(els,face,turn),tx))
    if name=='emergency_lamp':
     for level in range(16):
      if look=='broken' or (level>0)==(look=='on'):variants[f'facing={face},turn={turn},broken={str(look=="broken").lower()},power={level}']={'model':'electron:block/'+ref}
    else:variants[f'facing={face},turn={turn},broken={str(look=="broken").lower()}']={'model':'electron:block/'+ref}
 write(A/'blockstates'/f'{name}.json',{'variants':variants});write(A/'models/item'/f'{name}.json',model(els+dynamic,{'lens':'electron:block/fixture_off'}))
 if dynamic:write(A/'models/block'/f'{name}_mechanism.json',model(dynamic))
 base='rechargeable_battery' if name=='emergency_lamp' else 'dc_motor'
 loot=(D/'loot_table/blocks'/f'{base}.json').read_text().replace('electron:'+base,'electron:'+name);write(D/'loot_table/blocks'/f'{name}.json',json.loads(loot))

item_names={'blank_pcb':('覆铜板','Copper-clad Laminate'),'solder_alloy':('焊料','Solder Alloy'),'copper_foil':('铜箔','Copper Foil'),'assembled_pcb':('已焊接 PCB','Assembled PCB'),'photo_film':('光绘膜','Photoplotting Film'),'pcb_photomask':('PCB 光绘底片','PCB Photomask'),'exposed_pcb':('已曝光 PCB','Exposed PCB'),'drilled_pcb':('已钻孔 PCB','Drilled PCB'),'etched_pcb':('已蚀刻 PCB','Etched PCB'),'masked_pcb':('已阻焊 PCB','Solder-masked PCB'),'gold_finished_pcb':('沉金 PCB','Gold-finished PCB'),'micro_drill_bit':('微型钻头','Micro Drill Bit'),'etchant_cartridge':('蚀刻清洗耗材盒','Etching and Rinse Cartridge'),'solder_mask_resin':('阻焊树脂','Solder Mask Resin'),'gold_bath_cartridge':('沉金耗材盒','Gold Finishing Cartridge'),'spent_process_cartridge':('废处理液盒','Spent Process Cartridge'),'process_residue':('处理废渣','Process Residue')}
for name,label in [('r1k',('贴片电阻 1 kΩ','SMD Resistor 1 kΩ')),('r10k',('贴片电阻 10 kΩ','SMD Resistor 10 kΩ')),('led',('贴片 LED','SMD LED')),('not',('微型非门','Miniature NOT Gate')),('and',('微型与门','Miniature AND Gate')),('or',('微型或门','Miniature OR Gate')),('xor',('微型异或门','Miniature XOR Gate')),('nand',('微型与非门','Miniature NAND Gate'))]:item_names['smd_'+name]=label
for name in item_names:
 if name.endswith('_pcb') or name=='assembled_pcb':els=board_geometry(name)
 elif name in ('photo_film','pcb_photomask'):
  els=[cube((2,7,2),(14,7.2,14),'porcelain')]
  if name=='pcb_photomask':
   for x in (4,7,10):els += [cube((x,7.2,3),(x+.6,7.25,12),'ic_package'),cube((x,7.2,5),(x+2,7.25,5.3),'ic_package')]
 elif name.startswith('smd_'):
  gate=name not in ('smd_r1k','smd_r10k','smd_led');els=[cube((4,6,5),(12,8 if gate else 7,11),'ic_package' if gate else 'led_off' if name=='smd_led' else 'resistor')]
  for x in (4.5,6.5,8.5,10.5) if gate else (4,11):
   for z in (4.5,11):els.append(cube((x,5.8,z),(x+.8,6.6,z+.7),'solder_joint'))
 elif name=='micro_drill_bit':
  els=round_vertical(8,2,8,1.6,6,'drill')+[cube((7.5,8,7.5),(8.5,14,8.5),'drill')]
 elif name in ('copper_foil','solder_alloy'):
  els=round_vertical(8,3,8,5,1,'cast_base')+round_vertical(8,4,8,4.3,6,'gold_trace' if name=='copper_foil' else 'solder_joint')+round_vertical(8,10,8,5,1,'cast_base')
 else:
  els=[cube((3,2,4),(13,11,12),'galvanized'),cube((4,4,3.9),(12,9,4),'bath_gold' if name=='gold_bath_cartridge' else 'resin' if name=='solder_mask_resin' else 'chemical'),cube((6,11,6),(10,12.5,10),'cast_base')]
  for x in (4,11):els+=screw(x,11,8)
 write(A/'models/item'/f'{name}.json',model(els))

recipe('precision_workbench',['electron:assembly_table','electron:iron_plate','electron:iron_plate','electron:copper_coil','electron:magnifying_visor','electron:electrical_relay','minecraft:glass'])
recipe('pcb_socket',['electron:terminal_strip','electron:iron_plate','minecraft:copper_ingot'])
recipe('copper_foil',['minecraft:copper_ingot'],8)
recipe('blank_pcb',['electron:copper_foil','minecraft:paper','minecraft:slime_ball'],2)
recipe('solder_alloy',['minecraft:iron_ingot','minecraft:copper_ingot'],8)
recipe('photo_film',['minecraft:paper','minecraft:glass_pane'],4)
recipe('micro_drill_bit',['minecraft:diamond','minecraft:iron_ingot'],16)
recipe('etchant_cartridge',['minecraft:water_bucket','minecraft:slime_ball','minecraft:iron_ingot','minecraft:glass_bottle'],4)
recipe('solder_mask_resin',['minecraft:slime_ball','minecraft:green_dye','minecraft:glass_bottle'],4)
recipe('gold_bath_cartridge',['minecraft:gold_ingot','minecraft:water_bucket','minecraft:glass_bottle'],4)
for name,part in [('r1k','resistor_1000'),('r10k','resistor_10000'),('led','indicator_led'),('not','wire_not_gate'),('and','wire_and_gate'),('or','wire_or_gate'),('xor','wire_xor_gate'),('nand','wire_nand_gate')]:recipe('smd_'+name,['electron:'+part,'electron:copper_foil'],2)
for name in machine_names:
 recipe(name,['electron:iron_plate','electron:iron_plate','electron:copper_coil','electron:electrical_relay','minecraft:glass','electron:dc_motor' if name=='pcb_drill' else 'electron:indicator_led','minecraft:diamond' if name=='pcb_drill' else 'minecraft:bucket'])
recipe('water_level_sensor',['electron:electrical_relay','minecraft:bucket','electron:resistor_1000','minecraft:copper_ingot'])
recipe('temperature_sensor',['electron:electrical_relay','electron:resistor_1000','minecraft:quartz','minecraft:copper_ingot'])
recipe('emergency_lamp',['electron:workshop_lamp','electron:rechargeable_battery','electron:iron_plate','electron:silicon_diode'])
# A used battery is never copied through this recipe: the new lamp starts empty.
for rel in ('mineable/pickaxe','needs_iron_tool'):
 p=ROOT/'src/main/resources/data/minecraft/tags/block'/f'{rel}.json';data=json.loads(p.read_text());data['values']=sorted(set(data['values']+['electron:'+n for n in block_names]));write(p,data)

mat=Image.new('RGB',(436,314),'#365467');d=ImageDraw.Draw(mat)
for x in range(0,436,12):d.line((x,0,x,314),fill='#405f72',width=1)
for y in range(0,314,12):d.line((0,y,436,y),fill='#405f72',width=1)
d.rounded_rectangle((3,3,432,310),radius=12,outline='#7595a5',width=3)
for x in range(16,430,24):d.line((x,4,x,10),fill='#a3bac4',width=1)
d.text((312,294),'ESD / CF-06',fill='#b2c3ca')
(A/'textures/gui').mkdir(exist_ok=True);mat.resize((218,157),Image.Resampling.LANCZOS).save(A/'textures/gui/precision_mat.png')

strings={**{'block.electron.'+k:v for k,v in block_names.items()},**{'item.electron.'+k:v for k,v in item_names.items()},
 'itemGroup.electron.integrated':('Electron · 集成电路与 PCB','Electron · Integrated Circuits and PCB'),
 'pin.electron.z':('输出 Z','Output Z'),
 'pcb.electron.insert':('手持已焊接 PCB 放入接口底座。','Insert an assembled PCB into the socket.'),
 'pcb.electron.remove':('潜行空手右键取出板卡；用万用表测量接口。','Sneak-use with an empty hand to remove; measure the terminals with a multimeter.'),
 'pcb.electron.board_rating':('集成电路 · %s 个元件 / %s 段铜线 · 5 V 逻辑','Integrated circuit: %s parts / %s tracks; 5 V logic'),
 'tooltip.electron.pcb_socket':('六端口：VCC / GND / A / B / Y / Z；逻辑板工作 4.5–5.5 V，输出建议不超过 20 mA。','Six ports: VCC / GND / A / B / Y / Z. Logic: 4.5–5.5 V, recommended output up to 20 mA.'),
 'tooltip.electron.precision_workbench':('额定 24 V / 24 W，工作 18–30 V；拖放设计、光绘底片和装配焊接。','24 V / 24 W, operates at 18–30 V; drag-and-drop design, photomasks and solder assembly.'),
 'tooltip.electron.powered_sensor':('5 V 供电（4.5–5.5 V）；隔离触点 30 V / 2 A。检测相邻水槽／电站核心，右键选择预设。','5 V supply (4.5–5.5 V); isolated contact 30 V / 2 A. Senses an adjacent tank/plant core; use to choose a preset.'),
 'tooltip.electron.emergency_lamp':('24 V 输入；1.2 W 灯 / 6 W 充电，200 J 储能；断电自动照明。新灯无电。','24 V input; 1.2 W lamp / 6 W charging, 200 J storage. Automatic backup lighting; crafted empty.'),
 'pcb_ui.electron.move':('拖放','Move'),'pcb_ui.electron.track':('走线','Track'),'pcb_ui.electron.outline':('板型','Outline'),'pcb_ui.electron.erase':('擦除','Erase'),'pcb_ui.electron.size':('尺寸','Size'),'pcb_ui.electron.clear':('清空','Clear'),
 'pcb_ui.electron.example':('载入非门示例','Load NOT example'),'pcb_ui.electron.load':('读取板卡','Read board'),'pcb_ui.electron.make':('加工／焊接','Build / solder'),'pcb_ui.electron.stop':('取消','Stop'),
 'pcb_ui.electron.need':('需%s','x%s'),'pcb_ui.electron.tray':('材料托盘','Material trays'),'pcb_ui.electron.dimensions':('%s×%s · 旋转 %s° · R 键','%s×%s / rotate %s° / R'),
 'pcb_ui.electron.help_0':('从下方拖入微型元件。拖动已放元件可移动。R／右键旋转。','Drag packages from below or move existing parts. R/right click rotates.'),
 'pcb_ui.electron.help_1':('点击节点拉铜线。Shift 先竖后横。交叉处导通！右键结束。','Click nodes to route copper. Shift: vertical first. Crossings connect! Right click ends.'),
 'pcb_ui.electron.help_2':('点击削边或开孔，再点恢复。不能切断板体或已有器件。','Click to mill edges/holes or restore. Board must stay in one piece with parts supported.'),
 'pcb_ui.electron.help_3':('点击删除元件及该点走线。移动元件不移动旧铜线。','Click to remove a package and tracks at that point. Moving parts leaves old copper.'),
 'pcb_ui.electron.state_0':('设计就绪。放光绘膜与黑染料制作底片，或读取沉金板焊接。','Ready. Film + black dye makes a mask, or read a gold board and solder it.'),
 'pcb_ui.electron.state_1':('等待有效供电','Await valid power'),'pcb_ui.electron.state_2':('正在加工／焊接','Processing / soldering'),'pcb_ui.electron.state_3':('缺材料或板卡设计不匹配','Missing materials or board design mismatch'),'pcb_ui.electron.state_4':('请取出成品','Collect output'),'pcb_ui.electron.state_5':('工作台已损坏','Workbench damaged'),
 'pcb_ui.electron.error_1':('加工中不能改图，请先取消。','Stop processing before editing.'),
 'pcb_ui.electron.error_2':('需要元件，且电源铜线不能短接。','Add components and remove supply shorts.'),
 'pcb_ui.electron.error_3':('检查材料、输出槽和板卡设计。','Check materials, output and board design.'),
 'pcb_ui.electron.error_4':('操作超出板型、重叠或切断支撑。','Outside board, overlapping or unsupported.'),
 'automation.electron.water_0':('补水模式：低于 25% 闭合，到 75% 断开。','Refill: closes below 25%, opens at 75%.'),
 'automation.electron.water_1':('水量联锁：至少 25%，缺水断开。','Water interlock: at least 25%, opens on shortage.'),
 'automation.electron.water_2':('水量联锁：至少 75%，缺水断开。','Water interlock: at least 75%, opens on shortage.')}
for i,temp in enumerate((60,90,120)):strings['automation.electron.temperature_'+str(i)]=(f'温度联锁：高于 {temp}°C 断开，低 5°C 恢复。',f'Temperature interlock: opens above {temp}°C, resets 5°C below.')
for typ in ('r1k','r10k','led','not','and','or','xor','nand'):
 strings['pcb.electron.part_rating_'+typ]=('PCB 小配件；精密工作台焊接。'+('额定 0.125 W。' if typ.startswith('r') else '需串联限流电阻，建议 1–15 mA。' if typ=='led' else '4.5–5.5 V；输出建议 ≤20 mA；传播延迟 2 tick。'),'Miniature PCB part; solder at the precision bench. '+('Rated 0.125 W.' if typ.startswith('r') else 'Requires series resistance; recommended 1–15 mA.' if typ=='led' else '4.5–5.5 V; recommended output up to 20 mA; two-tick propagation.'))
strings.update({
 'map.electron.pcb_flow':('底片→曝光→钻孔→蚀刻','Mask > UV > drill > etch'),
 'map.electron.pcb_book':('手册：PCB 制作与沉金','Handbook: PCB process'),
 'map.electron.pcb_tools':('材料箱在南侧走道','Supplies: south walkway'),
 'map.electron.pcb_control':('14 PCB 非门控制','14 PCB inverter control'),
 'map.electron.pcb_control_1':('开关改变 A，灯显示 Y','Switch A / lamp shows Y'),
 'map.electron.pcb_control_2':('潜行空手取板查看','Sneak-empty-use removes'),
 'map.electron.pcb_automation':('15 检测与应急照明','15 Sensors and backup'),
 'map.electron.pcb_automation_1':('水少闭合，灯模拟水泵','Low water switches load'),
 'map.electron.pcb_automation_2':('应急灯充电后断电常亮','Charged lamp backs up')})
for i,key in enumerate(('uv_exposure_unit','pcb_drill','pcb_etcher','solder_mask_applicator','immersion_gold_line','precision_workbench')):strings['map.electron.pcb_step_'+str(i)]=(f'13.{i+1} '+block_names[key][0],f'13.{i+1} '+block_names[key][1])
pages={
 'pcb_design':(('俯视工作台与板型','Top-down PCB design'),('防静电垫 · 拖放元件 · 单层铜线','Antistatic mat / drag packages / single copper layer'),[
 ('工作台需要 24 V 电源。下方托盘的元件图标可以拖到板上；拖动已有元件可移动，R 或右键旋转。托盘图标是设计工具，真实元件在最后焊接时消耗。右侧材料槽支持普通拖放与 Shift 转移。','Power the bench at 24 V. Drag package icons from the lower tray onto the board, drag placed parts to move, and use R or right click to rotate. Icons design the board; real parts are consumed during soldering. Material slots support normal dragging and shift transfers.'),
 ('尺寸键轮换 16×12、14×10、12×8、10×8。板型工具点击削角、开孔，再点恢复；必须保留接口且板材保持连成一片。已有器件或铜线挡住时，先移动或擦除它们再裁切。','Size cycles through 16×12, 14×10, 12×8 and 10×8. Outline clicks mill or restore cells. Preserve the connectors and keep the substrate in one piece. Move parts or erase copper before cutting occupied cells.'),
 ('走线工具点击起点，再点击转折点／终点。Shift 先竖后横。单层铜线交叉必然连通；不会自动形成跨线桥。元件移动后需要重新检查旧走线。光绘前会阻止 VCC 与 GND 直接短接，其余设计仍需玩家检查。','Click a start node, then corners/endpoints. Shift routes vertically first. Copper intersections on this single layer connect; no automatic insulated crossings. Check old tracks after moving parts. Direct VCC/GND copper shorts block photoplotting; inspect the rest of your design.'),
 ('非门示例可作为可编辑起点。用光绘膜和黑染料输出带设计数据的底片；修改板型和铜线后，需要重新制作底片及裸板。此阶段没有真实 STM32 兼容、固件烧录、双面过孔或自动数字编程。','Use the editable NOT example to start. Film and black dye produce a mask carrying the layout. Changes require new masks and bare boards. This stage has no actual STM32 compatibility, firmware programming, double-sided vias or MCU programming.')]),
 'pcb_process':(('PCB 制作与沉金','PCB fabrication and gold finish'),('底片 → 曝光 → 钻孔 → 蚀刻 → 阻焊 → 沉金 → 焊接','Mask > exposure > drilling > etching > mask > gold > solder'),[
 ('用铜箔、纸和黏液球制作覆铜板。曝光机放覆铜板＋光绘底片，生成曝光板；钻孔机放曝光板＋微型钻头。底片和钻头按耗材消耗，全部工序需要 18–30 V 实际供电。板型、开孔和走线在每一步完整传递。','Craft copper-clad laminate from foil, paper and slime. Expose laminate plus the printed photomask, then drill the exposed board with a micro bit. Masks and bits are consumables. Every stage uses real 18–30 V power and preserves the outline, holes and tracks.'),
 ('钻孔板＋蚀刻清洗耗材盒→蚀刻板；蚀刻板＋阻焊树脂→阻焊板；阻焊板＋沉金耗材盒→沉金板。清洗水已包含在耗材盒配方中。蚀刻与沉金产生废液盒，可送电炉消耗 120 J 干化，得到废渣和玻璃瓶。','Drilled board + etch/rinse cartridge gives etched board. Add solder mask resin, then a gold-finishing cartridge. Rinse water is included in cartridge crafting. Wet stages produce spent cartridges; the electric kiln uses 120 J to dry them into residue and a glass bottle.'),
 ('沉金板放回精密工作台第一个槽，点击“读取板卡”。按右侧用量放入焊料和实际微型元件，再加工焊接。铜线和板型必须与沉金板完全一致；不能免费修改已制好的铜层。输出满或断电都会暂停，材料在成品完成时统一扣除。','Return the gold board to the bench first slot and read its design. Supply solder and the required miniature parts, then assemble. Copper and outline must match the gold board exactly. Full output or power loss pauses work; materials commit together on completion.'),
 ('已焊接板手持插入 PCB 底座，接 VCC/GND 与信号端。5 V 逻辑输出控制继电器或 MOSFET，再控制 24 V 水泵和照明。板内电阻、LED、逻辑门参与同一电路计算；过压、短路、LED 缺少限流会导致过载。潜行空手取板，参数用万用表测。','Insert the assembled board into its socket and connect supply and signal terminals. Use 5 V logic to drive a relay/MOSFET controlling 24 V pumps or lights. Resistors, LEDs and gates share the electrical solver. Overvoltage, shorts and unresisted LEDs overload. Sneak-empty-use removes the board; measure with the multimeter.')]),
 'automation':(('水位联锁与应急照明','Level interlocks and emergency lighting'),('电力检测 → 隔离触点 → 负载控制','Powered sensing > isolated contacts > load control'),[
 ('水位开关与温度联锁器用 5 V 供电，输出为隔离的 COM/NO 触点，不凭空输出电。触点承受 30 V / 2 A，可以串入水泵供电，或作为 PCB 信号开关。没有目标、供电中断或器件损坏时触点断开。','Level and temperature sensors use 5 V; COM/NO is an isolated contact, not an energy source. Contacts handle 30 V / 2 A and can switch pump supply or PCB signals. Missing target, power loss and damage open the contact.'),
 ('水位开关检测六个相邻位置的水槽／蓄水管体，只应相邻一个目标。默认补水：低于 25% 开始，到 75% 停止；右键可选至少 25%／75% 水量联锁。多个目标会断开，避免控制错误对象。','The level switch senses one immediately adjacent tank/fluid reservoir. Default refill closes below 25% and opens at 75%; use to select minimum 25%/75% interlocks. Multiple targets open it rather than selecting an arbitrary one.'),
 ('温度联锁器紧邻电站核心，右键选 60、90、120°C 的过温断开预设；降温 5°C 后恢复。它控制外部电气回路，不替代电站自身停堆保护。隔离触点同样可以连接小型电路，用于告警或切断负载。','Place the temperature interlock next to a plant core. Presets open above 60, 90 or 120°C and reset 5°C below. It switches an external electrical circuit and supplements the plant protection. Use its contacts for alarms or load shedding.'),
 ('应急灯从 24 V 线路充电，储能 200 J，灯功率 1.2 W，充电支路额定 6 W、效率 90%。断电自动使用电池，耗尽熄灭。新合成灯为空电；拆下保存已有电量。它提供原版光照，不需要额外光影。','The emergency lamp charges from 24 V into 200 J storage. Lamp: 1.2 W; charging: 6 W at 90% efficiency. Power loss switches to finite battery energy until empty. Crafted lamps start empty; removal preserves stored energy. It uses vanilla block light.')])}
for key,(title,subtitle,paras) in pages.items():
 strings[f'guide.electron.{key}.title']=title;strings[f'guide.electron.{key}.subtitle']=subtitle
 for j,pair in enumerate(paras):strings[f'guide.electron.{key}.p{j}']=pair
for locale,index in [('zh_cn',0),('en_us',1)]:
 p=A/'lang'/f'{locale}.json';data=json.loads(p.read_text(encoding='utf-8'));data.update({k:v[index] for k,v in strings.items()});write(p,data)
 for page in pages:
  im=Image.new('RGB',(960,320),'#1b2631');d=ImageDraw.Draw(im);font=lambda n:ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',n)
  d.text((22,15),data[f'guide.electron.{page}.title'],font=font(26),fill='#c5d8e6')
  if page=='pcb_process':
   for j,(zh,en) in enumerate([('底片','Mask'),('曝光','Expose'),('钻孔','Drill'),('蚀刻','Etch'),('阻焊','Resist'),('沉金','Gold'),('焊接','Solder')]):
    x=20+j*134;d.rounded_rectangle((x,92,x+112,219),radius=8,fill='#344959',outline='#aabac8',width=2);d.text((x+12,121),[zh,en][index],font=font(21),fill='#e1d2a6');d.text((x+12,169),'24 V',font=font(18),fill='#c9d5df')
  elif page=='pcb_design':
   d.rounded_rectangle((25,74,680,290),radius=12,fill='#375c6b',outline='#7899a8',width=3)
   d.rectangle((86,100,539,260),fill='#356652');d.rectangle((480,100,539,135),fill='#375c6b')
   for x in (120,250,410):d.rectangle((x,155,x+61,187),fill='#26343e');d.line((x+30,125,x+30,215),fill='#ceab68',width=3)
   d.line((115,125,440,125),fill='#ceab68',width=3);d.ellipse((295,208,322,235),fill='#375c6b')
   d.text((701,111),'拖入 · 旋转' if index==0 else 'Drag / rotate',font=font(22),fill='#d8e3ea');d.text((701,158),'削角 · 开孔' if index==0 else 'Mill / cut holes',font=font(22),fill='#d8e3ea')
  else:
   for j,label in enumerate([('水槽','Tank'),('5 V 检测','5 V sensor'),('隔离触点','Isolated contact'),('24 V 水泵','24 V pump')]):
    x=28+j*235;d.rounded_rectangle((x,97,x+194,223),radius=8,fill='#354a59',outline='#c5d3df',width=2);d.text((x+12,145),label[index],font=font(20),fill='#f2e0b5')
  im.save(A/f'textures/gui/tutorial/{locale}/{page}.png')
print('10 distinct blocks, board stages and miniature 3D packages; top-down ESD mat; bilingual process tutorials')
