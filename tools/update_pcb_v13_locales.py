from pathlib import Path
import json
from PIL import Image,ImageDraw,ImageFont
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/electron'
entries={
'block.electron.pcb_socket_extension':('PCB 底座扩展片','PCB Carrier Extension'),
'block.electron.pixel_display_panel':('可拼接像素屏面板','Modular Pixel Matrix Panel'),
'pcb.electron.support_required':('这张 PCB 需要 %s×%s 的水平底座；在主插座旁补齐底座扩展片。','This PCB needs a %s×%s horizontal carrier. Complete it with carrier extensions beside the socket.'),
'pcb.electron.board_controls':('空手点板上拨盘/开关操作；潜行点空白处取出 PCB。','Use the onboard dial/switch with an empty hand. Sneak-use a blank area to remove the PCB.'),
'pcb.electron.dimensions':('设计网格 %s×%s · %s 层铜线','Design grid %s×%s / %s copper layers'),
'pcb_ui.electron.calculator':('单板计算器','Calculator PCB'),
'pcb_ui.electron.layers':('层数','Layers'),
'pcb_ui.electron.layer':('铜层','Copper'),
'pcb_ui.electron.layer_value':('L%s / %s','L%s / %s'),
'pcb_ui.electron.via':('过孔','Via'),
'pcb_ui.electron.port':('接口','Port'),
'pcb_ui.electron.materials_page':('材料翻页','Materials'),
'pcb_ui.electron.materials_page_value':('材料 %s / %s','Materials %s / %s'),
'pcb_ui.electron.help_4':('点击放置或移除贯穿过孔。铜线只通过过孔跨层连接。','Click to add/remove a plated through via. Layers join only at vias.'),
'pcb_ui.electron.help_5':('先选 VCC/GND/A/B/Y/Z，再点板边设置接口。','Choose VCC/GND/A/B/Y/Z, then click the board edge to place its contact.'),
'pcb_ui.electron.shortcuts':('滚轮：缩放/元件翻页\n中键或空格拖动：平移\nR：旋转　Del：删除\nCtrl+Z/Y：撤销/重做\n示例：反相器\nShift+示例：半加器\nCtrl+示例：像素屏','Wheel: zoom / scroll parts\nMiddle or Space-drag: pan\nR: rotate / Del: delete\nCtrl+Z/Y: undo / redo\nExample: inverter\nShift+Example: half-adder\nCtrl+Example: pixel board'),
'display.electron.pixel_help':('VCC/GND 供电；DATA 数据，CLK 上升沿移位，LATCH 上升沿刷新，CLEAR 高电平清屏。','VCC/GND power; DATA bits, CLK rising edge shifts, LATCH rising edge refreshes, CLEAR high resets.'),
'map.electron.decimal_single':('单张 PCB 计算器','Single-PCB calculator'),
'map.electron.decimal_real':('52 个板内基本逻辑门','52 basic gates on one PCB'),
'map.electron.decimal_input':('空手点板上拨盘加一','Use onboard dial: +1'),
'map.electron.decimal_input_back':('潜行点拨盘减一','Sneak-use dial: -1'),
'map.electron.pixel_demo':('像素与 RGB 练习板','Pixels and RGB practice'),
'map.electron.pixel_controls':('从左到右数据/时钟','Left: DATA / CLK'),
'map.electron.pixel_latch':('第三开关上升沿刷新','Third: LATCH rising edge'),
'map.electron.pixel_clear':('第四开关开启清屏','Fourth: CLEAR high'),
}
types={
'nor':('微型或非门','Miniature NOR Gate','Y=NOT(A OR B)'),
'xnor':('微型同或门','Miniature XNOR Gate','Y=NOT(A XOR B)'),
'buffer':('微型缓冲器','Miniature Buffer','Y=A'),
'rgb':('微型 RGB 灯珠','Miniature RGB LED','R/G/B'),
'pixel':('微型 8×8 像素屏','Miniature 8x8 Pixel Matrix','DATA / CLK / LATCH / CLEAR'),
'dial':('微型十进制拨盘','Miniature Decimal Dial','0–9 / D0–D3'),
'switch':('微型拨动开关','Miniature Slide Switch','Y=0/1'),
'dff':('微型 D 触发器','Miniature D Flip-Flop','D / CLK / Q'),
'clock':('微型时钟振荡器','Miniature Clock Oscillator','CLK OUT'),
'tristate':('微型三态缓冲器','Miniature Tri-State Buffer','A / EN / Y'),
'mux':('微型二选一选择器','Miniature 2:1 Multiplexer','A / B / SEL / Y'),
'sign':('微型负号显示条','Miniature Minus-Sign Display','SIGN'),
}
for t,(zh,en,pins) in types.items():
 entries['item.electron.smd_'+t]=(zh,en)
 entries['pcb.electron.part_rating_'+t]=(f'5 V 数字器件 · {pins}；接线与额定参数见工程手册',f'5 V digital component / {pins}; wiring and ratings are in the handbook')
pages={
'pcb_design':[
('高密度 PCB 设计','Dense PCB design'),('独立元件盘、缩放、多层铜线与自定义接口','Parts tray, zoom, copper layers and custom ports'),
('设计站绘制底片，焊接站按已沉金的铜线装配，不能在焊接站免费改线。默认 32×24 网格，板型按钮切换尺寸，最大 128×96；最多 256 个元件。元件仍有独立焊盘，导线变细后可在更多格点布置。','The design station plots masks; the assembly station follows already-manufactured copper. It cannot redesign a gold-finished board. Default grid: 32x24; size presets extend to 128x96, with up to 256 packages and individually routed pads.'),
('左侧搜索、分类和滚轮翻页选择元件，拖到工作垫放置。拖动已有元件可移动；R 旋转，Del 删除，Ctrl+Z/Y 撤销/重做。滚轮以光标为中心缩放，中键或空格拖动平移，“适应”显示整张大板。移动元件后请检查原有铜线。','Search, filter and scroll the parts tray, then drag a package onto the mat. Drag existing packages to move; R rotates, Del deletes and Ctrl+Z/Y undo/redo. Wheel zoom anchors at the pointer; middle/Space-drag pans; Fit shows the entire board. Check existing copper after moving a package.'),
('“层数”设置一到四层，下方 L 按钮切换当前层。布线工具连续点击两点，Shift 切换折线方向。同层交叉自动相连，异层交叉绝缘；过孔工具放置贯穿孔才会将各层相连。轮廓工具可铣掉空白区域，不能切断整张基板。','Layers selects one to four copper layers; the L button changes the active layer. Click successive nodes to route, holding Shift for the other elbow direction. Same-layer crossings join; different layers remain isolated until a plated via connects them. Outline milling cannot split the substrate.'),
('接口工具先选 VCC、GND、A、B、Y、Z，再点板边调整焊盘位置；插座保留六个固定外部接线触点。示例按钮提供反相器，Shift 提供半加器，Ctrl 提供像素练习板；“单板计算器”载入可逐线修改的完整门电路。','Choose VCC, GND, A, B, Y or Z and click the board edge to relocate its contact. The carrier retains six fixed external terminals. Example loads an inverter; Shift selects a half-adder, Ctrl a pixel practice board. Calculator PCB loads a complete editable gate circuit.'),
],
'pcb_process':[
('按面积付费的 PCB 工艺','PCB fabrication by area'),('基材、铜层、药液、钻孔和能量都计入加工','Substrate, copper layers, reagents, drilling and energy'),
('面积单位 U=向上取整(宽×高÷768)，工艺单位 M=U×层数。曝光消耗 M 张覆铜基材和一份图样底片；底片绘制消耗 M 张胶片与 M 份黑色染料。孔洞不返还整张基材的成本，旧小板仍按最低一单位计费。','Area units U=ceil(width*height/768); process units M=U*layers. Exposure consumes M laminate sheets and one patterned mask. Plotting consumes M films and M black dyes. Milled cutouts do not refund the original sheet; legacy small boards cost at least one unit.'),
('钻孔、蚀刻、阻焊、沉金分别消耗 M 份对应耗材，湿法步骤产生相同数量的废药液筒。基准工序能量乘 M，每个贯穿孔另收 2 J；供电功率有限，板越大、层越多，加工时间和总能耗越高。','Drilling, etching, masking and immersion gold each consume M matching reagents. Wet stages produce the same number of spent cartridges. Base process energy is multiplied by M, plus 2 J per via. Limited input power means larger multilayer boards take longer and spend more energy.'),
('装配消耗一张保留原图样的沉金板、实际元件和焊料，每 32 个引脚一份焊料。装配能量为 40M＋12×元件数＋0.5×铜线格数＋4×过孔数 J。右侧材料盘可翻页，超过一组的元件可分批补充；已投入材料保存，取消时归还。','Assembly consumes one matching gold-finished board, the actual packages and one solder unit per 32 pads. Energy is 40M + 12 per package + 0.5 per copper edge + 4 per via, in joules. Page through the tray and feed more than one stack in batches. Reserved materials persist and are returned on cancel.'),
('主插座承载左上角，水平铺设底座扩展片补足向上取整(宽÷32)×向上取整(高÷24) 的底座。缺少支撑不能插入大板，拆掉支撑会停止电路。板上元件、过孔与顶层铜线会实际显示；空手点板上输入控件，潜行点空白处取板。','Place a socket at the anchor corner and complete a horizontal carrier of ceil(width/32) by ceil(height/24) tiles using extensions. Incomplete support rejects insertion; removing support stops the circuit. Installed packages, vias and top copper are visible. Use onboard inputs with an empty hand; sneak-use blank board space to remove it.'),
],
'digital_display':[
('单板门电路计算器','Single-PCB gate calculator'),('输入、二进制加减与十进制显示在同一张板上','Inputs, binary arithmetic and decimal readout on one board'),
('教学样板有两只 0–9 拨盘、一只加减开关、52 个基本门、负号显示条和两位数码屏，共 58 个元件。一张 80×88、四层 PCB 完成运算，下面的 3×4 底座只是支撑，没有额外计算器控制器。','The teaching board has two 0–9 dials, an add/subtract switch, 52 basic gates, a minus bar and two numeric displays: 58 packages total. One 80x88 four-layer PCB performs the calculation. Its 3x4 carrier provides support, with no separate arithmetic controller.'),
('空手右键 A/B 拨盘加一，潜行点击减一；运算开关关闭为加法、开启为减法。等待门电路传播稳定，结果范围 −9…18。内部 XOR、AND、OR、NOT 完成进位、补码、负数幅值与十进制修正；显示器只译码四个输入位。','Use dial A/B to increment; sneak-use decrements. The switch selects addition when off, subtraction when on. Wait for gate propagation; results range from -9 to 18. XOR, AND, OR and NOT implement carry, complement, magnitude and decimal correction. Displays only decode their four input bits.'),
('板内是可编辑铜线和贯穿过孔，板外 5 V 供电使用小型导线。切断电源、拆掉支撑、改错焊盘或短接网络，都会影响运算。全套数字器件工作范围 4.5–5.5 V，逻辑输出每路额定 20 mA，并从真实供电轨取能。','Internal connections are editable copper and vias; external 5 V power uses fine wire. Removing power/support, moving pads or shorting nets affects computation. Digital packages operate at 4.5–5.5 V, with logic outputs rated 20 mA each and powered by actual supply rails.'),
('数码模块显示 0–F，负号条的 SIGN 高电平显示 −。旧的数码大屏仍可拼接；需要自定义图案、文字或动画时使用像素屏，详见“像素与 RGB”章节。主线样板与独立原版红石计算器模组没有依赖关系。','Numeric modules show 0–F; SIGN high lights the minus bar. Existing numeric panels can still be tiled. Use pixel panels for arbitrary patterns, text or animations; see Pixels and RGB. The main-mod sample has no dependency on the separate vanilla-redstone calculator mod.'),
],
'pcb_logic':[
('板内逻辑与时序元件','PCB logic and sequential parts'),('组合逻辑、寄存与时钟从真实供电轨工作','Combinational logic, storage and clocks use real supply rails'),
('基础门齐备：AND、OR、NOT、XOR、NAND、NOR、XNOR、BUFFER。常规门引脚 VCC/GND/A/B/Y；NOT 和 BUFFER 只使用 A。逻辑门有两游戏刻传播延迟；悬空、未供电和中间电压不会被当成有效的 0 或 1。','The complete basic set is AND, OR, NOT, XOR, NAND, NOR, XNOR and BUFFER. Standard pins are VCC/GND/A/B/Y; NOT and BUFFER use only A. Gates have two-tick propagation delay; floating, unpowered and intermediate voltages are not valid binary inputs.'),
('D 触发器在 CLK 从低到高时保存 D；保持期间 Q 不跟随 D。三态缓冲器 EN=1 时传递 A，EN=0 时释放输出。二选一选择器 SEL=0 取 A、SEL=1 取 B。不要把相反的推挽输出并接，真实电流会造成过载。','A D flip-flop samples D on the rising CLK edge and holds Q between edges. A tri-state buffer passes A when EN=1 and releases Y when EN=0. A multiplexer selects A at SEL=0 or B at SEL=1. Opposing push-pull outputs draw real short-circuit current.'),
('拨盘输出四位二进制，开关在供电轨的 0/1 之间选择。时钟振荡器默认 1 Hz，空手点击切换半周期 5、10、15、20、25 游戏刻。供电中断会丢失触发器状态；输入控件设置在存档中保留。','Dials output four binary bits; switches select the supply rails for 0/1. The oscillator defaults to 1 Hz; use it to cycle half-periods of 5, 10, 15, 20 or 25 game ticks. Flip-flop state is volatile across power loss; input control settings persist in the save.'),
('按器件脚位拉线，不能依靠图形相近就认为相连。基准电源是 5 V；每个普通逻辑封装约 0.5 mA 静态负载，数码屏约 5 mA。数字电路只接受电气信号；原版红石信号不能直接接入 PCB。','Route to the actual pads; visually close lines are not necessarily connected. Use a 5 V supply. Ordinary logic packages draw about 0.5 mA at idle; numeric displays about 5 mA. These are electrical circuits; vanilla redstone cannot directly power or signal a PCB.'),
],
'pcb_pixels':[
('像素与 RGB','Pixels and RGB'),('每个点自行写入，不使用固定数字字库','Write every pixel directly, with no fixed numeric font'),
('RGB 灯珠有 VCC/GND/R/G/B 五个引脚，每个颜色独立由高低电平控制，可组合八种状态；供电不足或输入悬空时熄灭。在 5 V 下，基础负载约 1 mA，每点亮一个颜色增加约 3 mA。','The RGB package has VCC/GND/R/G/B pins. Three independent binary color inputs produce eight states. Insufficient power or floating inputs blank it. At 5 V its base draw is about 1 mA, plus 3 mA per illuminated channel.'),
('微型像素屏有 8×8 点，六针为 VCC/GND/DATA/CLK/LATCH/CLEAR。按从左上角逐行到右下角的顺序发送 64 位，先稳定 DATA，再让 CLK 从 0 变 1。只有 LATCH 从 0 变 1 才刷新；CLEAR=1 清除缓存和画面。','The miniature matrix has 8x8 pixels and VCC/GND/DATA/CLK/LATCH/CLEAR pins. Send 64 bits in row-major order, starting at the top left. Stabilize DATA before raising CLK. A rising LATCH edge refreshes the screen; CLEAR=1 erases both shift register and frame.'),
('像素屏方块同面、同朝向拼成完整矩形后合并；每块增加 8×8 像素，最多 256 块。整幅画面连续逐行发送，总位数=块数×64；边缘触点传递六路电气连接。例：2×2 面板需要 256 位，得到 16×16 图像。','Coplanar pixel panels with matching rotation form one rectangular display, up to 256 tiles. Each tile contributes 8x8 pixels. Send a complete row-major frame of tile_count*64 bits. Edge contacts share all six electrical signals. A 2x2 panel needs 256 bits for a 16x16 image.'),
('设计站 Ctrl+点击示例，载入四开关、RGB 与像素屏练习板。开关从左到右 DATA/CLK/LATCH/CLEAR；置数据、升降时钟，最后拉高锁存。地图有已通电示范。每块像素屏在 5 V 下基础约 2 mA，每个亮点约 0.3 mA；断电熄灭并清空易失缓存。','Ctrl-click Example at the design station to load a four-switch RGB/pixel practice PCB. Left to right: DATA/CLK/LATCH/CLEAR. Set a bit, pulse the clock, then raise latch after the frame. The map includes a powered example. At 5 V each tile draws about 2 mA plus 0.3 mA per lit pixel. Power loss blanks and clears volatile buffers.'),
]}
for page,values in pages.items():
 for suffix,pair in zip(('title','subtitle','p0','p1','p2','p3'),values):entries[f'guide.electron.{page}.{suffix}']=pair
for i,locale in enumerate(('zh_cn','en_us')):
 p=A/'lang'/f'{locale}.json';obj=json.loads(p.read_text(encoding='utf-8'));obj.update({k:v[i] for k,v in entries.items()});p.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
 font=lambda n:ImageFont.truetype('C:/Windows/Fonts/msyh.ttc' if i==0 else 'C:/Windows/Fonts/segoeui.ttf',n)
 for page in pages:
  im=Image.new('RGB',(960,320),'#1e2b35');d=ImageDraw.Draw(im);d.text((22,15),pages[page][0][i],font=font(26),fill='#ecf3f4')
  if page in ('pcb_design','pcb_logic','digital_display'):
   d.rounded_rectangle((25,62,665,295),12,fill='#28574c',outline='#9cbea6',width=3)
   for n in range(24):
    x=58+n%8*76;y=94+n//8*65;d.line((x-22,y+15,x+59,y+15),fill='#dbb477',width=1);d.rectangle((x,y,x+32,y+30),fill='#29313b',outline='#6a7f8a');d.rectangle((x+3,y+4,x+6,y+7),fill='#d9d6c7')
   texts=[('一张板 · 独立引脚','One board / real pads'),('顶层与内层铜线','Top and inner copper'),('贯穿孔才跨层相连','Vias join copper layers'),('拖动 · 缩放 · 平移','Drag / zoom / pan')]
   for n,t in enumerate(texts):d.text((690,88+n*46),t[i],font=font(18),fill='#d4e0e7')
  elif page=='pcb_process':
   labels=[('底片','Plot'),('曝光/钻孔','Expose/drill'),('蚀刻/阻焊','Etch/mask'),('沉金','Gold'),('装配','Assembly')]
   for n,t in enumerate(labels):
    x=22+n*186;d.rounded_rectangle((x,100,x+164,185),8,fill='#385565',outline='#9eb4c0',width=2);d.text((x+10,126),t[i],font=font(18),fill='#eff0e1')
    if n<4:d.line((x+166,142,x+184,142),fill='#d2b177',width=3)
   d.text((30,235),('U=ceil(W×H/768)    M=U×铜层数    材料、电能随 M 增加','U=ceil(W*H/768)    M=U*layers    Materials and energy scale with M')[i],font=font(20),fill='#afcdc4')
  else:
   for y in range(8):
    for x in range(8):d.rectangle((36+x*25,75+y*25,57+x*25,96+y*25),fill='#f8b663' if x==y or x==7-y else '#3b454a')
   for n,t in enumerate([('DATA → 0/1 数据','DATA -> bit'),('CLK ↑ → 移入一位','CLK rising -> shift'),('LATCH ↑ → 显示整帧','LATCH rising -> show'),('CLEAR=1 → 清空','CLEAR=1 -> clear')]):d.text((295,82+n*48),t[i],font=font(21),fill='#e1e7df')
   for n,col in enumerate(('#de6b5c','#81cb7e','#6fa3df')):d.ellipse((775,87+n*60,815,127+n*60),fill=col)
  path=A/'textures/gui/tutorial'/locale/f'{page}.png';path.parent.mkdir(parents=True,exist_ok=True);im.save(path)
print('Updated bilingual dense-PCB, component, manufacturing and pixel instructions.')
