# EliteForge v3 — 第11-14轮全面优化迭代与精打细磨计划

> **文档版本**: v3.4
> **日期**: 2025-03-06
> **状态**: 执行中
> **前置**: 第1-10轮已完成（见 optimization-rounds-1-5.md 与 optimization-rounds-6-10.md）

---

## 一、当前状态评估

经过3个独立审计代理对157个Java文件（31500行）的全面审查，发现 **200+ 个问题**，其中**关键编译错误**会导致模组完全无法构建。

### 审计关键发现

#### 🔴 关键编译错误（模组无法构建）

| # | 文件 | 问题 | 严重度 |
|---|------|------|--------|
| C1 | `handler/SpawnEventHandler.java` | 使用不存在的 `QualityTier.COMMON/UNCOMMON/RARE`（实际为 NORMAL/GOOD/FINE） | 致命 |
| C2 | `handler/SpawnEventHandler.java` | `Ability.values()` —— Ability 是抽象类非枚举，无 values() | 致命 |
| C3 | `handler/SpawnEventHandler.java` | `Ability.AbilityCategory` —— AbilityCategory 是顶层枚举，非嵌套 | 致命 |
| C4 | `handler/SpawnEventHandler.java` | `tier.getMaxAbilities()` / `tier.getStatMultiplier()` / `tier.getId()` —— QualityTier 无这些方法 | 致命 |
| C5 | `handler/SpawnEventHandler.java` | `switch(ability) case PHASE/ARMORED/UNDYING/CLONE` —— Ability 非枚举 | 致命 |
| C6 | `handler/DifficultyEventHandler.java` | 同样的 QualityTier 不存在值 + 非穷尽 switch | 致命 |
| C7 | `util/TextHelper.java` | `getCategoryColor` switch 缺少 CREATOR case（非穷尽） | 致命 |
| C8 | `util/TextHelper.java` | `getQualityColor` switch 缺少 MYTHIC case（非穷尽） | 致命 |
| C9 | `util/TextHelper.java` | `Ability.AbilityCategory` 引用错误 | 致命 |
| C10 | `ability/legendary/AbilityClone.java` | 缺少 `import java.util.Map` | 致命 |
| C11 | `item/DominionScepter.java` | 缺少 `import net.minecraft.world.effect.MobEffects` | 致命 |

**关键发现**: `handler/` 包中的 3 个文件（SpawnEventHandler、DifficultyEventHandler、RenderEventHandler）是**早期开发的死代码**，从未被任何代码引用（实际事件处理在 `spawn/EliteEventHandler.java`）。但 Java 编译所有源文件，这些编译错误会导致整个模组构建失败。

#### 🔴 关键 Bug（系统失效）

| # | 文件 | 问题 |
|---|------|------|
| B1 | `EliteEcosystem.java` | 跨维度造物主追踪失效：`level.getEntity(uuid)` 对其他维度实体返回 null，被误判为失效移除 |
| B2 | `EliteAwakening.java` | 觉醒永不触发：`killCount` 在精英**死亡**时递增（应为精英**击杀玩家**时） |
| B3 | `EliteSpawnHandler.convertToCreator` | capability 缺失时仍返回 true（成功），实体获得神话属性但无能力/同步 |
| B4 | `ChunkHeatManager` / `PlayerExperienceManager` | 非原子 read-modify-write 竞态条件 |
| B5 | `EliteEventHandler` Iron Wall/Evade | 缺少上限检查，高等级时 reduction/dodge > 1.0 |
| B6 | `EliteForgeCommand` spawn/reroll/setlevel/creator | 缺少 onApply/onRemove、属性缩放、造物主状态设置 |
| B7 | 3 个附魔（EliteBane/HeatShield/ForgingMaster） | 核心方法从未被调用，附魔无效 |
| B8 | `PurifyingTouchEnchantment` | 直接修改 NBT 而非 capability，净化不生效 |
| B9 | 4 个能力（ArrowRain/Shield/Knockback/Supreme） | 使用已废弃的 AbilityManager，周期效果失效 |
| B10 | 7 个物品 | 检查 `forge.eliteforge:elite` 子复合标签，但生成系统写 `EliteForge_IsElite` 直接键 —— 物品无法检测精英 |
| B11 | `RerollScroll` | ABILITY_POOL 含 12/20 无效能力 ID；设置永久无敌无移除处理 |
| B12 | `TRACKED_ELITES` / `ACTIVE_CREATORS` | 区块卸载/实体移除时无清理，内存泄漏 |

#### 🟡 性能问题
- `EliteEventHandler.onLivingHurt` 同一实体 4 次重复 capability 查询
- `DynamicStrengthening` 每 20 tick 无条件重添加属性修饰符
- `S2CParticleEvent` 无粒子数量上限（恶意包可致客户端卡顿）
- 多处 `new Random()` 而非 `ThreadLocalRandom.current()`

#### 🟢 内容与 i18n
- 6+ 处硬编码中文字符串未使用翻译键
- `DifficultyMode` 显示名硬编码英文
- 实体预设部分功能（abilityWeights/budgetOverrides 等）加载但未使用

---

## 二、4轮优化迭代详细计划

### 第11轮：关键编译错误修复与死代码清除

**目标**: 让模组能够成功编译构建

1. **删除 3 个死代码 handler 文件**（C1-C6）
   - `handler/SpawnEventHandler.java` —— 完全死代码，从未注册
   - `handler/DifficultyEventHandler.java` —— 完全死代码
   - `handler/RenderEventHandler.java` —— 完全死代码
   - 实际事件处理在 `spawn/EliteEventHandler.java`（@Mod.EventBusSubscriber 注册）

2. **修复 TextHelper.java**（C7-C9）
   - `Ability.AbilityCategory` → `AbilityCategory`（顶层枚举）
   - `getCategoryColor` 添加 `CREATOR` case
   - `getQualityColor` 添加 `MYTHIC` case
   - 添加缺失的翻译键 `display.eliteforge.elite_prefix`

3. **修复 AbilityClone.java**（C10）
   - 添加 `import java.util.Map`

4. **修复 DominionScepter.java**（C11）
   - 添加 `import net.minecraft.world.effect.MobEffects`

5. **验证编译**: grep 确认无残留 `QualityTier.COMMON/UNCOMMON/RARE`、`Ability.values()`、`Ability.AbilityCategory`

### 第12轮：关键 Bug 修复与系统校准

**目标**: 修复导致核心系统失效的 Bug

1. **修复 EliteEcosystem 跨维度追踪**（B1）
   - 在 CreatorInfo 存储维度 key
   - 仅当维度相同且实体未找到时才移除
   - 或使用 `server.getPlayerList().getPlayer(uuid)` 跨维度查询

2. **修复 EliteAwakening killCount 语义**（B2）
   - 在精英击杀玩家时递增 killCount（非精英死亡时）
   - 移至 player-death 处理路径

3. **修复 convertToCreator 错误成功**（B3）
   - 检查 capability 是否存在，缺失时返回 false
   - 不应用神话属性修饰符直到 capability 就绪

4. **修复非原子 RMW 竞态**（B4）
   - `ChunkHeatManager.addHeat/reduceHeat/decayHeat` → 使用 `compute()` 或 `merge()`
   - `PlayerExperienceManager.addExperience` → 同上

5. **修复 Iron Wall/Evade 上限**（B5）
   - `reduction = Math.min(0.95f, 0.10f + ironWallLevel * 0.08f)`
   - `dodgeChance = Math.min(0.95f, 0.05f + evadeLevel * 0.07f)`

6. **修复 DifficultyManager 边界**（B5 续）
   - `LEVEL_SYMBOLS[Math.max(0, Math.min(level - 1, LEVEL_SYMBOLS.length - 1))]`

7. **修复 EliteForgeCommand 命令**（B6）
   - spawnElite: 添加 `DifficultyManager.applyEliteModifiers` + `EliteCapabilitySync.broadcastEliteDataUpdate`
   - rerollAbilities: 调用旧能力 `onRemove()` + 新能力 `onApply()`
   - setLevel: 同上
   - creatorSpawn/creatorAwaken: 设置所有造物主字段 + `EliteEcosystem.registerCreator()`

### 第13轮：非功能系统接线与内容扩展

**目标**: 让已实现但未接线的系统真正生效

1. **接线 3 个附魔**（B7）
   - EliteBaneEnchantment: 在 `DifficultyEventHandler.onLivingHurt` 调用 bonus damage
   - HeatShieldEnchantment: 在 LivingHurtEvent 调用 heat damage reduction
   - ForgingMasterEnchantment: 在 anvil/tempering 事件调用

2. **修复 PurifyingTouchEnchantment**（B8）
   - 通过 `entity.getCapability(EliteCapability.CAPABILITY)` 修改能力
   - 而非直接修改 NBT

3. **替换已废弃 AbilityManager 依赖**（B9）
   - AbilityArrowRain: `AbilityManager.getTickCounter` → 静态 tick 计数
   - AbilityShield: `AbilityManager.getEntityAbilities` → capability 查询
   - AbilityKnockback: 同上
   - AbilitySupreme: 同上

4. **修复物品 NBT 检测**（B10）
   - 7 个物品: `getPersistentData().getCompound("forge").getBoolean("eliteforge:elite")` → `getPersistentData().getBoolean(NBTKeys.ENTITY_IS_ELITE)`

5. **修复 RerollScroll**（B11）
   - 替换 ABILITY_POOL 为 `AbilityRegistry.getAllAbilities()` 动态查询
   - 移除永久无敌，改用临时效果或定时移除

6. **修复内存泄漏**（B12）
   - `TRACKED_ELITES`: 在 EntityLeaveLevelEvent / chunk unload 清理
   - `ACTIVE_CREATORS`: 在实体死亡/移除时清理

7. **添加缺失 i18n**
   - 6+ 处硬编码中文 → Component.translatable
   - DifficultyMode 显示名 → 翻译键

### 第14轮：性能优化、打磨与最终验证

**目标**: 性能提升与最终质量保证

1. **缓存 capability 查询**
   - `EliteEventHandler.onLivingHurt`: 同一实体 4 次查询 → 1 次

2. **减少属性修饰符抖动**
   - `DynamicStrengthening.tickGroupStrengthening`: 添加 `lastGroupBonus` 检查
   - 同理 heat attack speed modifier

3. **粒子数量上限**
   - `S2CParticleEvent.handle`: `count = Math.min(200, count)`

4. **清理死代码**
   - 移除 `EliteData.disengage()` 等未调用方法
   - 移除 `antiFarmRadius` 死配置
   - 移除 SpawnEventHandler 死代码块

5. **标准化 Random 使用**
   - 所有 `new Random()` → `ThreadLocalRandom.current()`

6. **标准化属性修饰符持久化**
   - 统一使用 transient modifier（避免 save/load 时叠加）

7. **修复静默异常捕获**
   - 多处 `catch(Exception)` 无堆栈 → `LOGGER.debug("...", e)`

8. **最终验证**
   - UUID 唯一性验证
   - NBT 键集中化验证
   - 括号平衡检查
   - JSON 文件语法验证
   - 翻译键完整性验证

9. **文档同步**
   - 更新 elite-system-update-plan.md
   - 更新 worklog.md

---

## 三、执行原则

1. **先修复编译错误，再修复 Bug，最后优化性能**
2. **每个修改都要验证不引入新编译错误**
3. **保留向后兼容（NBT 键值不变）**
4. **优先删除死代码，而非修复死代码**
5. **使用 `grep` 验证修改的完整性**

---

*文档结束 — EliteForge v3 第11-14轮优化迭代计划*
