# EliteForge v3 — 第6-10轮全面优化迭代与精打细磨计划

> **文档版本**: v3.3
> **日期**: 2025-03-06
> **状态**: 执行中
> **前置**: 第1-5轮已完成（见 optimization-rounds-1-5.md）

---

## 一、当前状态评估

经过第1-5轮优化，以下问题已修复：
- ✅ B1: AbilityBestowal 幂等性检查
- ✅ B2: AbilityInteraction 所有 counter 类型处理
- ✅ B4: Ability.canCoexistWith null 处理
- ✅ Q1: DynamicStrengthening NBTKeys 迁移
- ✅ Q2: EliteAwakening NBTKeys 迁移
- ✅ Q3: MutualExclusion 注释校准（19对）
- ✅ 25个 AttributeModifier UUID 唯一性验证

### 新发现的问题清单

#### 🔴 NBT键集中化未完成
| # | 文件 | 问题 | 严重度 |
|---|------|------|--------|
| N1 | `NBTKeys.java` | 缺少8个通用实体数据键常量（IsElite/Level/QualityTier/Abilities/AbilityCount/Experience/ChunkHeat/BerserkLevel） | 高 |
| N2 | `EliteEventHandler.java:339,345` | 使用硬编码 `"EliteForge_BerserkRevived"` 而非 `NBTKeys.BERSERK_REVIVED`（常量已存在） | 高 |
| N3 | `SpawnEventHandler.java` | 7处硬编码 EliteForge_ 键字符串（put/get） | 中 |
| N4 | `RenderEventHandler.java` | 3处硬编码 EliteForge_ 键字符串 | 中 |
| N5 | `DifficultyEventHandler.java` | 4处硬编码 EliteForge_ 键字符串 | 中 |
| N6 | `PurifyingTouchEnchantment.java` | 4处硬编码 EliteForge_ 键字符串 | 中 |
| N7 | `EliteBaneEnchantment.java` | 1处硬编码 EliteForge_ 键字符串 | 低 |

#### 🟡 代码质量与防御性编程
| # | 文件 | 问题 | 优先级 |
|---|------|------|--------|
| Q1 | 多文件 | capability 查询 `.orElse(null)` 后未判空直接调用 | 中 |
| Q2 | 多文件 | AABB 查询结果未判空 | 中 |
| Q3 | `AbilityRegistry` | getAbility 返回 null 时的调用方处理不一致 | 中 |

#### 🟢 内容与平衡
| # | 文件 | 问题 | 优先级 |
|---|------|------|--------|
| C1 | `AbilityInteraction` | 可增加更多造物主级间 synergy | 中 |
| C2 | lang 文件 | 需验证所有 synergy/counter 翻译键完整 | 中 |
| C3 | `EntityPresetLoader` | 仅有3个 preset，可扩展 | 低 |
| C4 | `EliteForgeConfig` | 部分硬编码数值可配置化 | 低 |

---

## 二、5轮优化迭代详细计划

### 第6轮：NBT键集中化完成（N1-N7）

**目标**: 消除所有硬编码NBT键字符串，统一使用NBTKeys常量

1. **N1**: 在 `NBTKeys.java` 添加8个通用实体数据键常量
   - `ENTITY_IS_ELITE = "EliteForge_IsElite"`
   - `ENTITY_LEVEL = "EliteForge_Level"`
   - `ENTITY_QUALITY_TIER = "EliteForge_QualityTier"`
   - `ENTITY_ABILITIES = "EliteForge_Abilities"`
   - `ENTITY_ABILITY_COUNT = "EliteForge_AbilityCount"`
   - `PLAYER_EXPERIENCE = "EliteForge_Experience"`
   - `CHUNK_HEAT = "EliteForge_ChunkHeat"`
   - `BERSERK_LEVEL` 已存在，验证迁移

2. **N2**: 修复 `EliteEventHandler.java` BerserkRevived 硬编码
3. **N3-N7**: 迁移所有 handler/enchantment 文件的硬编码键

### 第7轮：空安全与防御性编程审计（Q1-Q3）

**目标**: 消除所有潜在NPE风险，确保健壮性

1. 审计所有 `getCapability().orElse(null)` 调用链
2. 审计 AABB 查询结果判空
3. 统一 `AbilityRegistry.getAbility()` 返回 null 的处理
4. 检查 stream/resource 关闭

### 第8轮：Synergy/Counter系统完善与平衡（C1-C2）

**目标**: 扩展交互系统，验证翻译完整性

1. 添加造物主级间 synergy（Nexus+Commander等）
2. 验证所有 synergy/counter 的 en_us/zh_cn 翻译键
3. 平衡性审查 multiplier

### 第9轮：内容与配置扩展（C3-C4）

**目标**: 丰富模组内容，提升可配置性

1. 扩展 entity preset（spider/enderman/blaze/witch）
2. 新增配置选项
3. 增强难度管理器

### 第10轮：最终打磨与文档同步

**目标**: 最终质量保证

1. 全量代码审查
2. UUID/NBT键唯一性最终验证
3. 设计文档同步
4. 性能热点审查
5. 完整性验证

---

## 三、执行优先级

1. **第6轮** (NBT集中化) — 最高优先级，防止键名漂移
2. **第7轮** (空安全) — 高优先级，防止运行时崩溃
3. **第8轮** (Synergy) — 中优先级，提升深度
4. **第9轮** (内容) — 中优先级，丰富体验
5. **第10轮** (打磨) — 收尾优先级

## 四、风险与注意事项

- 所有NBT键值不能改变（向后兼容存档）
- UUID不能改变
- 互斥规则修改需谨慎
- 新增内容需确保不影响现有生成平衡
