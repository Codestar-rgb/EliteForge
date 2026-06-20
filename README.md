# EliteForge（精英锻造）

> 一个面向 Minecraft 1.20.1 (Forge) 的次世代精英怪模组，融合 Champions、Infernal Mobs、L2Hostility 精华，以独特的"精英生态"主题再创新。

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green)](https://minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.3.0-orange)](https://files.minecraftforge.net/)
[![Java](https://img.shields.io/badge/Java-17-blue)](https://adoptium.net/)
[![Version](https://img.shields.io/badge/Version-0.5.0-purple)]()
[![License](https://img.shields.io/badge/License-LGPL--2.1-blue)](LICENSE.txt)

## ✨ 核心特色

- **56 个能力**：攻击 12 / 防御 12 / 控制 12 / 传奇 10 / 造物主 8（+ Supreme 被动）
- **6 级品质**：普通 → 优良 → 精良 → 史诗 → 传说 → **神话**
- **1-1500 等级系统**：基于区块热度 + 玩家经验动态计算，全程可配置
- **造物主级词条**：8 个独占型终极能力，超越传奇的"Boss 核心"
- **精英生态链**：造物主 → 被滋养精英 → 普通精英，形成战术编组
- **召唤物锁链**：亡灵术/分身召唤的随从与本体间有紫色锁链视觉 + 拉扯保护
- **运行时动态强化**：精英怪在存活期间持续进化
- **3 种难度模式**：FORGE / CASUAL / MIXED + 5 种配置预设（均衡/硬核/休闲/沙盒/噩梦）
- **19 对互斥 + 20 组协同**：能力搭配有深度策略
- **饰品系统**：5 种 Curios 兼容饰品（戒指/项链/腰带/护符/之冠），6 品质 × 可升级
- **材料掉落系统**：12 种材料按品质分层，从精英怪掉落
- **指南书 UI**：右键打开精美双页书本界面，6 页完整教学
- **击杀 Toast 通知**：击杀精英时弹出成就式通知
- **数据包驱动 + KubeJS 集成** + **Jade 兼容**

## 🆕 v0.5.0 更新

- **精英召唤图腾**：新物品，右键召唤测试用精英怪（可配置实体/品质/等级）
- **指南书 UI 打磨**：文字阴影提升可读性
- **`/ef nearby` 命令**：列出 64 格内所有精英的等级/品质/能力/距离
- **击杀 Toast 通知**：击杀精英时客户端弹出 advancement 风格通知
- **README 更新**：反映 v0.2.0+ 现状

## 📦 构建与安装

### 环境要求
- JDK 17
- Gradle 8.x（项目自带 `gradlew` 包装器）

### 从源码构建
```bash
git clone https://github.com/Codestar-rgb/EliteForge.git
cd EliteForge
./gradlew build
# 输出: build/libs/eliteforge-0.5.0.jar
```

将 JAR 文件放入 Minecraft 服务端/客户端的 `mods/` 文件夹即可。

### 开发运行
```bash
./gradlew runClient    # 启动开发客户端
./gradlew runServer    # 启动开发服务器
./gradlew runData      # 生成数据资源
```

## 🎮 游戏内命令

```
/eliteforge spawn <entity> [level] [mode]    生成精英怪
/eliteforge creator spawn <player>           在玩家附近生成造物主级精英
/eliteforge creator awaken <entity>          将现有精英觉醒为造物主级
/eliteforge reroll <target>                  重洗能力
/eliteforge setlevel <target> <level>        设置等级（1-9999）
/eliteforge addability <target> <ability> [level]  添加能力
/eliteforge removeability <target> <ability>       移除能力
/eliteforge nearby                           列出 64 格内所有精英
/eliteforge heat get/set/reset [chunk]       区块热度管理
/eliteforge experience get/set/reset <player> 玩家经验管理
/eliteforge config mode <mode>               切换难度模式
/eliteforge config preset <name>             应用配置预设（balanced/hardcore/casual/sandbox/nightmare）
/eliteforge config info                      查看当前配置
/eliteforge config reload                    保存配置到磁盘
/eliteforge revenge trigger/status           复仇系统
/ef ...                                      /eliteforge 的别名
```

## ⚙️ 配置

模组生成 3 个配置文件（`config/eliteforge-*.toml`）：
- **common** — 核心玩法（难度模式、生成率、等级上限）
- **server** — 服务器平衡（能力预算、热度、掉落、召唤物锁链、按实体覆盖）
- **client** — 客户端视觉（图标/名牌/粒子/光环/热度叠加层）

### 配置预设
```
/eliteforge config preset balanced    # 均衡（默认）
/eliteforge config preset hardcore    # 硬核（高生成/强能力/频繁造物主）
/eliteforge config preset casual      # 休闲（低生成/弱能力/无造物主）
/eliteforge config preset sandbox     # 沙盒（超高生成/造物主常见）
/eliteforge config preset nightmare   # 噩梦（极端/仅推荐挑战）
```

### 按实体类型覆盖
在 `eliteforge-server.toml` 的 `entity_overrides` 列表中添加：
```
entityOverrides = ["minecraft:creeper|false|EPIC|*|1.5|2.0", "minecraft:enderman|true|*|*|*|*"]
# 格式: entity_id|disabled|forcedQuality|forcedLevel|healthMult|damageMult
# * = 使用默认值
```

## 📚 文档

- **[HANDOVER.md](HANDOVER.md)** — 完整项目交接文档（新开发者必读）
- **[docs/elite-system-update-plan.md](docs/elite-system-update-plan.md)** — 造物主级系统设计总纲
- **[docs/WORKLOG.md](docs/WORKLOG.md)** — 全量工作日志
- **[docs/optimization-rounds-*.md](docs/)** — 各轮优化详细记录

## 🏗️ 项目结构

```
src/main/java/com/eliteforge/
├── EliteForge.java              # 主类
├── ability/                     # 能力系统（56 能力 + Supreme 被动）
│   ├── attack/  defense/  control/  legendary/  creator/
├── accessory/                   # 饰品系统（Curios 兼容）
├── capability/                  # 实体数据存储 + 客户端同步
├── client/                      # 客户端（指南书 UI / 配置界面 / Toast / Jade）
├── config/                      # 配置系统 + 预设 + 按实体覆盖
├── spawn/                       # 生成 / 生态 / 觉醒 / 复仇 / 召唤物锁链
├── difficulty/                  # 区块热度 / 玩家经验 / 难度公式
├── quality/  material/          # 品质系统 / 材料掉落
├── item/  block/  blockentity/  # 物品 / 方块 / 方块实体
├── effect/  enchantment/        # 药水效果 / 附魔
├── network/  render/            # 网络同步 / 渲染（名牌/图标/光环/锁链/热度）
└── util/  datapack/  kubejs/    # 工具 / 数据包 / KubeJS 集成
```

## 🎨 设计灵感

| 模组 | 仓库 |
|------|------|
| Champions | https://github.com/TheIllusiveC4/Champions |
| Infernal Mobs | https://github.com/atomicstryker/infernal-mobs |
| L2Hostility | https://github.com/LiUKJin/L2Hostility |

## 📝 许可证

LGPL-2.1，详见 [LICENSE.txt](LICENSE.txt)。

## 🤝 贡献

请先阅读 [HANDOVER.md](HANDOVER.md) 了解项目架构与开发规范。
