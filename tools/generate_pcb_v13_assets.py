"""Dense PCB packages, flush carrier tiles and arbitrary-pixel panels; no painted voltage labels."""
from pathlib import Path
import ast,json,random,math,copy
from PIL import Image,ImageDraw
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/electron';D=R/'src/main/resources/data/electron'
tree=ast.parse((R/'tools/generate_processing_assets.py').read_text(encoding='utf-8'))
nodes=[n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name in ('cube','model','round_vertical','transform') or isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id in ('TX','DISPLAY') for t in n.targets)]
exec(compile(ast.Module(body=nodes,type_ignores=[]),'asset_helpers','exec'))
TX.update({k:'electron:block/pcb/'+k for k in ('brushed_steel','enamel','rubber','panel_seam','terminal_red','terminal_black','laminate','gold_trace','solder_joint','ic_package')})
TX.update({k:'electron:block/display/'+k for k in ('frame','glass','segment_on','segment_off')})
def write(p,obj):
 p.parent.mkdir(parents=True,exist_ok=True);s=json.dumps(obj,ensure_ascii=False,indent=2)+'\n'
 if not p.exists() or p.read_text(encoding='utf-8')!=s:p.write_text(s,encoding='utf-8')
def recipe(name,items,count=1):
 write(D/'recipe'/f'{name}.json',{'type':'minecraft:crafting_shapeless','category':'misc','ingredients':[{'item':i} for i in items],'result':{'id':'electron:'+name,'count':count}})
 write(D/'advancement/recipes'/f'{name}.json',{'criteria':{'material':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':[items[0]]}]}}},'requirements':[['material']],'rewards':{'recipes':['electron:'+name]}})
c=cube
def screw(x,y,z):return [c((x-.19,y,z-.19),(x+.19,y+.12,z+.19),'brushed_steel'),c((x-.12,y+.121,z-.03),(x+.12,y+.13,z+.03),'rubber')]
for bits in range(8):
 color=tuple(52+193*((bits>>i)&1) for i in range(3));im=Image.new('RGB',(64,64),color);d=ImageDraw.Draw(im)
 for inset in range(12):
  f=.52+.04*inset;d.rounded_rectangle((inset,inset,63-inset,63-inset),radius=10,fill=tuple(int(v*f) for v in color))
 d.ellipse((15,9,37,20),fill=tuple(min(255,int(v*.5+127)) for v in color));im.save(A/'textures/block/pcb'/f'rgb_{bits}.png');TX['rgb_'+str(bits)]='electron:block/pcb/rgb_'+str(bits)
types=('nor','xnor','buffer','rgb','pixel','dial','switch','dff','clock','tristate','mux','sign')
for index,t in enumerate(types):
 e=[c((3.4,5.7,4),(12.6,6.1,12),'rubber'),c((3.6,6.1,4.2),(12.4,7.8,11.8),'ic_package'),c((3.8,7.8,4.4),(12.2,7.95,11.6),'frame')]
 for x in (4.4,7.6,10.8):
  for z in (2.7,12):e += [c((x,5.4,z),(x+.8,5.72,z+1.3),'solder_joint'),c((x,5.7,z+.4),(x+.8,6.7,z+.8),'brushed_steel'),c((x,6.5,z+.4),(x+.8,6.7,z+1.6),'solder_joint')]
 e += [c((4.2,7.96,4.7),(4.8,8.01,5.3),'solder_joint')]
 if t=='rgb':
  e += [c((5,7.96,5),(11,8.15,11),'enamel')]
  for j,color in enumerate((1,2,4)):e += [c((5.6+j*1.6,8.15,6.4),(6.8+j*1.6,8.28,9.6),'rgb_'+str(color))]
  e += [c((5.3,8.3,5.3),(10.7,9.2,10.7),'rgb_7')]
 elif t=='pixel':
  e += [c((4,7.95,4.6),(12,8.1,11.4),'glass')]
  for y in range(8):
   for x in range(8):e += [c((4.3+x*.92,8.1,4.9+y*.78),(4.96+x*.92,8.15,5.45+y*.78),'segment_on' if x==y or x==7-y else 'segment_off')]
 elif t=='sign':
  e += [c((4.3,7.96,5),(11.7,8.1,11),'glass'),c((5.2,8.1,7.5),(10.8,8.17,8.5),'segment_on')]
 elif t=='dial':
  e+=round_vertical(8,7.96,8,2.6,1.4,'rubber')
  e += [c((7.7,9.38,6),(8.3,9.42,7.5),'solder_joint')]
  for x in (5,10):e+=screw(x,8,8)
 elif t=='switch':
  e += [c((4.8,7.96,6.4),(11.2,8.02,9.6),'rubber'),c((5.4,8.02,6.8),(7.4,9.6,9.2),'enamel'),c((5.7,9.6,7),(7.1,9.67,9),'brushed_steel')]
 elif t=='clock':
  e += [c((4.4,7.95,5.1),(11.6,8.1,10.9),'solder_joint'),c((4.7,8.1,5.4),(11.3,9.25,10.6),'brushed_steel'),c((5,9.25,5.7),(11,9.35,10.3),'enamel')]
 else:
  # Different bond-window patterns identify package families without tiny unreadable text.
  for j in range(1+(index%3)):e += [c((6+j*1.5,7.96,6.1),(6.7+j*1.5,8,9.9),'brushed_steel')]
  e += [c((7,7.96,4.4),(9,8.02,4.9),'rubber')]
 write(A/'models/item'/f'smd_{t}.json',model(e))
 recipes={'nor':['smd_or','smd_not'],'xnor':['smd_xor','smd_not'],'buffer':['smd_not','smd_not'],'rgb':['smd_led','smd_led','smd_led','smd_buffer'],'pixel':['smd_dff','smd_dff','smd_led','smd_led','smd_led','smd_led','smd_mux'],'dial':['smd_switch','smd_mux','copper_foil'],'switch':['copper_foil','iron_plate'],'dff':['smd_nand','smd_nand','smd_nand','smd_nand'],'clock':['smd_not','smd_r10k','solder_alloy'],'tristate':['smd_nand','smd_buffer'],'mux':['smd_and','smd_and','smd_or','smd_not'],'sign':['smd_led','smd_buffer']}
 recipe('smd_'+t,['electron:'+v for v in recipes[t]]+['electron:solder_alloy'])
base=[c((.12,0,.12),(15.88,.45,15.88),'rubber'),c((.18,.45,.18),(15.82,2.6,15.82),'frame'),c((.4,2.6,.4),(15.6,2.82,15.6),'enamel')]
for x in (1,15):
 for z in (1,15):base+=screw(x,2.82,z)
for name in ('pcb_socket','pcb_socket_extension','pixel_display_panel'):
 e=list(base)
 if name=='pcb_socket':
  for x in (1,15):
   for z in (3,7,13):e += [c((x-.55,2.82,z-.65),(x+.55,2.94,z+.65),'rubber'),c((x-.31,2.94,z-.4),(x+.31,2.98,z+.4),'gold_trace')]
 elif name=='pcb_socket_extension':
  for z in (4,8,12):e += [c((.4,2.84,z),(1.3,2.9,z+.6),'rubber'),c((14.7,2.84,z),(15.6,2.9,z+.6),'rubber')]
 else:
  e=[c((0,0,0),(16,2.55,16),'frame'),c((.1,2.55,.1),(15.9,2.6,15.9),'glass')]
 if name=='pcb_socket_extension':write(A/'models/block'/f'{name}.json',model(e));write(A/'blockstates'/f'{name}.json',{'variants':{'':{'model':'electron:block/'+name}}})
 else:
  variants={}
  for look in ('off','broken'):
   for face in ('up','down','north','south','west','east'):
    for turn in range(4):
     ref=f'{name}_{look}_{face}_{turn}';tx={k:'electron:block/scorched' for k in TX} if look=='broken' else {};write(A/'models/block'/f'{ref}.json',model(transform(e,face,turn),tx));variants[f'facing={face},turn={turn},broken={str(look=="broken").lower()}']={'model':'electron:block/'+ref}
  write(A/'blockstates'/f'{name}.json',{'variants':variants})
 item=list(e)
 if name=='pixel_display_panel':
  for y in range(8):
   for x in range(8):item += [c((x*2+.2,2.6,y*2+.2),(x*2+1.8,2.66,y*2+1.8),'segment_on' if x==y else 'segment_off')]
 write(A/'models/item'/f'{name}.json',model(item))
 entry={'type':'minecraft:item','name':'electron:'+name}
 if name=='pcb_socket':
  entry={'type':'minecraft:alternatives','children':[{'type':'minecraft:item','name':'electron:burnt_component','conditions':[{'condition':'minecraft:block_state_property','block':'electron:'+name,'properties':{'broken':'true'}}]},entry]}
 write(D/'loot_table/blocks'/f'{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[entry],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
recipe('pcb_socket_extension',['electron:iron_plate','electron:iron_plate','minecraft:black_dye'],2)
recipe('pixel_display_panel',['electron:smd_pixel','electron:iron_plate','electron:copper_foil','minecraft:glass'])
for rel in ('mineable/pickaxe','needs_iron_tool'):
 p=R/'src/main/resources/data/minecraft/tags/block'/f'{rel}.json';obj=json.loads(p.read_text());obj['values']=sorted(set(obj['values']+['electron:pcb_socket_extension','electron:pixel_display_panel']));write(p,obj)
print('Generated 12 distinct miniature packages, RGB lenses, flush PCB carriers and a pixel matrix panel.')
