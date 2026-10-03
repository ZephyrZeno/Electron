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
            for device in ('industrial_assembly_table','water_pump','wind_base','wind_mast','cable_breakout','low_profile_socket','plant_fuel_port','iron_pipe','glass_pipe','pipe_valve','boiler_water_tank','reactor_coolant_tank'):
                assert f'assets/electron/blockstates/{device}.json' in names
                assert f'data/electron/recipe/{device}.json' in names
            for module in ('boiler_module','turbine_module','alternator_module','condenser_module','plant_control_module','reactor_vessel_module','control_rod_drive','safety_valve_module'):
                assert f'assets/electron/models/item/{module}.json' in names
                assert f'data/electron/recipe/{module}.json' not in names, 'Industrial modules must be assembled in the workshop'
            for device in ('ore_crusher','centrifuge','electric_kiln','fuel_fabricator','workshop_lamp','yard_lamp'):
                assert f'assets/electron/blockstates/{device}.json' in names and f'data/electron/recipe/{device}.json' in names
            for item in ('raw_uranium','uranium_pellet','fuel_rod','spent_fuel_rod','crushed_uranium','fuel_concentrate','uranium_tailings','fuel_cladding','iron_plate','copper_coil'):
                assert json.loads(jar.read(f'assets/electron/models/item/{item}.json')).get('elements'), (item,'Expected solid model')
            assert not any(f'data/electron/recipe/{item}.json' in names for item in ('uranium_pellet','fuel_rod')), 'Powered processing must not have legacy recipe bypasses'
            assert 'top/lacrus/zephyrzeno/electron/industrial/ProcessingEntity.class' in names
            for device in ('precision_workbench','pcb_socket','uv_exposure_unit','pcb_drill','pcb_etcher','solder_mask_applicator','immersion_gold_line','water_level_sensor','temperature_sensor','emergency_lamp'):
                assert f'assets/electron/blockstates/{device}.json' in names and f'data/electron/recipe/{device}.json' in names
            for item in ('assembled_pcb','pcb_photomask','exposed_pcb','drilled_pcb','etched_pcb','masked_pcb','gold_finished_pcb','smd_r1k','smd_r10k','smd_led','smd_not','smd_and','smd_or','smd_xor','smd_nand'):
                assert json.loads(jar.read(f'assets/electron/models/item/{item}.json')).get('elements'), (item,'Expected miniature solid model')
            for item in ('assembled_pcb','pcb_photomask','exposed_pcb','drilled_pcb','etched_pcb','masked_pcb','gold_finished_pcb'):
                assert f'data/electron/recipe/{item}.json' not in names, (item,'PCB processing must not have a crafting bypass')
            for cls in ('PcbLayout','PcbCircuit','PcbBenchEntity','PcbSocketEntity','PcbScreenHandler','PcbFabrication','PcbNetworking'):
                assert f'top/lacrus/zephyrzeno/electron/pcb/{cls}.class' in names
            assert 'assets/electron/textures/gui/precision_mat.png' in names
            for machine in ('pcb_drill','immersion_gold_line'):
                assert f'assets/electron/models/block/{machine}_mechanism.json' in names

            sounds=json.loads(jar.read('assets/electron/sounds.json'))
            for event in ('wire_connect','cable_connect','wire_cut','terminal_attach','machine_assemble','machine_form','pump_run','valve_turn','water_fill','motor_run','wind_rotor','hydro_run','turbine_run','plant_alarm','pressure_burst'):
                assert event in sounds and jar.read(f'assets/electron/sounds/{event}.ogg').startswith(b'OggS')
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
summary['validation']={'junit':sum(int(t.get('tests','0')) for t in tests),'native_gametest':native_count,**assets,**localization,'client_visual':'Offline model previews and native light propagation tests. Multiblock geometry exported from native tests; no Computer Use or in-game visual acceptance.'}
audio_report=json.loads((ROOT/'build/industrial-audio-verification.json').read_text(encoding='utf-8'))
assert audio_report['decoded'] and not audio_report['clipped'] and len(audio_report['original_ogg_samples'])==23
summary['audio']=audio_report
summary['static_meshes']=json.loads((ROOT/'build/gametest-runs/assembly-render-audit.json').read_text())
shader_report=ROOT/'build/shader-rollback-verification.json'
if shader_report.exists():summary['local_shader_rollback']=json.loads(shader_report.read_text(encoding='utf-8'))
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
shutil.copy2(ROOT/'build/asset-review/assemblies.png',out/'assembled-preview.png')
shutil.copy2(ROOT/'build/asset-review/industrial.png',out/'industrial-preview.png')
shutil.copy2(ROOT/'docs/design/MULTIBLOCKS_V5.md',out/'多方块与高压-v5.md')
shutil.copy2(ROOT/'docs/design/INDUSTRIAL_PLANTS_V6.md',out/'工业机组与供水-v6.md')
shutil.copy2(ROOT/'docs/design/FAULTS_WATER_DETAIL_V7.md',out/'故障取水与模型-v7.md')
shutil.copy2(ROOT/'docs/design/PLANT_OPERATIONS_V8.md',out/'机组操作与装配-v8.md')
shutil.copy2(ROOT/'build/asset-review/service.png',out/'service-preview.png')
shutil.copy2(ROOT/'build/asset-review/arcs.png',out/'arc-preview.png')
shutil.copy2(ROOT/'docs/design/ELECTRICAL_GAMEPLAY_V9.md',out/'用电与加工-v9.md')
shutil.copy2(ROOT/'build/asset-review/processing.png',out/'processing-preview.png')
shutil.copy2(ROOT/'build/asset-review/materials.png',out/'material-preview.png')
shutil.copy2(ROOT/'build/asset-review/pcb-workshop.png',out/'pcb-workshop-preview.png')
shutil.copy2(ROOT/'docs/design/PCB_WORKSHOP_V10.md',out/'PCB工坊与联锁-v10.md')
(out/'安装说明.txt').write_text(f"""Electron: Circuit Foundry — {VERSION}
Minecraft 1.21.1 / Fabric / Java 21，另需 Fabric API。

把 electron-{VERSION}.jar 放入 mods，并替换旧版 Electron，勿同时装两个主线版本。
主线不依赖、引用或调用独立计算器；该模组未包含在本包中。

本版新增俯视防静电工作台、拖动微型配件与自定义板形、光绘→曝光→钻孔→蚀刻清洗→阻焊→沉金→焊接的耗电制板流程。六端口底座接入实际电路网络；新增水位开关、温度联锁和蓄电应急灯。新机器使用后侧矮接线盒、分体细节模型与加工动件；旧加工机将长柱收进接线柜并保留接线坐标。当前工序、配方、教程与限制见《PCB工坊与联锁-v10.md》。
此前修复手册偏绿、风机检修门穿模，增加按材料维修成型设施。新增四台 24 V 耗电加工机、工坊防护灯和庭院柱灯；铀矿、燃料及加工材料使用立体模型。提高机组部件制作门槛，移除工作台燃料棒和熔炉芯块捷径。当前玩法和清单见《用电与加工-v9.md》，大型结构坐标以《机组操作与装配-v8.md》为准。
细化 21 类大型材质与 9 类小型器件材质，管道使用圆管和法兰。静态模型缓存并在远处简化细节；电路端口接触使用相邻位置索引。新增加工动件和定位运行、完成音效。没有以网格减少量冒充帧率实测。
此前新增：有真实起止坐标的分叉电弧、定位音效、过热及熔断反馈、局部闪光、触电支路、原版动态容器水、按层减少的原版水域抽水、矮型双母线插排及电站模型细化。保留工业机组装配，原创声音共23段。
主要样机改由真实风机持续供电，入口架空线接入负载。5 V 手摇课程仍需补能，电容需按按钮，故障区需手摇启动。
普通器件放在平滑石上；风机使用独立白色塔筒与底部出线柜，引雷使用桁架塔。当前精确建造坐标与加料规则见《机组操作与装配-v8.md》。旧版小型火电、核电及桁架风机需要先拆解、再按新结构重建。
成型火电 11 kV、水电 6.6 kV、核电 24 kV，使用固定绕组降到 240 V、24 V；雷击为 300 MV / 30 kA 短时事件，仅回收有限能量。网络使用直流等效模型；无免费市电、无免费调压，不模拟交流相位或核反应物理。
八种颜色的导线和八种颜色的电缆均为独立物品；旧线轴与已付费长度保留。
电缆加粗并采用贴图，跨线桥加高；架空及垂直悬线按实际电压检查净距，240 V为0.45格，6.6/11/24 kV为1.2/1.6/2.4格；详见故障取水与模型-v7.md。
工程灯在额定供电时发出原版 15 级光照；光源增加 labPBR 发光材质。断电和损坏时熄灭。
不添加光影包。此前 Electron Lights 副本已备份移出 shaderpacks，原包保持不变。
29页手册、25张教程图各有中英版本。生存实时电学数值只通过万用表HUD测量。水槽和燃料有限；失水可能停机、报警、损坏并产生局部压力爆炸。
主线不依赖计算器。已实现单面 PCB 制作与焊接；双面过孔、可编程开发板、板上升降压芯片及三相交流尚未实现。开发板后续模拟游戏内功能，不兼容真实STM32固件。

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
