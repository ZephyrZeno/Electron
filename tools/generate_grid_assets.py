"""Extend the established cuboid/pixel asset generator for power, mechanics and light materials."""
from pathlib import Path
import runpy, json, random
from PIL import Image, ImageDraw, ImageFont

g=runpy.run_path(str(Path(__file__).with_name('generate_foundry_assets.py')))
A,RES,write,cube,model,BASE=[g[k] for k in ('A','RES','write','cube','model','BASE')]
def craft(name,ingredients,count=1):g['recipe'](name,ingredients,count)

colors=g['COLORS'];names=['red','black','cyan','yellow','green','purple','white','gray']
for i,(name,color) in enumerate(zip(names,colors)):
    for cable in (False,True):
        kind='cable' if cable else 'wire';im=Image.new('RGB',(64,64),color);d=ImageDraw.Draw(im)
        rgb=im.getpixel((0,0));shade=tuple(int(v*.68) for v in rgb);highlight=tuple(min(255,int(v*1.1+18)) for v in rgb)
        for x in range(64):
            factor=.65+.35*(1-abs(x-31.5)/31.5);d.line((x,0,x,63),fill=tuple(int(v*factor) for v in rgb))
        for y in range(-64,128,8 if cable else 16):d.line((0,y,63,y+24),fill=shade,width=2 if cable else 1)
        for y in range(4,64,16):d.line((8,y,15,y),fill=highlight,width=1)
        im.save(A/'textures/block'/f'{kind}_{i}.png')
        write(A/'models/item'/f'{kind}_spool_{name}.json',{'parent':f'electron:item/{kind}_spool_{i}'})
        ingredients=['copper_ingot']*(4 if cable else 2)+['paper']*2+['string' if cable else 'stick','iron_ingot' if cable else 'oak_planks',name+'_dye']
        craft(kind+'_spool_'+name,ingredients)
        if cable:
            write(RES/'data/electron/recipe'/f'cable_spool_128_{name}.json',{'type':'minecraft:crafting_shapeless','category':'redstone','ingredients':[{'item':'minecraft:'+x} for x in ['copper_block']*4+['paper','string','iron_ingot',name+'_dye']],'result':{'id':'electron:cable_spool_'+name,'count':1,'components':{'minecraft:custom_data':{'units':2048}}}})
# Legacy recipe IDs remain resolvable, but now yield the separately registered red item.
for old in ['wire_spool','cable_spool','cable_spool_128']:
    path=RES/'data/electron/recipe'/f'{old}.json';obj=json.loads(path.read_text());obj['result']['id']='electron:'+('wire_spool_red' if old=='wire_spool' else 'cable_spool_red');write(path,obj)

for name in ('flood_lens_on','flood_lens_off'):
    im=Image.new('RGB',(64,64),'#fff4d3' if name.endswith('_on') else '#54616b');d=ImageDraw.Draw(im)
    for y in range(0,64,8):
        for x in range(0,64,8):d.rectangle((x+1,y+1,x+6,y+6),fill='#fffdf1' if name.endswith('_on') else '#7a858d',outline='#d9d9c4' if name.endswith('_on') else '#313d45')
    im.save(A/'textures/block'/f'{name}.png')
# labPBR alpha 254 is fully emissive; 255 is explicitly non-emissive.
# https://shaderlabs.org/wiki/LabPBR_Material_Standard
for name in ['flood_lens_on','led_red_on','lamp_on','lamp_x']:
    p=A/'textures/block'/f'{name}.png';im=Image.open(p).convert('RGBA');im.save(p.with_stem(name+'_e'))
    Image.new('RGBA',im.size,(100,10,0,254)).save(p.with_stem(name+'_s'))
for name in ['flood_lens_off','led_red_off','lamp_off','scorched']:
    p=A/'textures/block'/f'{name}.png';size=Image.open(p).size;Image.new('RGBA',size,(80,10,0,255)).save(p.with_stem(name+'_s'))

def plain(name,elements,tx=BASE):
    model(name,elements,tx);write(A/'blockstates'/f'{name}.json',{'variants':{'':{'model':'electron:block/'+name}}});write(A/'models/item'/f'{name}.json',{'parent':'electron:block/'+name})
for name in ('plant_casing','reactor_casing'):
    els=[cube((0,0,0),(16,16,16),'case')]
    for x in (1,13):
        for z in (1,13):els.append(cube((x,15.8,z),(x+2,16,z+2),'silver'))
    for y in (1,14):
        for z in (0,15.8):els.append(cube((.2,y,z),(15.8,y+1,z+.2),'lamp_x' if name=='reactor_casing' else 'trim'))
    if name=='reactor_casing':els.append(cube((5,5,-.05),(11,11,.02),'lamp_x'))
    plain(name,els)
for name,table in [('transmission_shaft',False),('rotating_platform',True)]:
    bearing=[cube((4,3,4),(12,6,12),'case'),cube((4.5,3,4.5),(11.5,6.2,11.5),'trim'),cube((6,0,6),(10,3,10),'rubber')]
    model(name,bearing,BASE)
    variants={f'facing={f}':{'model':'electron:block/'+name,**rot} for f,rot in [('up',{}),('down',{'x':180}),('north',{'x':90}),('south',{'x':90,'y':180}),('east',{'x':90,'y':90}),('west',{'x':90,'y':270})]}
    write(A/'blockstates'/f'{name}.json',{'variants':variants})
    inventory=bearing+[cube((7,0,7),(9,16,9),'silver')]+([cube((1.5,12,1.5),(14.5,14.5,14.5),'trim')] if table else [])
    model(name+'_item',inventory,BASE,True);write(A/'models/item'/f'{name}.json',{'parent':'electron:item/'+name+'_item'})

g['texture']('uranium_ore','#414742','');p=A/'textures/block/uranium_ore.png';im=Image.open(p);d=ImageDraw.Draw(im);rng=random.Random(185)
for _ in range(24):
    x,y=rng.randrange(60),rng.randrange(60);d.polygon([(x,y),(x+4,y+1),(x+3,y+5),(x+1,y+3)],fill='#acaf62',outline='#69683c')
im.save(p);plain('uranium_ore',[cube((0,0,0),(16,16,16),'case')],{'case':'electron:block/uranium_ore'})
for name,color in [('raw_uranium','#a9ac66'),('uranium_pellet','#777c4b'),('fuel_rod','#e1ca6a'),('spent_fuel_rod','#91664e')]:
    im=Image.new('RGBA',(64,64));d=ImageDraw.Draw(im)
    if name.endswith('rod'):
        for x in (17,25,33,41):d.rounded_rectangle((x,5,x+5,58),radius=2,fill=color,outline='#c5cbd0')
        for y in (7,26,49):d.rectangle((14,y,49,y+4),fill='#53616e',outline='#b9c6d0')
    else:d.polygon([(9,27),(18,10),(43,8),(55,26),(47,50),(23,57),(7,44)],fill=color,outline='#ccd19d')
    im.save(A/'textures/item'/f'{name}.png');write(A/'models/item'/f'{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':'electron:item/'+name}})

recipes={
 'plant_casing':['iron_ingot']*4+['copper_ingot']*2,
 'reactor_casing':['electron:plant_casing','obsidian','obsidian','iron_ingot','iron_ingot'],
 'transmission_shaft':['iron_ingot','iron_ingot','copper_ingot'],
 'rotating_platform':['electron:transmission_shaft','iron_ingot','iron_ingot','smooth_stone'],
 'thermal_generator':['electron:dc_generator','furnace','copper_ingot','iron_ingot','electron:plant_casing'],
 'hydro_generator':['electron:dc_generator','bucket','iron_ingot','iron_ingot','electron:plant_casing'],
 'wind_generator':['electron:dc_generator','iron_ingot','iron_ingot','iron_ingot','electron:plant_casing'],
 'lightning_collector':['lightning_rod','electron:capacitor_100mf','electron:capacitor_100mf','electron:plant_casing','copper_block'],
 'nuclear_reactor':['electron:thermal_generator','electron:reactor_casing','electron:reactor_casing','diamond','quartz'],
 'substation':['electron:plant_casing','copper_block','copper_block','iron_block','quartz','quartz'],
 'mains_inlet':['electron:plant_casing','electron:dc_breaker','electron:multimeter','copper_ingot'],
 'fuel_rod':['electron:uranium_pellet']*4+['iron_ingot','iron_ingot','copper_ingot']}
for name,ingredients in recipes.items():craft(name,ingredients,4 if name=='transmission_shaft' else 1)
write(RES/'data/electron/recipe/uranium_pellet.json',{'type':'minecraft:smelting','category':'misc','ingredient':{'item':'electron:raw_uranium'},'result':{'id':'electron:uranium_pellet'},'experience':.7,'cookingtime':400})
extras=['plant_casing','reactor_casing','transmission_shaft','rotating_platform','uranium_ore']
for name in extras:
    entry={'type':'minecraft:item','name':'electron:'+('raw_uranium' if name=='uranium_ore' else name)}
    write(RES/'data/electron/loot_table/blocks'/f'{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[entry],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
tag=RES/'data/minecraft/tags/block/mineable/pickaxe.json';obj=json.loads(tag.read_text());obj['values']+=['electron:'+n for n in extras];write(tag,obj)
write(RES/'data/minecraft/tags/block/needs_iron_tool.json',{'replace':False,'values':['electron:uranium_ore']})
write(RES/'data/electron/worldgen/configured_feature/uranium_ore.json',{'type':'minecraft:ore','config':{'size':4,'discard_chance_on_air_exposure':.3,'targets':[{'target':{'predicate_type':'minecraft:tag_match','tag':'minecraft:stone_ore_replaceables'},'state':{'Name':'electron:uranium_ore'}},{'target':{'predicate_type':'minecraft:tag_match','tag':'minecraft:deepslate_ore_replaceables'},'state':{'Name':'electron:uranium_ore'}}]}})
write(RES/'data/electron/worldgen/placed_feature/uranium_ore.json',{'feature':'electron:uranium_ore','placement':[{'type':'minecraft:count','count':3},{'type':'minecraft:in_square'},{'type':'minecraft:height_range','height':{'type':'minecraft:uniform','min_inclusive':{'absolute':-48},'max_inclusive':{'absolute':16}}},{'type':'minecraft:biome'}]})
for p in (RES/'data/electron/recipe').glob('*.json'):
    write(RES/'data/electron/advancement/recipes'/p.name,{'parent':'minecraft:recipes/root','criteria':{'copper':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['minecraft:copper_ingot']}]}}},'requirements':[['copper']],'rewards':{'recipes':['electron:'+p.stem]}})
print('Grid, independent colors, mechanical models, ores and emissive materials generated.')

# Current multiblock assets are applied after the earlier grid catalog.
runpy.run_path(str(Path(__file__).with_name("generate_assembly_assets.py")))
runpy.run_path(str(Path(__file__).with_name("generate_industrial_assets.py")))
runpy.run_path(str(Path(__file__).with_name("generate_industrial_guide.py")))
runpy.run_path(str(Path(__file__).with_name("generate_fault_assets.py")))
runpy.run_path(str(Path(__file__).with_name("generate_fault_guide.py")))
runpy.run_path(str(Path(__file__).with_name("generate_service_assets.py")))
runpy.run_path(str(Path(__file__).with_name("generate_maintenance_assets.py")))
runpy.run_path(str(Path(__file__).with_name("generate_precision_materials.py")))
runpy.run_path(str(Path(__file__).with_name("generate_processing_assets.py")))
runpy.run_path(str(Path(__file__).with_name("generate_lighting_assets.py")))
