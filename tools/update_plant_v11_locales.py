from pathlib import Path
import json
from PIL import Image,ImageDraw,ImageFont
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/electron'
entries={
'block.electron.plant_fuel_port':('固体燃料输入器','Solid Fuel Intake'),
'block.electron.reactor_fuel_intake':('反应堆燃料棒输入器','Reactor Rod Intake'),
'block.electron.plant_ash_outlet':('炉灰输出器','Combustion Residue Outlet'),
'block.electron.spent_rod_outlet':('乏燃料棒输出器','Spent Rod Outlet'),
'block.electron.plant_water_inlet':('机组管道进水口','Plant Pipe Inlet'),
'item.electron.combustion_ash':('燃烧炉灰','Combustion Ash'),
'block.electron.wall_outlet':('市电插孔','Wall Power Outlet'),
'block.electron.power_strip':('三位插排','Three-way Power Strip'),
'block.electron.appliance_adapter':('工坊电源适配器','Workshop Power Adapter'),
'item.electron.appliance_power_cord':('双芯电器电源线','Twin-core Appliance Cord'),
'message.electron.plant_body':('机身不能加料；请使用固定的燃料输入器、输出器和管道进水口。接线需打开控制器的接线柜。','Supply the fixed fuel intake, residue outlet and pipe inlet. Open the controller cabinet for wiring.'),
'message.electron.plant_pipe_only':('机组水槽只能由专用进水口接水管补给；水桶可倒入独立的外部储水槽。','Supply plant water through its pipe inlet. Buckets can fill a separate external reservoir.'),
'message.electron.service_unlinked':('接口未接入：检查接口类型、固定位置与机组结构','Unlinked port: check its type, fixed position and plant structure'),
'message.electron.use_control_room':('到外部控制面板操作：%s, %s, %s','Use the external console at %s, %s, %s'),
'message.electron.cabinet_open':('接线柜已打开，可接入导线或电缆','Cabinet open: terminals are accessible'),
'message.electron.cabinet_closed':('接线柜已关好，已连接的线路保持供电','Cabinet closed: connected circuits remain powered'),
'error.electron.cabinet_closed':('先空手右键打开接线柜，再连接线路','Open the cabinet with an empty hand before connecting a wire'),
'message.electron.wind_base_output':('在塔筒底部接线柜出线：右键开关柜门；潜行右键通断','Wire the tower-base cabinet: use to open/close, sneak-use to switch power'),
'message.electron.outlet_use':('用电源线先选插孔/插排，再选用电器；潜行空手右键开关','Use a power cord on a socket, then an appliance; sneak-use with empty hand to switch'),
'message.electron.cord_selected':('已选择供电插孔，接下来点击用电器或插排','Supply socket selected; now click an appliance or power strip'),
'message.electron.cord_connected':('双芯电源线已插好；插排保持输入电压，低压电器需使用适配器','Twin-core cord connected; strips retain input voltage, low-voltage devices need an adapter'),
'error.electron.cord_target':('电源线需连接供电插孔与用电器、适配器或插排','Connect a supply socket to an appliance, adapter or strip'),
'error.electron.cord_occupied':('这个电器的电源入口已插入电源线','This appliance already has an incoming cord'),
'error.electron.cord_full':('插孔已占满，请使用插排分配','All sockets are occupied; add a power strip'),
'error.electron.cord_length':('这根成品电源线最多沿路径铺设 12 格','This prefabricated cord supports a routed length of up to 12 blocks'),
'error.electron.cord_route':('电源线需要连续台面或地面支撑；清理路径中的障碍','A cord needs a continuous supporting floor or bench; clear obstructions'),
'error.electron.cord_source':('先点击市电插孔、插排或电源适配器的输出','First select an outlet, strip or power adapter output'),
'plant_ui.electron.slot_1':('旧桶回收','Old buckets'),
'plant_ui.electron.slot_2':('副产物','Residue'),
'plant_ui.electron.slot_3':('旧空桶','Empty buckets'),
'plant_ui.electron.nuclear':('燃料棒入口供料 · 此面板只读','Rod intake feeds fuel / read-only'),
'plant_ui.electron.thermal':('固体入口供煤 · 此面板只读','Solid intake feeds coal / read-only'),
'tooltip.electron.plant_thermal':('11 kV · 120 W；煤经固体燃料输入器；水经专用水管口','11 kV / 120 W; coal via solid-fuel intake; water via pipe inlet'),
'tooltip.electron.plant_nuclear':('24 kV · 400 W；专用棒料入口与乏燃料出口；水管冷却','24 kV / 400 W; separate fresh/spent rod ports; piped cooling'),
'map.electron.service_1':('三种接口各司其职','Separate material and water ports'),
'map.electron.service_2':('箱子→入口；出口→箱子','Chest -> intake / outlet -> chest'),
'map.electron.service_3':('水泵→水管→进水口','Pump -> pipe -> water inlet'),
'guide.electron.industrial_build.p2':('控制面板 (0,0,−1)，安全阀 (0,1,−4)，补水槽 (−2,0,−1)，固体燃料输入器 (−3,1,−2)，炉灰输出器 (−3,1,−4)，水管入口 (−3,0,−1)。留出 8 格净空。煤箱放在输入器朝外一面，炉灰箱放在输出器朝外一面；上方漏斗可输入煤，下方漏斗可收集炉灰。','Console (0,0,-1), valve (0,1,-4), tank (-2,0,-1), solid intake (-3,1,-2), ash outlet (-3,1,-4), pipe inlet (-3,0,-1). Leave eight cells of headroom. Place coal and ash chests against the respective marked outward faces. Top hoppers feed coal; bottom hoppers extract ash.'),
'guide.electron.nuclear_site.p0':('以前沿控制器为原点，平滑石基础 X=−15…20、Z=−35…0，共 36×36，留 21 格净高。坐标随朝向旋转。X=−8…2、Z=−25…−15 的边界放三层屏蔽机壳。冷却池封闭；东侧为开放的管线与物料接口区，已取消控制室。','From the front controller build a 36×36 smooth-stone foundation: X=-15…20, Z=-35…0, with 21 cells of headroom. Offsets rotate with facing. Place a three-high shielding ring at X=-8…2, Z=-25…-15. The basin is sealed. The former control room is replaced by an external service yard.'),
'guide.electron.nuclear_site.p1':('压力容器 (−3,0,−20)、控制棒驱动 (−3,1,−20)、安全阀 (−3,3,−20)；汽轮机 (7,0,−7) 和 (10,0,−7)，发电机 (7,0,−3)，冷凝器 (6,0,−15)，外部控制面板 (16,0,−3)，燃料棒输入器 (20,1,−3)，乏燃料棒输出器 (20,1,−5)。','Vessel (-3,0,-20), rod drive (-3,1,-20), valve (-3,3,-20); turbines (7,0,-7)/(10,0,-7), alternator (7,0,-3), condenser (6,0,-15), external console (16,0,-3), rod intake (20,1,-3), spent outlet (20,1,-5).'),
'guide.electron.nuclear_site.p2':('冷却槽 (−14,0,−5) 必须经管道进水口 (−14,0,−4) 补水。电动水泵可从原版水域或独立储水槽抽水。输入器只接受制成的燃料棒：粗铀→粉碎→离心→电炉→封装，均需实际供电。东侧燃料箱供料，东侧出口箱收取乏燃料。控制面板不能放物品或水桶。','Tank (-14,0,-5) receives water only through the pipe inlet (-14,0,-4). Powered pumps draw from world water or external reservoirs. Only finished fuel rods are accepted: crush uranium, centrifuge, sinter and fabricate using electricity. Eastern chests feed rods and receive spent rods. The console accepts neither items nor buckets.'),
'guide.electron.nuclear_site.p3':('水量和燃料有限，输出堵塞会阻止换棒。停机后的余热仍需冷却；失水会停机、报警，过热可损坏并发生压力爆炸。机身不能加料；燃料、水和输出口不可互换。旧图升级后请按本章补齐专用接口，缺失的付费结构可用扳手修复。','Fuel and water are finite; a full output prevents rod replacement. Decay heat still needs cooling after shutdown. Water loss trips the plant; overheating can damage it and cause a pressure explosion. Body interaction cannot add supplies. Ports are not interchangeable. Upgrade old plants with the typed ports; use a wrench to repair missing paid structures.'),
'guide.electron.fluids.p3':('机组内水槽不能直接用水桶或相邻泵灌水；必须接到固定管道入口。独立外置水槽仍可用水桶填充，再用有电的水泵送水。透明水管和水槽使用原版水材质，水量是容器数据，不会在世界中生成水源。','Internal plant tanks reject buckets and direct pump feeds. Connect to their fixed pipe inlets. External reservoirs still accept buckets and require a powered pump for delivery. Pipes and tanks use vanilla water textures for stored fluid; they do not create world water blocks.'),
'guide.electron.plug_power.title':('插孔、插排与电源线','Outlets, strips and cords'),
'guide.electron.plug_power.subtitle':('先有供电，再分配，再匹配电器电压','Supply, distribute, then match appliance voltage'),
'guide.electron.plug_power.p0':('发电机→固定变压站→240 V 进线保护柜→市电插孔。插孔背面的 IN+/IN− 可接原有线路；一根双芯成品电源线同时传输去、回两路，外观只有一根绝缘线。先用线点击插孔，再点击电器、插排或适配器。不能凭空供电。','Generator -> fixed substation -> 240 V protected inlet -> wall outlet. Existing wires feed IN+/IN-. One jacket contains separate outgoing and return conductors. Use the cord on a socket, then on an appliance, strip or adapter. Outlets cannot create energy.'),
'guide.electron.plug_power.p1':('插孔一位、插排三位。插排不改变电压；工坊适配器输入 180–264 V、输出 24 V，最高约 48 W，效率 88%，必须合成硬件。先接适配器再接低压插排。电灯、水泵、电机、加工机与 PCB 工作台可使用电源线；直接把 240 V 接到 24 V 电器仍可能烧毁。','Outlets have one socket; strips have three. Strips preserve voltage. The crafted workshop adapter accepts 180–264 V and supplies 24 V, up to about 48 W at 88% efficiency. Put it before a low-voltage strip. Lamps, pumps, motors, processing machines and the PCB bench accept cords. Direct 240 V can still destroy a 24 V appliance.'),
'guide.electron.plug_power.p2':('每根成品电源线最多铺设 12 格，沿地面或台面绕行，支持垂直下引。插排与电源线额定 2.5 A，持续过流会跳闸或烧毁；每台电器只允许一根输入线。持电源线潜行右键设备拔下一根，返还线材；拆除任一端也会返还。','Each prefabricated cord supports up to 12 routed blocks on a floor or bench, including vertical end drops. Strips and cords are rated 2.5 A; sustained overload trips or burns them. Appliances accept one incoming cord. Sneak-use a cord on a device to unplug and recover one lead; breaking an end also returns it.'),
'guide.electron.plug_power.p3':('电压铭牌已从灯具、电器模型和贴图移到手册：工程灯/工坊灯 24 V 2.4 W，庭院灯 24 V 6 W，应急灯 24 V；粉碎机 16 W、离心机 20 W、电炉 24 W、封装机 12 W，均为 24 V；PCB 曝光/钻孔/蚀刻/阻焊/沉金为 24 V。实际电压、电流仍用万用表测。接线柜右键开门，接好再关，关门不切电。','Ratings are documented here instead of printed onto appliance models: engineering/bulkhead lamps 24 V 2.4 W, yard lamp 24 V 6 W, emergency lamp 24 V; crusher 16 W, centrifuge 20 W, kiln 24 W, fabricator 12 W, all at 24 V; PCB exposure/drilling/etching/masking/gold finishing also use 24 V. Measure live values with a multimeter. Open a wiring cabinet, connect, then close; closing does not disconnect power.'),
}
subtitles={'cabinet_open':('接线柜滑门打开','Cabinet shutters open'),'cabinet_close':('接线柜锁扣合上','Cabinet latch closes'),'contact_spark':('触点迸出火花','Contact sparks'),'reactor_cooling':('反应堆冷却机组运行','Reactor cooling runs'),'transformer_hum':('变压器低鸣','Transformer hums'),'crusher_run':('粉碎机研磨','Crusher grinds'),'centrifuge_run':('离心转子高速运行','Centrifuge spins'),'kiln_run':('电炉风机运行','Kiln fan runs'),'fabricator_run':('封装机压合','Fabricator presses'),'pcb_drill_run':('钻孔主轴旋转','PCB spindle drills'),'process_bath':('处理槽循环液流','Process bath circulates')}
entries.update({'subtitles.electron.'+k:v for k,v in subtitles.items()})
entries.update({'map.electron.plug_power':('插电工坊','Plug-in workshop'),'map.electron.plug_power_1':('风机→插孔→适配器','Wind -> outlet -> adapter'),'map.electron.plug_power_2':('插排带灯和 PCB 台','Strip powers lamp and PCB bench')})
for i,locale in enumerate(('zh_cn','en_us')):
 p=A/'lang'/f'{locale}.json';obj=json.loads(p.read_text(encoding='utf-8'));obj.update({k:v[i] for k,v in entries.items()});p.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
 font=lambda n:ImageFont.truetype('C:/Windows/Fonts/msyh.ttc' if i==0 else 'C:/Windows/Fonts/segoeui.ttf',n)
 for page in ('plug_power','nuclear_site','industrial_build'):
  im=Image.new('RGB',(960,320),'#19232b');d=ImageDraw.Draw(im)
  title={'plug_power':('插电供电：先适配，再分线','Plug-in power: adapt, then distribute'),'nuclear_site':('核电外部接口：物料与冷却水分开','Nuclear service yard: separate material and water ports'),'industrial_build':('火电专用接口：箱子送料，水管供水','Thermal plant: chest-fed fuel and piped cooling')}[page][i]
  d.text((22,16),title,font=font(25),fill='#dee7e6')
  rows=([('市电插孔','Wall outlet'),('适配器','Adapter'),('三位插排','Power strip'),('灯 / PCB 工坊','Lamp / PCB bench')] if page=='plug_power' else [('燃料箱','Fuel chest'),('专用输入器','Typed intake'),('机组','Plant'),('专用输出器 → 箱子','Typed outlet -> chest')])
  for j,row in enumerate(rows):
   x=25+j*235;d.rounded_rectangle((x,78,x+205,166),radius=8,fill='#344b58',outline='#758f97',width=2);d.text((x+12,107),row[i],font=font(18),fill='#e0d9bd')
   if j<3:d.line((x+208,121,x+229,121),fill='#cbbb84',width=4);d.polygon([(x+231,121),(x+225,116),(x+225,126)],fill='#cbbb84')
  lines=([('240 V → 24 V；适配器消耗实际输入能量','240 V -> 24 V; adapter pays for output energy'),('双芯同护套；沿台面铺设；过流保护','Two separate cores in one jacket; supported routing; overload protection')] if page=='plug_power' else [('水源 / 独立槽 → 有电水泵 → 管道 → 专用进水口','Source / reservoir -> powered pump -> pipe -> fixed water inlet'),('面板只读存量并控制启停；机身不接受燃料或水桶','Console shows stocks and switches power; the body accepts no fuel or buckets')])
  for j,line in enumerate(lines):d.text((25,210+j*42),line[i],font=font(20),fill='#b6c9d0')
  im.save(A/f'textures/gui/tutorial/{locale}/{page}.png')
screen=R/'src/main/java/top/lacrus/zephyrzeno/electron/client/EngineeringGuideScreen.java';s=screen.read_text()
if '"automation","plug_power"' not in s:s=s.replace('"pcb_process","automation"','"pcb_process","automation","plug_power"')
screen.write_text(s)
print('Updated typed logistics, supply ratings, bilingual subtitles and plug-in tutorial.')
