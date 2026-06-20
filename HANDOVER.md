# EliteForge v3 — 完整项目交接文档 (HANDOVER)

> **文档目的**: 让任何新的 AI 智能体（或人类开发者）从零开始**完全掌握** EliteForge v3 Minecraft 模组项目，并能够独立接手后续开发、优化、Bug 修复与内容扩展。
>
> **阅读顺序建议**: 先通读本文件 → 再阅读 `docs/elite-system-update-plan.md`（设计总纲）→ 浏览 `docs/WORKLOG.md`（历史变更记录）→ 然后按需深入源码。
>
> **最后更新**: 2025-06-17（第 1-18 轮优化 + R1 文档/测试 + R2 容器GUI/特效/集成桩 + R3-R6 编译修复/i18n清理/ThreadLocalRandom迁移）

---

## 目录

1. [项目概述](#1-项目概述)
2. [技术栈与开发环境](#2-技术栈与开发环境)
3. [项目目录结构](#3-项目目录结构)
4. [核心架构总览](#4-核心架构总览)
5. [能力系统（Ability System）详解](#5-能力系统详解)
6. [造物主级词条系统（Creator Tier）详解](#6-造物主级词条系统详解)
7. [品质系统（Quality System）](#7-品质系统)
8. [生成系统与精英生态](#8-生成系统与精英生态)
9. [难度系统（区块热度 / 玩家经验）](#9-难度系统)
10. [配置系统（Config）](#10-配置系统)
11. [物品 / 方块 / 附魔 / 效果](#11-物品--方块--附魔--效果)
12. [网络同步与客户端渲染](#12-网络同步与客户端渲染)
13. [数据包与 KubeJS 集成](#13-数据包与-kubejs-集成)
14. [国际化（i18n）](#14-国际化i18n)
15. [参考仓库与设计灵感](#15-参考仓库与设计灵感)
16. [构建与运行](#16-构建与运行)
17. [已完成优化轮次总结](#17-已完成优化轮次总结)
18. [已知问题与待办事项](#18-已知问题与待办事项)
19. [开发规范与约定](#19-开发规范与约定)
20. [接手开发的快速入门清单](#20-接手开发的快速入门清单)

---

## 1. 项目概述

**EliteForge（精英锻造）** 是一个面向 Minecraft 1.20.1 (Forge) 的次世代精英怪模组。

### 设计哲学

EliteForge 融合了三大经典精英怪模组的精华，并以独特的"锻造"主题进行再创新：

| 灵感来源 | 借鉴点 | EliteForge 的创新 |
|----------|--------|-------------------|
| **Champions** | 词条（affix）系统、等级显示 | 双预算系统、6 级品质、造物主级 |
| **Infernal Mobs** | 精英怪能力多样性、难度感 | 互斥机制、协同加成、动态强化 |
| **L2Hostility** | 难度阶梯、成长性精英 | 区块热度、玩家经验、精英生态链 |

### 核心特色

- **54 个能力**：攻击 12 / 防御 12 / 控制 12 / 传奇 10 / 造物主 8
- **6 级品质**：普通 → 优良 → 精良 → 史诗 → 传说 → **神话**
- **造物主级词条**：8 个独占型终极能力，超越传奇的"Boss 核心"
- **精英生态链**：造物主 → 被滋养精英 → 普通精英，形成战术编组
- **运行时动态强化**：精英怪在存活期间持续进化（时间/击杀/集群/热度）
- **3 种难度模式**：FORGE（硬核）/ CASUAL（休闲）/ MIXED（混合）
- **19 对互斥 + 20 组协同**：能力搭配有深度策略
- **完整锻造工具链**：锻造锤、锻造砧、淬火石、回火标记、重洗卷轴等 21 个物品
- **6 个自定义附魔 + 6 个自定义效果**：辅助对抗精英怪
- **数据包驱动 + KubeJS 集成**：实体预设可热重载

### 版本信息

| 项目 | 值 |
|------|----|
| Mod ID | `eliteforge` |
| Mod 版本 | 0.1.0 |
| Minecraft | 1.20.1 |
| Forge | 47.3.0 |
| Java | 17 |
| 映射 | official 1.20.1 |
| 许可证 | LGPL-2.1 |

---

## 2. 技术栈与开发环境

### 必需环境

- **JDK 17**（Minecraft 1.18+ 强制要求）
- **Gradle 8.x**（项目自带 `gradlew` 包装器，无需单独安装）
- ** IntelliJ IDEA**（推荐）或 Eclipse（项目已配置 `eclipse` + `idea` 插件）

### 构建命令

```bash
# 构建发布 JAR（输出到 build/libs/eliteforge-0.1.0.jar）
./gradlew build

# 仅编译 Java 源码（快速检查编译错误）
./gradlew compileJava

# 运行开发客户端
./gradlew runClient

# 运行开发服务器
./gradlew runServer

# 生成数据（方块/物品模型、语言文件等）
./gradlew runData

# 运行单元测试（JUnit 5，纯逻辑测试，无需启动 MC）
./gradlew test

# 清理构建
./gradlew clean
```

### 关键依赖

项目仅依赖 Forge 本身（无第三方模组硬依赖）。`build.gradle` 中预留了 JEI 等可选依赖的注释模板。KubeJS 集成通过反射式插件加载实现，不强制依赖 KubeJS（缺失时自动跳过）。

---

## 3. 项目目录结构

```
eliteforge-release/
├── build.gradle                  # Gradle 构建脚本（ForgeGradle 6）
├── settings.gradle
├── gradle.properties             # 版本号、MC/Forge 版本等
├── gradlew / gradlew.bat         # Gradle 包装器
├── gradle/wrapper/               # 包装器 JAR
├── CREDITS.txt
├── LICENSE.txt                   # LGPL-2.1
├── README.md                     # 项目说明
├── HANDOVER.md                   # ← 本文件（交接文档）
│
├── docs/                         # 设计文档与优化记录
│   ├── elite-system-update-plan.md      # 造物主级系统设计总纲（1175 行）
│   ├── optimization-plan-v1.md          # 第 1 轮优化计划
│   ├── optimization-rounds-1-5.md       # 第 1-5 轮记录
│   ├── optimization-rounds-6-10.md      # 第 6-10 轮记录
│   ├── optimization-rounds-11-14.md     # 第 11-14 轮记录
│   └── WORKLOG.md                       # 全量工作日志（18 轮，~5000 行）
│
└── src/main/
    ├── java/com/eliteforge/      # 154 个 Java 源文件（~31000 行）
    │   ├── EliteForge.java              # ★ 主类 (@Mod)
    │   ├── ability/                     # ★ 能力系统（60 文件）
    │   │   ├── Ability.java             #   抽象基类
    │   │   ├── AbilityCategory.java     #   类别枚举
    │   │   ├── AbilityBudget.java       #   预算系统
    │   │   ├── AbilityManager.java      #   ⚠️ 已废弃（保留向后兼容）
    │   │   ├── AbilityRegistry.java     #   ★ 能力注册表（54 能力）
    │   │   ├── AbilityInteraction.java  #   互斥 + 协同
    │   │   ├── MutualExclusion.java     #   互斥规则
    │   │   ├── attack/    (12 能力)
    │   │   ├── defense/   (12 能力)
    │   │   ├── control/   (12 能力)
    │   │   ├── legendary/ (10 能力)
    │   │   └── creator/   (8 造物主能力)
    │   ├── block/        (5)   # 自定义方块
    │   ├── blockentity/  (5)   # 方块实体
    │   ├── capability/   (5)   # ★ EliteCapability（实体数据存储）
    │   ├── command/      (1)   # /eliteforge 命令
    │   ├── config/       (2)   # ★ EliteForgeConfig（540 行）
    │   ├── datapack/     (2)   # 实体预设加载器
    │   ├── difficulty/   (3)   # 区块热度 / 玩家经验 / 难度管理
    │   ├── effect/       (6)   # 自定义药水效果
    │   ├── enchantment/  (6)   # 自定义附魔
    │   ├── init/         (7)   # 延迟注册（物品/方块/效果/附魔/声音）
    │   ├── item/         (21)  # 自定义物品
    │   ├── kubejs/       (2)   # KubeJS 插件
    │   ├── network/      (4)   # 网络包（S2C 同步）
    │   ├── quality/      (5)   # ★ 品质系统 + 掉落
    │   ├── render/       (5)   # 客户端渲染
    │   ├── setbonus/     (1)   # 套装加成
    │   ├── spawn/        (7)   # ★ 生成 / 生态 / 觉醒 / 复仇 / 动态强化
    │   └── util/         (4)   # 工具类（NBTKeys / TextHelper 等）
    │
    └── resources/
        ├── META-INF/mods.toml       # 模组元数据
        ├── pack.mcmeta               # 资源包声明
        ├── assets/eliteforge/
        │   ├── lang/en_us.json       # 英文（363 键）
        │   ├── lang/zh_cn.json       # 中文（363 键，完美对齐）
        │   └── sounds.json
        └── data/eliteforge/eliteforge/entity_presets/  # 7 个实体预设
            ├── zombie.json, skeleton.json, creeper.json
            ├── spider.json, enderman.json, blaze.json, witch.json
```

---

## 4. 核心架构总览

### 启动流程

```
Forge 加载 EliteForge (@Mod)
    │
    ├─→ 构造器: 注册延迟注册器到 Mod 事件总线
    │       (ModItems / ModBlocks / ModEffects / ModEnchantments / ModSounds / ...)
    │
    ├─→ FMLCommonSetupEvent:
    │       └─→ AbilityRegistry.init()  ← 注册全部 54 个能力
    │
    ├─→ RegisterCapabilitiesEvent:
    │       └─→ EliteCapability.register()  ← 注册 capability 接口
    │
    ├─→ AttachCapabilitiesEvent<Entity>:
    │       └─→ 为每个 LivingEntity 附加 EliteCapabilityProvider
    │
    ├─→ AddReloadListenerEvent:
    │       └─→ 注册 EntityPresetLoader（数据包热重载）
    │
    └─→ ServerStartingEvent:
            └─→ validateServerConfig() + initializeAntiFarmSystem()
```

### 运行时事件流（关键）

所有游戏事件由 `spawn/EliteEventHandler.java`（`@Mod.EventBusSubscriber` 自动注册）处理：

| 事件 | 处理逻辑 |
|------|----------|
| `EntityJoinLevelEvent` | 检测新实体 → 触发生成判定 → 转换为精英 |
| `LivingHurtEvent` | 能力 `onAttack` / `onHurt` 分发、协同伤害、互斥校验、附魔加成 |
| `LivingDeathEvent` | 能力 `onDeath`、造物主同化/湮灭、觉醒检查、掉落、复仇记录 |
| `LivingHealEvent` | 防御类能力（护盾/再生）拦截 |
| `PlayerEvent.PlayerLoggedOutEvent` | 清理追踪数据 |
| `TickEvent.ServerTickEvent` | 能力 tick、动态强化、区块热度衰减、觉醒检查 |
| `ChunkEvent.Unload` | 清理区块级追踪数据（防内存泄漏） |

### 数据存储架构

```
Entity (LivingEntity)
  └─→ Capability: EliteCapability (Forge Capability 系统)
        └─→ EliteData (POJO，可序列化为 NBT)
              ├─ isElite, quality, level, uuid
              ├─ abilities: Map<String, Integer>  (能力ID → 等级)
              ├─ creatorTierId, creatorTicks, isCreatorElite
              ├─ spawnTick, killCount, nurturingSource
              └─ maxAbilities
```

**关键原则**: 所有精英数据通过 `entity.getCapability(EliteCapability.CAPABILITY)` 访问，**绝不**直接读写 NBT（`PurifyingTouchEnchantment` 曾因直接改 NBT 导致 Bug，已在第 13 轮修复）。

---

## 5. 能力系统详解

### 能力基类：`Ability.java`（抽象类）

每个能力实现以下生命周期钩子（按需 override）：

```java
public abstract class Ability {
    public void onApply(LivingEntity entity, int level) {}   // 能力被赋予时
    public void onRemove(LivingEntity entity) {}              // 能力被移除时
    public void onAttack(LivingEntity attacker, LivingEntity target, int level, float damage) {}
    public void onHurt(LivingEntity entity, LivingEntity source, int level, float amount) {}
    public void onTick(LivingEntity entity, int level) {}     // 每游戏 tick
    public void onDeath(LivingEntity entity, LivingEntity killer, int level) {}
    public void onPlayerKill(LivingEntity entity, Player player, int level) {}
    // ... 还有 onShoot / onJump / onTeleport 等
}
```

### 能力类别：`AbilityCategory`（枚举）

| 类别 | 数量 | 预算池 | 最大等级 | 颜色 |
|------|------|--------|----------|------|
| `ATTACK` | 12 | 攻击预算 | 5 (Ⅰ~Ⅴ) | 红色 |
| `DEFENSE` | 10 | 防御预算 | 5 | 蓝色 |
| `CONTROL` | 12 | 控制预算 | 5 | 黄色 |
| `LEGENDARY` | 10 | 传奇预算（独立池） | 5 | 金色 |
| `CREATOR` | 8 | 不参与预算 | **3** (Ⅰ~Ⅲ) | 深红加粗 |

### 预算系统：`AbilityBudget.java`

双预算约束：
1. **类别预算**：攻击/防御/控制各有独立预算池，能力消耗对应池点数
2. **总量上限**：`maxAbilitiesPerElite`（配置，默认 5）硬性限制能力总数

预算分配公式（FORGE 模式示例）：
```
攻击预算 = attackBudgetBase (3.0) + quality.budgetBonus + level * 0.5
防御预算 = defenseBudgetBase (2.5) + ...
控制预算 = controlBudgetBase (2.0) + ...
传奇预算 = legendaryBudgetBase (1.5) + ...  (独立池，不与其他共享)
```

### 互斥机制：`MutualExclusion.java` + `AbilityInteraction.java`

- **19 对互斥**：如 `IRON_WALL ↔ VOID`、`SHIELD ↔ EVADE`、`BERSERK ↔ DEATH_TOUCH`，不能同时存在（第 9 对 `STORM ↔ LIGHTNING` 已移除以启用 Thunder Lord 协同）
- **20 组协同**：如 `FIRE + SPIRIT_BURN = "Inferno"` (1.5x)、`LIGHTNING + STORM = "Thunder Lord"` (1.5x)
- **造物主独占规则**（最高优先级）：
  - 造物主能力与**所有其他能力**（含其他造物主能力）互斥
  - 例外：`C4 同化 (Assimilate)` 可吸收死亡精英的能力；`C7 轮回 (Reincarnation)` 可在重生时赐予传奇能力
  - 互斥校验在 `MutualExclusion.isMutuallyExclusive()` 中始终强制执行，不受配置开关影响

### 54 个能力完整清单

**攻击类 (ATTACK) — 12 个**：
`fire`(点燃)、`corrosion`(腐蚀)、`spirit_burn`(灵焰)、`lightning`(闪电)、`death_touch`(死亡之触)、`explosion`(爆炸)、`arrow_rain`(箭雨)、`bloodthirst`(嗜血)、`sweep`(横扫)、`poison`(剧毒)、`wither`(凋零)、`rage`(狂暴)

**防御类 (DEFENSE) — 12 个**：
`iron_wall`(铁壁)、`regen`(再生)、`immunity`(免疫)、`thorns`(荆棘)、`shield`(护盾)、`evade`(闪避)、`armor`(重甲)、`absorption`(吸收)、`reflect`(反射)、`phase`(相位)、`leech`(吸血)、`bulwark`(壁垒)

**控制类 (CONTROL) — 12 个**：
`web`(蛛网)、`gravity`(重力)、`slow`(迟缓)、`blind`(致盲)、`fear`(恐惧)、`siphon`(虹吸)、`knockback`(击退)、`freeze`(冰冻)、`curse`(诅咒)、`immobilize`(禁锢)、`void`(虚空)、`confusion`(混乱)

**传奇类 (LEGENDARY) — 10 个**：
`clone`(分身)、`phase_shift`(相位转移)、`storm`(风暴)、`necromancy`(死灵)、`berserk`(狂战)、`time_warp`(时间扭曲)、`mutation`(变异)、`chaos`(混沌)、`doom`(末日)、`supreme`(至尊)

**造物主类 (CREATOR) — 8 个**：见下一节。

---

## 6. 造物主级词条系统详解

造物主级是 EliteForge 的**最高层级**，代表"超越传奇"的终极精英怪。完整设计见 `docs/elite-system-update-plan.md`。

### 8 个造物主能力

| 编号 | ID | 名称 | 预算(展示) | 核心机制 |
|------|----|------|-----------|----------|
| C1 | `nexus` | 源核·滋养 | 6.0 | 持续被动光环，滋养周围精英怪（提升等级/临时能力） |
| C2 | `dominion` | 域界·支配 | 5.5 | 创建领地区域，区域内精英增益、玩家减益 |
| C3 | `evolution` | 熔炉·进化 | 5.0 | 受伤积累进化值，进化后全属性暴增 |
| C4 | `assimilate` | 渊源·同化 | 5.5 | **突破独占**：吸收死亡精英的能力，永久成长 |
| C5 | `bestowal` | 铸造·赐能 | 5.0 | 将周围普通怪转化为精英（消耗自身生命） |
| C6 | `annihilate` | 湮灭·终焉 | 6.0 | 死亡时毁灭性爆炸 + 连锁反应 |
| C7 | `reincarnation` | 轮回·不灭 | 6.5 | **突破独占**：死亡后重生，重生时赐予传奇能力 |
| C8 | `commander` | 纷争·号令 | 5.5 | 召唤精英小队，形成战术编组 |

### 生成条件（极严苛）

```
必须全部满足:
1. 实体品质 ≥ LEGENDARY（传说）
2. 区块热度 ≥ 75（接近上限 100）
3. 附近 32 格内 ≥ 3 只活跃精英怪
4. 基础生成概率: FORGE 0.5% / CASUAL 0% / MIXED 0.2%
5. 服务器存活造物主实体 < maxCreatorEntities（默认 2）
6. 玩家精英击杀数 ≥ awakeningMinPlayerKills（默认 2，可配置）
```

### 独占机制实现

```java
// CreatorAbility.canCoexistWith() — 永远返回 false
public boolean canCoexistWith(Ability other) {
    return false;
}

// 转换为造物主时（EliteSpawnHandler.convertToCreator）：
//   1. 清空所有已分配能力（attak/defense/control/legendary）
//   2. 重置预算（展示用，实际不参与分配）
//   3. 仅赋予选定的造物主能力
//   4. 品质强制设为 MYTHIC
//   5. 应用神话属性修饰符（生命+100%、伤害+50% 等）
```

### 神话品质 (MYTHIC)

| 属性 | 值 |
|------|----|
| 权重 | **0**（自然生成不可能，仅造物主强制赋予） |
| 掉落倍率 | **10.0x** |
| 颜色 | DARK_RED + BOLD（深红加粗） |
| 光效 | 脉动红光 + 光环 |
| 生命加成 | +100%（在传说 +50% 基础上叠加） |
| 伤害加成 | +50% |

### 突破独占的两个例外

1. **C4 同化 (Assimilate)**：当其他精英怪死亡时，同化造物主可吸收其能力（最多 3 个），实现"造物主 + 传奇能力"组合
2. **C7 轮回 (Reincarnation)**：重生时，轮回造物主可赐予自身一个随机传奇能力

---

## 7. 品质系统

### 6 级品质：`QualityTier.java`

| 品质 | 颜色 | 权重 | 掉落倍率 | 预算加成 | 生命加成 |
|------|------|------|----------|----------|----------|
| NORMAL | WHITE | 50 | 1.0x | +0.0 | +0% |
| GOOD | GREEN | 30 | 1.5x | +0.5 | +10% |
| FINE | BLUE | 15 | 2.0x | +1.0 | +25% |
| EPIC | LIGHT_PURPLE | 4 | 3.0x | +1.5 | +50% |
| LEGENDARY | GOLD | 1 | 5.0x | +2.0 | +50% |
| **MYTHIC** | **DARK_RED+BOLD** | **0** | **10.0x** | **+3.0** | **+100%** |

### 品质决定流程

```
实体生成 → 按 qualityWeights 加权随机选品质
         → 品质决定预算加成（影响能力数量与等级）
         → 品质决定生命/伤害倍率（DifficultyManager.applyEliteModifiers）
         → 品质决定掉落倍率（LootHandler）
```

### 套装加成（Set Bonus）

`quality/SetBonus.java` + `setbonus/SetBonusType.java`：穿戴多件同品质淬火材料装备触发套装效果（如 4 件传说 +10% 暴击）。

---

## 8. 生成系统与精英生态

### 生成处理：`spawn/EliteSpawnHandler.java`

核心方法 `convertToElite(LivingEntity, ServerLevel)`：
1. 检查实体是否在黑名单（维度/实体类型）
2. 检查区块热度是否达阈值
3. 按难度模式计算生成概率
4. 随机选品质（按权重）
5. 分配预算 → 调用 `AbilityGenerator.generateAbilities()` 生成能力
6. 应用属性修饰符（`DifficultyManager.applyEliteModifiers`）
7. 注册到 `TRACKED_ELITES` 追踪集合
8. 广播生成公告 + 粒子效果

### 能力生成器：`spawn/AbilityGenerator.java`

替代了已废弃的 `AbilityManager`。按预算池分配：
```
for each category in [ATTACK, DEFENSE, CONTROL, LEGENDARY]:
    budget = getCategoryBudget(category)
    while budget > 0 and abilityCount < maxAbilities:
        candidate = randomNonCreatorAbility()
        if not mutuallyExclusiveWithExisting(candidate):
            level = min(5, floor(budget / candidate.cost))
            addAbility(candidate, level)
            budget -= candidate.cost * level
```

### 精英生态链：`spawn/EliteEcosystem.java`

造物主级实体作为"首领"，通过以下机制形成生态：
- **滋养 (Nurture)**：C1 源核持续提升周围精英的等级，偶尔赐予临时能力
- **赐能 (Bestowal)**：C5 将普通怪转化为精英（造物主的"繁殖"）
- **同化 (Assimilate)**：C4 吸收死亡精英的能力
- **号令 (Commander)**：C8 召唤精英小队形成编组

### 精英觉醒：`spawn/EliteAwakening.java`

传说级精英在满足条件时**觉醒**为造物主：
- 存活时间 ≥ `awakeningMinAliveTicks`（默认 6000）
- 区块热度 ≥ `awakeningMinHeat`（默认 80）
- 玩家击杀数 ≥ `awakeningMinPlayerKills`（默认 2）
- 概率触发（每 `awakeningCheckInterval` 秒检查一次）

### 精英复仇：`spawn/EliteRevenge.java`

防刷怪农场机制：当玩家在短时间内于小范围内击杀过多精英，触发"复仇"——生成强力精英怪反击玩家。

### 动态强化：`spawn/DynamicStrengthening.java`

精英怪存活期间持续强化：
- **时间强化**：每存活 N 分钟，等级 +1
- **击杀强化**：每击杀玩家 M 次，获得临时能力
- **集群强化**：附近有其他精英时，全属性加成
- **热度强化**：所在区块热度越高，攻击速度越快

> ⚠️ 动态强化使用 `lastBonus` 状态追踪（NBT 持久化），避免每 20 tick 重复添加/移除属性修饰符（第 1、3 轮修复的性能问题）。

---

## 9. 难度系统

### 区块热度：`difficulty/ChunkHeatManager.java`

每个区块有 0-100 的"热度"值：
- 精英怪击杀 → 热度 +`chunkHeatGainOnEliteKill`
- 每tick → 热度 -`chunkHeatDecayRate`
- 热度影响：生成概率、动态强化、觉醒条件

> ⚠️ 热度操作使用 `compute()` / `merge()` 保证原子性（第 12 轮修复的竞态条件）。

### 玩家经验：`difficulty/PlayerExperienceManager.java`

玩家通过击杀精英积累"锻造经验"，解锁锻造加成。同样使用原子操作。

### 难度管理：`difficulty/DifficultyManager.java`

根据品质、等级、难度模式计算属性修饰符：
- 生命值倍率
- 伤害倍率
- 移动速度倍率
- 经验掉落倍率

### 难度模式：`config/DifficultyMode.java`

| 模式 | 生成概率 | 精英强度 | 造物主概率 | CASUAL 消失 |
|------|----------|----------|------------|-------------|
| FORGE | 高 | 强 | 0.5% | 不消失 |
| CASUAL | 低 | 弱 | 0% | `casualDespawnTicks` 后消失 |
| MIXED | 中（混合） | 中 | 0.2% | 部分 |

---

## 10. 配置系统

`config/EliteForgeConfig.java`（540 行）分三组：

### COMMON（客户端+服务器同步）
- `difficultyMode`: FORGE / CASUAL / MIXED
- `enableEliteMobs`: 总开关
- `globalSpawnChance`: 0.0-1.0（默认 0.15）
- `maxAbilitiesPerElite`: 1-10（默认 5）
- `enableChunkHeat` / `enablePlayerExperience` / `enableQualitySystem` / `enableSetBonuses` / `enableTemperedMaterials`
- 各模式生成概率：`forgeModeSpawnChance` 等

### SERVER（服务器权威）
- 预算基线：`attackBudgetBase`(3.0) / `defenseBudgetBase`(2.5) / `controlBudgetBase`(2.0) / `legendaryBudgetBase`(1.5)
- 造物主：`enableCreatorTier` / `maxCreatorEntities`(2) / `creatorSpawnChanceForge`(0.005) / `creatorSpawnChanceMixed`(0.002)
- 觉醒：`awakeningMinAliveTicks`(6000) / `awakeningMinHeat`(80) / `awakeningMinPlayerKills`(2) / `awakeningCheckInterval`(300)
- CASUAL 消失：`casualDespawnTicks`(6000，0=禁用)
- 区块热度：`chunkHeatMax`(100) / `chunkHeatDecayRate` / `chunkHeatGainOnEliteKill`
- 反刷怪：`enableAntiFarm` / `antiFarmMaxKillsPerHour` / `antiFarmRadius`
- 黑名单：`dimensionBlacklist` / `entityBlacklist`

### CLIENT（仅客户端）
- 渲染：`showEliteNameplate` / `showAbilityIcons` / `showChunkHeatOverlay`
- 粒子：`eliteSpawnParticles` / `eliteDeathParticles`

> 所有配置在 `EliteForge.validateServerConfig()` 中有交叉校验（如 `awakeningMinHeat > chunkHeatMax` 会警告）。

---

## 11. 物品 / 方块 / 附魔 / 效果

### 自定义方块 (5)
| 方块 | 功能 |
|------|------|
| `ForgingAnvilBlock` | 锻造砧——淬火材料锻造 |
| `TemperingStationBlock` | 回火台——调整装备品质 |
| `HeatCollectorBlock` | 热量收集器——收集区块热度 |
| `EliteBeaconBlock` | 精英信标——召唤/标记精英 |
| `EliteSpawnerBlock` | 精英刷怪笼 |

### 自定义物品 (21)
**锻造工具链**：`ForgingHammer`(锻造锤)、`ForgingCompass`(锻造指南针)、`TemperedIngot`(淬火锭)、`TemperedMaterial`(淬火材料)、`TemperingMark`(回火标记)、`AnnealingBottle`(退火瓶)、`QuenchStone`(淬火石)

**能力操控**：`RerollScroll`(重洗卷轴)、`AbilityInfuser`(能力灌注器)、`AbilityExtractor`(能力提取器)、`PurificationFlask`(净化烧瓶)、`EvolutionCore`(进化核心)、`ScorchedCore`(焦黑核心)

**造物主相关**：`CreatorFragment`(造物主碎片)、`DominionScepter`(支配权杖)、`BestowalSigil`(赐能印记)、`ReincarnationCrystal`(轮回水晶)、`CommandBanner`(号令旗帜)、`NexusEssence`(源核精华)

**其他**：`EliteNameTag`(精英名牌)、`TemperedMaterialItem`(淬火材料物品)

### 自定义附魔 (6)
| 附魔 | 功能 |
|------|------|
| `EliteBaneEnchantment` | 精英克星——对精英额外伤害 |
| `HeatShieldEnchantment` | 热盾——减少精英能力伤害 |
| `ForgingMasterEnchantment` | 锻造大师——锻造台/回火台加成 |
| `TemperingFortuneEnchantment` | 回火幸运——提升品质重洗成功率 |
| `SoulCollectorEnchantment` | 灵魂收集——击杀精英掉落灵魂 |
| `PurifyingTouchEnchantment` | 净化之触——攻击时移除精英能力（通过 capability，非 NBT） |

### 自定义效果 (6)
`ChaosEffect`(混沌)、`FearEffect`(恐惧)、`CorrosionEffect`(腐蚀)、`ImmobilizeEffect`(禁锢)、`SpiritBurnEffect`(灵焰)、`MutationEffect`(变异)

---

## 12. 网络同步与客户端渲染

### 网络包：`network/NetworkHandler.java`

注册 3 个 S2C（服务器→客户端）包：
- `S2CEliteDataSync`：同步精英数据（品质/等级/能力列表）→ 客户端渲染名牌
- `S2CParticleEvent`：粒子事件（生成/死亡/能力触发）—— **含数量上限 200 防恶意包**（第 14 轮修复）
- `S2CChunkHeatSync`：区块热度同步 → HUD 覆盖层

### 客户端渲染：`render/` 包
- `EliteNameRenderer`：精英名牌（品质颜色 + 等级罗马数字 + 能力图标）
- `EliteRenderHandler`：主渲染调度
- `EliteParticleRenderer`：粒子效果
- `AbilityIconRenderer`：能力图标
- `ChunkHeatOverlay`：区块热度 HUD 覆盖层

---

## 13. 数据包与 KubeJS 集成

### 实体预设：`datapack/EntityPresetLoader.java`

服务器管理员可通过数据包自定义每种实体的精英配置。预设文件位于：
```
data/<namespace>/eliteforge/entity_presets/<entity_type>.json
```

预设字段示例（`zombie.json`）：
```json
{
  "enableElite": true,
  "qualityWeights": { "NORMAL": 50, "GOOD": 30, "FINE": 15, "EPIC": 4, "LEGENDARY": 1 },
  "budgetOverrides": { "attack": 3.5, "defense": 3.0 },
  "abilityBlacklist": ["eliteforge:clone"],
  "abilityWeights": { "eliteforge:fire": 1.5, "eliteforge:lightning": 1.2 },
  "maxLevel": 5
}
```

预设通过 `AddReloadListenerEvent` 注册，支持 `/reload` 热重载。

### KubeJS 集成：`kubejs/EliteForgeKubeJSPlugin.java`

提供 JavaScript API 供服务器脚本动态调整：
- 注册自定义能力
- 监听精英生成/死亡事件
- 修改品质权重

通过反射式插件加载，KubeJS 缺失时自动跳过（无硬依赖）。

---

## 14. 国际化（i18n）

- **`en_us.json` + `zh_cn.json`**：各 363 个翻译键，**完美对齐**（第 18 轮验证）
- 翻译键命名规范：`<type>.eliteforge.<name>`（如 `ability.eliteforge.fire.name`、`message.eliteforge.kill`）
- 所有玩家可见文本均使用 `Component.translatable()`（第 18 轮完成硬编码中文清理）

---

## 15. 参考仓库与设计灵感

EliteForge 的设计参考了以下开源 Minecraft 精英怪模组。**接手开发者强烈建议阅读这些仓库的源码**以理解设计模式：

### 主要参考仓库

| 模组 | 仓库 | 借鉴点 |
|------|------|--------|
| **Champions** | https://github.com/TheIllusiveC4/Champions | 词条（affix）系统架构、精英怪等级显示、奖杯掉落。EliteForge 的能力分类 + 等级系统由此演化 |
| **Infernal Mobs** | https://github.com/atomicstryker/infernal-mobs | 精英怪能力多样性、infernal 难度感。EliteForge 的互斥/协同机制受其启发 |
| **L2Hostility** | https://github.com/LiUKJin/L2Hostility | 难度阶梯、成长性精英、玩家追踪。EliteForge 的区块热度 + 玩家经验系统由此演化 |

### Minecraft Forge 官方参考

| 资源 | 链接 |
|------|------|
| Forge 官方文档 | https://docs.minecraftforge.net/ |
| Forge 1.20.1 源码 | https://github.com/MinecraftForge/MinecraftForge |
| ForgeGradle | https://github.com/MinecraftForge/ForgeGradle |
| 官方映射（official） | Mojang 官方映射，1.20.1 |

### 其他有用资源

| 资源 | 用途 |
|------|------|
| https://maven.blamejared.com/ | Forge 模组 Maven 仓库（JEI、Patchouli 等） |
| https://minecraft.wiki/ | Minecraft 1.20.1 API 参考 |
| https://github.com/MinecraftForge/EventBus | Forge 事件总线机制 |

### 阅读建议

1. **先读 Champions 的 `affix` 包**：理解词条系统的基本模式（每个 affix 一个类，挂载到事件）
2. **再读 L2Hostility 的 `difficulty` 包**：理解成长性精英和难度追踪
3. **最后对比 EliteForge 的 `ability` 包**：理解双预算 + 互斥 + 协同的改进

---

## 16. 构建与运行

### 首次构建

```bash
cd eliteforge-release
./gradlew build
# 输出: build/libs/eliteforge-0.1.0.jar
```

### 开发环境运行

```bash
# 启动开发客户端（会自动下载 MC + Forge 依赖，首次较慢）
./gradlew runClient

# 启动开发服务器
./gradlew runServer
```

### 生成数据

```bash
./gradlew runData
# 生成方块/物品模型、语言模板到 src/generated/resources/
```

### 常见构建问题

| 问题 | 解决 |
|------|------|
| 内存不足 | 编辑 `gradle.properties`，增大 `org.gradle.jvmargs`（默认 -Xmx3G） |
| 映射下载失败 | 检查网络，或切换 `mapping_channel=parchment` |
| Java 版本错误 | 确保使用 JDK 17（`java -version`） |
| Forge 依赖 404 | 检查 `forge_version` 是否为 1.20.1 的有效版本 |

---

## 17. 已完成优化轮次总结

项目经历了 **18 轮**独立的全面优化迭代（详见 `docs/WORKLOG.md` 和 `docs/optimization-rounds-*.md`）：

| 轮次 | 主题 | 关键成果 |
|------|------|----------|
| **1-5** | 基础体系搭建 | 造物主级系统实现、MYTHIC 品质、预算系统、互斥机制 |
| **6-10** | 系统接线与 Bug 修复 | 造物主能力完整实现、精英生态链、觉醒系统、复仇系统 |
| **11** | 关键编译错误修复 | 删除 3 个死代码 handler 文件、修复 TextHelper/DominionScepter/AbilityClone 编译错误 |
| **12** | 关键 Bug 修复 | 跨维度追踪、觉醒 killCount 语义、竞态条件、Iron Wall 上限、命令系统 |
| **13** | 非功能系统接线 | 附魔接线、PurifyingTouch 改用 capability、替换废弃 AbilityManager、NBT 键统一、内存泄漏修复 |
| **14** | 性能优化与打磨 | capability 查询缓存、属性修饰符抖动、粒子上限、ThreadLocalRandom、最终验证 |
| **15** | 造物主能力 Bug 修复 | Nexus 自滋养、Annihilate 死亡触发、经验阈值、LevelRoman 边界 |
| **16** | 代码质量 | canCoexistWith、安全修饰符辅助、分发辅助 |
| **17** | 性能优化 | TRACKED_ELITES 迭代器模式、组加成状态追踪、非造物主缓存 |
| **18** | 内容与打磨 | i18n 硬编码清理、可配置觉醒阈值、CASUAL 消失配置、Javadoc、主类特性列表更新 |

### 总体数据
- **154 个 Java 文件**，~31000 行代码
- **54 个能力**（12+12+12+10+8）
- **363 个翻译键**（en_us + zh_cn 完美对齐）
- **19 对互斥 + 20 组协同**
- **5 个自定义方块 + 21 个物品 + 6 附魔 + 6 效果**

---

## 18. 已知问题与待办事项

### 已知限制

1. **造物主能力数量固定为 8**：不支持数据包扩展（硬编码在 `AbilityRegistry.init()`）。未来可考虑改为注册表驱动。

2. **客户端渲染为桩代码**：`EliteForge.registerBlockEntityRenderers()` 和 `registerClientEventListeners()` 目前仅打日志，实际渲染逻辑分散在 `render/` 包的 `@Mod.EventBusSubscriber(Dist.CLIENT)` 类中。这是 Forge 的常见模式，但文档注释可能误导。

3. ~~**无单元测试**：项目无测试代码（符合 Minecraft 模组生态惯例，但建议补充 GameTest）。~~ **✅ 已修复（2025-06-17）**：新增 JUnit 5 单元测试基础设施，覆盖 AbilityRegistry（54 能力完整性）、MutualExclusion（19 对互斥 + 造物主独占）、AbilityInteraction（20 组协同 + 双向缓存）、QualityTier（6 级品质 + 加权随机）、AbilityBudget（双预算 + CASUAL 限制）。运行 `./gradlew test`。详见 `src/test/java/com/eliteforge/`。

4. **ExampleMod 残留已清除**：`com.example.examplemod` 包已从发布版移除（原是 Forge MDK 模板）。

5. **文档计数已校准**（2025-06-17 修复）：互斥对数从错误的"21"修正为实际的 **19**（第 9 对 Storm↔Lightning 已移除）；协同组数从错误的"18"修正为实际的 **20"；主类 Javadoc 物品数从"17"修正为 **21**。`.editorconfig` 已添加统一代码风格。`convertToCreator` 中 `new Random(uuid.hashCode())` 已加注释说明"刻意可复现随机"。

6. **编译错误已修复**（2025-06-17 第3轮）：修复了 3 个文件的 10 个阻塞性编译错误（详见 R3 提交）。`EliteParticleRenderer`：引用不存在的 `CLIENT.renderDistance`→改用 `iconRenderDistance`；`cap.getLevel()` 等调用错误→改为接收 `EliteData`；switch case 用错枚举名；`getHighestCategory` 迭代类型错误。`AbilityIconRenderer`：缺失 5 个 import + 同样错误 + 缺少 `CREATOR` 分支。`EliteForgeEventsJS`：`getDifficulty()` 调用 4 个不存在的方法。

7. **i18n 硬编码已清理**（2025-06-17 第4轮）：第18轮声称已清理但实际遗漏 ~20 处。已修复 14 个文件（`EliteNameRenderer`/`LootHandler`/`TemperedMaterial`/`EliteRevenge`/7 个物品 tooltip），新增 14 个 i18n 键，lang 文件 366→380 键，去重对齐。

8. **ThreadLocalRandom 迁移完成**（2025-06-17 第5轮）：20 个文件 50+ 处 `new Random()`→`ThreadLocalRandom.current()`，符合 §19 规则 6。

### 建议的未来工作

#### 高优先级
- [x] ~~实现 GameTest 自动化测试~~ → 已用 JUnit 5 替代（更适合纯逻辑测试，无需 MC 结构文件）
- [x] ~~添加造物主能力的视觉特效~~ → **✅ 已完成**：新增 `CreatorAuraRenderer`，为造物主级精英渲染脉动红光环 + 地面光环环 + 上升火焰粒子，受 `showCreatorAura` 客户端配置控制
- [x] ~~完善客户端 GUI（锻造砧/回火台的容器界面）~~ → **✅ 已完成**：锻造砧 3 槽位容器（装备/材料/结果）、回火台 10 槽位容器（3×3 网格 + 结果），含 `MenuType` 注册、`AbstractContainerMenu`/`AbstractContainerScreen` 实现、`BlockEntity` 实现 `MenuProvider`、`Block.use()` 调用 `openMenu` 发送 BlockPos 数据
- [ ] 扩展 JUnit 测试覆盖至 EliteData NBT 序列化往返、DifficultyManager 属性修饰符计算
- [ ] 为容器 GUI 添加自定义纹理 PNG（当前使用纯色程序化背景）
- [ ] 为锻造砧/回火台添加 ContainerData 同步（锻造/回火进度条）

#### 中优先级
- [x] ~~造物主能力改为注册表驱动~~ → **✅ 前向兼容层已完成**：新增 `CreatorAbilityRegistry`，提供 `registerExtension()` 供数据包/KubeJS 注册新造物主能力，委托到 `AbilityRegistry`。完整迁移（将 8 个内置能力改为数据包驱动）待后续
- [x] ~~添加配置 GUI~~ → **✅ 桩代码已完成**：新增 `EliteForgeConfigScreen`，文档说明 Configured 库集成步骤。启用需取消 build.gradle 注释
- [ ] 实现造物主能力的成就/进度（advancement）
- [x] ~~JEI 集成~~ → **✅ 桩代码已完成**：新增 `EliteForgeJEIPlugin`，文档说明 4 个配方类别（锻造增强/品质升级/元素添加/元素合并）。启用需取消 build.gradle JEI 依赖注释

#### 低优先级
- [ ] 1.21+ 移植（需重写大量 API）
- [ ] Fabric 版本（架构差异大，需重写 capability → Cardboard/Trinkets）
- [ ] 多人服务器压力测试与性能剖析

---

## 19. 开发规范与约定

### 代码风格

1. **Java 17 特性可用**：record、sealed class、switch expression、var（适度）
2. **包结构**：`com.eliteforge.<feature>.<subfeature>`，每个能力一个文件
3. **命名**：
   - 能力类：`Ability<Name>`（如 `AbilityFire`）
   - 造物主能力：`Ability<Name>`（如 `AbilityNexus`），继承 `CreatorAbility`
   - 物品类：`<Name>`（如 `ForgingHammer`）
   - 配置项：`camelCase`，Javadoc 注释说明范围与默认值

### 关键约定（必须遵守）

1. **精英数据访问**：永远通过 `entity.getCapability(EliteCapability.CAPABILITY)`，**绝不**直接读写 NBT。唯一例外是 `EliteCapabilityStorage`（序列化）。

2. **NBT 键集中化**：所有 NBT 键定义在 `util/NBTKeys.java`，**绝不**硬编码字符串。常量前缀：
   - `ENTITY_*`：实体级（如 `ENTITY_IS_ELITE`）
   - `GROUP_*` / `KILL_*` / `TIME_*`：动态强化
   - `NEXUS_*` / `DOMINION_*` / `EVOLUTION_*` / `ASSIMILATE_*` / `BESTOWAL_*` / `ANNIHILATE_*` / `REINCARNATION_*` / `COMMANDER_*`：各造物主能力

3. **能力互斥校验**：新能力必须调用 `MutualExclusion.isMutuallyExclusive()` 检查，造物主能力始终强制互斥（不受配置开关影响）。

4. **属性修饰符**：
   - 使用 `safeAddMultiplierModifier` / `safeAddFlatModifier` / `safeRemoveModifier`（`CreatorAbility` 提供的辅助方法）
   - 每个 modifier 必须有**全局唯一 UUID**（当前共 25 个，已验证无冲突）
   - 动态强化使用 `lastBonus` 状态追踪，避免重复 add/remove

5. **国际化**：所有玩家可见文本用 `Component.translatable("key.eliteforge.xxx")`，并在 `en_us.json` + `zh_cn.json` 同步添加（两文件必须对齐）。

6. **随机数**：使用 `ThreadLocalRandom.current()`，**不**用 `new Random()`（性能）。

7. **集合迭代**：遍历 `TRACKED_ELITES` 等共享集合时，使用 `Iterator` 模式而非 `.stream().collect()`（避免每秒多次 ArrayList 拷贝）。

8. **配置校验**：新增配置项时，在 `EliteForge.validateServerConfig()` 添加交叉校验逻辑。

### Git 提交规范

- 提交信息格式：`<type>(<scope>): <description>`，如 `fix(creator): Nexus self-nurture now correctly targets self`
- type：`feat` / `fix` / `refactor` / `perf` / `docs` / `i18n` / `test`
- 每轮优化对应一个 `docs(worklog): Round N summary` 提交

---

## 20. 接手开发的快速入门清单

新接手的 AI 智能体/开发者，按以下步骤可在 30 分钟内进入工作状态：

### Step 1: 环境验证（5 分钟）
```bash
java -version          # 确认 JDK 17
./gradlew --version    # 确认 Gradle 可用
./gradlew compileJava  # 确认项目可编译（无编译错误）
```

### Step 2: 阅读核心文档（10 分钟）
1. 本文件（HANDOVER.md）—— 你正在读
2. `docs/elite-system-update-plan.md` —— 设计总纲（重点读第 2-4 节造物主级设计）
3. `docs/WORKLOG.md` 的**最后 3 个 Task ID** —— 了解最近变更

### Step 3: 阅读核心源码（10 分钟）
按以下顺序读，建立心智模型：
1. `EliteForge.java` —— 主类，理解启动流程
2. `ability/Ability.java` + `AbilityCategory.java` —— 能力基类
3. `ability/AbilityRegistry.java` —— 54 能力注册表（看 `init()` 方法）
4. `ability/MutualExclusion.java` —— 互斥规则
5. `capability/EliteData.java` —— 数据存储
6. `spawn/EliteSpawnHandler.java` —— 生成流程（`convertToElite` + `convertToCreator`）
7. `spawn/EliteEventHandler.java` —— 事件分发（最核心，~全系统枢纽）

### Step 4: 运行验证（5 分钟）
```bash
./gradlew runClient    # 启动游戏
# 在游戏中: /eliteforge spawn zombie legendary 5  ← 生成传说级僵尸
#           /eliteforge creator zombie nexus      ← 生成造物主级僵尸
```

### Step 5: 开始开发

- **修 Bug**：先在 `docs/WORKLOG.md` 搜索关键词，确认是否已知问题
- **加能力**：复制 `ability/attack/AbilityFire.java` 为模板，修改后注册到 `AbilityRegistry.init()`
- **加造物主能力**：复制 `ability/creator/AbilityNexus.java` 为模板，继承 `CreatorAbility`，注意 UUID 唯一性
- **改配置**：在 `EliteForgeConfig.java` 对应组别添加，在 `validateServerConfig()` 加校验
- **加翻译**：**同时**修改 `en_us.json` 和 `zh_cn.json`，保持对齐

### 关键文件速查表

| 需求 | 文件 |
|------|------|
| 理解整体架构 | `EliteForge.java` + 本文件 |
| 能力相关 | `ability/Ability*.java` |
| 造物主级 | `ability/creator/Ability*.java` + `docs/elite-system-update-plan.md` |
| 精英生成 | `spawn/EliteSpawnHandler.java` + `spawn/AbilityGenerator.java` |
| 事件处理 | `spawn/EliteEventHandler.java` |
| 数据存储 | `capability/EliteData.java` + `util/NBTKeys.java` |
| 配置 | `config/EliteForgeConfig.java` |
| 品质 | `quality/QualityTier.java` |
| 难度 | `difficulty/*.java` |
| 物品 | `item/*.java` + `init/ModItems.java` |
| 翻译 | `src/main/resources/assets/eliteforge/lang/*.json` |
| 历史变更 | `docs/WORKLOG.md` |
| 设计依据 | `docs/elite-system-update-plan.md` |

---

## 附录：术语表

| 术语 | 含义 |
|------|------|
| 精英怪 (Elite) | 被赋予额外能力/品质的普通怪物 |
| 词条 / 能力 (Ability) | 精英怪拥有的特殊技能（如点燃、闪电） |
| 品质 (Quality) | 精英怪的稀有度层级（普通→神话） |
| 预算 (Budget) | 限制精英怪能力数量与等级的积分系统 |
| 互斥 (Mutual Exclusion) | 两个能力不能同时存在于同一精英 |
| 协同 (Synergy) | 两个能力同时存在时触发额外加成 |
| 造物主级 (Creator Tier) | 最高层级，8 个独占型终极能力 |
| 神话品质 (MYTHIC) | 造物主级专属品质，超越传说 |
| 区块热度 (Chunk Heat) | 每区块 0-100 的累积值，影响生成与觉醒 |
| 觉醒 (Awakening) | 传说级精英转化为造物主的过程 |
| 淬火材料 (Tempered Material) | 精英掉落的锻造材料，分 6 品质 |
| FORGE / CASUAL / MIXED | 三种难度模式 |

---

*文档结束 — EliteForge v3 完整交接文档*

> 本文档由主开发智能体编写，涵盖截至第 18 轮优化的全部项目状态。任何接手开发者通读本文 + 设计总纲 + 工作日志后，应能完全独立地进行后续开发。如有疑问，优先查阅 `docs/WORKLOG.md` 中的历史决策记录。
