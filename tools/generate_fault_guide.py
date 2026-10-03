"""Localized fault/withdrawal instructions, kept in sync with server constants."""
from pathlib import Path
import json,math
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1];A=ROOT/'src/main/resources/assets/electron'
entries={
'guide.electron.faults.title':('电弧、过热与触电','Arcs, overheating and shock'),
'guide.electron.faults.subtitle':('先断电，再排障；声音来自实际放电点','Isolate power before repair; sound comes from the discharge'),
'guide.electron.faults.p0':('架空电缆和双端垂直悬线会检测周围实际碰撞面、水面与其他带电悬线。净距随电压变化：240 V 为 0.45 格，6.6 kV 为 1.2 格，11 kV 为 1.6 格，24 kV 为 2.4 格。这是游戏尺度规则。贴面绝缘导线不会对自己的安装底板凭空放电。','Overhead and anchored vertical conductors check real solid surfaces, water and other energized spans. Clearance is 0.45 cells at 240 V, 1.2 at 6.6 kV, 1.6 at 11 kV and 2.4 at 24 kV. These are gameplay distances. Surface-insulated wire does not discharge into its mounting plate.'),
'guide.electron.faults.p1':('漏电是消耗电能的真实求解支路；短路电流、源限流、导线发热、熔断和断路器仍一起计算。危险点出现分叉蓝白电弧、下落火星与局部闪光；过热绝缘先嘶响冒烟，烧断时出现熔滴和断线声。故障效果不替代实际电路状态。','Leakage is a passive energy-consuming solver branch. Shorts, current limiting, wire heating, fuses and breakers remain part of the circuit. Branching blue-white arcs, falling sparks and local flashes mark faults. Hot insulation sizzles and smokes before glowing fragments and a melt snap.'),
'guide.electron.faults.p2':('靠近高压裸露悬线或触碰高压细线会形成触电支路。实际电流达到阈值才造成伤害；同一生物每秒最多受击一次，并短暂迟缓。地面放电周围有危险区，水中更宽；创造和旁观模式免疫。断电后不会继续凭空打火。','Approaching a high-voltage span or touching a high-voltage fine wire can create a shock path. Actual fault current is required for damage, capped at one hit per second with brief slowness. Ground strikes create a local hazard, wider in water. Creative/spectator players are exempt; unpowered wires produce no sustained arcs.'),
'guide.electron.faults.p3':('先关闭电源，再用万用表确认，升高架空线、拉开并行线间距，检查过载及短路。声音采用单声道空间衰减，来自击穿点而非玩家头部。矮型插排有 A/B 两条独立母线，每条四个端点，额定 5 A；铜锭＋石英＋铁粒合成。','Disconnect power, check with a multimeter, raise low cables and separate adjacent live spans. Locate shorts and overloaded loads. Mono positional audio attenuates from the strike, not the listener. A low-profile socket provides separate A/B buses with four taps each, rated 5 A; craft with copper, quartz and an iron nugget.'),
'guide.electron.fluids.p1':('水泵额定 24 V、2.4 W，最高每秒一桶。后侧可吸专用槽或原版水域，前侧接管道。有限水域由所有泵共同累计抽水量，每个源水格按 1000 mB 记账，达到当前最上层总量才一次移除整层；进水口必须仍浸在水中。累计量会保存到存档。','A 24 V / 2.4 W pump transfers up to one bucket per second. Rear intake accepts a tank or vanilla water body; front connects to pipes. All pumps share the withdrawal ledger. At 1000 mB per source cell, a finite body loses its whole top layer only when that layer is paid for. The intake must stay submerged. Partial consumption is saved.'),
'guide.electron.fluids.p2':('已确认连通的水层面积达到 1024 格时作为大型可再生水域，不降低水面；小水池即使符合原版无限水条件，也会逐层耗尽。多台泵抽得更快；未加载边界或超过搜索预算会暂停取水。透明管及水槽使用原版水的动画纹理和生物群系颜色，不向世界放出流体。','A verified connected layer of at least 1024 water cells is renewable. Smaller ponds drain by layers, even when they would normally regenerate as vanilla infinite pools. Multiple pumps drain faster. Unloaded boundaries or search-budget overflow pause intake. Contained water uses vanilla animated sprites and biome tint without placing world fluid.'),
'guide.electron.power_grid.p2':('架空线按曲线长度扣材料。架空和垂直悬线的电压越高，需要的净空越大；过近时会漏电、击穿并造成触电。净距、短路反馈和安全处理见“电弧、过热与触电”。','Aerial wire costs its curved length. Higher voltage requires greater clearance for overhead and vertical spans. Insufficient clearance causes leakage, arcing and shock. See Arcs, overheating and shock for distances and fault handling.'),
'tooltip.electron.tower_build':('架空电缆需保持电压对应的绝缘净距','Aerial cables require clearance appropriate to their voltage'),
'map.electron.lake':('逐层抽水演示','Layered water intake'),
'map.electron.lake_1':('水泵后侧吸入原版水','Rear intake draws world water'),
'map.electron.lake_2':('累计抽完一层才降水位','Level drops one paid layer'),
'map.electron.arc_demo':('悬线放电演示','Hanging-wire arc demo'),
'map.electron.arc_demo_1':('默认断电；护栏外观察','Power off by default; stand back'),
'map.electron.arc_demo_2':('风机底座 60,65,51','Wind foot: 60,65,51'),
'map.electron.arc_demo_3':('潜行右键启停供电','Sneak-use toggles supply'),
}
for idx,locale in enumerate(('zh_cn','en_us')):
 p=A/'lang'/f'{locale}.json';obj=json.loads(p.read_text(encoding='utf-8'));obj.update({k:v[idx] for k,v in entries.items()});p.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
 im=Image.new('RGB',(960,320),'#13232d');d=ImageDraw.Draw(im);font=lambda size:ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',size)
 d.text((24,16),entries['guide.electron.faults.title'][idx],font=font(27),fill='#a6e5d0')
 d.line((80,92,570,92),fill='#6f8295',width=12);d.rectangle((380,213,560,267),fill='#647276')
 points=[(438,92),(426,114),(447,130),(425,144),(447,163),(436,182),(460,212)]
 for width,c in ((13,'#28355d'),(6,'#789de9'),(2,'#ebf8ff')):d.line(points,fill=c,width=width)
 d.line((447,130,468,139,473,155),fill='#b9d9ff',width=2)
 for x,y in ((431,188),(486,191),(472,208),(404,207)):d.line((x,y,x+6,y+12),fill='#ffbd61',width=2)
 labels=['真实击穿落点','分叉电弧 / 熔化火星','单声道定位音效','漏电消耗实际电能'] if idx==0 else ['Physical strike point','Arcs and molten sparks','Mono positional sounds','Leakage consumes energy']
 for j,s in enumerate(labels):d.text((600,83+j*44),s,font=font(21),fill='#dcebe8')
 d.text((55,275),'断电 → 万用表检查 → 修复 → 复位' if idx==0 else 'Power off > measure > repair > reset',font=font(19),fill='#a7c1ce')
 im.save(A/f'textures/gui/tutorial/{locale}/faults.png')
