"""Inspect saved Minecraft NBT and package the playable map independently of the gameplay JAR."""
from pathlib import Path
import json,zipfile,hashlib
from minecraft_nbt import chunks, read_gzip, position, assert_no_dropped_items
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1]
WORLD=ROOT/'build/showcase-run/Circuit_Foundry_Academy'
VERSION=dict(l.split('=',1) for l in (ROOT/'gradle.properties').read_text().splitlines() if '=' in l)['mod_version']

report=(ROOT/'build/showcase-run/academy-verification.txt').read_text()
assert 'build_verified=true' in report and 'reload_verified=true' in report
assert report.count('dropped_item_entities=0')==2 and report.count('all_examples_intact=true')==2
# Scan saved entity regions too, including entities outside loaded chunks.
assert_no_dropped_items(WORLD)
level=read_gzip(WORLD/'level.dat')['Data'];assert level['GameType']==0 and level['Difficulty']==0
assert (level['SpawnX'],level['SpawnY'],level['SpawnZ'])==(8,65,-4)
circuit=read_gzip(WORLD/'data/electron_circuits.dat')['data']
assert len(circuit['devices'])==39 and len(circuit['wires'])==50
assert any(w.get('cable') for w in circuit['wires']) and any(not w.get('cable') for w in circuit['wires'])
blocks=[]
for file in (WORLD/'region').glob('*.mca'):
    for data in chunks(file):
        blocks.extend(data.get('block_entities',[]))
components=[b for b in blocks if b['id']=='electron:component'];signs=[b for b in blocks if b['id']=='minecraft:sign'];chests=[b for b in blocks if b['id']=='minecraft:chest']
assert len(components)==39 and len(signs)==10 and len(chests)==11
chest_stacks=sum(len(b.get('Items',[])) for b in chests)
assert chest_stacks==234, f'Missing toolbox contents: {chest_stacks}/234 stacks'
assert all('map.electron.' in message and 'translate' in message for sign in signs for message in sign['front_text']['messages']),'Sign instructions must remain client-translated'
assert len({(b['x'],b['y'],b['z']) for b in components})==39
assert {(b['x'],b['y'],b['z']) for b in components}=={position(d['p']) for d in circuit['devices']}
assert all(not b.get('converter_target') for b in components),'Fixed modules must have no free setting'

out=ROOT/f'dist/circuit-academy-{VERSION}';out.mkdir(parents=True,exist_ok=True)
font=lambda n:ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',n)
im=Image.new('RGB',(1000,1336),'#0d1922');d=ImageDraw.Draw(im)
d.text((30,18),'ELECTRON · CIRCUIT FOUNDRY',font=font(27),fill='#a3e9ce')
d.text((30,61),'教学馆实际存档线路俯视示意 · 非游戏截图',font=font(18),fill='#92a9b9')
titles=['01 测量与第一颗灯','02 储能与分线','03 按钮与电容','04 MOSFET 控制','05 隔离继电器','06 固定升降压','07 配电与电机','08 堵转与保护','09 自由实验']
colors=['#327d88','#416d48','#8c6036','#395d8c','#705280','#917b36','#844c4b','#505963','#617f46']
short={'hand_crank':'5V','dc_generator':'24V','resistor_220':'220Ω','resistor_10000':'10kΩ','indicator_led':'LED','rechargeable_battery':'电池','terminal_strip':'接线排','momentary_button':'按钮','capacitor_100mf':'电容','circuit_switch':'开关','power_mosfet':'MOS','electrical_relay':'继电器','engineering_lamp':'灯','boost_converter_12':'升12V','buck_converter':'降5V','distribution_box':'分线箱','dc_breaker':'断路器','dc_motor':'电机'}
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
        x,y,z=position(device['p'])
        if not (bx<=x<bx+12 and bz<=z<bz+17):continue
        px,py=xy(x+.5,z+.5);name=device['block'].split(':')[1]
        d.rounded_rectangle((px-21,py-8,px+22,py+15),radius=3,fill='#254738',outline='#9ccdba')
        d.text((px,py+3),short.get(name,name),anchor='mm',font=font(11),fill='white')
    if i==8:d.text((x0+29,y0+139),'工作台 · 装配台\n工具箱与空白台面\n\n所有实验可拆改',font=font(18),fill='#9ec4b3',spacing=12)
d.text((30,1294),'39 个器件 / 50 段导线与电缆 / 23 座跨线桥 / 和平生存 / 中英双语说明',font=font(18),fill='#a3bbca')
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

工作样机预充有限能量，会正常耗尽；需要时空手右键摇动电源，每秒一次。
生存模式准确读数只显示在万用表 HUD；铭牌上的电压/电流是额定值。
固定转换模块没有调压界面，换规格须合成并更换器件；PCB 电源芯片及焊接仍是后续设计。
选 COM 再选探针测压；潜行点设备/导线测流；潜行对空切换 V/Ω；对空右键清除测点。
未加载区块中的网络会暂停，走近实验区会恢复；没有免费持续发电或远程逻辑控制器。
地图导览是从实际保存的线路数据绘制的平面示意，不是游戏截图。
验证：每次从干净世界生成。创建后通电 100 刻，再从存档重开运行 100 刻，检查 5 个指示负载与 1 个旋转电机。
生成与重载后均不允许掉落物；打包前扫描全部实体区域，确认 0 个掉落物、11 个工具箱中的 234 组物品完整。
全部 39 个器件完好；存档含真实世界器件、物品箱和持久化线路，未完成逐区人工客户端视觉验收。
'''
(out/'地图说明.txt').write_text(instructions,encoding='utf-8')
audit={'version':VERSION,'game_mode':'survival','difficulty':'peaceful','spawn':[8,65,-4],'devices':len(components),'wires':len(circuit['wires']),'bridges':len(circuit['bridges']),'signs':len(signs),'chests':len(chests),'chest_stacks':chest_stacks,'dropped_item_entities':0,'fresh_world':True,'reopened_and_powered':True,'generator_required':False}
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
