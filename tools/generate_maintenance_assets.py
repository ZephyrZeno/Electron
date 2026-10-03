"""Code-native three-dimensional inventory/hand models and bilingual maintenance strings."""
from pathlib import Path
import json
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[1]
A=ROOT/'src/main/resources/assets/electron'
def write(path,value):
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def cube(a,b,texture,rotation=None):
    result={'from':a,'to':b,'faces':{side:{'texture':'#'+texture,'uv':[0,0,16,16]} for side in ('north','south','west','east','up','down')}}
    if rotation:result['rotation']=rotation
    return result
DISPLAY={
    'gui':{'rotation':[25,225,0],'translation':[0,0,0],'scale':[.85,.85,.85]},
    'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.45,.45,.45]},
    'fixed':{'rotation':[0,180,0],'translation':[0,0,0],'scale':[.8,.8,.8]},
    'firstperson_righthand':{'rotation':[0,-35,0],'translation':[0,1,0],'scale':[.7,.7,.7]},
    'firstperson_lefthand':{'rotation':[0,35,0],'translation':[0,1,0],'scale':[.7,.7,.7]},
    'thirdperson_righthand':{'rotation':[10,0,0],'translation':[0,2,0],'scale':[.55,.55,.55]},
    'thirdperson_lefthand':{'rotation':[10,0,0],'translation':[0,2,0],'scale':[.55,.55,.55]},
}
TEXTURES={'rock':'electron:block/uranium_ore','metal':'electron:block/assembled/galvanized','dark':'electron:block/assembled/cast_base','cap':'electron:block/assembled/copper_bus','ceramic':'electron:block/ceramic'}
def model(name,elements,extra=None):
    textures={**TEXTURES,**(extra or {})};textures['particle']=next(iter(textures.values()))
    write(A/'models/item'/f'{name}.json',{'parent':'minecraft:block/block','ambientocclusion':True,'gui_light':'side','textures':textures,'display':DISPLAY,'elements':elements})

rock=[]
for a,b,axis,angle in [([3,3,4],[10,10,11],'z',22.5),([7,4,5],[13,11,12],'y',-22.5),([5,8,6],[11,14,11],'z',-22.5),([2,4,7],[6,8,12],'y',22.5),([9,3,3],[13,8,8],'z',0)]:
    rock.append(cube(a,b,'rock',{'origin':[8,8,8],'axis':axis,'angle':angle}))
model('raw_uranium',rock)

# A stepped cylindrical ceramic pellet, built from non-overlapping strips.
pellet=[]
for x0,x1,z0,z1 in [(3,4.5,5.5,10.5),(4.5,5.5,4.5,11.5),(5.5,10.5,3.5,12.5),(10.5,11.5,4.5,11.5),(11.5,13,5.5,10.5)]:
    pellet.append(cube([x0,3,z0],[x1,12,z1],'dark'))
    pellet.append(cube([x0,12,z0],[x1,12.2,z1],'ceramic'))
model('uranium_pellet',pellet)
for name,spent in [('fuel_rod',False),('spent_fuel_rod',True)]:
    rods=[]
    for x in (5,8,11):
        for z in (5,8,11):
            rods.append(cube([x-.5,2.5,z-.5],[x+.5,13.5,z+.5],'dark' if spent else 'metal'))
            rods.append(cube([x-.56,3,z-.56],[x+.56,3.6,z+.56],'ceramic'))
    for y in (2.2,7.4,13.3):
        for z in (3.8,11.8):rods.append(cube([3.8,y,z],[12.2,y+.5,z+.4],'metal'))
        for x in (3.8,11.8):rods.append(cube([x,y,4.2],[x+.4,y+.5,11.8],'metal'))
    for y in (1.8,13.8):rods.append(cube([5.5,y,5.5],[10.5,y+.4,10.5],'dark' if spent else 'cap'))
    rods+= [cube([6.4,14.2,7.5],[6.9,15,8.5],'metal'),cube([9.1,14.2,7.5],[9.6,15,8.5],'metal'),cube([6.4,14.8,7.5],[9.6,15.3,8.5],'metal')]
    model(name,rods)

# Neutral-blue book cover also removes the old green item appearance.
cover=Image.new('RGB',(64,64),'#29394f');d=ImageDraw.Draw(cover)
d.rectangle((4,4,59,59),outline='#8ea6c4',width=2)
for y in (12,17):d.line((12,y,49,y),fill='#e1e5ec',width=2)
d.line([(13,43),(24,43),(24,30),(44,30),(44,45),(52,45)],fill='#a7c4e8',width=2)
for x,y in ((13,43),(24,30),(44,45)):d.ellipse((x-3,y-3,x+3,y+3),fill='#d7e1ed')
cover.save(A/'textures/block/assembled/manual_cover.png')
paper=Image.new('RGB',(64,64),'#dbd9d1');d=ImageDraw.Draw(paper)
for y in range(2,64,4):d.line((0,y,63,y),fill='#acb0b7')
paper.save(A/'textures/block/assembled/manual_pages.png')
model('engineering_handbook',[
    cube([3,3,5.5],[13,13,6],'cover'),cube([3,3,9.2],[13,13,9.7],'cover'),
    cube([3,3,6],[3.7,13,9.2],'cover'),cube([3.7,3.4,6],[12.6,12.6,9.2],'pages')
],{'cover':'electron:block/assembled/manual_cover','pages':'electron:block/assembled/manual_pages'})

strings={
 'plant.electron.repaired':('Structure repaired; missing parts consumed from your inventory','结构已修复，已从背包扣除缺失部件'),
 'plant.electron.repair_intact':('Structure intact; sneak-use the wrench to dismantle','结构完整；潜行使用扳手可拆解'),
 'plant.electron.repair_materials':('Missing repair parts; the required items are listed in chat','维修材料不足，所需物品已列在聊天栏'),
 'plant.electron.repair_obstructed':('Repair space occupied; remove the obstruction first','维修位置被占用，请先移开阻挡方块'),
 'plant.electron.repair_hot':('Plant still hot; stop it and maintain cooling before servicing','机组尚未冷却，请停机并保持冷却后再维修'),
 'plant.electron.repair_controller_done':('Controller replaced; plant is off until restarted','损坏控制器已更换；机组处于停机状态，请手动重启'),
 'message.electron.repair_material':('Repair requires %s × %s','维修还需要 %s × %s'),
 'message.electron.wrench_help':('Devices: rotate / sneak-rotate back. Multiblocks: repair using carried parts / sneak-dismantle.','器件：旋转／潜行反向旋转；大型设施：使用背包部件维修／潜行拆解。'),
 'tooltip.electron.wrench_turn':('Devices rotate; multiblocks repair. Sneak to dismantle a structure.','旋转器件，修复大型设施；潜行可拆解结构'),
 'subtitles.electron.wrench_turn':('Wrench turns','扳手转动'),
 'guide.electron.multiblocks.p3':('Complete structures form automatically. Breaking a part restores the others and pauses automatic formation. Use a wrench with the missing parts in your inventory to repair; occupied cells are never overwritten. Sneak-use dismantles intentionally. Repair preserves stored fuel and water; a replacement tank starts empty.','结构完整后自动成型。破坏一处会恢复其余材料，并暂停自动成型。背包携带缺失部件后使用扳手修复，维修不会覆盖占用位置。潜行使用扳手可主动拆解。已有燃料和水量保留；补装的新水槽为空槽。'),
}
for locale,i in [('en_us',0),('zh_cn',1)]:
    p=A/'lang'/f'{locale}.json';data=json.loads(p.read_text(encoding='utf-8'));data.update({k:v[i] for k,v in strings.items()});write(p,data)
print('3D fuel items, neutral handbook and maintenance language generated')
