"""Render the tested board's actual saved copper; no illustrative substitute for its netlist."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
R=Path(__file__).resolve().parents[1];out=R/'build/asset-review';s=(out/'single-board-calculator.pcb').read_text().split('|');w,h,layers=map(int,s[3].split(','))
parts=[list(map(int,x.split(','))) for x in s[1].split(';') if x];edges=[list(map(int,x.split(','))) for x in s[2].split(';') if x];vias=[int(x) for x in s[5].split(',') if x];ports=[int(x) for x in s[6].split(',') if x]
font=lambda n:ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',n)
im=Image.new('RGB',(1280,1570),'#15232b');d=ImageDraw.Draw(im);d.text((25,15),'单张 PCB 加减计算器 · 自动化测试实际使用的铜线图',font=font(28),fill='#edf0e6')
d.text((25,62),f'{w}×{h} / {layers} 层 / {len(parts)} 个元件 / {len(edges)} 格铜线 / {len(vias)} 个贯穿孔',font=font(21),fill='#bdd3d5')
colors=('#d6b26d','#739dce','#ce8299','#92ba77');names=['R1K','R10K','LED','NOT','AND','OR','XOR','NAND','HA','7S','NOR','XNOR','BUF','RGB','PIXEL','DIAL','SW','DFF','CLK','3S','MUX','SIGN']
for layer in range(layers):
 x0=25+(layer%2)*630;y0=140+(layer//2)*680;scale=6.55
 d.text((x0,y0-33),f'L{layer+1}'+(' · 元件面' if layer==0 else ' · 内部铜层'),font=font(21),fill=colors[layer]);d.rectangle((x0,y0,x0+w*scale,y0+h*scale),fill='#25483e',outline='#8da393',width=2)
 def xy(n):return x0+(n%128+.5)*scale,y0+(n%12288//128+.5)*scale
 for a,b in edges:
  if a//12288==layer:d.line((*xy(a),*xy(b)),fill=colors[layer],width=1)
 for at in vias:
  x,y=xy(at);d.ellipse((x-1.7,y-1.7,x+1.7,y+1.7),fill='#dfc18c');d.ellipse((x-.6,y-.6,x+.6,y+.6),fill='#16382f')
 for i,(kind,x,y,turn) in enumerate(parts):
  a=x0+(x+.5)*scale;b=y0+(y+.5)*scale
  if layer==0:
   d.rectangle((a-1,b-1,a+scale*2+1,b+scale*2+1),fill='#202b33',outline='#82928d');d.text((a+scale,b+scale),names[kind],anchor='mm',font=font(8),fill='#efeadc')
  else:d.rectangle((a,b,a+scale*2,b+scale*2),outline='#527667')
 for i,at in enumerate(ports):
  x,y=xy(at);d.rectangle((x-3,y-3,x+3,y+3),fill='#edd190')
  if i<2:d.text((x+8,y-6),('VCC','GND')[i],font=font(10),fill='#ffead2')
d.text((25,1510),'板内连接均为真实铜线与过孔。显示只译码，计算由 52 个基本门完成。',font=font(21),fill='#c5d9d8')
d.text((25,1542),'这是对设计数据的离线预览，不是游戏截图。',font=font(17),fill='#9eb2bf');im.save(out/'single-board-calculator.png')
print(out/'single-board-calculator.png')
