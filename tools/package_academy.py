"""Inspect saved Minecraft NBT and package the playable map independently of the gameplay JAR."""
from pathlib import Path
import json,zipfile,hashlib
from minecraft_nbt import chunks, read_gzip, position, assert_no_dropped_items, block_state
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1]
WORLD=ROOT/'build/showcase-run/Circuit_Foundry_Academy'
VERSION=dict(l.split('=',1) for l in (ROOT/'gradle.properties').read_text().splitlines() if '=' in l)['mod_version']

report=(ROOT/'build/showcase-run/academy-verification.txt').read_text()
assert 'build_verified=true' in report and 'reload_verified=true' in report
assert report.count('dropped_item_entities=0')==2 and report.count('all_examples_intact=true')==2
assert report.count('arc_demo_powered_test=true')==2 and report.count('arc_demo_default_off=true')==2
# Scan saved entity regions too, including entities outside loaded chunks.
assert_no_dropped_items(WORLD)
level=read_gzip(WORLD/'level.dat')['Data'];assert level['GameType']==0 and level['Difficulty']==0
assert (level['SpawnX'],level['SpawnY'],level['SpawnZ'])==(8,65,-4)
circuit=read_gzip(WORLD/'data/electron_circuits.dat')['data']
assert len(circuit['devices'])==140 and len(circuit['wires'])==168
assert any(w.get('cable') for w in circuit['wires']) and any(not w.get('cable') for w in circuit['wires'])
blocks=[];saved_chunks={}
for file in (WORLD/'region').glob('*.mca'):
    for data in chunks(file):
        saved_chunks[(data['xPos'],data['zPos'])]=data
        blocks.extend(data.get('block_entities',[]))
components=[b for b in blocks if b['id']=='electron:component'];signs=[b for b in blocks if b['id']=='minecraft:sign'];chests=[b for b in blocks if b['id']=='minecraft:chest']
assert len(components)==140 and len(signs)==25 and len(chests)==23
chest_stacks=sum(len(b.get('Items',[])) for b in chests)
ash_chest=next(b for b in chests if (b['x'],b['y'],b['z'])==(64,67,6))
assert len(ash_chest['Items'])==1 and ash_chest['Items'][0]['id']=='electron:combustion_ash' and ash_chest['Items'][0]['count']>0
assert chest_stacks==361, f'Missing toolbox contents: {chest_stacks}/361 stacks'
assert all('map.electron.' in message and 'translate' in message for sign in signs for message in sign['front_text']['messages']),'Sign instructions must remain client-translated'
assert len({(b['x'],b['y'],b['z']) for b in components})==140
assert {(b['x'],b['y'],b['z']) for b in components}=={position(d['p']) for d in circuit['devices']}
assert all(not b.get('converter_target') for b in components),'Fixed modules must have no free setting'

assert report.count('components_above_bench=true')==2
parts={(b['x'],b['y'],b['z']):b for b in blocks if b['id']=='electron:assembly_part'}
for device in circuit['devices']:
    x,y,z=position(device['p']);below=block_state(saved_chunks[(x//16,z//16)],x,y-1,z)['Name']
    if below=='electron:assembly_part':below=parts[(x,y-1,z)]['original']['Name']
    expected='electron:wind_mast' if device['block']=='electron:wind_generator' else 'electron:tower_section' if device['block'] in ('electron:tower_terminal','electron:lightning_collector') else 'minecraft:smooth_stone'
    assert below==expected, (device,below)
assert sum(bool(w.get('overhead')) for w in circuit['wires'])==2
assert sum(bool(w.get('vertical')) for w in circuit['wires'])==2
assert report.count('pumps_working=true')==2
assert report.count('plug_appliances_working=true')==2 and len(circuit['appliance_cords'])==4
assert report.count('fuel_services_working=true')==2 and report.count('six_module_workshop=true')==2
services=[b for b in blocks if b['id']=='electron:plant_service']
assert len(services)==6
for service in services:
    assert position(service['controller']) in [(68,66,10),(103,66,58)]
fluids=[b for b in blocks if b['id']=='electron:fluid_component']
assert len(fluids)==8, ('Dedicated reservoirs and primed pipes',len(fluids))
for fluid in fluids:
    state=block_state(saved_chunks[(fluid['x']//16,fluid['z']//16)],fluid['x'],fluid['y'],fluid['z'])
    capacity=256000 if state['Name']=='electron:reactor_coolant_tank' else 16000 if state['Name']=='electron:boiler_water_tank' else 250
    assert 0<fluid['water_mb']<=capacity, ('Invalid or empty teaching fluid storage',fluid)
tables=[b for b in blocks if b['id']=='electron:industrial_assembly_table']
assert len(tables)==6 and sum(len(b.get('Items',[])) for b in tables)==5, 'Workshop origin must retain all five boiler ingredients'
assert report.count('pcb_fabrication_stages=5')==2 and report.count('pcb_control_working=true')==2 and report.count('emergency_lamp_charged=true')==2
assert report.count('processing_machines_working=4')==2
for i,product in enumerate(('exposed_pcb','drilled_pcb','etched_pcb','masked_pcb','gold_finished_pcb')):
    machine=next(b for b in components if (b['x'],b['y'],b['z'])==(i*18+8,66,103))
    assert any(v['Slot']==2 and v['id']=='electron:'+product and v.get('components',{}).get('minecraft:custom_data',{}).get('layout','').startswith('2|') for v in machine['Items']), ('PCB line failed',product)
for i,product in enumerate(('crushed_uranium','fuel_concentrate','uranium_pellet','fuel_rod')):
    machine=next(b for b in components if (b['x'],b['y'],b['z'])==(i*18+8,66,83))
    assert any(v['Slot']==2 and v['id']=='electron:'+product and v['count']>0 for v in machine['Items']), ('Processing line failed',product)
for entity in components:
    x,y,z=entity['x'],entity['y'],entity['z']
    state=block_state(saved_chunks[(x//16,z//16)],x,y,z)
    if state['Name'].startswith('electron:substation'):
        assert state['Properties']['power']=='15' and entity['converter_energy']>.001,('Unpowered station',x,y,z)
    if state['Name'] in ('electron:thermal_generator','electron:hydro_generator','electron:wind_generator','electron:nuclear_reactor'):
        assert state['Properties']['power']=='15' and entity['energy']>0,('Inactive plant',x,y,z)

assert report.count('all_multiblocks_formed=true')==2
owners={(b['x'],b['y'],b['z']):b for b in components}
for xyz,part in parts.items():
    owner=position(part['origin'])
    assert owner in owners,(xyz,'orphan part')
    assert block_state(saved_chunks[(owner[0]//16,owner[2]//16)],*owner)['Properties']['formed']=='true'
for xyz,owner in owners.items():
    for packed in owner.get('assembly_cells',[]):
        offset=position(packed);part=tuple(a+b for a,b in zip(xyz,offset))
        assert part in parts and position(parts[part]['origin'])==xyz,('missing assembly cell',xyz,part)

out=ROOT/f'dist/circuit-academy-{VERSION}';out.mkdir(parents=True,exist_ok=True)
font=lambda n:ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',n)
im=Image.new('RGB',(1000,1760),'#0d1922');d=ImageDraw.Draw(im)
d.text((30,18),'ELECTRON · CIRCUIT FOUNDRY',font=font(27),fill='#a3e9ce')
d.text((30,61),'教学馆实际存档线路俯视示意 · 非游戏截图',font=font(18),fill='#92a9b9')
titles=['01 测量与第一颗灯','02 储能与分线','03 按钮与电容','04 MOSFET 控制','05 隔离继电器','06 固定升降压','07 配电与电机','08 堵转与保护','09 自由实验']
colors=['#327d88','#416d48','#8c6036','#395d8c','#705280','#917b36','#844c4b','#505963','#617f46']
short={'hand_crank':'5V','dc_generator':'24V','resistor_220':'220Ω','resistor_10000':'10kΩ','indicator_led':'LED','rechargeable_battery':'电池','terminal_strip':'接线排','momentary_button':'按钮','capacitor_100mf':'电容','circuit_switch':'开关','power_mosfet':'MOS','electrical_relay':'继电器','engineering_lamp':'灯','boost_converter_12':'升12V','buck_converter':'降5V','distribution_box':'分线箱','dc_breaker':'断路器','dc_motor':'电机','substation':'变电柜'}
for i in range(9):
    x0=24+i%3*326;y0=105+i//3*393
    d.rounded_rectangle((x0,y0,x0+302,y0+377),radius=12,fill='#182a35',outline=colors[i],width=3)
    d.text((x0+15,y0+13),titles[i],font=font(19),fill='#eaf4f1')
    bx=(i%3)*18;bz=(i//3)*24+3
    def xy(x,z):return x0+27+(x-bx)*20,y0+54+(z-bz)*18
    for wire in circuit['wires']:
        hosts=[position(v) for v in wire['hosts']]
        if not hosts or not (bx<=hosts[0][0]<bx+12 and bz<=hosts[0][2]<bz+17):continue
        points=[xy(p[0]+((v>>3)&15)/16,p[2]+((v>>7)&15)/16) for p,v in zip(hosts,wire['points'])]
        d.line(points,fill=['#f28072','#acc0ce','#65dcd2','#e6cb70','#89d388','#cda6e6','#eeeeee','#8296ab'][wire['color']],width=3 if wire.get('cable') else 2)
    for device in circuit['devices']:
        if i==8:continue
        x,y,z=position(device['p'])
        if not (bx<=x<bx+12 and bz<=z<bz+17):continue
        px,py=xy(x+.5,z+.5);name=device['block'].split(':')[1]
        d.rounded_rectangle((px-21,py-8,px+22,py+15),radius=3,fill='#254738',outline='#9ccdba')
        d.text((px,py+3),short.get(name,name),anchor='mm',font=font(11),fill='white')
    if i==8:d.text((x0+29,y0+139),'工作台 · 装配台\n工具箱与矮型插排\n\n所有实验可拆改',font=font(18),fill='#9ec4b3',spacing=12)
d.rounded_rectangle((24,1294,977,1477),radius=12,fill='#182a35',outline='#437a91',width=3)
d.text((40,1307),'10 大型供电与机械传动 · 入口架空线与东侧设施',font=font(23),fill='#eaf4f1')
d.text((40,1345),'风机 → 两座输电塔 → 市电进线 → 24 V 变电 → 工程灯 / 电机 / 传动轴 / 夹持台',font=font(19),fill='#a3e9ce')
d.text((40,1380),'火电 (68,66,10)    水电 (68,66,32)    36×36 核电 (103,66,58)',font=font(19),fill='#c5d6df')
d.text((40,1413),'机组装配台 (59,66,12)    引雷 (66,72,60)    悬线 (60,66,60)',font=font(19),fill='#c5d6df')
d.text((40,1444),'两套透明供水回路已通电；补充燃料与储水，使用手册查看装配坐标。',font=font(17),fill='#92a9b9')
d.rounded_rectangle((24,1489,977,1699),radius=12,fill='#182a35',outline='#437a91',width=3)
d.text((40,1503),'13–15 PCB 制造、板控与自动联锁 · 南侧新工坊',font=font(23),fill='#eaf4f1')
d.text((40,1541),'24 V：曝光 → 钻孔 → 蚀刻清洗 → 阻焊固化 → 沉金 → 精密台焊接',font=font(19),fill='#a3e9ce')
d.text((40,1578),'工序机 x=8 / 26 / 44 / 62 / 80，y=66，z=103；工作台 (98,66,103)',font=font(18),fill='#c5d6df')
d.text((40,1614),'5 V 板控实验 (5,66,123)；水位联锁与应急灯 (x=18..29, z=123..131)',font=font(18),fill='#c5d6df')
d.text((40,1650),'材料箱 (40,65,126) / (43,65,126)；光绘与微型配件拖放见手册新增教程。',font=font(18),fill='#92a9b9')
d.text((30,1720),f"140 个器件 / 168 段线路 + 4 根电源线 / {len(circuit['bridges'])} 座跨线桥 / 9 台加工机 / 2 套水泵",font=font(18),fill='#a3bbca')
im.save(out/'地图导览.png')
instructions=f'''Electron: Circuit Foundry 教学与展示地图 — {VERSION}

安装：把压缩包中的 Circuit_Foundry_Academy 文件夹完整放入 Minecraft 的 saves。
需要 Minecraft 1.21.1、Java 21、Fabric Loader、Fabric API 和 electron-{VERSION}.jar。
不需要独立计算器模组；也不需要开发期的 electron_showcase 模组。
启动游戏，选择 Circuit_Foundry_Academy。和平生存、保留物品、固定白天，可正常拆改和合成。
出生点 (8, 65, -4)，入口工具箱在 (5, 65, -1)。所有文字取自模组中英语言文件。

01 测量 (0..11, z=3..20)：摇 5 V 源，观察 LED。持表选两个端子测压，潜行点器件测电流。
02 储能 (18..29, z=3..20)：5 V 源给电池充电，经接线排驱动 LED。剪断充电正线可观察电池继续供电。
03 延时 (36..47, z=3..20)：按一下按钮，观察电容充电和松开后 LED 的渐暗。默认未触发。
04 MOSFET (0..11, z=27..44)：切换控制回路开关，控制另一 24 V 电源上的工程灯。控制与负载共地。
05 继电器 (18..29, z=27..44)：切换 5 V 线圈开关，观察隔离触点控制 24 V 灯。
06 转换 (36..47, z=27..44)：上方为固定 12 V 升压和 10 kΩ 小负载，下方为固定 5 V 降压供继电器。
07 配电 (0..11, z=51..68)：发电机→1 A 断路器→双母线分线箱→工程灯与电机。电机右键切换负载。
08 故障 (18..29, z=51..68)：电机预设锁轴、电源初始为空；摇动电源后观察断路器跳闸。把电机切回空载，再复位断路器。
09 实验 (36..47, z=51..68)：拿材料和工具，自行搭线、合成器件，使用工程手册查看接法。
12 加工 (0..65, z=80..92)：四台通电机器从左到右为粉碎机、离心分离机、电炉、压制封装机。每台都有有限演示原料，检查输出后可取出并放到下一台；不是自动跨机物流。材料箱 (10,66,92)，右端有工坊防护灯和庭院柱灯。手册新增加工配方页。

13 PCB 工坊 (x=0..107, z=100..114)：五台制造机器位于 (8/26/44/62/80,66,103)，精密工作台 (98,66,103)。各工序已放置独立演示批次，通电后可取产物；光绘台中已有可用非门设计。不是自动跨机生产线。
14 板控 (5,66,123)：底座装有非门板，手摇源输出5 V，右键控制开关切换输入；默认输入低、LED亮，输入高时LED灭。源耗尽需手摇补电。
15 自动联锁 (x=18..29, z=123..131)：5 V水位开关监测相邻锅炉槽，隔离触点控制24 V工坊灯；空水时灯亮、装满后熄灭。旁边应急灯由实际电源充能，断线后消耗电池继续亮。
新PCB材料箱 (40,65,126)、(43,65,126)，内有微型配件、带实际设计的各阶段板和工序耗材。

入口外 (14,74,-4)、(46,74,-4) 两座玩家可拆改的电线塔，A/B 各一条架空电缆，由风机供电，经市电进线柜和变电柜带动灯和旋转夹持台。旁边箱子有塔架、塔顶座与长电缆卷。
所有台面器件放在平滑石上方，导线沿台面跨高差连接；不再把器件嵌进台面。

主要 24 V 负载由风机底柜和变电柜持续供电；5 V 控制/储能课程仍使用手摇源，耗尽后空手右键补能，每秒一次。火电控制器 (68,66,10)、水电 (68,66,32)、36×36 核电 (103,66,58)、引雷 (66,72,60)。三组机组均经对应高压变电站、24 V 变电站实际驱动灯；火电和核电还给自己的水泵供电，透明管送水进入专用槽。
火电水泵 (63,66,9) 从玻璃围住的原版水域抽水，累计抽完一层才降低水面；核电补给槽 (89,66,57) 是有限水量。按需向机组补充煤和燃料棒。发电停止后仍可能有余热，宜另设独立冷却电源。
核电控制面板 (119,66,55)，东侧输入口 (123,67,55)；火电控制柜 (68,66,9)，输入口 (65,67,8)。外侧箱子会自动向输入口送料，独立炉灰口 (65,67,6) 和乏燃料口 (123,67,53) 的外侧箱子收集产物，下方也可接漏斗。水由专用进水口供给，不能直接投水桶。核电必须使用燃料棒，不能直接投矿石。
大型机组装配台 (59,66,12) 由六块独立模块拼成，已放好一套锅炉配料；右侧选择锅炉并装配。新材料箱 (60,66,17) 含塔筒、水泵、透明/铁皮管、专用水槽和机组部件。悬线示例 (60,66,60) 的两根电缆由上、下接线座固定，由新风机供电，出厂断开底座开关。在 (60,65,51) 潜行右键底座启停，护栏外可观察悬线向旁边铁块放电。自由实验台 (41,66,57) 放有矮型双母线插排。
16 插电工坊 (112..126, z=100..114)：风机 → 市电插孔 → 固定24 V适配器 → 三位插排 → 工坊灯和PCB台。持电源线先点插孔再点电器，潜行点电器拔线。
生存模式准确读数只显示在万用表 HUD；电压铭牌已移至工程手册。
固定转换模块没有调压界面，换规格须合成并更换器件；PCB 光绘、曝光、钻孔、蚀刻、阻焊、沉金和焊接已在南侧新工坊开放；电源芯片仍是后续设计。
选 COM 再选探针测压；潜行点设备/导线测流；潜行对空切换 V/Ω；对空右键清除测点。
未加载区块中的网络会暂停，走近实验区会恢复；没有免费持续发电或远程逻辑控制器。
地图导览是从实际保存的线路数据绘制的平面示意，不是游戏截图。
验证：每次从干净世界生成。创建后通电 1200 刻，再从存档重开运行 1200 刻，检查 14 个指示负载、2 个旋转电机、4 台原料加工机与 5 台 PCB 工序产出及光绘工作台；两次均接通悬线展台 40 刻并确认漏电电流，随后断开再保存。
生成与重载后均不允许掉落物；打包前扫描全部实体区域，确认 0 个掉落物、19 个工具／材料箱、2 个供料箱和2 个出料箱中的 361 组物品（含实际生成的炉灰）完整，并检查两套供水和装配台配料。
全部 140 个器件完好；存档含真实世界器件、物品箱和持久化线路，未完成逐区人工客户端视觉验收。
'''
(out/'地图说明.txt').write_text(instructions,encoding='utf-8')
audit={'version':VERSION,'game_mode':'survival','difficulty':'peaceful','spawn':[8,65,-4],'devices':len(components),'wires':len(circuit['wires']),'bridges':len(circuit['bridges']),'signs':len(signs),'chests':len(chests),'chest_stacks':chest_stacks,'dropped_item_entities':0,'fresh_world':True,'components_above_bench':True,'overhead_spans':2,'reopened_and_powered':True,'all_plants_and_substations_powered':True,'generator_required':False,'formed_parts':len(parts),'all_multiblocks_formed':True}
audit.update(vertical_drops=2,powered_pumps=2,processing_machines=4,pcb_stages=5,pcb_control_working=True,emergency_lamp_charged=True,fluid_stores=len(fluids),workshop_cells=6,nuclear_foundation=[36,36],arc_demo_powered_test=True,arc_demo_default_off=True,fuel_services_working=True,six_module_workshop=True,plug_cords=4,plug_appliances_working=True)
(out/'map-verification.json').write_text(json.dumps(audit,indent=2)+'\n',encoding='utf-8')
archive=ROOT/f'dist/circuit-academy-{VERSION}.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for p in sorted(WORLD.rglob('*')):
        if not p.is_file() or p.name in ('session.lock','level.dat_old') or any(v in p.relative_to(WORLD).parts for v in ('playerdata','advancements','stats')):continue
        z.write(p,'Circuit_Foundry_Academy/'+p.relative_to(WORLD).as_posix())
    for p in sorted(out.iterdir()):z.write(p,p.name)
with zipfile.ZipFile(archive) as z:assert z.testzip() is None and 'Circuit_Foundry_Academy/level.dat' in z.namelist()
audit.update(file=str(archive),bytes=archive.stat().st_size,sha256=hashlib.sha256(archive.read_bytes()).hexdigest())
(ROOT/'build/academy-package-verification.json').write_text(json.dumps(audit,indent=2)+'\n',encoding='utf-8')
print(json.dumps(audit,indent=2))
