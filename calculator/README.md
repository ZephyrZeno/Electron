# Electron Calculator

Minecraft **1.21.1 / Fabric / Java 21** 独立原版红石计算器生成模组，版本 **0.2.0-alpha.4**。Mod ID 为 `electron_calculator`，Java 包为 `top.lacrus.zephyrzeno.electroncalculator`。本工程不依赖主线 Electron，可以单独安装，也可与主线共存。

默认生成器已更新为**原版按钮操作台与大型立式红石灯屏**，沿用已有十进制四则运算核心。按数字、运算符、数字、等号完成计算；输入存储、执行控制、定时、运算和显示均由世界中的原版红石方块完成。此版体积大、等待长，请以“输入就绪”灯为操作依据。

主线电线、器件与逻辑门见 [主线说明](../README.md)，本工程只负责原版计算器。

## 安装

用 build/libs/electron-calculator-0.2.0-alpha.4.jar 安装到 mods 中；如果之前使用旧的合并版 Electron，请换成新版主线和本独立 JAR，同时安装适用于 Minecraft 1.21.1 的 Fabric Loader 和 Fabric API，使用 Java 21。测试代码、测试结构、测试存档不进入发行 JAR。

## 生成命令

需要权限等级 2（单人启用作弊或服务器 OP）。

~~~mcfunction
/electron calculator preview
/electron calculator build x y z
/electron calculator repair x y z
/electron status
/electron calculator cancel
~~~

坐标是结构起点，向 **+X、+Z** 延伸。当前蓝图为 **515×209×256**，含 **677,866 个原版方块**，另加中文告示牌。主世界起点 Y 不得高于 **111**。操作台在前方右侧，结果屏面向 −Z；屏幕正面为 **105×76**，有实体边框、厚边和支架。旧平面灯屏及专用支线已清理，部分共用线段保留为普通羊毛支撑。

生成器逐批检查整个长方体区域为空气，随后放置、初始化空白状态、激活线路；每刻最多处理 512 个放置/激活位置，初始化阶段最多处理 4,096 个位置。区域必须全加载；取消或中途受阻会保留已生成部分。

已有 `0.2.0-alpha.3` 按钮计算器用 `/electron calculator repair x y z` 修复，坐标填写原生成起点。修复会清空当前输入和结果，删除旧灯屏及废弃支线，并恢复空白状态；先核对原蓝图位置，发现其他方块时停止。原版结构不会仅因更换 JAR 自动更新。更早的拉杆计算器不属于此修复命令的匹配结构。

运行时需要保持整个电路在可运行红石的区块中。起点与区块边界对齐时覆盖 33×16 个区块，普通模拟距离不能在操作台处覆盖全机。以下是在空中区域生成的例子；每个强制加载命令不超过 256 个区块：

~~~mcfunction
/forceload add 0 0 255 255
/forceload add 256 0 511 255
/forceload add 512 0 514 255
/electron calculator build 0 100 0
~~~

停止使用后可分别用对应的 /forceload remove 命令解除加载，结构会保留。以上示例需要三个区域都保持加载。

## 按键与结果

生成或修复完成后等待“输入就绪”灯亮，可以直接输入第一题，无需先按 C。之后**每次按一个键，等待就绪，再按下一键**。告示牌是固定说明，只有对应灯亮才表示正在处理或输入错误。

~~~text
1 → + → 1 → =             结果：0002
1 → 2 → × → 3 → =         结果：0036
9 → − → 1 → 2 → =         结果：0003，负数灯亮
1 → ÷ → 6 → =             结果：00.17
9 → 9 → × → 9 → 9 → =     结果：9801
~~~

- 甲、乙各输入 00–99，数字按一次追加一位，允许前导零。
- +、−、×、÷ 选择运算；乙数尚为空时可替换运算符。
- = 保存本次执行快照并启动运算。缺少甲数、乙数或运算符时亮输入错误灯。
- C 清空输入与执行快照，启动零输入计算；← 删除末位，乙数为空时撤销运算符。
- 第三位、非法阶段选择和重叠按键会被拒绝并亮输入错误灯。忙时按键不接受、不排队。
- 完成后按数字开始新表达式；正常输入期间保留前次结果。

上方显示两个操作数及运算选择灯，下方四位七段屏显示结果，保留前导零。负数用独立指示灯加绝对值表示。除法四舍五入到两位小数，除零亮运算错误灯。忙灯亮时可能出现中间值，以就绪后的结果为准。

结果存入 14 位原版锁存寄存器，负号、错误与小数点另外保存。一排二进制灯左高右低；除法存放放大 100 倍的整数，例如 00.17 对应 17。这是结果寄存器，不是通用可寻址 RAM，也没有独立保存/读回按钮。

保守事务预算为 **33,474 游戏刻**，20 TPS 下约 **27 分 54 秒**，不含服务器卡顿。该预算覆盖按键时序与完整运行，是上界估算，不是每次按键或运算的实测耗时。中继器时序采用较长等待，原版红石更新也可能降低服务器 TPS。

原生测试中，普通数字/运算符按键等待全部计划更新结束用了约 **15,436–15,700 游戏刻**，有效等号约 **26,784 游戏刻**，清空约 **26,810 游戏刻**。20 TPS 下分别约 13 分钟、22 分 19 秒与 22 分 21 秒；记录的终点可能晚于就绪灯亮起。测试使用服务器冲刺模式，墙钟时间不能直接代表普通游戏中的等待。

在单人作弊世界或有相应权限的服务器中，按一个键后可以用原版命令加速处理，再等就绪灯：

~~~mcfunction
/tick sprint 35000t
~~~

冲刺会加速整个世界，实际速度取决于服务器处理能力；需要提前结束时使用 /tick sprint stop。计算仍由原版红石完成。

## 蓝图组织与配色

生成相关代码独立放在 **top.lacrus.zephyrzeno.electroncalculator.BluePrint** 软件包，包含编译、模块布局、布线、验证、蓝图读写、命令和世界放置。

基础逻辑门、多位加法器、锁存器及接口使用预设模板，由代码推导整机逻辑、组合模块并自动布线。新操作台追加到旧算术蓝图，已有运算模块保留其坐标。发行 JAR 内置压缩蓝图，游戏内载入并放置，避免重新布线。

发行包另带与蓝图逐格匹配的空白初始状态，由 Minecraft 原生测试在清空后采集，仅用于生成或修复时恢复寄存器与线路、取消放置引起的瞬态更新。它不保存算式答案；初始化完成后所有状态变化和计算都由原版红石完成。

生成物使用原版羊毛、红石粉、火把、中继器、比较器、铜灯、石按钮、红石灯、混凝土、平滑石和告示牌；生成完成后 Java 不读取按键代算、不运行隐藏指令、不驱动灯屏。DecimalCircuit.evaluate 和 KeypadCircuit.step 只供验证使用。

| 羊毛颜色 | 模块 |
| --- | --- |
| 黄绿色 | 输入 |
| 灰色 | 控制与定时 |
| 青色 | 进制转换 |
| 橙色 | 加法 |
| 红色 | 减法 |
| 黄色 | 乘法 |
| 蓝色 | 除法 |
| 紫色 | 存储 |
| 白色 | 显示 |

连接线支撑沿用来源模块颜色。原算术模块说明见 [docs/MODULES.md](../docs/MODULES.md)。

## 构建与验证

以下命令在 `calculator/` 目录运行。历史整机验收日志仍保存在仓库根目录的 `build/`；拆分后的单独构建与冷启动复测记录在根目录 `build/calculator-split-build.log`、`build/calculator-isolated-cold.log`。独立测试运行时只加载 `electron_calculator`，没有加载主线。

固定依赖：Minecraft 1.21.1、Yarn 1.21.1+build.3、Loader 0.16.14、Fabric API 0.116.1+1.21.1、Loom 1.9.2、Gradle 8.12.1、Java 21。

~~~powershell
.\tools\gradle.ps1 build
.\tools\gradle.ps1 runClient
.\tools\gradle.ps1 runGametest
.\tools\gradle.ps1 runGametest -PconsoleOnly
.\tools\gradle.ps1 runGametest -PconsoleFront
.\tools\gradle.ps1 runGametest -PconsoleAuto
.\tools\gradle.ps1 runGametest -PconsoleWhole
.\tools\gradle.ps1 runGametest -PconsoleWhole -PcoldOnly
.\tools\gradle.ps1 runGametest -PconsoleWhole -PrepairOnly
~~~

本机 tools/gradle.ps1 为旧 JDK 的 Windows Unix socket 问题提供本次调用内的 TCP 回退；其他环境可用 gradlew.bat。

- 运算逻辑和模块化逻辑分别穷举 40,000 组操作数与运算，比较二进制结果、BCD、标志及七段码。
- 按键逻辑覆盖追加、退格、清空、前导零、两位限制、阶段错误、重叠、连续表达式及 40,000 组执行快照。
- 原算术核心的 14 组原生 GameTest 已通过，证据为 build/full-gametest-results.xml 和 build/full-modular-gametest.log。
- 单按键原生夹具已通过：实际按钮请求保持、复位、忙时锁定与恢复接收。
- 前端物理夹具已通过 30 次真实按钮事务，每次比较所有锁存状态和物理组合门；证据为 build/console-front-gametest-v3.log。
- 自动时序夹具已通过 32 次真实按钮事务，覆盖连续四则运算输入、等号、除零后的清空与缺项等号；每次比较全部输入/快照状态、请求清理与实际就绪灯。证据为 build/console-auto-gametest-v12.log 和 build/console-auto-gametest-results.xml。
- alpha.4 实际执行 /electron calculator build 的整机原生 GameTest 已通过**无输入冷启动、就绪后空闲 200 游戏刻及 31 次连续按钮事务**，第一题直接输入，没有预先按 C。覆盖 1+1、12×3、9−12、1÷6、99×99、99÷0、清空与缺项等号。每次核对全部锁存状态、上方输入屏每个像素、运算选择、下方结果屏每个像素、负号/错误/小数点、14 位二进制灯、请求清理和就绪状态。本机整轮构建与运行约 9 分 30 秒（冲刺模式）；证据为 build/console-hotfix-whole-v2.log 和 build/console-hotfix-whole-results.xml。
- alpha.3 旧结构的原生修复测试已通过：先验证拒绝覆盖其他方块，再验证删除废弃位置、保留模块间其他方块及其计划更新、修复后空闲 200 刻不误报，并直接输入 1+1 核对全部灯屏。证据为 build/console-hotfix-repair-v3.log 和 build/console-hotfix-repair-results.xml。
- 12 项单元测试通过，包含旧显示清理、固定算术模块保持和新屏幕共用线路连通性检查；原生测试以发行资源中的蓝图和初始状态运行。
- 最终代码另通过新生成结构的独立冷启动复测，未按键空闲 200 刻，全部编辑与执行寄存器保持为零；证据为 build/console-hotfix-cold.log 和 build/console-hotfix-cold-results.xml。发行 JAR 的版本、三份蓝图/初始状态资源及测试代码隔离校验记录在 build/release-verification.json。
- 尚未进行客户端人工视觉验收。

### 重新编译蓝图

~~~powershell
.\tools\gradle.ps1 compileCalculator
.\tools\gradle.ps1 compileConsole -PkeypadOnly
.\tools\gradle.ps1 compileConsole
Copy-Item build\console-calculator.ebp src\main\resources\data\electron_calculator\calculator.ebp
.\tools\gradle.ps1 runGametest -PconsoleWhole -PcaptureStartup
Copy-Item build\calculator.startup src\main\resources\data\electron_calculator\calculator.startup
.\tools\gradle.ps1 runGametest -PconsoleWhole
.\tools\gradle.ps1 runGametest -PconsoleWhole -PrepairOnly
.\tools\gradle.ps1 build
~~~

算术核心保存于 build/modular-calculator.ebp，前端保存于 build/keypad.ebp，最终整机为 build/console-calculator.ebp。修改前端逻辑或固定几何后必须先重新生成 keypad 蓝图；断点只是中间文件，不能用于发行。

每次改动最终蓝图都必须重新采集 calculator.startup；加载时校验结构指纹，拒绝搭配旧的初始状态。calculator-legacy.ebp 保留 alpha.3 结构作为修复参考，不随重新编译替换。checkCleanup 验证旧显示专用支线的清理和共用线路的连通性。

