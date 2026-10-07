"""Remove buried and coplanar opaque faces, preserving UVs and all original solid bounds.

Run after the model generators. Transparent/cutout and rotated elements never occlude
another element. Partial overlaps are cut into visible face patches, not depth-biased.
"""
from pathlib import Path
from functools import lru_cache
import copy,json
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
A=ROOT/'src/main/resources/assets/electron'
FAMILIES=('pcb_design_station','precision_workbench','pcb_socket','pcb_extension',
          'engineering_lamp','workshop_lamp','yard_lamp','thermal_generator','nuclear_reactor',
          'industrial_assembly_table','plant_control_module','ore_crusher','centrifuge',
          'electric_kiln','fuel_fabricator')
# plane axis, outward sign, u axis/sign, v axis/sign, matching vanilla's face UV convention.
FACES={'up':(1,1,0,1,2,1),'down':(1,-1,0,1,2,-1),
       'north':(2,-1,0,-1,1,-1),'south':(2,1,0,1,1,-1),
       'west':(0,-1,2,1,1,-1),'east':(0,1,2,-1,1,-1)}
EPS=1e-7

@lru_cache(None)
def opaque_texture(ref):
    if not ref.startswith('electron:'):return False
    p=A/'textures'/(ref.split(':')[1]+'.png')
    with Image.open(p) as im:return im.convert('RGBA').getchannel('A').getextrema()==(255,255)

def opaque(e,tx):
    if 'rotation' in e or set(e.get('faces',{}))!=set(FACES):return False
    for f in e['faces'].values():
        ref=f['texture'];seen=set()
        while ref.startswith('#'):
            if ref in seen:return False
            seen.add(ref);ref=tx.get(ref[1:],'')
        if not opaque_texture(ref):return False
    return True

def subtract(rect,cut):
    x,y,X,Y=rect;a,b,c,d=max(x,cut[0]),max(y,cut[1]),min(X,cut[2]),min(Y,cut[3])
    if c-a<EPS or d-b<EPS:return [rect]
    return [r for r in ((x,y,a,Y),(c,y,X,Y),(a,y,c,b),(a,d,c,Y)) if r[2]-r[0]>EPS and r[3]-r[1]>EPS]

def crop_uv(face,e,spec,rect):
    _,_,u,us,v,vs=spec
    a,b,c,d=rect
    def normalize(lo,hi,axis,sign):
        start,end=e['from'][axis],e['to'][axis]
        return ((lo-start)/(end-start),(hi-start)/(end-start)) if sign>0 else ((end-hi)/(end-start),(end-lo)/(end-start))
    a,c=normalize(a,c,u,us);b,d=normalize(b,d,v,vs)
    rotation=face.get('rotation',0)
    if rotation==90:a,b,c,d=b,1-c,d,1-a
    elif rotation==180:a,b,c,d=1-c,1-d,1-a,1-b
    elif rotation==270:a,b,c,d=1-d,a,1-b,c
    default=[e['from'][u] if us>0 else 16-e['to'][u],e['from'][v] if vs>0 else 16-e['to'][v],
             e['to'][u] if us>0 else 16-e['from'][u],e['to'][v] if vs>0 else 16-e['from'][v]]
    x,y,X,Y=face.get('uv',default)
    result=copy.deepcopy(face);result['uv']=[round(x+(X-x)*a,9),round(y+(Y-y)*b,9),round(x+(X-x)*c,9),round(y+(Y-y)*d,9)]
    return result

def refine(obj):
    elements=obj.get('elements',[]);original=copy.deepcopy(elements);tx=obj.get('textures',{})
    solids=[(i,e) for i,e in enumerate(original) if opaque(e,tx)]
    removed=patched=0;extras=[]
    for i,e in enumerate(original):
        if 'rotation' in e:continue
        for side,face in e.get('faces',{}).items():
            axis,sign,u,us,v,vs=FACES[side];plane=e['to' if sign>0 else 'from'][axis]
            initial=(e['from'][u],e['from'][v],e['to'][u],e['to'][v]);visible=[initial]
            for j,other in solids:
                if i==j:continue
                lo,hi=other['from'][axis],other['to'][axis]
                covered=(lo<=plane+EPS and hi>plane+EPS) if sign>0 else (lo<plane-EPS and hi>=plane-EPS)
                overlay=j>i and abs((hi if sign>0 else lo)-plane)<EPS
                if not covered and not overlay:continue
                cut=(other['from'][u],other['from'][v],other['to'][u],other['to'][v])
                # Partial intersection at different depths is already hidden by the depth buffer.
                # Splitting it around every screw would add triangles without fixing any flicker.
                if not overlay and not (cut[0]<=initial[0]+EPS and cut[1]<=initial[1]+EPS and cut[2]>=initial[2]-EPS and cut[3]>=initial[3]-EPS):continue
                visible=[part for rect in visible for part in subtract(rect,cut)]
                if not visible:break
            if visible==[initial]:continue
            elements[i]['faces'].pop(side);removed+=1
            for rect in visible:
                patch={k:copy.deepcopy(value) for k,value in e.items() if k!='faces'}
                patch['from'][u],patch['from'][v],patch['to'][u],patch['to'][v]=rect
                patch['faces']={side:crop_uv(face,e,FACES[side],rect)};extras.append(patch);patched+=1
    elements.extend(extras)
    # Minecraft rejects elements with zero faces (an empty MODEL is valid, an empty element is not).
    obj['elements']=[e for e in elements if e.get('faces')]
    return removed,patched

def main():
    report={'models_changed':0,'faces_replaced_or_removed':0,'visible_patches':0,'families':list(FAMILIES)}
    for folder in ('block','item'):
        for p in (A/'models'/folder).glob('*.json'):
            if not any(p.stem==n or p.stem.startswith(n+'_') for n in FAMILIES):continue
            obj=json.loads(p.read_text(encoding='utf-8'));removed,patches=refine(obj)
            if not removed:continue
            report['models_changed']+=1;report['faces_replaced_or_removed']+=removed;report['visible_patches']+=patches
            p.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    out=ROOT/'build/asset-review/surface-refinement.json';out.parent.mkdir(exist_ok=True,parents=True)
    out.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report))

if __name__=='__main__':main()
