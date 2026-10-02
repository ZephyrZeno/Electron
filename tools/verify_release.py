"""Check the two distributable JARs and assemble a reviewable local release folder."""
from pathlib import Path
import hashlib
import re
import json
import shutil
import zipfile
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
properties=dict(line.split('=',1) for line in (ROOT/'gradle.properties').read_text(encoding='utf-8').splitlines() if '=' in line)
VERSION=properties['mod_version']
MAIN=ROOT/f'build/libs/electron-{VERSION}.jar'
CALC=ROOT/'calculator/build/libs/electron-calculator-0.2.0-alpha.4.jar'
summary={}
for label,path,modid,forbidden in [('main',MAIN,'electron',b'top/lacrus/zephyrzeno/electroncalculator'),('calculator',CALC,'electron_calculator',b'top/lacrus/zephyrzeno/electron/')]:
    with zipfile.ZipFile(path) as jar:
        names=jar.namelist();meta=json.loads(jar.read('fabric.mod.json'))
        assert meta['id']==modid
        assert not any(n.endswith('GameTests.class') or '/electron/test/' in n or '/electron/showcase/' in n or n.startswith('data/electron/structure/') for n in names)
        for n in names:
            if n.endswith('.class'):assert forbidden not in jar.read(n),(label,n,'Cross-mod class dependency')
        if label=='main':
            assert meta['version']==VERSION
            assert not any('ConverterSettings' in n or 'ConverterScreen' in n or 'insulated_screwdriver' in n or 'trimmer_potentiometer' in n for n in names),'Unreleased free-setting module design must not be packaged'
            assert not any('RedstoneInterface' in n or '/redstone_input' in n or '/redstone_output' in n for n in names),'Obsolete redstone/electric converter included'
            for device in ('rechargeable_battery','terminal_strip','electrical_redstone_switch','power_mosfet','electrical_relay','boost_converter','boost_converter_12','buck_converter','buck_converter_3v3','buck_converter_12','dc_generator','distribution_box','dc_breaker','engineering_lamp','dc_motor'):
                assert f'assets/electron/blockstates/{device}.json' in names
                assert f'data/electron/recipe/{device}.json' in names
            for device in ('thermal_generator','hydro_generator','wind_generator','lightning_collector','nuclear_reactor','substation','mains_inlet','transmission_shaft','rotating_platform'):
                assert f'assets/electron/blockstates/{device}.json' in names
                assert f'data/electron/recipe/{device}.json' in names
            for item in ('CircuitToolItem','ComponentItem','MagnifierItem'):
                assert f'top/lacrus/zephyrzeno/electron/item/{item}.class' in names
                assert not any(f'/{old}/{item}.class' in n for old in ('circuit','foundry') for n in names)
            assert 'electron_calculator' not in meta.get('depends',{})
            assert not any(n.endswith(('.ebp','.startup')) or 'BluePrint' in n or 'electron_calculator' in n for n in names)
            for file in (ROOT/'src/main/resources').rglob('*'):
                if not file.is_file() or file.name=='fabric.mod.json':continue
                packaged=jar.read(file.relative_to(ROOT/'src/main/resources').as_posix())
                if file.name=='electron.mixins.json':
                    # Loom adds a refmap and reformats this JSON during remapping.
                    config=json.loads(packaged)
                    refmap=config.pop('refmap')
                    assert refmap=='electron-refmap.json' and refmap in names
                    assert config==json.loads(file.read_text(encoding='utf-8')),file
                else:assert packaged==file.read_bytes(),file
        else:
            assert 'electron' not in meta.get('depends',{})
            for name in ['calculator.ebp','calculator-legacy.ebp','calculator.startup']:
                key='data/electron_calculator/'+name
                assert jar.read(key)==(ROOT/'calculator/src/main/resources'/key).read_bytes()
        summary[label]={'file':str(path),'mod_id':modid,'version':meta['version'],'bytes':path.stat().st_size,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'isolated':True}

tests=[ET.parse(p).getroot() for p in (ROOT/'build/test-results/test').glob('TEST-*.xml')]
assert tests and all(int(t.get('failures','0'))==0 and int(t.get('errors','0'))==0 for t in tests)
native=ET.parse(ROOT/'build/foundry-gametest-results.xml').getroot()
native_count=len(native.findall('.//testcase'))
assert not native.findall('.//failure') and native_count==sum(len(re.findall(r'@GameTest\(',p.read_text(encoding='utf-8'))) for p in (ROOT/'src/gametest/java').rglob('*.java'))
assets=json.loads((ROOT/'build/asset-review/validation.json').read_text(encoding='utf-8'))
localization=json.loads((ROOT/'build/localization-verification.json').read_text(encoding='utf-8'))
summary['map']=json.loads((ROOT/'build/academy-package-verification.json').read_text(encoding='utf-8'))
summary['validation']={'junit':sum(int(t.get('tests','0')) for t in tests),'native_gametest':native_count,**assets,**localization,'client_visual':'Offline model previews and native light propagation tests. Shader mappings audited; per-pack visual acceptance remains incomplete.'}
shader_report=ROOT/'build/shader-compat-verification.json'
if shader_report.exists():summary['local_shader_compatibility']=json.loads(shader_report.read_text(encoding='utf-8'))
(ROOT/'build/foundry-release-verification.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
out=ROOT/f'dist/circuit-foundry-{VERSION}'
out.mkdir(parents=True,exist_ok=True)
shutil.copy2(MAIN,out/MAIN.name)
shutil.copy2(ROOT/'build/foundry-release-verification.json',out/'verification.json')
shutil.copy2(ROOT/'build/asset-review/gates.png',out/'gates-preview.png')
shutil.copy2(ROOT/'build/asset-review/equipment.png',out/'equipment-preview.png')
shutil.copy2(ROOT/'build/asset-review/towers.png',out/'tower-preview.png')
shutil.copy2(ROOT/'docs/design/ELECTRON_THREE_SCALES_V3.md',out/'三层电路设计-v3.md')
shutil.copy2(ROOT/'README.md',out/'玩法与合成说明.md')
shutil.copy2(ROOT/'docs/design/GRID_AND_MECHANICS_V4.md',out/'大型供电与传动-v4.md')
shutil.copy2(ROOT/'build/asset-review/grid.png',out/'grid-preview.png')
(out/'安装说明.txt').write_text(f"""Electron: Circuit Foundry — {VERSION}
Minecraft 1.21.1 / Fabric / Java 21，另需 Fabric API。

把 electron-{VERSION}.jar 放入 mods，并替换旧版 Electron，勿同时装两个主线版本。
主线不依赖、引用或调用独立计算器；该模组未包含在本包中。

本版新增：火电、水电、风电、引雷、核电、市电进线、240→24 V 变电、传动轴与旋转夹持台。
主要样机改由真实风机持续供电，入口架空线接入负载。5 V 手摇课程仍需补能，电容需按按钮，故障区需手摇启动。
普通器件放在平滑石上；风机与引雷装置放在玩家搭建的塔架上。详细结构与接线见《大型供电与传动-v4.md》。
大型网络使用 240 V 整流直流模型；无免费市电、无免费调压，不模拟交流相位或核反应物理。
八种颜色的导线和八种颜色的电缆均为独立物品；旧线轴与已付费长度保留。
电缆加粗并采用贴图，跨线桥加高；240 V 架空线离地或障碍不足两格会漏电放电。
工程灯在额定供电时发出原版 15 级光照；光源增加 labPBR 发光材质。断电和损坏时熄灭。
本机光影兼容工具生成 Electron Lights 副本，保留原光影；第三方光影不随本发行包分发。
18 页手册、14 张教程图各有中英版本。生存实时数值仍只通过万用表 HUD 测量。
主线不依赖计算器。PCB 制板、焊接、开发板、三相交流等尚未实现。

教学地图：解压 circuit-academy-{VERSION}.zip，将 Circuit_Foundry_Academy 放入 saves。
旧玩家存档不会被覆盖。生成器仅用于开发期导出，不进入主线发行 JAR。

验证：{summary['validation']['junit']} 项单元测试、{native_count} 项 Minecraft GameTest、模型/方块状态/双语言检查通过。
教学地图已经保存后重新载入并通电验证。预览图为离线几何渲染或存档数据示意；未完成逐项人工客户端视觉验收和大型多人压力测试。
详见 verification.json、玩法与合成说明.md、三层电路设计-v3.md。
""",encoding='utf-8')
archive=ROOT/f'dist/circuit-foundry-{VERSION}.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for path in sorted(out.iterdir()):z.write(path,path.name)
print(json.dumps(summary,ensure_ascii=False,indent=2))
print('Bundle:',archive)
