"""Original mono pressure explosions plus a four-cell translucent billow atlas."""
from pathlib import Path
import sys,json,math
from PIL import Image,ImageDraw,ImageFilter
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/electron';sys.path.insert(0,str(R/'build/audio-tools'))
import numpy as np
import soundfile as sf
size=128
atlas=Image.new('RGBA',(size*4,size))
for tile in range(4):
 rng=np.random.default_rng(70231+tile);yy,xx=np.mgrid[-1:1:complex(size),-1:1:complex(size)];density=np.zeros((size,size))
 for j in range(18):
  a=rng.uniform(0,2*np.pi);radius=rng.uniform(.05,.60);x,y=np.cos(a)*radius,np.sin(a)*radius;sigma=rng.uniform(.10,.25);density+=rng.uniform(.3,.9)*np.exp(-((xx-x)**2+(yy-y)**2)/(2*sigma**2))
 density=np.clip(density/np.max(density),0,1);edge=np.clip((1-np.sqrt(xx**2+yy**2))*5,0,1)
 # Several turbulence scales create distinct billows without a hard sprite silhouette.
 detail=np.zeros((size,size))
 for cells,weight in ((8,.5),(20,.3),(42,.2)):
  noise=Image.fromarray((rng.random((cells,cells))*255).astype(np.uint8)).resize((size,size),Image.Resampling.BICUBIC)
  detail+=np.asarray(noise)/255*weight
 alpha=np.power(density,.58)*edge*np.clip(.8+detail*.5,0,1)
 light=np.clip(138+82*density-35*xx+22*yy+(detail-.5)*95,65,240)
 arr=np.zeros((size,size,4),dtype=np.uint8);arr[:,:,:3]=light[:,:,None];arr[:,:,3]=(alpha*235).astype(np.uint8);im=Image.fromarray(arr).filter(ImageFilter.GaussianBlur(.3));atlas.paste(im,(tile*size,0))
effect=A/'textures/effect';effect.mkdir(exist_ok=True);atlas.save(effect/'plant_smoke.png')
y,x=np.mgrid[-1:1:complex(size),-1:1:complex(size)];radius=np.sqrt(x*x+y*y);alpha=np.clip((1-radius),0,1)**2
arr=np.full((size,size,4),255,dtype=np.uint8);arr[:,:,3]=(alpha*255).astype(np.uint8);Image.fromarray(arr).save(effect/'blast_flash.png')
sounds=json.loads((A/'sounds.json').read_text(encoding='utf-8'))
names={'machine_burst':(2.8,89,('机组外壳爆裂','Machine housing bursts')),'boiler_burst':(5.5,57,('锅炉压力爆裂','Boiler pressure burst')),'reactor_burst':(11.5,34,('反应堆压力爆炸与沉闷回响','Reactor pressure blast and rumble'))}
for index,(name,(seconds,base,subtitle)) in enumerate(names.items()):
 rate=22050;n=int(seconds*rate);t=np.arange(n)/rate;rng=np.random.default_rng(8461+index);noise=rng.normal(0,1,n)
 # Broadband pressure front, low rumble, metal resonances and several irregular falling fragments.
 low=np.convolve(noise,np.ones(90+index*70)/(90+index*70),mode='same');mid=np.convolve(noise,np.ones(7)/7,mode='same')
 attack=np.clip(t/.003,0,1);body=np.exp(-t/(.45+index*.65));rumble=np.exp(-t/(1.0+index*1.2));signal=attack*(1.1*mid*np.exp(-t/.1)+2.4*low*rumble+.19*np.sin(2*np.pi*(base*t+4*(1-np.exp(-t*5))))*body)
 for f in (171+index*8,259+index*5,397+index*11):signal+=.04*np.sin(2*np.pi*f*t)*np.exp(-t/(.6+index*.2))
 for j in range(12+index*4):
  at=rng.uniform(.2,seconds*.72);dt=np.maximum(0,t-at);env=np.where(t>=at,np.exp(-dt/.065),0);signal+=.13*mid*env*np.sin(2*np.pi*rng.uniform(500,1800)*dt)
 signal*=np.clip((seconds-t)/.35,0,1);signal*=.78/max(np.max(np.abs(signal)),1e-9);sf.write(A/'sounds'/f'{name}.ogg',signal,rate,format='OGG',subtype='VORBIS')
 sounds[name]={'subtitle':'subtitles.electron.'+name,'sounds':[{'name':'electron:'+name,'attenuation_distance':128 if index==2 else 64 if index==1 else 32}]}
 for i,locale in enumerate(('zh_cn','en_us')):
  p=A/'lang'/f'{locale}.json';obj=json.loads(p.read_text(encoding='utf-8'));obj['subtitles.electron.'+name]=subtitle[i];p.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
(A/'sounds.json').write_text(json.dumps(sounds,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Created 3 distinct mono explosion samples, soft smoke atlas and pressure flash texture.')
