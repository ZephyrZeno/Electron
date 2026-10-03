"""Split PCB stations and detailed, text-free electrical displays. Deterministic assets."""
from pathlib import Path
import ast,copy,json,math,random
from PIL import Image,ImageDraw
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/electron';D=R/'src/main/resources/data/electron'
tree=ast.parse((R/'tools/generate_processing_assets.py').read_text(encoding='utf-8'))
nodes=[n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name in ('cube','model','round_vertical','transform') or isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id in ('TX','DISPLAY') for t in n.targets)]
exec(compile(ast.Module(body=nodes,type_ignores=[]),'asset_helpers','exec'))
def write(p,v):
 p.parent.mkdir(parents=True,exist_ok=True);s=json.dumps(v,ensure_ascii=False,indent=2)+'\n'
 if not p.exists() or p.read_text(encoding='utf-8')!=s:p.write_text(s,encoding='utf-8')
def recipe(n,items,count=1):
 write(D/'recipe'/f'{n}.json',{'type':'minecraft:crafting_shapeless','category':'misc','ingredients':[{'item':i} for i in items],'result':{'id':'electron:'+n,'count':count}})
 write(D/'advancement/recipes'/f'{n}.json',{'criteria':{'material':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':[items[0]]}]}}},'requirements':[['material']],'rewards':{'recipes':['electron:'+n]}})
TX.update({k:'electron:block/pcb/'+k for k in ('brushed_steel','enamel','rubber','panel_seam','terminal_red','terminal_black','display_glass','laminate','gold_trace','solder_joint','ic_package','mat_detail')})
for name,color in [('frame',(47,58,68)),('glass',(14,25,29)),('segment_on',(244,142,64)),('segment_off',(51,36,29))]:
 im=Image.new('RGB',(128,128),color);d=ImageDraw.Draw(im);rng=random.Random(name)
 for y in range(128):
  delta=rng.randrange(-2,3);d.line((0,y,127,y),fill=tuple(max(0,min(255,c+delta)) for c in color))
 if name=='glass':
  for x in range(6,120,12):d.line((x,0,x+12,127),fill='#112127')
 if name=='frame':
  d.line((0,1,127,1),fill='#91a2a8');d.line((0,125,127,125),fill='#222c34')
 if name.startswith('segment'):
  for y in range(4,128,8):
   for x in range(4,128,8):d.ellipse((x,y,x+2,y+2),fill='#ffddac' if name.endswith('on') else '#3c2e25')
 p=A/'textures/block/display'/f'{name}.png';p.parent.mkdir(parents=True,exist_ok=True);im.save(p);TX[name]='electron:block/display/'+name
c=cube
def dark_segments(x,z,w,h,y):
 return [c((x+a*w,y,z+b*h),(x+d*w,y+.04,z+e*h),'segment_off') for a,b,d,e in ((.2,.06,.8,.14),(.8,.15,.9,.45),(.8,.55,.9,.85),(.2,.86,.8,.94),(.1,.55,.2,.85),(.1,.15,.2,.45),(.2,.46,.8,.54))]
def screw(x,y,z):return [c((x-.16,y,z-.16),(x+.16,y+.11,z+.16),'brushed_steel'),c((x-.12,y+.111,z-.025),(x+.12,y+.12,z+.025),'rubber')]
def station():
 e=[]
 for x in (1.2,13.4):
  for z in (1.2,13.4):e += [c((x-.2,0,z-.2),(x+1.2,.4,z+1.2),'rubber'),c((x,.4,z),(x+1,10.85,z+1),'brushed_steel')]
 e += [c((.45,10.85,.45),(15.55,11.5,15.55),'brushed_steel'),c((.5,11.5,.5),(15.5,11.72,15.5),'enamel'),c((1,11.72,6),(15,11.8,15),'mat_detail')]
 # Drawing screen and individual keycaps, flat drafting tablet, separate component trays.
 e += [c((5.5,11.8,2.7),(10.5,12.2,4.5),'rubber'),c((7.5,12.2,2.7),(8.5,13.3,3.3),'brushed_steel'),c((3.5,13,1.8),(12.5,15.4,2.6),'frame'),c((4,13.3,2.6),(12,15.1,2.64),'display_glass'),c((4,11.8,4.5),(12,12,6.1),'rubber')]
 for x in range(5,12):
  for z in (4.7,5.3):e += [c((x,12,z),(x+.7,12.18,z+.45),'brushed_steel')]
 e += [c((3,11.8,7.6),(13,12.02,14),'rubber'),c((3.4,12.02,8),(12.6,12.06,13.6),'laminate'),c((13.5,11.8,8),(13.7,12.2,13.6),'brushed_steel')]
 for x in (2,3.5,5):e += [c((x,11.8,6.5),(x+.9,12.1,7.2),'ic_package')]
 # Film plotter body under the rear of the desk, receiving tray and rollers.
 e += [c((2,7.2,2),(14,10.85,7),'enamel'),c((2.3,7.6,7),(13.7,10.4,7.15),'panel_seam'),c((3.5,8.5,7.15),(12.5,8.9,7.3),'rubber'),c((3.5,8.3,7.3),(12.5,8.4,9.4),'porcelain')]
 for x in (4,6,8,10):e += [c((x,8.4,7.4),(x+.2,8.41,9.2),'ic_package')]
 # Canonical rear power sockets plus the appliance cord inlet.
 e += [c((.8,5.2,.12),(13.2,8.8,1.5),'enamel'),c((1.3,5.7,.10),(12.7,8.3,.13),'rubber')]
 for x,tx in ((3,'terminal_red'),(11,'terminal_black')):
  e += [c((x-.85,6.1,.06),(x+.85,7.9,.3),'brushed_steel'),c((x-.62,6.35,.04),(x+.62,7.65,.13),tx),c((x-.31,6.65,.018),(x+.31,7.35,.045),'rubber'),c((x-.11,6.9,0),(x+.11,7.1,.019),'gold_trace')]
 e += [c((6.7,5.8,.06),(9.3,8.2,.24),'rubber'),c((7,6.1,.01),(9,7.9,.06),'frame')]
 for x in (1.5,14.5):
  for z in (1.5,14.5):e+=screw(x,11.72,z)
 return e
names=('pcb_design_station','decimal_input','numeric_display','display_panel')
for n in names:
 if n=='pcb_design_station':e=station()
 elif n=='display_panel':
  e=[c((0,0,0),(16,2.55,16),'frame')]
  for x in (1,14.5):
   for z in (1,14.5):e+=screw(x,2.55,z)
 else:
  e=[c((.3,0,.3),(15.7,1.0,15.7),'rubber'),c((.7,1,.7),(15.3,2.65,15.3),'enamel'),c((3.8,2.65,2.5),(11.8,3.55,12.6),'frame'),c((4,3.55,2.7),(11.6,3.84,12.4),'glass')]
  for x in (1,15):
   for z in (3,7,13):e += [c((x-.55,2.6,z-.55),(x+.55,2.9,z+.55),'rubber'),*screw(x,2.9,z)]
  if n=='decimal_input':
   e+=round_vertical(8,2.65,14.2,.9,.7,'rubber')
   for x in (6,10):e += [c((x-.5,2.65,13.6),(x+.5,3.1,14.6),'brushed_steel')]
  for z in (4,6,8,10):e += [c((12.5,2.65,z),(13.6,2.85,z+.25),'rubber')]
 variants={}
 for look in ('off','broken'):
  tx={k:'electron:block/scorched' for k in ('enamel','glass','frame')} if look=='broken' else {}
  for face in ('up','down','north','south','west','east'):
   for turn in range(4):
    ref=f'{n}_{look}_{face}_{turn}';write(A/'models/block'/f'{ref}.json',model(transform(e,face,turn),tx));variants[f'facing={face},turn={turn},broken={str(look=="broken").lower()}']={'model':'electron:block/'+ref}
 write(A/'blockstates'/f'{n}.json',{'variants':variants})
 item_e=e
 if n=='display_panel':item_e=e+[c((.7,2.66,.7),(15.3,2.85,15.3),'glass')]+dark_segments(3,1,10,14,2.86)
 elif n in ('decimal_input','numeric_display'):item_e=e+dark_segments(4.5,3.3,6.5,8.5,3.85)
 write(A/'models/item'/f'{n}.json',model(item_e))
 write(D/'loot_table/blocks'/f'{n}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'electron:'+n}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
for n in ('smd_half_adder','smd_display'):
 e=[c((4,6,4),(12,8,12),'ic_package')]
 for x in (4.5,7.5,10.5):
  for z in (3.3,12):e += [c((x,5.8,z),(x+.9,6.6,z+.9),'solder_joint')]
 if n=='smd_display':
  e += [c((4.4,8,4.4),(11.6,8.1,11.6),'glass')]
  e += dark_segments(4.5,4.5,7,7,8.11)
 else:
  e += [c((4.8,8,4.8),(5.6,8.03,5.6),'solder_joint')]
 write(A/'models/item'/f'{n}.json',model(e))
recipe('pcb_design_station',['electron:assembly_table','electron:iron_plate','electron:copper_coil','electron:blank_pcb','minecraft:glass','minecraft:black_dye'])
recipe('smd_half_adder',['electron:smd_xor','electron:smd_and','electron:solder_alloy'])
recipe('smd_display',['electron:smd_led','electron:smd_led','electron:smd_led','electron:smd_nand','electron:smd_nand','electron:copper_foil'])
recipe('numeric_display',['electron:smd_display','electron:pcb_socket','minecraft:glass_pane'])
recipe('decimal_input',['electron:numeric_display','electron:circuit_switch','electron:circuit_switch','electron:copper_foil'])
recipe('display_panel',['electron:smd_display','electron:iron_plate','minecraft:glass','electron:copper_foil'])
for rel in ('mineable/pickaxe','needs_iron_tool'):
 p=R/'src/main/resources/data/minecraft/tags/block'/f'{rel}.json';o=json.loads(p.read_text());o['values']=sorted(set(o['values']+['electron:'+n for n in names]));write(p,o)
mat=Image.new('RGB',(272,211),'#2e4858');d=ImageDraw.Draw(mat)
for x in range(0,272,12):d.line((x,0,x,210),fill='#3b5967')
for y in range(0,211,12):d.line((0,y,271,y),fill='#3b5967')
d.rounded_rectangle((2,2,269,208),radius=8,outline='#7894a3',width=2);mat.save(A/'textures/gui/precision_mat.png')
print('Created split design station, four display/input assets and two miniature packages.')
