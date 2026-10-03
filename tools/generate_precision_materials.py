"""Refine the existing procedural material library without changing any registry or circuit layout."""
from pathlib import Path
import math, random
from PIL import Image,ImageDraw,ImageFont

ROOT=Path(__file__).resolve().parents[1];A=ROOT/'src/main/resources/assets/electron/textures/block'
PALETTE={
 'turbine_white':(222,226,230),'reactor_shell':(187,193,199),'cooling_concrete':(169,171,171),
 'galvanized':(153,167,181),'cast_base':(49,54,62),'control_case':(49,63,79),'service_panel':(62,77,94),
 'porcelain':(226,222,207),'copper_bus':(169,104,57),'boiler':(92,99,108),'soot_stack':(57,58,64),
 'hydro_blue':(52,105,147),'transformer_oil':(79,98,91),'reactor_rib':(137,150,159),
 'capacitor_bank':(44,68,85),'steel_pipe':(119,132,143),'roof_panels':(66,82,104),'grating':(90,101,110),
 'hatch':(101,115,130),'vent':(59,77,95),'firebrick':(137,91,66)
}
def tint(color,delta):return tuple(max(0,min(255,c+delta)) for c in color)
def bolt(d,x,y,r=3):
 d.ellipse((x-r-1,y-r-1,x+r+1,y+r+1),fill='#33404d');d.ellipse((x-r,y-r,x+r,y+r),fill='#afbbc8');d.line((x-r+1,y,x+r-1,y),fill='#536171')
for name,color in PALETTE.items():
 size=256 if name in ('turbine_white','reactor_shell','cooling_concrete','boiler','hatch','service_panel','hydro_blue') else 128
 rng=random.Random('precision:'+name);im=Image.new('RGB',(size,size));d=ImageDraw.Draw(im)
 for y in range(size):
  for x in range(size):
   brushed=math.sin(y*.85)*1.5 if name in ('galvanized','steel_pipe','copper_bus','reactor_rib') else math.sin(x/24+y/41)*1.2
   im.putpixel((x,y),tint(color,int(brushed)+rng.choice((-1,0,0,0,1))))
 if name in ('turbine_white','reactor_shell','cooling_concrete'):
  d.line((0,0,size-1,0),fill=tint(color,-35),width=2);d.line((0,3,size-1,3),fill=tint(color,15))
  if name!='turbine_white':
   for x in (0,size//2):d.line((x,0,x,size-1),fill=tint(color,-16))
   for _ in range(80):
    x,y=rng.randrange(size),rng.randrange(size);d.point((x,y),fill=tint(color,-rng.randrange(5,12)))
  if name=='reactor_shell':
   for x in (9,size-10):
    for y in (10,size-10):bolt(d,x,y,2)
 elif name in ('galvanized','steel_pipe','copper_bus','reactor_rib'):
  for _ in range(26):
   y=rng.randrange(size);x=rng.randrange(size);d.line((x,y,min(size,x+rng.randrange(5,22)),y),fill=tint(color,rng.choice((-5,4))))
  d.line((0,0,size-1,0),fill=tint(color,24));d.line((0,size-1,size-1,size-1),fill=tint(color,-27))
 elif name=='porcelain':
  for x in range(size):
   glow=int(14*math.cos((x/size-.35)*math.pi));d.line((x,0,x,size-1),fill=tint(color,glow))
 elif name in ('boiler','hydro_blue','transformer_oil','capacitor_bank','control_case','cast_base','soot_stack'):
  d.rectangle((1,1,size-2,size-2),outline=tint(color,-25),width=2)
  d.line((4,4,size-5,4),fill=tint(color,24))
  if name in ('boiler','hydro_blue'):
   for y in (size//4,3*size//4):
    d.line((0,y,size-1,y),fill=tint(color,-24),width=2)
    for x in range(0,size,5):d.line((x,y+2,x+3,y+3),fill=tint(color,22))
  if name=='capacitor_bank':
   for x in range(14,size-10,16):d.rounded_rectangle((x,20,x+8,size-20),radius=3,fill=tint(color,15),outline=tint(color,-15))
 elif name in ('service_panel','hatch'):
  d.rounded_rectangle((6,5,size-7,size-6),radius=10,fill=tint(color,-5),outline='#263746',width=4)
  for y in (size//5,4*size//5):
   d.rounded_rectangle((4,y,18,y+20),radius=2,fill='#91a0b2',outline='#344352',width=2);bolt(d,11,y+5,2);bolt(d,11,y+15,2)
  d.rounded_rectangle((size-45,size//2-12,size-29,size//2+31),radius=3,fill='#a6b5c5',outline='#263746',width=3)
  d.rectangle((size-41,size//2-5,size-35,size//2+23),fill='#334758')
  d.rectangle((size//3,17,2*size//3,33),fill='#d2c085');d.line((size//3+5,24,2*size//3-5,24),fill='#5b5860',width=2)
  if name=='service_panel':
   for y in range(57,size//2,11):d.rounded_rectangle((30,y,size-60,y+5),radius=2,fill='#1b2837');d.line((31,y-1,size-61,y-1),fill='#8a9bad')
  for x in (26,size-24):
   for y in (45,size-22):bolt(d,x,y,2)
 elif name=='grating':
  d.rectangle((0,0,size,size),fill='#26323e')
  for x in range(0,size,8):d.line((x,0,x,size-1),fill='#aeb8c3',width=2);d.line((x+2,0,x+2,size-1),fill='#546879')
  for y in range(0,size,24):d.line((0,y,size-1,y),fill='#7f91a2',width=3)
 elif name=='vent':
  d.rectangle((2,2,size-3,size-3),outline='#a1b0c1',width=2)
  for y in range(12,size-10,9):d.rectangle((11,y,size-12,y+5),fill='#1b2938');d.line((11,y,size-12,y),fill='#8798ab',width=2)
  for x in (7,size-8):
   for y in (7,size-8):bolt(d,x,y,2)
 elif name=='roof_panels':
  for x in range(0,size,16):d.line((x,0,x,size),fill=tint(color,-25),width=3);d.line((x+3,0,x+3,size),fill=tint(color,25))
 elif name=='firebrick':
  for row,y in enumerate(range(0,size,16)):
   d.line((0,y,size,y),fill='#4f4844',width=2)
   for x in range(-16+(row%2)*16,size,32):d.line((x,y,x,y+16),fill='#514a44',width=2);d.line((x+3,y+3,x+28,y+3),fill='#b88765')
 im.save(A/'assembled'/f'{name}.png')

# Shared small-circuit surfaces gain coherent material detail without moving a single pin.
for name,color in {'case':(44,53,68),'trim':(119,135,155),'copper':(187,128,72),'silver':(187,201,217),'ceramic':(207,204,190),'chip':(29,36,47),'rubber':(36,41,49),'green_board':(29,73,65),'red_board':(105,43,58)}.items():
 im=Image.new('RGB',(128,128),color);d=ImageDraw.Draw(im);rng=random.Random(name)
 for y in range(128):
  for x in range(128):im.putpixel((x,y),tint(color,rng.choice((-1,0,0,1))))
 if name.endswith('board'):
  for y in (15,30,96,111):
   d.line([(8,y),(36,y),(46,y+6),(119,y+6)],fill=tint(color,22),width=2)
   for x in (8,119):d.ellipse((x-3,y-3,x+3,y+3),fill='#a88b57');d.ellipse((x-1,y-1,x+1,y+1),fill='#283e3a')
 elif name=='chip':
  d.rectangle((2,2,125,125),outline='#101926',width=3);d.line((6,6,121,6),fill='#485463');d.ellipse((11,11,19,19),fill='#687583')
 elif name in ('case','trim'):
  d.rectangle((1,1,126,126),outline=tint(color,-24),width=2);d.line((4,4,123,4),fill=tint(color,24))
 else:
  for y in range(1,128,4):d.line((0,y,127,y),fill=tint(color,3 if name!='rubber' else -3))
 im.save(A/f'{name}.png')

out=ROOT/'build/asset-review';out.mkdir(parents=True,exist_ok=True)
sheet=Image.new('RGB',(1024,((len(PALETTE)+5)//6)*170+45),'#17202a');d=ImageDraw.Draw(sheet);font=ImageFont.truetype('C:/Windows/Fonts/consola.ttf',13)
d.text((16,14),'MATERIAL LIBRARY / actual source textures',font=font,fill='#d4e0ef')
for i,name in enumerate(PALETTE):
 x=12+(i%6)*169;y=45+(i//6)*170;sheet.paste(Image.open(A/'assembled'/f'{name}.png').resize((150,140)),(x,y));d.text((x,y+145),name,font=font,fill='#becbd9')
sheet.save(out/'materials.png')
print('Refined 21 assembly materials and 9 small-circuit surfaces')
