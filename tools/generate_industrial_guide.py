"""Bilingual build plans for the industrial expansion. World offsets use the controller's facing."""
from pathlib import Path
import json
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1];A=ROOT/'src/main/resources/assets/electron'
pages={
 'industrial_build':[
 ('火电机组装配','Thermal plant assembly'),('大型装配台 → 机组部件 → 7×7 电站','Workbench → modules → 7×7 plant'),
 ('大型机组装配台占 3×2 格，下方铺实心基础。右键打开，把电阻、继电器、电机和金属放入材料槽；选择右侧部件配方，再按装配。配方会消耗材料，成品放在独立输出槽。','The industrial workbench occupies 3×2 cells on solid supports. Open it, add electrical components and metals, select a module and press Assemble. Materials are consumed and the part appears in the output slot.'),
 ('控制器位于基础前沿中央。以控制器为 (0,0,0)，右为 +X，后为 −Z：平滑石覆盖 X=−3…3、Z=−6…0。锅炉 (0,0,−4)，汽轮机 (2,0,−3)，发电机 (2,0,−1)，冷凝器 (−2,0,−4)。','Place the controller at the centre of the front edge. Relative to its service face, right is +X and rear is −Z. Foundation: X=−3…3, Z=−6…0. Boiler (0,0,−4), turbine (2,0,−3), alternator (2,0,−1), condenser (−2,0,−4).'),
 ('控制柜 (0,0,−1)，安全阀 (0,1,−4)，锅炉补水槽 (−2,0,−1)。留出 8 格上方净空；最后一个部件放好后自动成型。用水桶或通电水泵补水，再给控制器加煤；输出接 11 kV 变电站。','Control cabinet (0,0,−1), safety valve (0,1,−4), feedwater tank (−2,0,−1). Leave eight cells overhead. The final component triggers automatic formation. Fill the tank, fuel with coal and connect an 11 kV substation.'),
 ('缺水会停止供电；热态缺水会保护停机并报警，余热仍可能继续升温。及时补水，冷却后再启动。长期失冷会损坏机组并产生有限范围的蒸汽压力爆裂，影响附近方块。','Low water stops generation. A hot dry plant trips and alarms, but residual heat remains. Refill and cool before restarting. Prolonged cooling loss can damage the plant and cause a bounded steam-pressure explosion affecting nearby blocks.')],
 'fluids':[
 ('水槽、管道与电动泵','Reservoirs, pipes and pumps'),('储水有上限，搬运水需要实际电能','Finite storage; pumping consumes real electricity'),
 ('锅炉补水槽容量 16 桶，核电冷却水槽容量 256 桶。右键水桶加入 1000 mB，空桶取回一桶。槽内水位随真实储量变化；拆掉水槽会丢失其中的水。','Feedwater tanks hold 16 buckets; nuclear reservoirs hold 256. Use a water bucket to add 1000 mB or an empty bucket to withdraw one bucket. Visible level follows actual storage; breaking a tank discards its contents.'),
 ('水泵额定 24 V、2.4 W。接两个电极，后侧吸水、前侧输出。后侧可以接水槽或原版水源方块；前侧接铁皮管、透明管或目标水槽。水泵会消耗水源方块，原版可再生水源按游戏规则补回。','A pump requires 24 V / 2.4 W at its two terminals. The rear draws from a tank or vanilla source-water block; the front feeds a pipe or destination tank. Source blocks are consumed; vanilla renewable pools refill according to normal game rules.'),
 ('每节管道先装满 250 mB，再送到目标槽。透明管显示储水及流动标记；铁皮管有金属套和法兰。截止阀右键开关，断电或关闭阀门立即停止主动输水。单次泵路搜索最多 256 个连接格。','Each pipe holds a real 250 mB priming volume. Transparent pipes show water and moving flow marks; steel pipes have metal sleeves and flanges. Use a valve to open or close it. Power loss or a closed valve stops transfer. Each pump searches up to 256 connected cells.'),
 ('水电只要求平滑石上的发电端和接触流水的涡轮端。涡轮位于机组后方一格，必须放在非静止水中；无需旧版固定落差。火电与核电读取自己的专用槽，旁边直接倒一格水不能替代供水系统。','Hydro needs a supported generator and flowing water touching the turbine, one cell behind it. Still water cannot drive it; the former fixed-head layout is removed. Thermal and nuclear plants require their dedicated reservoirs; adjacent world water is not a substitute.')],
 'nuclear_site':[
 ('30×30 核电设施','30×30 nuclear facility'),('安全壳、汽轮机、冷凝器与独立冷却水槽','Containment, turbine hall, condenser and reservoir'),
 ('控制器在前沿，以其为原点：铺 X=−15…14、Z=−29…0 的 30×30 平滑石基础，上方留 21 格。所有坐标随控制器方向旋转。安全壳范围 X=−8…2、Z=−25…−15，用屏蔽机壳围四边，高 3 格。','At the front-edge controller, build a 30×30 smooth-stone foundation spanning X=−15…14 and Z=−29…0, with 21 cells overhead. Offsets rotate with the controller. Build a three-cell-high shielding ring along the edges X=−8…2, Z=−25…−15.'),
 ('压力容器 (−3,0,−20)，控制棒驱动 (−3,1,−20)，安全阀 (−3,3,−20)；两台汽轮机 (7,0,−7)、(10,0,−7)，发电机 (7,0,−3)，冷凝器 (6,0,−15)，控制柜 (−8,0,−3)。','Vessel (−3,0,−20), rod drive (−3,1,−20), safety valve (−3,3,−20); turbines (7,0,−7) and (10,0,−7), alternator (7,0,−3), condenser (6,0,−15), control cabinet (−8,0,−3).'),
 ('冷却水槽放 (−14,0,−5)，可接水泵或用桶补水。外观中的冷却池同步显示水槽水位。结构完整后自动成型，加入燃料棒并接 24 kV 变电站。运行及余热冷却都会消耗水，水槽需要持续补给。','Put the cooling reservoir at (−14,0,−5), refill by pump or bucket. The large basin displays its water level. A complete structure forms automatically; insert a fuel rod and connect a 24 kV substation. Operation and residual-heat cooling consume water.'),
 ('失冷触发停堆；停止发电并不等于余热消失。报警后应恢复冷却，长期不处理可能发生压力事故。这里模拟的是游戏化机组、冷却与余热，不模拟核武器、真实辐射剂量或真实核反应计算。','Cooling loss trips the reactor. Stopping generation does not remove residual heat. Restore cooling after the alarm; prolonged neglect can cause a pressure accident. This is a gameplay model of plant heat and cooling, without nuclear weapons, real radiation dosimetry or nuclear-reaction calculations.')],
 'wind_and_wires':[
 ('风机、悬线与扩展接线','Wind, hanging drops and breakout sockets'),('独立塔筒 · 底部接线 · 双端固定','Tubular mast / base output / two fixed anchors'),
 ('先放风机底部接线柜，上方堆 6–63 节风机塔筒，顶端放风机控制器。留出直径约 10 格的叶片空间；自动形成锥形白色塔筒与三叶风机。电从底柜 OUT+/OUT− 引出，顶部不再提供接线端点。','Place a wind base cabinet, stack 6–63 mast sections, then add the turbine controller. Leave a roughly ten-cell rotor sweep. It automatically forms a white tapered tower with three blades. Connect OUT+/OUT− at the base; top contacts are removed.'),
 ('导线和电缆可以垂直悬挂：上下两端必须在同一竖线上，且都是有效器件端点、接线端子或穿墙口。不能用悬空自由节点收尾。最多 64 格，按实际长度扣线材；中间不能穿过方块或水。','Wire and cable can hang vertically between aligned device contacts, screw terminals or wall ports. Free nodes cannot anchor a hanging end. Maximum length is 64 cells, charged at real length. Solids and water may not intersect the run.'),
 ('任一端被拆掉，整段悬线会断开并掉落已支付的线材。横向悬空电缆仍需要输电塔。粗电缆靠近端子时逐渐收细；用电缆扩展接线座把干线分接到两条独立母线，再以细导线连接精密器件，保持额定电流限制。','Removing either anchor disconnects and refunds the drop. Horizontal suspended cables still require transmission towers. Heavy insulation tapers near contacts. Use a breakout socket to split two isolated buses and fine wire for precision devices, respecting current ratings.'),
 ('成型引雷塔可截获水平 64 格内、原目标低于塔尖的雷击；多座符合条件时随机选择一座。只有真实雷击才增加储能。这个范围是游戏规则，不表示现实避雷针能吸引范围内所有雷电。','A formed collector can redirect strikes within 64 horizontal cells whose original target is below its tip. One eligible tower is chosen randomly. Only a real strike charges it. This radius is a gameplay rule, not a real lightning-protection claim.')]
}
updates={
 'multiblocks.subtitle':('搭建完整后自动成型 → 接线 → 供能','Complete structure → automatic formation → connect power'),
 'multiblocks.p0':('输电塔由 3–63 节塔架和塔顶接线座组成，塔脚需要实体基础；完成后自动生成锥形桁架和横担。风机使用独立风机塔筒与底柜，结构见“风机、悬线与扩展接线”。','Transmission towers use 3–63 lattice sections, a terminal head and solid footings; completion automatically forms the tapered truss and crossarms. Wind turbines use separate tubular masts and a base cabinet; see the wind chapter.'),
 'multiblocks.p2':('火电需要 7×7 基础与装配台制成的机组部件，核电需要 30×30 基础和专用冷却系统。分别查看后面的装配章节。水电涡轮端放入流水；变电站仍使用 3×3 基础和四面机壳。','Thermal plants need a 7×7 foundation and workshop-built modules; nuclear plants need a 30×30 site and dedicated cooling. See the build chapters. Place the hydro turbine in flowing water. Substations retain a 3×3 foundation and four casings.'),
 'multiblocks.p3':('结构完整后约半秒自动成型；模型有实际分格碰撞，已有物品和容器不会被吞掉。拆掉部件会恢复其余材料。用扳手解除成型会锁定自动成型，再次点击可恢复。旧版小型电站需要按新结构重建。','Complete structures form automatically within about half a second with cell-local collision. Existing inventories are preserved. Breaking a part restores the others. Wrench unforming locks automatic formation until used again. Legacy compact plants require rebuilding.'),
 'power_grid.p0':('先按机组章节搭建；火电加煤、核电加燃料棒，并给专用水槽补水。水电的涡轮端接触流水，风电从底柜输出；引雷塔只收集真实雷击。各成型机组的电压见高压章节。','Build the specified plant, fuel thermal/nuclear controllers and fill their dedicated tanks. Hydro requires flowing water at the turbine; wind outputs through its base cabinet. Lightning collectors use real strikes. See high-voltage ratings for each formed plant.'),
 'high_voltage.p3':('核电用燃料热量驱动机组。失冷会停止供电并停堆，但余热仍需要冷却；持续失冷可能发生压力事故。旧版小型火电、核电和风机需要重建。高压端只能接匹配变电站，不能直接供给低压器件。','Fuel heat drives the nuclear plant. Cooling loss trips generation but residual heat needs cooling; persistent loss can cause a pressure accident. Legacy compact thermal, nuclear and wind plants must be rebuilt. Match high-voltage outputs to suitable substations.')
}
font=lambda n:ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',n)
for lang_i,locale in enumerate(('zh_cn','en_us')):
 p=A/'lang'/f'{locale}.json';obj=json.loads(p.read_text(encoding='utf-8'))
 for page,values in pages.items():
  for suffix,pair in zip(('title','subtitle','p0','p1','p2','p3'),values):obj[f'guide.electron.{page}.{suffix}']=pair[lang_i]
 for key,pair in updates.items():obj['guide.electron.'+key]=pair[lang_i]
 p.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
 for page in [*pages,'multiblocks','power_grid']:
  im=Image.new('RGB',(960,320),'#12242e');d=ImageDraw.Draw(im);d.text((22,12),obj[f'guide.electron.{page}.title'],font=font(26),fill='#98e4c9')
  if page in ('industrial_build','nuclear_site'):
   nuclear=page=='nuclear_site';size=30 if nuclear else 7;step=7 if nuclear else 29;ox,oz=68,63
   for x in range(size+1):d.line((ox+x*step,oz,ox+x*step,oz+size*step),fill='#50767d');d.line((ox,oz+x*step,ox+size*step,oz+x*step),fill='#50767d')
   rects=[(1,17,6,24,'#4da6c2'),(7,4,18,15,'#bac4b5'),(19,19,28,28,'#e2a753'),(21,2,29,10,'#627985')] if nuclear else [(1,2,2,3,'#4da6c2'),(3,2,4,3,'#db9454'),(5,3,6,4,'#7cad9c'),(5,5,6,6,'#9facce'),(1,5,2,6,'#4da6c2')]
   for x,z,X,Z,c in rects:d.rectangle((ox+x*step+1,oz+z*step+1,ox+X*step-1,oz+Z*step-1),fill=c)
   d.rectangle((ox+size//2*step,oz+(size-1)*step,ox+(size//2+1)*step,oz+size*step),fill='#f0d97d')
   labels=['30 × 30' if nuclear else '7 × 7', '控制器在前沿 / 金色格' if lang_i==0 else 'Controller: gold cell at front', '供水：蓝色 · 机组：橙色' if lang_i==0 else 'Water: blue / machinery: orange', '坐标随控制器朝向旋转' if lang_i==0 else 'Offsets rotate with controller', '点击放大后，按下文坐标摆放' if lang_i==0 else 'Enlarge; follow the exact offsets below']
   for i,line in enumerate(labels):d.text((340,62+i*43),line,font=font(23 if i==0 else 20),fill='#dcece7')
  else:
   if page=='fluids':labels=['水源 / 槽','24 V 水泵','透明管 + 阀','专用水槽'] if lang_i==0 else ['Water source','24 V pump','Pipe + valve','Reservoir']
   elif page=='wind_and_wires':labels=['塔筒 + 风机','底柜 OUT','电缆扩展座','细线到负载'] if lang_i==0 else ['Mast + rotor','Base OUT','Breakout','Fine wires']
   elif page=='multiblocks':labels=['放置基础','放齐部件','自动成型','接线供水'] if lang_i==0 else ['Foundation','All modules','Auto form','Power + water']
   else:labels=['机组高压','固定变电站','低压配电','控制 + 负载'] if lang_i==0 else ['Plant HV','Fixed station','Distribution','Control + load']
   for i,label in enumerate(labels):
    x=22+i*235;d.rounded_rectangle((x,100,x+210,224),radius=12,fill='#24434b',outline='#72baa8',width=2);d.text((x+14,116),f'0{i+1}',font=font(24),fill='#83c9b9');d.text((x+14,172),label,font=font(22),fill='#e3eee9')
    if i<3:d.polygon([(x+214,149),(x+230,157),(x+214,165)],fill='#f0d97d')
   d.text((24,267),'有限水量 · 真实耗电 · 端点固定' if lang_i==0 else 'Finite water / real power / secured endpoints',font=font(22),fill='#bed3d9')
  im.save(A/'textures/gui/tutorial'/locale/f'{page}.png')
print('Updated industrial guide in both languages')
