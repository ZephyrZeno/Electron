"""A restrained two-note cabinet warning: rounded attacks, no hard gating or FM siren."""
from pathlib import Path
import json,sys

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'build/audio-tools'))
import numpy as np
import soundfile as sf

rate=22050
data=np.zeros(int(.94*rate))
for start,freq in ((.03,440),(.43,554.37)):
    t=np.arange(int(.34*rate))/rate
    attack=np.sin(np.minimum(t/.045,1)*np.pi/2)**2
    release=np.sin(np.minimum((.34-t)/.11,1)*np.pi/2)**2
    signal=.16*(np.sin(2*np.pi*freq*t)+.09*np.sin(2*np.pi*freq*2*t))*attack*release
    offset=int(start*rate);data[offset:offset+len(t)]+=signal
assets=ROOT/'src/main/resources/assets/electron'
sf.write(assets/'sounds/plant_alarm.ogg',data.astype('float32'),rate,format='OGG',subtype='VORBIS')
path=assets/'sounds.json';sounds=json.loads(path.read_text(encoding='utf-8'))
sounds['plant_alarm']={'subtitle':'subtitles.electron.plant_alarm','sounds':[{'name':'electron:plant_alarm','attenuation_distance':16,'preload':True}]}
path.write_text(json.dumps(sounds,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
decoded,_=sf.read(assets/'sounds/plant_alarm.ogg')
assert np.max(np.abs(decoded))<.2
assert np.max(np.abs(np.diff(decoded)))<.04
print('Generated 0.94 s mono two-note warning, softened edges, peak < 0.2.')
