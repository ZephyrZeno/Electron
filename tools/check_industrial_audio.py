"""Decode the original positional sound samples and validate their bilingual subtitles."""
from pathlib import Path
import json
import re
import sys

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'build/audio-tools'))
import numpy as np
import soundfile as sf

assets=ROOT/'src/main/resources/assets/electron'
sounds=json.loads((assets/'sounds.json').read_text(encoding='utf-8'))
locales=[json.loads((assets/'lang'/f'{locale}.json').read_text(encoding='utf-8')) for locale in ('zh_cn','en_us')]
source=(ROOT/'src/main/java/top/lacrus/zephyrzeno/electron/foundry/ElectricalEffects.java').read_text(encoding='utf-8')
assert set(re.findall(r'sound\("([a-z_]+)"\)',source))==set(sounds), 'Sound-event registration mismatch'
rows=[]
for event,settings in sounds.items():
    assert all(settings['subtitle'] in locale for locale in locales)
    for entry in settings['sounds']:
        identifier=entry if isinstance(entry,str) else entry['name']
        if not identifier.startswith('electron:'):
            continue
        path=assets/'sounds'/(identifier.split(':',1)[1]+'.ogg')
        data,rate=sf.read(path,always_2d=True)
        assert data.shape[1]==1 and rate==22050 and np.all(np.isfinite(data)),event
        peak=float(np.max(np.abs(data)))
        rms=float(np.sqrt(np.mean(data**2)))
        assert .01<peak<1 and rms>.005,(event,peak,rms)
        rows.append({'event':event,'seconds':round(len(data)/rate,3),'peak':round(peak,3),'rms':round(rms,3),'channels':1,'rate':rate})
assert len(rows)==15
report={'original_ogg_samples':rows,'decoded':True,'clipped':False}
(ROOT/'build/industrial-audio-verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(f"Validated {len(rows)} mono Ogg samples and both subtitle catalogs; highest decoded peak {max(row['peak'] for row in rows):.3f}.")
