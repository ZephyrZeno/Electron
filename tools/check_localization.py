"""Validate client-resolved messages, bilingual captions and worn magnifier orientation."""
from pathlib import Path
import json,re,math
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/electron'
zh=json.loads((ASSETS/'lang/zh_cn.json').read_text(encoding='utf-8'))
en=json.loads((ASSETS/'lang/en_us.json').read_text(encoding='utf-8'))
assert zh.keys()==en.keys(),('Locale key mismatch',zh.keys()-en.keys(),en.keys()-zh.keys())
for key,value in zh.items():
    assert isinstance(value,str) and isinstance(en[key],str) and value and en[key],key
    assert re.findall(r'%(?:\d+\$)?s',value)==re.findall(r'%(?:\d+\$)?s',en[key]),(key,'Placeholder mismatch')
    assert not re.search(r'[\u3400-\u9fff]',en[key]),(key,'Chinese text in English locale')
for path in (ROOT/'src/main/java').rglob('*.java'):
    code=path.read_text(encoding='utf-8')
    assert not re.search(r'[\u3400-\u9fff]',code),('Hardcoded Chinese remains',path)
    for key in re.findall(r'"((?:message|error|tooltip|hud|guide|pin|reading|color|direction|itemGroup|block|item|key|meter|motor|map|screen|assembly|plant|plant_ui|container|subtitles)\.electron\.[a-z_0-9.]+)"',code):
        if not key.endswith(('.', '_')):assert key in en,(path,key)
    for suffix in re.findall(r'line\(lines,"([a-z_0-9]+)"',code):
        if not suffix.endswith('_'):assert 'tooltip.electron.'+suffix in en,suffix
for page in ['first_light','series_parallel','timing','power_modules','redstone_switch','wiring','wall_ports','measurement','logic','control','conversion','distribution','crafting','protection','precision','scales','power_grid','mechanics','multiblocks','high_voltage','industrial_build','fluids','processing','nuclear_site','wind_and_wires','faults']:
    for suffix in ['title','subtitle','p0','p1','p2','p3']:assert f'guide.electron.{page}.{suffix}' in en
for locale in ('zh_cn','en_us'):
    for name in ('first_light','series_parallel','rc_timing','power_modules','redstone_switch','manual_wiring','gate_pins','protection','bridge_tap','meter_probes','control_power','dc_conversion','power_grid','mechanics','multiblocks','high_voltage','industrial_build','fluids','processing','nuclear_site','wind_and_wires','faults'):
        with Image.open(ASSETS/f'textures/gui/tutorial/{locale}/{name}.png') as image:assert image.size==(960,320)
visor=json.loads((ASSETS/'models/item/magnifying_visor.json').read_text(encoding='utf-8'))
head=visor['display']['head'];assert head['rotation'][0]==head['rotation'][2]==0
angle=math.radians(head['rotation'][1]);lens=[e for e in visor['elements'] if e['faces']['north']['texture']=='#glass']
assert len(lens)==2
for e in lens:
    x=((e['from'][0]+e['to'][0])/2-8)/16;z=((e['from'][2]+e['to'][2])/2-8)/16
    # Vanilla HeadFeatureRenderer applies Y=180 then (0.625,-0.625,-0.625).
    # Their combined depth sign is positive. An extra model Y=180 puts lenses behind the head.
    world_z=(-math.sin(angle)*x+math.cos(angle)*z)*head['scale'][2]*.625
    assert world_z<-.25,('Lens is behind or inside the head',world_z)
result={'locale_keys':len(en),'locales':['en_us','zh_cn'],'localized_tutorial_images':44,'java_hardcoded_chinese':0,'worn_lenses_in_front':True}
out=ROOT/'build/localization-verification.json';out.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result,indent=2))
