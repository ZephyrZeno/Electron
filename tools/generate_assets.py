"""Generate code-native block models and pixel markings (no external artwork)."""
from pathlib import Path
import json
from PIL import Image, ImageDraw, ImageFont

root = Path(__file__).resolve().parents[1] / 'src/main/resources'
assets = root / 'assets/electron'
names = {'and_gate':('AND','与门'), 'or_gate':('OR','或门'), 'not_gate':('NOT','非门'),
         'xor_gate':('XOR','异或门'), 'nand_gate':('NAND','与非门'), 'nor_gate':('NOR','或非门'),
         'xnor_gate':('XNOR','同或门'), 'buffer_gate':('BUF','缓冲器'), 'binary_memory':('D-FF','二进制存储器')}

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

for name, (label, zh) in names.items():
    for powered in (False, True):
        suffix = '_on' if powered else ''
        img = Image.new('RGB',(32,32),'#263b43')
        d = ImageDraw.Draw(img)
        d.rectangle((1,1,30,30),outline='#63838d',width=2)
        color = '#72f5b4' if powered else '#de9254'
        d.line((16,4,16,10),fill=color,width=3)
        d.polygon([(12,7),(16,3),(20,7)],fill=color)
        unary = name in ('not_gate','buffer_gate','binary_memory')
        if unary: d.line((16,23,16,30),fill='#68baff',width=3)
        else:
            d.line((1,16,6,16),fill='#68baff',width=3)
            d.line((25,16,30,16),fill='#68baff',width=3)
        if name == 'binary_memory':
            d.line((1,16,6,16),fill='#ffe58a',width=3)
            d.line((25,16,30,16),fill='#fa6b79',width=3)
        d.text((16,17), label, fill='white',font=ImageFont.load_default(size=8),anchor='mm')
        path=assets/'textures/block'/f'{name}{suffix}.png'
        path.parent.mkdir(parents=True,exist_ok=True)
        img.save(path)
        write(assets/'models/block'/f'{name}{suffix}.json',{'parent':'minecraft:block/cube_bottom_top','textures':{
            'top':f'electron:block/{name}{suffix}','side':'minecraft:block/smooth_stone','bottom':'minecraft:block/iron_block'}})
    variants={}
    for facing,rotation in [('north',0),('east',90),('south',180),('west',270)]:
        for powered in (False,True):
            variants[f'facing={facing},powered={str(powered).lower()}']={'model':f'electron:block/{name}'+('_on' if powered else ''),'y':rotation}
    write(assets/'blockstates'/f'{name}.json',{'variants':variants})
    write(assets/'models/item'/f'{name}.json',{'parent':f'electron:block/{name}'})
    write(root/'data/electron/loot_table/blocks'/f'{name}.json',{'type':'minecraft:block','pools':[{
        'rolls':1,'entries':[{'type':'minecraft:item','name':f'electron:{name}'}], 'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    write(root/'data/electron/recipe'/f'{name}.json',{'type':'minecraft:crafting_shaped','category':'redstone',
        'pattern':['RTR','SIS','SSS'],'key':{'R':{'item':'minecraft:redstone'},'T':{'item':'minecraft:repeater' if name=='binary_memory' else 'minecraft:redstone_torch'},
        'S':{'item':'minecraft:smooth_stone'},'I':{'item':{'and_gate':'minecraft:iron_ingot','or_gate':'minecraft:copper_ingot','not_gate':'minecraft:gold_ingot','xor_gate':'minecraft:quartz','nand_gate':'minecraft:lapis_lazuli','nor_gate':'minecraft:amethyst_shard','xnor_gate':'minecraft:diamond','buffer_gate':'minecraft:coal','binary_memory':'minecraft:comparator'}[name]}},
        'result':{'id':f'electron:{name}','count':1}})

for locale in ('zh_cn','en_us'):
    lang={'itemGroup.electron.components':'Electron · 电路元件' if locale=='zh_cn' else 'Electron · Circuit Components'}
    lang.update({f'block.electron.{name}':labels[1] if locale=='zh_cn' else ('Binary Memory' if name=='binary_memory' else labels[0]+' Gate') for name,labels in names.items()})
    language_path=assets/'lang'/f'{locale}.json'
    localized=json.loads(language_path.read_text(encoding='utf-8')) if language_path.exists() else {}
    for key,value in lang.items():localized.setdefault(key,value)
    write(language_path,localized)
write(root/'data/minecraft/tags/block/mineable/pickaxe.json',{'replace':False,'values':[f'electron:{name}' for name in names]})
print(f'Generated assets for {len(names)} components')
