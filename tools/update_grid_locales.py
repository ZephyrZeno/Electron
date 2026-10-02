"""Bilingual catalog and playable instructions for the power-grid milestone."""
from pathlib import Path
import json
A=Path(__file__).resolve().parents[1]/'src/main/resources/assets/electron'
entries={
'block.electron.thermal_generator':('火力发电控制器','Thermal Generator Controller'),
'block.electron.hydro_generator':('水力发电控制器','Hydro Generator Controller'),
'block.electron.wind_generator':('风力发电机','Wind Generator'),
'block.electron.lightning_collector':('引雷储能装置','Lightning Energy Collector'),
'block.electron.nuclear_reactor':('核反应堆控制器','Nuclear Reactor Controller'),
'block.electron.substation':('变电站变流柜','Substation Converter'),
'block.electron.mains_inlet':('市电进线保护柜','Mains Service Inlet'),
'block.electron.plant_casing':('发电站机壳','Plant Casing'),
'block.electron.reactor_casing':('反应堆屏蔽机壳','Reactor Shielding Casing'),
'block.electron.uranium_ore':('铀矿石','Uranium Ore'),
'block.electron.transmission_shaft':('传动轴','Transmission Shaft'),
'block.electron.rotating_platform':('旋转夹持台','Rotating Platform'),
'item.electron.raw_uranium':('粗铀矿','Raw Uranium'),
'item.electron.uranium_pellet':('铀燃料芯块','Uranium Fuel Pellet'),
'item.electron.fuel_rod':('反应堆燃料棒','Reactor Fuel Rod'),
'item.electron.spent_fuel_rod':('乏燃料棒','Spent Fuel Rod'),
'tooltip.electron.spool_controls':('右键空气结束走线；潜行右键切换层。颜色由线轴物品决定','Use in air to end a run; sneak-use switches layers. Each color is a separate item.'),
'tooltip.electron.grid_source':('240 V 整流母线 · 输出限流 2 A · 储能 4 kJ；只接大型电缆','240 V rectified bus · 2 A output limit · 4 kJ buffer; use power cables'),
'tooltip.electron.plant_thermal':('120 W；3×3 平滑石基础；东侧熔炉，北/南/西机壳；右键加煤','120 W; 3x3 smooth-stone foundation; furnace east, casings north/south/west; insert coal'),
'tooltip.electron.plant_hydro':('160 W；3×3 平滑石基础，南北机壳；东侧进水，西侧距 2 格、低 2 格尾水','160 W; 3x3 smooth-stone foundation, north/south casings; east intake, tailwater 2 west and 2 below'),
'tooltip.electron.plant_wind':('最高 100 W；至少 3 节塔架和基础；转子周围需留出 5×5×5 空间','Up to 100 W; at least 3 tower sections on a foundation; keep a 5x5x5 rotor volume clear'),
'tooltip.electron.plant_lightning':('至少 3 节塔架；正上方避雷针；每次实际雷击收集 3 kJ','At least 3 tower sections; lightning rod directly above; collects 3 kJ per actual strike'),
'tooltip.electron.plant_nuclear':('400 W；四面屏蔽机壳、3×3 基础、东西各距 2 格冷却水；燃料棒供能','400 W; shielding on four sides, 3x3 foundation, coolant 2 blocks east/west; insert a fuel rod'),
'tooltip.electron.substation':('输入 180–264 V；输出 24 V / 4 A；效率 92%；IN 与 OUT 电气隔离','Input 180–264 V; output 24 V / 4 A; 92% efficiency; isolated IN and OUT'),
'tooltip.electron.plant_substation':('3×3 平滑石基础，柜体四面各 1 个发电站机壳；向上安装','3x3 smooth-stone foundation and one plant casing on each side; mount upright'),
'tooltip.electron.mains_inlet':('240 V / 2 A 进线保护与计量；需接外部发电站；潜行右键通断/复位','240 V / 2 A service protection and metering; connect an external plant; sneak-use toggles/resets'),
'reading.electron.plant':(' | 储能 %s J · 燃料 %s J · 累计用电 %s Wh',' | Buffer %s J · Fuel %s J · Meter %s Wh'),
'message.electron.plant_status':('设施状态：%s','Plant status: %s'),
'plant.electron.upright':('控制器必须朝上安装','Controller must face upward'),
'plant.electron.tower':('缺少至少 3 节完整塔架或实体基础','Needs at least 3 intact tower sections and a solid foundation'),
'plant.electron.rod':('正上方需要原版避雷针','A vanilla lightning rod is required directly above'),
'plant.electron.wind_clearance':('转子被挡住；清空周围 5×5×5 空间','Rotor obstructed; clear the surrounding 5x5x5 space'),
'plant.electron.sky':('风机需要露天安装','Wind generator needs open sky'),
'plant.electron.foundation':('缺少下方 3×3 平滑石基础','Needs a 3x3 smooth-stone foundation below'),
'plant.electron.casing':('机壳结构不完整，查看铭牌布局','Casing incomplete; check the rated layout'),
'plant.electron.furnace':('东侧需要熔炉','A furnace is required to the east'),
'plant.electron.vent':('上方排气口被堵住','Exhaust above is blocked'),
'plant.electron.water_head':('东侧需水；西侧距 2 格、低 2 格需尾水，出口两格留空','Water required east and 2 blocks west and 2 below; keep the two outlet cells clear'),
'plant.electron.shielding':('四面屏蔽机壳不完整','Shielding casing required on all four sides'),
'plant.electron.coolant':('缺少东西两侧冷却水，停止发电','Missing coolant east/west; generation stopped'),
'plant.electron.scram':('失去冷却，已紧急停堆','Cooling lost; reactor shut down'),
'plant.electron.fuel':('燃料不足；火电加煤，反应堆加燃料棒','Fuel needed: coal for thermal, fuel rod for nuclear'),
'plant.electron.unloaded':('附近区块尚未加载，设施暂停','Nearby chunks unavailable; plant paused'),
'plant.electron.ready':('结构就绪；潜行右键可切换供电','Structure ready; sneak-use toggles output'),
'map.electron.grid':('大型供电与传动','Grid and mechanics'),
'map.electron.grid_1':('风电→进线→变电','Wind > mains > station'),
'map.electron.grid_2':('线缆通电，注意高度','Live cables: mind clearance'),
'map.electron.grid_3':('木方放夹持台；潜取回','Mount wood; sneak to take'),
'map.electron.live_supply':('风电持续供电','Continuous wind supply'),
'map.electron.crank':('电量用完需右键手摇','Use the crank to recharge'),
'map.electron.welcome_1':('设备均在平滑石台上','Devices sit on stone benches'),
'map.electron.welcome_2':('手摇供电需补；按钮触发','Cranks need input; press buttons'),
'map.electron.welcome_3':('去右侧看大型供电区','Grid workshop to the right'),
'tooltip.electron.tower_build':('至少 3 节塔架和基础；240 V 架空线离障碍不足 2 格会漏电','At least 3 tower sections on a base; 240 V spans leak if clearance is below 2 blocks'),
}
entries.update({
 'guide.electron.power_grid.title':('大型电网与发电站','Power grids and plants'),
 'guide.electron.power_grid.subtitle':('240 V 整流母线 → 24 V 负载 → 5 V 控制','240 V rectified bus > 24 V loads > 5 V control'),
 'guide.electron.power_grid.p0':('先搭建设施结构。控制器朝上，铭牌列出基础、机壳和环境要求。火电加煤、核电加燃料棒；水电检查两格落差，风电检查塔架和空间；引雷装置只收集实际雷击。','Build the structure first, with the controller facing up. Tooltips list foundations, casings and environmental checks. Thermal uses coal; nuclear uses fuel rods; hydro needs a two-block head; wind needs a clear rotor and tower; collectors need actual lightning.'),
 'guide.electron.power_grid.p1':('发电端 +/− 经两根电缆分别到塔顶 A/B，再接对侧市电柜 IN。市电柜 OUT 接变电柜 IN，变电柜 OUT 给 24 V 配电供电。市电柜自身不发电，变电损耗按 92% 效率扣能。','Route generator +/− through separate power cables to tower A/B rails, then the receiving service IN. Service OUT feeds station IN; station OUT supplies 24 V loads. The service inlet creates no energy. The station runs at 92% efficiency.'),
 'guide.electron.power_grid.p2':('架空线按曲线长度扣材料。240 V 线缆离地或障碍不足两格会漏电、打火并伤害接近放电点的生物。加高塔架，不要让粗电缆穿过建筑。','Aerial cables cost their curved length. A 240 V span with less than two blocks of ground/obstacle clearance leaks current, arcs and hurts entities near the arc. Raise the towers and keep cables clear of buildings.'),
 'guide.electron.power_grid.p3':('准确读数仍用万用表。核电失去冷却会停止发电，热态自动停堆。当前是有能耗的简化直流电网，不模拟交流相位、辐射或燃料再处理。','Use the multimeter for live readings. Nuclear generation stops without coolant and a hot reactor shuts down. This is an energy-accounted rectified DC model, without AC phases, radiation or fuel reprocessing.'),
 'guide.electron.mechanics.title':('电机、传动轴与夹持台','Motors, shafts and platforms'),
 'guide.electron.mechanics.subtitle':('沿方块中心输出轴接起来','Connect along the central output axis'),
 'guide.electron.mechanics.p0':('电机面板的中心是输出轴。沿输出方向放传动轴和旋转夹持台；三个轴向均可，直线传动最多查找 16 格。','The motor face has a central output shaft. Place aligned shafts and a rotating platform along it. All three axes work; straight transmissions scan up to 16 blocks.'),
 'guide.electron.mechanics.p1':('手持普通方块右键夹持台，消耗一个方块装入；潜行空手右键取回。拆台也返还夹持物。容器和带额外数据的物品不能装入。','Use a plain block on the platform to mount one item. Sneak-use with an empty hand to retrieve it. Breaking the platform returns the mounted item. Containers and data-bearing items are rejected.'),
 'guide.electron.mechanics.p2':('夹持台会增加电机负载；轴断开或电机停转，台面随之停止。夹持物只是旋转展示，不会作为世界方块工作或推动玩家。','A mounted block adds motor load. Breaking the shaft or stopping the motor stops the platform. The mounted item is a rotating display; it does not execute world-block behavior or move players.'),
 'guide.electron.mechanics.p3':('每个颜色都是独立线材物品，右键空气结束连线，不再切色。工程灯需要真实供电；光影需识别对应方块或发光材质，断电状态不应发光。','Each conductor color is a separate item. Using a spool in air ends the run instead of changing color. Lamps need actual power, and shaders need the appropriate light mapping or emissive materials; unpowered states stay dark.')
})
colors=[('red','红色','Red'),('black','黑色','Black'),('cyan','青色','Cyan'),('yellow','黄色','Yellow'),('green','绿色','Green'),('purple','紫色','Purple'),('white','白色','White'),('gray','灰色','Gray')]
for name,zh,en in colors:
    entries['item.electron.wire_spool_'+name]=(zh+'绝缘导线线轴',en+' Insulated Wire Spool')
    entries['item.electron.cable_spool_'+name]=(zh+'电力电缆卷',en+' Power Cable Spool')
for i,locale in enumerate(('zh_cn','en_us')):
    p=A/'lang'/f'{locale}.json';data=json.loads(p.read_text(encoding='utf-8'));data.update({key:value[i] for key,value in entries.items()})
    p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
