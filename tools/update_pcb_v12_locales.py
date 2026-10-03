from pathlib import Path
import json
from PIL import Image,ImageDraw,ImageFont
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/electron'
entries={
'block.electron.pcb_design_station':('PCB 设计站','PCB Design Station'),
'block.electron.precision_workbench':('PCB 焊接装配站','PCB Soldering and Assembly Station'),
'block.electron.decimal_input':('十进制输入拨盘','Decimal Input Dial'),
'block.electron.numeric_display':('四位输入数码显示器','Four-bit Numeric Display'),
'block.electron.display_panel':('可拼接显示屏面板','Modular Display Panel'),
'item.electron.smd_half_adder':('微型半加器（异或门＋与门）','Miniature Half-adder (XOR + AND)'),
'item.electron.smd_display':('微型数码显示模块','Miniature Numeric Display'),
'pcb.electron.part_rating_half_adder':('4.5–5.5 V · 每路输出 20 mA；SUM=A XOR B，CARRY=A AND B','4.5–5.5 V / 20 mA per output; SUM=A XOR B, CARRY=A AND B'),
'pcb.electron.part_rating_display':('4.5–5.5 V · D0–D3 四位输入 · 0–F 显示','4.5–5.5 V / D0–D3 inputs / hexadecimal display'),
'tooltip.electron.display':('电气数字接口；额定参数与引脚说明见工程手册','Electrical digital interface; ratings and pin assignments are in the handbook'),
'tooltip.electron.precision_workbench':('有电的 PCB 制作工位；设计、加工与焊接分站完成','Powered PCB production station; separate design, fabrication and soldering'),
'display.electron.input':('输入设为 %s；潜行右键减一','Input set to %s; sneak-use to decrement'),
'display.electron.sign':('负号模式：D0 高电平显示负号','Sign mode: high D0 displays a minus'),
'display.electron.hex':('数码模式：D0–D3 显示 0–F','Numeric mode: D0–D3 display 0–F'),
'pcb_ui.electron.search':('搜索元件','Find a part'),
'pcb_ui.electron.category':('分类：全部/基础/数字','All / passive / digital'),
'pcb_ui.electron.category_0':('全部元件','All parts'),
'pcb_ui.electron.category_1':('基础元件','Passive parts'),
'pcb_ui.electron.category_2':('数字器件','Digital parts'),
'pcb_ui.electron.reference':('参考板','Ref.'),
'pcb_ui.electron.parts':('元件选取区','Parts tray'),
'pcb_ui.electron.locked_design':('已加工板预览','Fabricated board'),
'pcb_ui.electron.undo':('撤销','Undo'),
'pcb_ui.electron.redo':('重做','Redo'),
'pcb_ui.electron.half_adder':('半加器样板','Half-adder sample'),
'pcb_ui.electron.fit':('复位视图','Fit view'),
'pcb_ui.electron.export':('输出光绘','Plot mask'),
'pcb_ui.electron.assemble':('焊接装配','Solder board'),
'pcb_ui.electron.output':('成品','Output'),
'pcb_ui.electron.pad':('%s · 焊盘 %s','%s / pad %s'),
'pcb_ui.electron.assembly_hint':('放入沉金 PCB 后自动读取设计。补齐焊料与元件，再通电装配。修改设计请回设计站。','Insert a gold-finished PCB to read its layout. Add solder and parts, then supply power. Edit at the design station.'),
'pcb_ui.electron.shortcuts':('拖动放置或移动元件\nR 旋转 · Delete 删除\nCtrl+Z / Y 撤销与重做\n滚轮缩放 · 中键拖动\n空格＋左键也可平移\n布线时 Shift 切换拐角\n右键取消当前连线','Drag to place or move\nR rotate / Delete remove\nCtrl+Z / Y undo and redo\nWheel zoom / middle drag\nSpace + left drag also pans\nShift changes track bend\nRight-click cancels track'),
'pcb_ui.electron.error_5':('装配站不能改变已加工的铜线；请使用设计站。','The assembly station cannot alter finished copper. Use the design station.'),
'error.electron.solver_budget':('电网过于复杂，超过单个网络的计算预算；请拆分供电与负载','This connected network exceeds the solver budget; split supplies and loads'),
'guide.electron.pcb_design.p0':('设计站与焊接装配站是两个独立方块。设计站采用俯视工作垫：左边搜索、分类并拖出元件；中间设计板型与铜线；右边放光绘膜和黑色染料；参考槽可放已有底片或板，点“载入设计”复制布局。参考品不消耗。绿色预览可放，红色预览表示越界、切口或重叠。','Design and soldering use separate blocks. The top-down design station has a searchable parts tray on the left, a board mat in the middle, and film/ink inventory on the right. Put an existing mask or board in the reference slot and load its layout; the reference is not consumed. Green placement previews fit; red previews overlap, cross a cutout or leave the board.'),
'guide.electron.pcb_design.p1':('滚轮缩放，中键或空格＋左键平移，复位视图恢复比例。R 旋转，Delete 删除选中器件；Ctrl+Z 撤销、Ctrl+Y 重做，最多保留 32 步。移动元件不移动铜线，需重新接到焊盘。快捷键在搜索框输入文字时停用。','Wheel zooms; middle-drag or Space+left-drag pans. Fit view resets the viewport. R rotates, Delete removes a selected package; Ctrl+Z/Ctrl+Y retain 32 history steps. Moving a package leaves copper in place: reconnect its pads. Search focus reserves typing keys.'),
'guide.electron.pcb_design.p2':('布线工具点击起点与终点，预览直角路径；Shift 切换转折顺序，右键结束。单铜层交叉就是相连。板型可选 16×12、14×10、12×8、10×8；轮廓工具切除网格，不能切断整块板、边缘端口、已放器件或铜线。','Track mode connects two clicked points with an L-shaped preview. Shift reverses the bend; right-click ends routing. Same-layer crossings connect. Sizes are 16×12, 14×10, 12×8 and 10×8. Outline cuts cannot split the board or remove edge ports, packages or copper.'),
'guide.electron.pcb_design.p3':('设计站输出光绘底片，不直接生成成品。可载入反相器或半加器样板后修改。半加器内由异或门产生 SUM、与门产生 CARRY，必须接 VCC/GND；外部六针仍为 VCC/GND/A/B/Y/Z。微型显示模块需要 VCC/GND 和 D0–D3 四路信号。','The design station plots a photomask. Editable inverter and half-adder samples are provided. The half-adder contains XOR for SUM and AND for CARRY and needs VCC/GND. Socket terminals remain VCC/GND/A/B/Y/Z. The miniature display needs VCC/GND and four data bits D0–D3.'),
'guide.electron.pcb_process.p0':('设计站用光绘膜＋黑色染料输出底片；24 V 电源和实际工作能量必不可少。之后依次曝光、钻孔、蚀刻清洗、阻焊固化和沉金处理。各阶段保留同一张板的尺寸、轮廓、焊盘与铜线。','Use film and black dye in the design station to plot a mask, with real 24 V power. Continue through exposure, drilling, etching/rinsing, solder-mask curing and immersion gold. Every stage preserves the same board outline, pads and copper.'),
'guide.electron.pcb_process.p3':('将沉金 PCB 放入独立的焊接装配站，自动读取这块板的设计；补入清单中的贴片元件与焊料，接 24 V 后装配。装配台不接受修改已加工板型。旧工作台升级为装配站，旧材料与输出保留；旧未完成任务停止，避免跨工序混制。','Insert the gold-finished board into the separate assembly station. Its design loads automatically. Add the required miniature parts and solder and supply 24 V. Assembly cannot redesign finished copper. Old workbenches become assembly stations; stored materials/output survive, while old unfinished jobs stop.'),
'guide.electron.digital_display.title':('显示屏与电路计算器','Displays and gate calculators'),
'guide.electron.digital_display.subtitle':('从二进制进位到十进制显示','From binary carry to decimal readout'),
'guide.electron.digital_display.p0':('输入拨盘和数码显示器均使用 4.5–5.5 V。六针依次为 VCC、GND、D0、D1、D2、D3；D0 是最低位。拨盘普通右键加一，潜行右键减一，范围 0–9。输出由供电轨驱动，停电不能继续为电路供能。','Dials and numeric displays require 4.5–5.5 V. Terminals are VCC, GND, D0, D1, D2, D3; D0 is the least significant bit. Use increments a dial, sneak-use decrements it within 0–9. Output switches actual supply rails; an unpowered dial cannot energize a circuit.'),
'guide.electron.digital_display.p1':('小显示器和 PCB 微型显示模块读取四位数值并显示 0–F。右键外部显示器切换负号模式：高电平显示“−”，低电平留空。供电不足或输入悬空时熄灭。拨盘额定每路 20 mA；小显示器约 5 mA，大屏每块面板约 10 mA，均在 5 V 下。','Small displays and PCB display packages decode four bits as 0–F. Use an external display to toggle sign mode: high shows a minus, low is blank. Insufficient supply or floating inputs blank the readout. Dial outputs are rated 20 mA each; at 5 V a small display draws about 5 mA and a panel about 10 mA.'),
'guide.electron.digital_display.p2':('同一面、同一朝向的面板拼成完整矩形，会合并成一个显示区域；最多 256 块，缺口时分开显示。边缘触点共享电源和四位信号；从外侧边缘接线，左侧为 VCC/GND/D0，右侧为 D1/D2/D3。不同数位之间留一格，避免数据线相连；每个显示区域显示一位。','Panels on the same plane and orientation merge into a rectangular screen, up to 256 tiles. Incomplete shapes display separately. Edge contacts share power and data. Wire the outside edges: VCC/GND/D0 on the left, D1/D2/D3 on the right. Leave a gap between independent digits to keep data separate. Each rectangle displays one digit.'),
'guide.electron.digital_display.p3':('教学地图给出两个 0–9 输入的加减计算器：16 张半加器 PCB 与 20 个逻辑门完成二进制加减、负数取绝对值和十进制修正。关掉运算开关为加法，打开为减法，结果 −9…18；等待电路传播稳定即可。显示器只译码信号，不执行算术。切断电源或拆掉门，运算就会受影响。','The map contains an add/subtract calculator with two 0–9 inputs: 16 half-adder PCBs and 20 logic gates perform binary arithmetic, absolute value and decimal correction. Switch off for addition, on for subtraction; results range from -9 to 18 after propagation settles. Displays only decode signals. Removing power or gates affects computation.'),
}
maps={'decimal_a':('输入 A','Input A'),'decimal_b':('输入 B','Input B'),'decimal_input':('右键数字加一','Use: increment digit'),'decimal_input_back':('潜行右键减一','Sneak-use: decrement'),'decimal_operation':('运算选择开关','Operation switch'),'decimal_add':('关闭：加法','OFF: add'),'decimal_subtract':('开启：减法','ON: subtract'),'decimal_result':('符号 / 十位 / 个位','Sign / tens / units'),'decimal_range':('输入 0–9，结果 −9…18','Inputs 0–9, result −9…18'),'decimal_real':('16 PCB＋20 逻辑门','16 PCBs + 20 gates'),'decimal_manual':('手册：显示与计算器','Guide: Displays & gates')}
entries.update({'map.electron.'+k:v for k,v in maps.items()})
for i,locale in enumerate(('zh_cn','en_us')):
 p=A/'lang'/f'{locale}.json';obj=json.loads(p.read_text(encoding='utf-8'));obj.update({k:v[i] for k,v in entries.items()});p.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
 font=lambda n:ImageFont.truetype('C:/Windows/Fonts/msyh.ttc' if i==0 else 'C:/Windows/Fonts/segoeui.ttf',n)
 for page in ('pcb_design','pcb_process','digital_display'):
  im=Image.new('RGB',(960,320),'#1f2b34');d=ImageDraw.Draw(im)
  headings={'pcb_design':('分站设计：拖动元件，检查焊盘与铜线','Design station: drag parts, inspect pads and copper'),'pcb_process':('完整 PCB 工艺链：每一步都消耗材料与电能','PCB process chain: materials and energy at every stage'),'digital_display':('由真实逻辑门与半加器 PCB 驱动显示','Real gates and half-adder PCBs drive the displays')}
  d.text((20,16),headings[page][i],font=font(24),fill='#e0e8ec')
  if page=='pcb_design':
   d.rounded_rectangle((20,65,210,275),8,fill='#3e505d');d.text((35,78),('独立元件区','Parts tray')[i],font=font(20),fill='#e2d5b3')
   for j in range(9):
    x=40+j%3*52;y=125+j//3*43;d.rectangle((x,y,x+35,y+25),fill='#212e35',outline='#bbc5c0');d.line((x-5,y+12,x+40,y+12),fill='#ddb56f',width=2)
   d.rounded_rectangle((235,65,935,275),8,fill='#355e53');d.rectangle((420,132,528,204),fill='#25323c',outline='#adbbb6',width=3)
   for j in range(4):
    y=144+j*16;d.line((260,y,410,y,410,y+4,430,y+4),fill='#d4b16e',width=3);d.line((520,y+4,570,y+4,570,y,860,y),fill='#d4b16e',width=3)
   d.text((600,220),('预览 · 旋转 · 撤销 · 缩放','Preview / rotate / undo / zoom')[i],font=font(18),fill='#d2e2dc')
  else:
   labels=([('设计','Design'),('曝光钻孔','Expose/drill'),('蚀刻阻焊','Etch/mask'),('沉金','Gold'),('独立装配','Assembly')] if page=='pcb_process' else [('输入 A / B','Inputs A / B'),('二进制加减','Binary add/sub'),('绝对值','Magnitude'),('十进制修正','Decimal adjust'),('拼接显示屏','Modular screen')])
   for j,word in enumerate(labels):
    x=20+j*186;d.rounded_rectangle((x,105,x+165,200),8,fill='#385364',outline='#8aa0ab',width=2);d.text((x+9,142),word[i],font=font(18),fill='#e2dfc7')
    if j<4:d.line((x+166,150,x+182,150),fill='#d4b16e',width=4);d.polygon([(x+182,145),(x+186,150),(x+182,155)],fill='#d4b16e')
   d.text((25,245),('24 V 工坊供电 · 5 V 数字电路 · 实测值请用万用表','24 V workshop / 5 V logic / use a multimeter for live readings')[i],font=font(22),fill='#b9ced8')
  im.save(A/f'textures/gui/tutorial/{locale}/{page}.png')
p=R/'src/main/java/top/lacrus/zephyrzeno/electron/client/EngineeringGuideScreen.java';s=p.read_text();s=s.replace('"automation","plug_power"};','"automation","plug_power","digital_display"};');p.write_text(s)
print('Updated split stations, editor gestures, fabrication and gate calculator in both languages.')
