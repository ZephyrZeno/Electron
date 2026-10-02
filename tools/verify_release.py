"""Check the two distributable JARs and assemble a reviewable local release folder."""
from pathlib import Path
import hashlib
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
assert not native.findall('.//failure') and native_count==37
assets=json.loads((ROOT/'build/asset-review/validation.json').read_text(encoding='utf-8'))
localization=json.loads((ROOT/'build/localization-verification.json').read_text(encoding='utf-8'))
summary['map']=json.loads((ROOT/'build/academy-package-verification.json').read_text(encoding='utf-8'))
summary['validation']={'junit':sum(int(t.get('tests','0')) for t in tests),'native_gametest':native_count,**assets,**localization,'client_visual':'Offline model previews and worn-head transform verification. Full manual in-client visual acceptance remains incomplete.'}
(ROOT/'build/foundry-release-verification.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
out=ROOT/f'dist/circuit-foundry-{VERSION}'
out.mkdir(parents=True,exist_ok=True)
shutil.copy2(MAIN,out/MAIN.name)
shutil.copy2(ROOT/'build/foundry-release-verification.json',out/'verification.json')
shutil.copy2(ROOT/'build/asset-review/gates.png',out/'gates-preview.png')
shutil.copy2(ROOT/'build/asset-review/equipment.png',out/'equipment-preview.png')
shutil.copy2(ROOT/'docs/design/ELECTRON_THREE_SCALES_V3.md',out/'三层电路设计-v3.md')
shutil.copy2(ROOT/'README.md',out/'玩法与合成说明.md')
(out/'安装说明.txt').write_text(f"""Electron: Circuit Foundry — {VERSION}
Minecraft 1.21.1 / Fabric / Java 21，另需 Fabric API。

把 electron-{VERSION}.jar 放入 mods，并替换旧版 Electron，勿同时装两个主线版本。
主线不依赖、引用或调用独立计算器；该模组未包含在本包中。

44 种方块、11 种工具/附件。大型低压配电、小型电路、红石兼容、工程工具分栏。
生存实时数值只能用万用表 HUD 测量；创造可诊断，铭牌额定参数保留。
新加 MOSFET、隔离继电器、五种固定输出 DC 转换模块、24 V 手摇发电机、电缆、分线箱、1 A 断路器、工程灯与电机。
转换模块固定规格，换标称电压需要合成并替换；没有免费调压界面。输入范围、功率、效率、过载和接线见玩法说明。
128 段线总数上限已取消；每段 32 格、单网络 256 节点、每格导体/几何预算仍保留。
细线 250 mA、电缆 5 A，正极与回线分别接，持续过流会烧断；大负载推荐电缆和断路器。
右键电机切换空载/带载/锁轴；堵转会增大电流，排障后复位断路器。
小型器件板面仍为 3/16，总厚度不超过 4/16；大型电气设备用整格机壳。
红石与电不能互相供电或转换；电控红石触点仅决定已有红石信号是否通过。
16 页手册、12 张教程图各有中英版本；手持放大镜可佩戴，按住 Z 放大。

教学地图单独下载 circuit-academy-{VERSION}.zip，将其中 Circuit_Foundry_Academy 文件夹放入 saves。
地图含九区，和平生存，可拆改，工具与材料箱齐备；样机只有有限储能，须手摇补电。
地图为实际存档，游玩不需要任何地图生成器，也不需要计算器模组。
PCB 小配件、焊接、开发板、交流、多方块输电和外部机械传动仍为后续内容。

验证：{summary['validation']['junit']} 项单元测试、{native_count} 项 Minecraft GameTest、模型/方块状态/双语言检查通过。
教学地图已经保存后重新载入并通电验证。预览图为离线几何渲染或存档数据示意；未完成逐项人工客户端视觉验收和大型多人压力测试。
详见 verification.json、玩法与合成说明.md、三层电路设计-v3.md。
""",encoding='utf-8')
archive=ROOT/f'dist/circuit-foundry-{VERSION}.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for path in sorted(out.iterdir()):z.write(path,path.name)
print(json.dumps(summary,ensure_ascii=False,indent=2))
print('Bundle:',archive)
