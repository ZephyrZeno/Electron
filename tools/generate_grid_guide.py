"""Tutorial diagrams whose visible captions all come from the bilingual language files."""
from pathlib import Path
import json
from PIL import Image, ImageDraw, ImageFont
A=Path(__file__).resolve().parents[1]/'src/main/resources/assets/electron'
font=lambda n:ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',n)
for lang in ('en_us','zh_cn'):
    strings=json.loads((A/'lang'/f'{lang}.json').read_text(encoding='utf-8'))
    def label(draw,key,xy,width,size=20):
        text=strings[key]
        while font(size).getlength(text)>width and size>10:size-=1
        draw.text(xy,text,font=font(size),anchor='mm',fill='#ebf4ed')
    for page,items in [('power_grid',['wind_generator','tower_terminal','mains_inlet','substation','engineering_lamp']),('mechanics',['dc_motor','transmission_shaft','rotating_platform'])]:
        im=Image.new('RGB',(960,320),'#101e29');d=ImageDraw.Draw(im)
        label(d,'guide.electron.'+page+'.title',(480,27),920,25)
        count=len(items);step=900/count;centers=[]
        for i,name in enumerate(items):
            x=30+i*step;mid=x+step/2;centers.append(mid)
            d.rounded_rectangle((x+6,90,x+step-6,224),radius=12,fill='#28463e',outline='#a5c9b7',width=2)
            label(d,'block.electron.'+name,(mid,158),step-24)
            d.ellipse((mid-26,204,mid-18,212),fill='#ed796c');d.ellipse((mid+18,204,mid+26,212),fill='#a2b8c3')
        for a,b in zip(centers,centers[1:]):
            if page=='power_grid':
                d.line([(a-22,208),(a-22,63),(b-22,63),(b-22,208)],fill='#ed796c',width=4)
                d.line([(a+22,208),(a+22,254),(b+22,254),(b+22,208)],fill='#9aafbc',width=4)
            else:d.line([(a+70,140),(b-70,140)],fill='#c9cfd3',width=12);d.polygon([(b-78,129),(b-60,140),(b-78,151)],fill='#f1c967')
        label(d,'guide.electron.'+page+'.subtitle',(480,290),916,19)
        out=A/'textures/gui/tutorial'/lang/(page+'.png');im.save(out)
