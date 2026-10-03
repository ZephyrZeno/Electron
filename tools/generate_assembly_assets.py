"""Deterministic engineering materials, fixed-winding hardware, and bilingual assembly lessons."""
from pathlib import Path
import json, random
from PIL import Image, ImageDraw, ImageFont
ROOT=Path(__file__).resolve().parents[1]
A=ROOT/'src/main/resources/assets/electron'
R=ROOT/'src/main/resources/data/electron'
def write(p,value):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
materials={'galvanized':(157,173,183),'cast_base':(51,62,67),'control_case':(45,69,80),'service_panel':(72,95,101),'porcelain':(213,231,215),'copper_bus':(161,94,48),'turbine_white':(225,230,217),'capacitor_bank':(49,97,90),'boiler':(87,96,98),'soot_stack':(62,64,64),'hydro_blue':(51,110,143),'reactor_shell':(187,195,179),'transformer_oil':(89,108,99)}
for index,(name,color) in enumerate(materials.items()):
    rng=random.Random(1800+index);im=Image.new('RGB',(64,64));d=ImageDraw.Draw(im)
    for y in range(64):
        for x in range(64):
            n=rng.randint(-4,4);im.putpixel((x,y),tuple(max(0,min(255,c+n)) for c in color))
    if name=='porcelain':
        for y in range(5,64,12):d.line((0,y,63,y),fill='#f1f8e4',width=2);d.line((0,y+3,63,y+3),fill='#a4bcb0')
    elif name=='galvanized':
        for y in range(2,64,5):d.line((0,y,63,y),fill='#a5b3b8')
    else:
        d.rectangle((1,1,62,62),outline=tuple(int(c*.65) for c in color),width=2)
        for x in (6,57):
            for y in (6,57):d.ellipse((x-2,y-2,x+2,y+2),fill='#a9b6b9',outline='#303d42');d.line((x-1,y,x+1,y),fill='#39464b')
    if name in ('control_case','service_panel'):
        for y in range(21,47,5):d.line((16,y,47,y),fill='#12232a',width=2)
        d.rectangle((19,9,45,15),fill='#92b79e')
    if name=='reactor_shell':
        for x in range(-10,70,12):d.line((x,50,x+10,60),fill='#ccac4a',width=5)
    if name=='capacitor_bank':
        for x in range(12,59,10):d.rectangle((x,14,x+5,51),outline='#80a697',width=2)
    p=A/'textures/block/assembled'/f'{name}.png';p.parent.mkdir(parents=True,exist_ok=True);im.save(p)
write(A/'blockstates/assembly_part.json',{'variants':{'':{'model':'electron:block/assembly_part'}}})
write(A/'models/block/assembly_part.json',{'textures':{'particle':'electron:block/assembled/galvanized'},'elements':[]})
for suffix in ('6k6','11k','24k'):
    name='substation_'+suffix
    write(A/'blockstates'/f'{name}.json',json.loads((A/'blockstates/substation.json').read_text()))
    write(A/'models/item'/f'{name}.json',{'parent':'electron:item/substation'})
    for suffix_model in ('_inventory','_off_up'):
        write(A/'models/block'/f'{name}{suffix_model}.json',{'parent':'electron:block/substation'+suffix_model})
    write(R/'loot_table/blocks'/f'{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'electron:'+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    write(R/'recipe'/f'{name}.json',{'type':'minecraft:crafting_shapeless','category':'redstone','ingredients':[{'item':s} for s in ['electron:substation']+['minecraft:copper_block']*({'6k6':2,'11k':3,'24k':4}[suffix])+['minecraft:quartz_block','minecraft:iron_block']],'result':{'id':'electron:'+name,'count':1}})
    advancement=json.loads((R/'advancement/recipes/substation.json').read_text())
    advancement['rewards']['recipes']=['electron:'+name]
    write(R/'advancement/recipes'/f'{name}.json',advancement)
    for relative in ('minecraft/tags/block/mineable/pickaxe.json','minecraft/tags/block/needs_iron_tool.json'):
        p=ROOT/'src/main/resources/data'/relative
        if p.exists():
            obj=json.loads(p.read_text());obj['values']=sorted(set(obj['values']+['electron:'+name]));write(p,obj)

entries={
 'block.electron.assembly_part':('成型结构部件','Formed Machine Part'),
 'block.electron.substation_6k6':('6.6 kV → 240 V 变电站','6.6 kV / 240 V Substation'),
 'block.electron.substation_11k':('11 kV → 240 V 变电站','11 kV / 240 V Substation'),
 'block.electron.substation_24k':('24 kV → 240 V 变电站','24 kV / 240 V Substation'),
 'plant.electron.formed':('整体结构已成型','Multiblock formed'),
 'plant.electron.dismantled':('已解除成型，原结构方块已恢复','Unformed; original structural blocks restored'),
 'plant.electron.space':('整体模型所需空间被占用，请查看手册','Assembly volume obstructed; consult the handbook'),
 'plant.electron.attached':('结构方块已有接线或容器，先解除连接','Structural block has attachments or inventory; disconnect first'),
 'plant.electron.permission':('部分结构未加载或不允许修改','Part of the structure is unloaded or protected'),
 'plant.electron.tower_base':('塔脚下方缺少实体基础','A solid foundation is required under each tower foot'),
 'plant.electron.casing':('机壳结构不完整，查看工程手册','Casing incomplete; consult the engineering handbook'),
 'tooltip.electron.source_thermal':('成型额定输出 11 kV · 120 W · 煤热效率 35%','Formed output: 11 kV / 120 W / 35% thermal efficiency'),
 'tooltip.electron.source_hydro':('成型额定输出 6.6 kV · 160 W','Formed output: 6.6 kV / 160 W'),
 'tooltip.electron.source_wind':('机内变流输出 240 V · 最高 92 W','Integrated converter: 240 V / up to 92 W'),
 'tooltip.electron.source_nuclear':('成型机组额定输出 24 kV · 400 W · 热效率 30%','Formed generator: 24 kV / 400 W / 30% thermal efficiency'),
 'tooltip.electron.source_lightning':('雷击峰值 300 MV / 30 kA · 缓冲输出 240 V','Lightning peak: 300 MV / 30 kA; buffered output: 240 V'),
 'tooltip.electron.station_rating':('输入 %s–%s V · 输出 %s V · 效率 92% · 隔离绕组','Input %s-%s V / output %s V / 92% efficiency / isolated windings'),
 'tooltip.electron.mains_inlet':('240 V / 2 A · 进线保护与电能计量','240 V / 2 A / service protection and energy metering'),
 'reading.electron.lightning_pulse':(' | 最近雷击 %s V / %s A · 实收 %s J',' | Last pulse: %s V / %s A; captured %s J'),
 'guide.electron.contents':('工程目录','CONTENTS'),
 'guide.electron.zoom':('点击图片放大','Click the diagram to enlarge'),
 'guide.electron.zoom_close':('点击或按 Esc 返回正文','Click or press Esc to return'),
 'guide.electron.multiblocks.title':('多方块成型与塔架','Multiblocks and towers'),
 'guide.electron.multiblocks.subtitle':('搭建结构 → 扳手成型 → 接线 → 供能','Build > form with wrench > connect > supply energy'),
 'guide.electron.multiblocks.p0':('先搭 3–63 节垂直塔架，在顶部放向上的塔顶接线座、风机或引雷控制器。塔脚需要实体基础：矮塔建议 5×5，长塔建议 7×7。塔身随节数加高、加宽，上窄下宽；四周留空。用扳手点控制器成型，再点可还原。','Stack 3-63 tower sections, then an upward-facing tower terminal, wind generator or collector. Support all four feet: use a 5x5 base for short towers or 7x7 for tall ones. Height and base width follow the section count. Leave clearance, then use the wrench on the controller to form or restore it.'),
 'guide.electron.multiblocks.p1':('电缆连接塔两侧横担末端的瓷绝缘子：左侧 A 母线、右侧 B 母线，各有四个接点。点击相应铜端子，再点另一座塔。中央服务端子可接入沿塔架支撑的引下线。架空段按真实弧长消耗材料。','Connect cables at the porcelain insulators on the crossarms: four taps on the left A rail and four on the right B rail. Click a copper tip, then another tower. Central service pins accept supported downleads. Suspended cable costs its curved length.'),
 'guide.electron.multiblocks.p2':('台式机组先铺 3×3 平滑石。火电东侧放熔炉，北/南/西放机壳，上方至少留 4 格；水电南北放机壳、东侧进水、西侧距 2 格低 2 格放尾水。核电四面放屏蔽机壳，东西距 2 格冷却水，上方留 3 格。变电站四面放机壳；进线柜北侧留一格，上方留两格。然后扳手成型。','Use a 3x3 smooth-stone foundation. Thermal: furnace east, casings north/south/west, four blocks clear above. Hydro: casings north/south, water east, tailwater two west and two below. Nuclear: shielding on four sides, coolant two east/west, three clear above. Substation: four casings. Inlet: clear one block north and two above. Use the wrench to form.'),
 'guide.electron.multiblocks.p3':('成型占用的空间有实际碰撞，不能塞入其他设备。风机转子前方保留扫掠空间。拆掉结构的一部分会解除成型并清空电气缓冲，未破坏的材料原位恢复；熔炉和物品不被吞掉。旧存档设施可保留原样运行，升级前先拆掉结构机壳上的线路。','Formed machines reserve real collision space. Keep the wind rotor sweep clear. Removing a part unforms the machine and clears its electrical buffer; unbroken materials are restored in place. Furnace inventories are preserved. Legacy structures can keep running; remove wiring from structural casings before upgrading.'),
 'guide.electron.high_voltage.title':('高压发电与固定变压','Generation and fixed ratios'),
 'guide.electron.high_voltage.subtitle':('11 / 6.6 / 24 kV → 240 V → 24 V → 控制电路','11 / 6.6 / 24 kV > 240 V > 24 V > control'),
 'guide.electron.high_voltage.p0':('成型火电输出 11 kV，水电 6.6 kV，核电汽轮发电机组 24 kV。选择对应高压变电站降至 240 V，再经普通变电站降至 24 V。固定绕组须合成，不能免费切换输入档位。风机和引雷装置内置 240 V 输出级。','Formed thermal generators output 11 kV, hydro 6.6 kV and nuclear turbine generators 24 kV. Craft the matching fixed-ratio station to reach 240 V, then a normal station for 24 V. Winding ratios cannot be changed for free. Wind and lightning collectors include a 240 V output stage.'),
 'guide.electron.high_voltage.p1':('变电输入允许额定值的 75%–110%，效率为 92%，只从实际输入功率积累能量再向输出供电。高电压并不代表无限电量；发电功率和燃料消耗独立计算。当前网络使用直流等效求解，额定电压参考现实机组，不模拟三相相位。','Stations accept 75%-110% of nominal input with 92% efficiency. Actual input power charges the finite buffer before output can supply loads. High voltage is not unlimited energy. Power and fuel are accounted separately. Networks use a DC equivalent with real-world-inspired voltage ratings, without three-phase simulation.'),
 'guide.electron.high_voltage.p2':('雷击是短时脉冲：本装置记录 3 亿伏、3 万安的事件峰值，捕获支路等效电容 0.1 pF，转换效率 2/3；每次最多回收 3 kJ，储能总量 4 kJ。输出级仍为 240 V。万用表可读最近峰值和实收能量。只计一次真实雷击，不会持续产出 3 亿伏。','Lightning is a brief pulse. The collector records a 300 MV / 30 kA event peak. Its 0.1 pF equivalent capture branch and 2/3 conversion efficiency recover up to 3 kJ per strike, with a 4 kJ total buffer and 240 V output. Meter readings include the last peak and captured energy. Each actual strike is counted once.'),
 'guide.electron.high_voltage.p3':('核电通过燃料热量带动机组，失去冷却会停止供电，热态触发停堆。没有模拟真实辐射或核反应物理。旧版未成型控制器保留 240 V 兼容输出；成型前先断开负载并准备合适变电站。不要把高压端直接接到低压灯或逻辑器件。','Nuclear fuel heat drives the generator; loss of coolant stops power and triggers a hot shutdown. Radiation and nuclear reaction physics are not simulated. Legacy unformed controllers retain 240 V for compatibility: disconnect loads and prepare a matching station before forming them. Keep high voltage away from low-voltage devices.'),
 'guide.electron.power_grid.p0':('先搭建设施，结构要求见“多方块成型与塔架”。火电加煤、核电加燃料棒；水电检查落差，风电检查塔脚和转子空间；引雷装置只收集真实雷击。成型后的额定输出与旧版不同，见高压章节。','Build structures as shown in Multiblocks and towers. Thermal uses coal, nuclear fuel rods, hydro a head of water, wind a foundation and rotor clearance, and collectors actual strikes. Formed output ratings differ from legacy controllers; see the voltage chapter.'),
}
for i,locale in enumerate(('zh_cn','en_us')):
    p=A/'lang'/f'{locale}.json';data=json.loads(p.read_text(encoding='utf-8'));data.update({k:v[i] for k,v in entries.items()});write(p,data)
    font=lambda n:ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',n)
    for page in ('multiblocks','high_voltage'):
        im=Image.new('RGB',(960,320),'#0e1b24');d=ImageDraw.Draw(im)
        d.text((25,15),data['guide.electron.'+page+'.title'],font=font(26),fill='#a3ead1')
        if page=='multiblocks':
            for x,h in ((180,145),(440,205),(720,205)):
                y=265;d.rectangle((x-70,y,x+70,y+13),fill='#858f93')
                for yy in range(0,h,20):
                    r=16+42*(1-yy/h);r2=16+42*(1-min(h,yy+20)/h)
                    for side in (-1,1):d.line((x+side*r,y-yy,x+side*r2,y-min(h,yy+20)),fill='#9fafb9',width=3);d.line((x-side*r,y-yy,x+side*r2,y-min(h,yy+20)),fill='#536f80',width=2)
                d.line((x-96,y-h+20,x+96,y-h+20),fill='#b0c1ca',width=4)
                for side in (-1,1):
                    d.line((x,y-h+65,x+side*96,y-h+20),fill='#6f8c9b',width=3)
                    for j in range(5):d.line((x+side*96-7,y-h+23+j*4,x+side*96+7,y-h+23+j*4),fill='#bfe0cd',width=3)
            d.arc((535,83,625,142),0,180,fill='#e47b69',width=4)
        else:
            labels=['11 / 6.6 / 24 kV','240 V','24 V','5 V'];xs=[130,360,590,820]
            for x,label in zip(xs,labels):
                d.rounded_rectangle((x-95,104,x+95,232),radius=10,fill='#264339',outline='#6caf97',width=2)
                d.text((x,166),label,font=font(24),anchor='mm',fill='#e4f4e9')
            for a,b in zip(xs,xs[1:]):d.line((a+97,166,b-98,166),fill='#e5bd73',width=5);d.polygon([(b-104,157),(b-91,166),(b-104,175)],fill='#e5bd73')
        caption=data['guide.electron.'+page+'.subtitle'];size=19
        while font(size).getlength(caption)>916:size-=1
        d.text((480,299),caption,font=font(size),anchor='mm',fill='#a6bdca');im.save(A/'textures/gui/tutorial'/locale/f'{page}.png')
print('Assembly materials, fixed substations and 4 localized tutorial images generated.')
