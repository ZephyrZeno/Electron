"""Render native-exported arc filaments for review, not a Minecraft screenshot."""
from pathlib import Path
import json
from PIL import Image,ImageDraw,ImageFilter,ImageFont
ROOT=Path(__file__).resolve().parents[1];out=ROOT/'build/asset-review';out.mkdir(exist_ok=True)
frames=json.loads((ROOT/'build/gametest-runs/arc-meshes.json').read_text())
im=Image.new('RGB',(1280,670),'#10202c');d=ImageDraw.Draw(im);font=lambda n:ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',n)
d.text((30,20),'ELECTRON / 实际电弧网格预览',font=font(29),fill='#a8e5cf');d.text((30,65),'由 Minecraft 测试导出的分叉路径 · 离线投影（非游戏截图）',font=font(18),fill='#96adba')
for i,frame in enumerate(frames[:4]):
 x=(i%2)*625+20;y=(i//2)*268+104;d.rounded_rectangle((x,y,x+612,y+251),radius=10,fill='#182a34',outline='#375563')
 def xy(p):return (x+90+(p['x']-.183)*310,y+48+(4.214-p['y'])*150+(p['z']-.971)*40)
 start=frame[0]['points'][0];end=frame[0]['points'][-1];a=xy(start);b=xy(end)
 d.line((x+36,a[1],a[0]+30,a[1]),fill='#6f8595',width=10);d.rectangle((b[0]-65,b[1]+4,b[0]+65,b[1]+33),fill='#6b7a7f')
 glow=Image.new('RGBA',im.size);g=ImageDraw.Draw(glow)
 for f in frame:g.line([xy(p) for p in f['points']],fill=(100,144,255,170),width=max(2,int(9*f['width'])))
 im=Image.alpha_composite(im.convert('RGBA'),glow.filter(ImageFilter.GaussianBlur(5)));d=ImageDraw.Draw(im)
 for f in frame:
  pts=[xy(p) for p in f['points']];d.line(pts,fill='#aacfff',width=max(1,int(3*f['width'])));d.line(pts,fill='#f0fbff',width=1)
 d.text((x+18,y+222),f'FRAME {i+1} · 同一对触点 / 分叉闪动',font=font(16),fill='#b2c9d3')
im.convert('RGB').save(out/'arcs.png');print(out/'arcs.png')
