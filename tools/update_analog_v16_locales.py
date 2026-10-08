"""Bilingual component specifications and original circuit diagrams for the analog expansion."""
from pathlib import Path
import json
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[1]
A=ROOT/'src/main/resources/assets/electron'
PARTS={
 'r220':('贴片电阻 · 220 Ω','220 Ohm SMD Resistor','220 Ω / 0.125 W；LED 限流和负载分压','220 ohm / 0.125 W; LED limiting and voltage division'),
 'r100k':('贴片电阻 · 100 kΩ','100 kOhm SMD Resistor','100 kΩ / 0.125 W；弱上拉、下拉与 RC 延时','100 kohm / 0.125 W; weak pull-up/down and RC delay'),
 'diode':('贴片硅二极管','SMD Silicon Diode','A → K 单向导通；约 0.7 V / 100 mA；反向耐压 50 V','A to K conduction; about 0.7 V / 100 mA; 50 V reverse rating'),
 'cap100uf':('贴片电解电容 · 100 μF','100 uF SMD Electrolytic Capacitor','100 μF / 10 V；有极性，断电后仍可能带电','100 uF / 10 V; polarized, may retain charge after disconnect'),
 'pot10k':('贴片微调电位器 · 10 kΩ','10 kOhm SMD Trimmer','A/B/W 三端无源分压；10 kΩ / 0.1 W；右键调大，潜行调小','A/B/W passive divider; 10 kohm / 0.1 W; use to increase, sneak-use to decrease'),
 'comparator':('贴片电压比较器','SMD Voltage Comparator','4.5–5.5 V；IN+ / IN− 差分比较；输出 20 mA，20 mV 回差','4.5–5.5 V; differential IN+ / IN-; 20 mA output, 20 mV hysteresis'),
 'schmitt':('贴片施密特缓冲器','SMD Schmitt Buffer','4.5–5.5 V；非反相；低于 30% 拉低，高于 70% 拉高；输出 20 mA','4.5–5.5 V; non-inverting; 30% low / 70% high thresholds; 20 mA output'),
 'mosfet':('贴片 N 沟道 MOSFET','SMD N-Channel MOSFET','G/D/S；30 V / 200 mA；G-S 额定 12 V；3 V 开、1.5 V 关','G/D/S; 30 V / 200 mA; gate-source rated 12 V; on at 3 V, off at 1.5 V'),
 'buck5':('微型 5 V 降压模块','Miniature 5 V Buck Module','7–30 V 输入 → 固定 5 V 输出 / 500 mA；效率 85%；共地、有损耗','7–30 V input to fixed 5 V / 500 mA; 85% efficiency; common ground, paid energy')}
VALUES={
 'tooltip.electron.powered_sensor':('5 V 供电（4.5–5.5 V），约 2 mA；隔离触点 30 V / 2 A。右键选择模式，检测条件见工程手册。','5 V supply (4.5–5.5 V), about 2 mA; isolated contacts rated 30 V / 2 A. Use to select mode; see the handbook for sensing conditions.'),
 'block.electron.light_sensor':('光照感应开关','Light-Sensing Switch'),
 'block.electron.rain_sensor':('雨水探测开关','Rain-Sensing Switch'),
 'pcb_ui.electron.category_6':('模拟 / 电源','Analog / power'),
 'pcb_ui.electron.example_4':('RC 延时板','RC timer'),
 'pcb_ui.electron.example_5':('稳压驱动板','Buck driver'),
 'automation.electron.light_0':('夜灯模式：光照 ≤4 闭合，≥8 断开。','Night light: closes at light <=4, opens at >=8.'),
 'automation.electron.light_1':('明亮联锁：光照 ≥11 闭合，≤7 断开。','Bright interlock: closes at light >=11, opens at <=7.'),
 'automation.electron.light_2':('阴暗模式：光照 ≤8 闭合，≥12 断开。','Dim-area mode: closes at light <=8, opens at >=12.'),
 'automation.electron.rain_0':('遇雨闭合：探测面必须直接淋雨。','Closes in rain: the sensing face must receive rain.'),
 'automation.electron.rain_1':('干燥闭合：遮雨、停雨或干燥区域时闭合。','Closes when dry, sheltered or outside rainfall.'),
 'automation.electron.rain_2':('雷雨闭合：同时满足淋雨与雷暴。','Closes when exposed to rain during a thunderstorm.'),
 'guide.electron.pcb_analog.title':('模拟元件与 RC 延时','Analog parts and RC timing'),
 'guide.electron.pcb_analog.subtitle':('电荷、分压与比较，再驱动实际负载','Charge, divide and compare, then drive a real load'),
 'guide.electron.pcb_analog.p0':('设计站新增“模拟 / 电源”分类。220 Ω、100 kΩ 电阻均额定 0.125 W；二极管正向 A→K，约 0.7 V、100 mA，反向额定 50 V。100 μF 电容额定 10 V，必须分清正负；电量随存档保留，断电后通过负载逐渐放完。','Find the Analog / power tray in the design station. The 220 ohm and 100 kohm resistors are rated 0.125 W. The diode conducts A to K at about 0.7 V, rated 100 mA and 50 V reverse. The 100 uF capacitor is polarized and rated 10 V. Its charge persists in saves and drains through the connected load.'),
 'guide.electron.pcb_analog.p1':('10 kΩ / 0.1 W 电位器用 A、B 两端接电源和回线，W 输出无源分压。装好的 PCB 上右键旋钮每次调 10%，潜行右键反向；中位出厂，不会凭空供电。比较器供电 4.5–5.5 V、输出上限 20 mA；IN+ 比 IN− 高 20 mV 时拉高，低 20 mV 时拉低。','Connect the 10 kohm / 0.1 W trimmer A and B across the supply and use W as a passive divider. Use the mounted knob for 10% steps; sneak-use reverses. It starts centered and cannot supply free energy. The comparator needs 4.5–5.5 V and drives up to 20 mA. It switches high when IN+ exceeds IN- by 20 mV, low when it falls 20 mV below.'),
 'guide.electron.pcb_analog.p2':('施密特缓冲器供电 4.5–5.5 V，输入超过电源 70% 变高，低于 30% 变低，中间保持；NC 留空。RC 延时样板只需接 5 V：点开关后，10 kΩ 电阻给 100 μF 电容充电，超过电位器阈值才点灯；中位约一秒。关开关通过二极管加速复位。','The Schmitt buffer needs 4.5–5.5 V; it goes high above 70% of supply, low below 30%, and holds between them. Leave NC open. Feed the RC timer sample 5 V and toggle its switch: 10 kohm charges 100 uF until the adjustable threshold lights the LED, about one second at center. Switching off discharges faster through the diode.'),
 'guide.electron.pcb_analog.p3':('MOSFET 是 30 V / 200 mA 的 N 沟道低端开关：G 接控制，D 接负载，S 接回线；G-S 额定 12 V，3 V 开启、1.5 V 关闭，带体二极管。微型降压模块含线圈与电容：7–30 V 输入、固定 5 V / 500 mA 输出、85% 效率。稳压驱动样板可接 24 V，调高电位器点亮 LED。电路按游戏刻计算，不模拟高频开关波形。','The 30 V / 200 mA N-channel MOSFET switches a low-side load: G control, D load, S return; G-S rated 12 V, on at 3 V and off at 1.5 V, with a body diode. The buck module includes its coil and capacitor: 7–30 V in, fixed 5 V / 500 mA out, 85% efficiency. Feed the driver sample 24 V and increase the trimmer to light its LED. Simulation is tick-based, without high-frequency switching waveforms.'),
 'guide.electron.environment.title':('光照与天气自动控制','Light and weather automation'),
 'guide.electron.environment.subtitle':('给传感器供电，再用隔离触点控制设备','Power the sensor, then switch a load through isolated contacts'),
 'guide.electron.environment.p0':('光照感应开关与雨水探测开关使用 4.5–5.5 V、约 2 mA；COM/NO 隔离触点额定 30 V / 2 A，可接电灯、水泵或 PCB 输入。触点只通断外部电路，本身不产生电压；不能接市电或高压，也不能把电变成红石。','Light and rain switches require 4.5–5.5 V, about 2 mA. Isolated COM/NO contacts handle 30 V / 2 A for lamps, pumps or PCB inputs. Contacts switch an external circuit and produce no voltage. Do not connect mains or high voltage; they cannot convert electricity into redstone.'),
 'guide.electron.environment.p1':('光照开关读取探测面前方的天空光与方块光，包含昼夜变化。右键轮换夜灯、明亮联锁、阴暗三种模式，开关阈值不同以免临界点反复抖动。电灯不要正对自己的感光面，否则会形成明灭反馈。','The light switch reads sky and block light in front of its sensing face, including day/night changes. Use to cycle night-light, bright-interlock and dim-area modes. Separate on/off thresholds prevent chatter. Keep the controlled lamp away from its own sensing face to avoid feedback.'),
 'guide.electron.environment.p2':('雨水开关读取探测面是否实际淋雨，屋顶遮挡、无雨群系、雪和天气都会影响。右键选择遇雨闭合、干燥闭合或雷雨闭合。可以把触点串入灌溉泵、警示灯或防雨机构的控制回路。','The rain switch senses actual rainfall at its face; roofs, rainless biomes, snow and weather affect it. Use selects wet, dry or thunderstorm mode. Put its contacts into an irrigation pump, warning light or rain-cover control circuit.'),
 'guide.electron.environment.p3':('失去供电、严重过压或损坏时触点断开。传感器触点切换时在本体播放继电器声，状态灯表示通断；实时电压、电流仍需万用表。教学区 (63,66,158) 的元件箱备有两种传感器、九种新元件和两组可焊接样板。','Power loss or damage opens the contacts; severe overvoltage can damage the device. A local relay click and status light show transitions. Use the multimeter for live electrical values. The crate at (63,66,158) contains both sensors, nine new parts and two assembled/bare-board sample pairs.')}
for name,(zh,en,zrating,erating) in PARTS.items():
    VALUES['item.electron.smd_'+name]=(zh,en)
    VALUES['pcb.electron.part_rating_'+name]=(zrating,erating)
for index,locale in enumerate(('zh_cn','en_us')):
    path=A/'lang'/f'{locale}.json';data=json.loads(path.read_text(encoding='utf-8'))
    data.update({key:values[index] for key,values in VALUES.items()});path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    font=lambda size:ImageFont.truetype('C:/Windows/Fonts/msyh.ttc' if index==0 else 'C:/Windows/Fonts/arial.ttf',size)
    for name in ('pcb_analog','environment'):
        im=Image.new('RGB',(960,320),'#edf2ed');d=ImageDraw.Draw(im)
        d.rounded_rectangle((2,2,957,317),16,outline='#7e9d98',width=2)
        d.text((24,18),VALUES[f'guide.electron.{name}.title'][index],font=font(25),fill='#254c54')
        if name=='pcb_analog':
            boxes=[(30,96,215,200),(276,96,450,200),(515,96,695,200),(755,96,925,200)]
            labels=[('5 V 开关','5 V switch'),('10 kΩ / 100 μF','10 kohm / 100 uF'),('比较器','Comparator'),('1 kΩ + LED','1 kohm + LED')]
            for box,label in zip(boxes,labels):d.rounded_rectangle(box,9,fill='#d5e6df',outline='#3d7067',width=2);d.text((box[0]+13,box[1]+34),label[index],font=font(18),fill='#173e44')
            for x1,x2 in ((215,276),(450,515),(695,755)):d.line((x1,148,x2,148),fill='#bd7153',width=4);d.polygon(((x2,148),(x2-10,142),(x2-10,154)),fill='#bd7153')
            d.line((360,200,360,251,603,251,603,200),fill='#526c96',width=3)
            d.text((36,275),('电容存电 → 超过可调阈值才亮灯；断电逐渐放电','Stored charge crosses an adjustable threshold; disconnect to discharge')[index],font=font(20),fill='#355b67')
        else:
            for box,label in [((34,90,240,216),('5 V 电源','5 V supply')),((310,90,572,216),('光照 / 雨水开关','Light / rain sensor')),((678,90,922,216),('外部电源 + 负载','External supply + load'))]:
                d.rounded_rectangle(box,10,fill='#d5e6df',outline='#3d7067',width=2);d.text((box[0]+16,box[1]+48),label[index],font=font(18),fill='#173e44')
            d.line((240,119,310,119),fill='#bd7153',width=4);d.line((240,195,310,195),fill='#526c96',width=4)
            d.line((572,119,678,119),fill='#bd7153',width=4);d.line((572,195,678,195),fill='#526c96',width=4)
            d.text((591,147),'COM / NO',font=font(14),fill='#254c54')
            d.text((34,268),('隔离触点只通断外部回路；30 V / 2 A，禁止接市电','Isolated contacts switch a separate circuit: 30 V / 2 A; no mains')[index],font=font(20),fill='#355b67')
        im.save(A/'textures/gui/tutorial'/locale/f'{name}.png')

tag=ROOT/'src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json';data=json.loads(tag.read_text())
for name in ('light_sensor','rain_sensor'):
    if 'electron:'+name not in data['values']:data['values'].append('electron:'+name)
tag.write_text(json.dumps(data,indent=2)+'\n')
print('Analog specifications, two guide pages and four original diagram images updated.')
