<div align="center">

<img src="icon.png" width="160" alt="litematica-printer-EMT-Azusa">

# litematica-printer-EMT-Azusa

**基于 [MoMortis 的 litematica-printer-EMT](https://github.com/MoMortis/litematica-printer-EMT)（EMT+260925）的个人改写版**

投影打印机 · 球形放置 / 平面无边界挖掘 / 发包限流 / 自动工具切换 / 简单排流体 / 破基岩

当前版本：**litematica-printer-EMT-Azusa-dt261001u-26.2** ｜ 游戏版本：Minecraft **26.2** + Fabric ｜ 许可：**AGPL-3.0**

仓库地址：`https://github.com/sd-dt/litematica-printer-EMT-Azusa`


**[中文](README.md)** | [English](README_EN.md)

</div>

---

## 这是什么

这是 [litematica-printer](https://github.com/aleksilassila/litematica-printer) → MoMortis 的 **EMT** 分支 → 我（`sd_dt`）的**个人改写版**。
以官方 **EMT+260925**（26.2 内层）为基线，把此前积累的一批改动逐项搬了过来，并保留上游全部功能。

> 只做了客户端逻辑与配置界面层级的改动，**不包含任何服务端组件**；使用时请遵守你所游玩服务器的规则。

## 相比上游 EMT+260925 增加了什么

### 放置与挖掘

| 功能 | 说明 |
|---|---|
| **球形放置** | 打印时按"到玩家脚底的距离"由近到远排序，形成以玩家为中心、由内向外扩散的推进；开关在「打印 → 球形放置」 |
| **平面无边界挖掘** | 挖掘选区类型多出「平面无边界」：**不受投影选区水平边界限制**，只按配置的两个 Y 数据栏（`minePlaneMinY` / `minePlaneMaxY`）整平面挖掘（工作半径仍生效） |
| **挖掘栏也能切形状** | 「工作范围形状」（正方体 / 球体 / 八面体）直接放进挖掘分页，不必翻回核心分页 |
| **冰换水优化** | 排流体时可选"有普通方块待放就先等"的优化策略，减少来回空跑 |

### 工具与限流

| 功能 | 说明 |
|---|---|
| **自动工具切换** | 核心开关；挖掘时自动解析"当前方块的最优工具"并从背包取用，开挖前切好，避免换工具把进度清零 |
| **发包限流器** | 自动检测服务器响应与自身发包速率，卡顿或被限包时自动下调每 tick 动作上限，稳定后缓慢回升；「重置发包上限」按钮可一键清空所有服务器学到的上限记录 |

### 破基岩（自研实现）

整套替换为自研流程（`printer/bedrockUtils/*`：目标收集、环境检查、背包管理、破坏/放置、状态机驱动），
配置分页新增**破基岩**一页；与外部 BedrockMiner / BlockMiner 模组解耦。

### 排流体

| 功能 | 说明 |
|---|---|
| **简单排流体（放一挖一）** | 用非重力方块把水源"盖住再挖掉"；检测到相邻水源（无限水）时先把整片水源都盖上，再逐个挖掉，避免重新渗水。填充方块列表默认**圆石**（可用 `排流体 - 替换方块列表` 修改；列表里若只配了沙子这类重力方块，会自动回退使用，不会出现"只挖不放"） |
| **副手放置修复** | 主手或副手已拿所需物品时不再多此一举换手；副手拿建材可直接用副手放置 |
| **告示牌修复** | 站立牌/悬挂牌按牌型低头、抬头放置，避免原版"按最近注视方向挑变体"导致的牌型错放；世界里的牌子与投影要求的不是同一种时，开启「破坏错误状态方块」会先拆掉再按投影重放 |
| **玻璃多放修复** | 空中放置引入"未确认点击"防护：世界状态变化或超过 40 tick 才视为已确认，避免同一格被重复点击而把方块放到相邻格（区域外多放一块） |
| **铁轨放置** | 修复含水铁轨、形状修复等铁轨放置细节 |

### 界面与显示

| 功能 | 说明 |
|---|---|
| **MiniHUD 工作状态** | 在 MiniHUD 里追加一行「打印机 / 工作模式 / 范围」状态（可在核心分页关闭） |
| **容器 GUI 守卫** | 打开容器界面时不再误触打印/挖掘操作 |
| **分页** | 核心 / 快捷键 / 打印 / 挖掘 / **破基岩** / 填充 / 排流体 / 特殊 / 寻路 / 危险 |

### 潜影盒与背包

| 功能 | 说明 |
|---|---|
| **潜影盒取货** | 只能取"当前真正需要的那一种"，并且**只在打印机工作开关打开时**才自动补货；开盒 3 秒等不到容器界面会整体复位，不再把玩家自己打开的容器当成自己开的盒 |
| **背包满** | 背包满时自动挑一个**不是潜影盒**的快捷栏槽做交换把材料换出来；实在取不出来就提示「背包已满」并退避 5 秒，不再反复开盒打断打印 |

> 上游 EMT+260925 自带的 **防饿死（`EatUtils` / `EatMode`）**、GO 寻路增强、更新检查、`SIMIAO` 等特性**原样保留**，本改版不再重复实现。

## 安装

1. 需要 Minecraft **26.2** + Fabric Loader。
2. 依赖：[malilib](https://github.com/maruohon/malilib) ≥ 0.29.6、[litematica](https://github.com/maruohon/litematica) ≥ 0.28.8、`fabric-content-registries-v0`（Fabric API 模块）。
3. 把 `litematica-printer-EMT-Azusa-dt261001u-26.2.jar` 放进 `.minecraft/mods/`。
4. **同一时间只能有一个 litematica-printer 系模组**（本改版与上游 EMT、其他分支不能共存，mod id 都是 `litematica-printer`）。

## 从源码构建

不加 Gradle，直接用 `javac` + `jar`（仓库里的 `scripts/`）：

```powershell
# 26.2 改版线（本仓库）
powershell -File scripts/build.ps1          -Line emt-260925
# 产物：versions\emt-260925\dist\litematica-printer-EMT-Azusa-dt<日期><字母>-26.2.jar

# 重建校验（逐条目逐字节对比发布件与重新编译结果）
powershell -File scripts/verify-rebuild.ps1 -Line emt-260925
```

* 需要 **JDK 25**（class 版本 69）与 26.2 的编译期依赖（MC 客户端 jar、malilib、litematica、Fabric Loader/API、Mixin、MixinExtras…）。
* 产物命名规则：`dt<YYMMDD><a|b|c…>`，字母是**当天第几次构筑**。

## 配置

配置界面：`.minecraft/config/litematica-printer.json`（游戏内 GUI 修改）。
几个常用项：

| 键 | 说明 |
|---|---|
| `workingSwitch` | 打印机总开关（快捷键默认 `CAPS_LOCK`） |
| `printerMode` | 工作模式：打印 / 挖掘 / 破基岩 / 排流体 / 填充 / 替换 |
| `workingRange` | 工作半径 |
| `sphericalPlace` | 球形放置 |
| `mineSelectionType` | 挖掘选区类型（含「平面无边界」） |
| `minePlaneMinY` / `minePlaneMaxY` | 平面无边界的上下限 |
| `autoToolSwitch` | 自动工具切换 |
| `autoPacketLimit` / `resetPacketLimit` | 发包限流 / 重置上限 |
| `fluidSimpleMode` / `fluidReplaceBlockList` | 简单排流体 / 填充方块列表 |
| `quickShulker*` | 快捷潜影盒相关 |

## 致谢与许可

* 原模组：[aleksilassila/litematica-printer](https://github.com/aleksilassila/litematica-printer)
* EMT 分支与本次改写所基于的基线：[MoMortis](https://github.com/MoMortis/litematica-printer-EMT) 的 **litematica-printer-EMT (EMT+260925)**
* 本改版作者：**sd_dt**、**MoMortis**、**deepseekfl4.1**

本项目沿用上游许可：**AGPL-3.0**。你可以自由使用、修改、再分发，但**必须保留同样的许可与署名**，并且分发修改版时**必须提供完整源码**。
