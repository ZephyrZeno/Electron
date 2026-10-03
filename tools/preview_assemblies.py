"""Orthographic review of mesh data exported by native GameTests; not a game screenshot."""
from pathlib import Path
import json,math
from PIL import Image,ImageDraw,ImageFont,ImageStat
ROOT=Path(__file__).resolve().parents[1]
data=json.loads((ROOT/'build/gametest-runs/assembly-meshes.json').read_text())
out=ROOT/'build/asset-review';out.mkdir(parents=True,exist_ok=True)
S=2
canvas=Image.new('RGB',(1440*S,1050*S),'#101b25');d=ImageDraw.Draw(canvas)
font=lambda n:ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',n*S)
d.text((26*S,18*S),'ELECTRON · 多方块模型 / 实际网格离线预览',font=font(27),fill='#a2e2ca')
names={'DISTRIBUTION':'锥形输电塔 · 三层横担','WIND':'风机 · 塔架 / 机舱 / 叶轮','THERMAL':'火电 · 锅炉 / 排烟管','HYDRO':'水电 · 涡壳 / 发电机','NUCLEAR':'核电 · 容器 / 换热器','LIGHTNING':'引雷 · 瓷套 / 储能组','SUBSTATION':'变电 · 油箱 / 散热片','MAINS':'进线 · 立式配电柜'}
order=['DISTRIBUTION','WIND','THERMAL','NUCLEAR','LIGHTNING','HYDRO','SUBSTATION','MAINS']
def add(a,b):return tuple(x+y for x,y in zip(a,b))
def sub(a,b):return tuple(x-y for x,y in zip(a,b))
def mul(a,n):return tuple(x*n for x in a)
def cross(a,b):return (a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0])
def norm(v):return mul(v,1/math.sqrt(sum(x*x for x in v)))
def verts(p):
    a,b=p['a'],p['b']
    if p.get('sides',0)>4:
        n=p['sides'];return [(a[0]+p['r']*math.cos(i*math.tau/n),y,a[2]+p['r']*math.sin(i*math.tau/n)) for y in (a[1],b[1]) for i in range(n)]
    if not p['beam']:
        return [(a[0],a[1],a[2]),(b[0],a[1],a[2]),(b[0],b[1],a[2]),(a[0],b[1],a[2]),(a[0],a[1],b[2]),(b[0],a[1],b[2]),(b[0],b[1],b[2]),(a[0],b[1],b[2])]
    axis=norm(sub(b,a));u=cross(axis,(0,1,0))
    if sum(v*v for v in u)<1e-8:u=cross(axis,(1,0,0))
    u=mul(norm(u),p['r']);v=mul(norm(cross(axis,u)),p['r'])
    return [add(add(pt,mul(u,su)),mul(v,sv)) for pt in (a,b) for su,sv in [(1,1),(-1,1),(-1,-1),(1,-1)]]
def projection(v):x,y,z=v;return ((x-z)*.83,(x+z)*.31-y)
for i,key in enumerate(order):
    x0=18+(i%4)*354;y0=76+(i//4)*475
    d.rounded_rectangle((x0*S,y0*S,(x0+342)*S,(y0+459)*S),radius=10*S,fill='#192a35',outline='#34525e',width=2*S)
    d.text(((x0+13)*S,(y0+12)*S),names[key],font=font(16),fill='#e0eee8')
    parts=data[key];meshes=[(verts(p),p) for p in parts]
    if key=='WIND':
        for irot in range(3):
            angle=irot*math.tau/3+.2
            for a,b in [((-.12,.12,-.09),(.12,.7,.09)),((-.105,.7,-.07),(.07,1.5,.07)),((-.08,1.5,-.045),(.015,2.15,.045))]:
                p={'a':a,'b':b,'beam':False,'texture':'turbine_white'};vs=verts(p)
                meshes.append(([(.5+x*math.cos(angle)-y*math.sin(angle),2.5+x*math.sin(angle)+y*math.cos(angle),-.72+z) for x,y,z in vs],p))
    points=[projection(v) for vs,p in meshes for v in vs];xmin=min(v[0] for v in points);xmax=max(v[0] for v in points);ymin=min(v[1] for v in points);ymax=max(v[1] for v in points)
    scale=min(310/(xmax-xmin),380/(ymax-ymin));cx=x0+171-(xmin+xmax)/2*scale;cy=y0+248-(ymin+ymax)/2*scale
    faces=[]
    for vs,p in meshes:
        tx=ROOT/'src/main/resources/assets/electron/textures/block/assembled'/f"{p['texture']}.png";assert tx.exists()
        color=ImageStat.Stat(Image.open(tx).convert('RGB')).mean
        inds=[[0,3,2,1],[4,5,6,7],[0,4,7,3],[1,2,6,5],[3,7,6,2],[0,1,5,4]] if not p['beam'] else [[0,1,5,4],[1,2,6,5],[2,3,7,6],[3,0,4,7],[0,3,2,1],[4,5,6,7]]
        if p.get('sides',0)>4:
            n=p['sides'];inds=[[j,(j+1)%n,(j+1)%n+n,j+n] for j in range(n)]+[list(range(n-1,-1,-1)),list(range(n,2*n))]
        for face in inds:
            points=[vs[k] for k in face];normal=norm(cross(sub(points[1],points[0]),sub(points[2],points[0])))
            shade=.55+.45*abs(normal[1])+.15*max(0,-normal[2])
            depth=sum(v[0]+v[2]+v[1]*.2 for v in points)/4
            projected=[((cx+projection(v)[0]*scale)*S,(cy+projection(v)[1]*scale)*S) for v in points]
            faces.append((depth,projected,tuple(min(255,int(c*shade)) for c in color)))
    for depth,polygon,color in sorted(faces,key=lambda x:x[0]):d.polygon(polygon,fill=color)
    d.text(((x0+14)*S,(y0+432)*S),f"{len(parts)} 个网格部件 · 分格碰撞",font=font(13),fill='#8eaebb')
canvas.resize((1440,1050),Image.Resampling.LANCZOS).save(out/'assemblies.png')
print(out/'assemblies.png')
