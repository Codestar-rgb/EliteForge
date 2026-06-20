# EliteForge v3 — 5轮全面优化迭代与精打细磨计划

> **文档版本**: v3.2  
> **日期**: 2025-03-05  
> **状态**: 执行中

---

## 一、已识别问题清单

### 🔴 严重Bug/编译错误

| # | 文件 | 问题 | 严重度 |
|---|------|------|--------|
| B1 | `AbilityBestowal.onTick` | 缺少幂等性检查模式。当NBT键不存在时直接调用onApply()而不先检查capability是否已有creator数据，可能导致重复应用属性修饰符 | 高 |
| B2 | `AbilityInteraction.getActiveCounters` | switch语句仅处理3种counter类型(quench_stone/wither_effect/invisibility_hit)，但定义了9个CounterEntry，缺失6种counter类型的处理逻辑 | 高 |
| B3 | `EliteEventHandler` | 使用硬编码字符串`"EliteForgeReincarnationReviving"`而非NBTKeys常量 | 中 |
| B4 | `Ability.canCoexistWith` | `other == null`时返回`false`，语义错误——null不应视为互斥，应返回`true` | 中 |

### 🟡 代码质量问题

| # | 文件 | 问题 | 优先级 |
|---|------|------|--------|
| Q1 | `DynamicStrengthening` | 使用本地KEY_*常量而非NBTKeys集中常量，不一致 | 中 |
| Q2 | `EliteAwakening` | 使用本地KEY_*常量而非NBTKeys集中常量，不一致 | 中 |
| Q3 | `MutualExclusion` | 注释称"20 mutual exclusion pairs"但实际为19对(Storm↔Lightning被注释移除) | 低 |
| Q4 | `AbilityManager` | 已标记@Deprecated但仍在AbilityRegistry.init()中被间接引用，应添加更完整的迁移文档 | 低 |
| Q5 | `QualityTier` | `next()`方法在MYTHIC(最大ordinal)时返回自身，应明确文档说明 | 低 |

### 🟢 内容完善

| # | 文件 | 问题 | 优先级 |
|---|------|------|--------|
| C1 | `AbilityInteraction` | 缺少更多造物主级counter条目(Nexus/Evolution/Bestowal缺少counter) | 中 |
| C2 | `en_us.json/zh_cn.json` | 缺少部分synergy和counter的完整翻译键 | 中 |
| C3 | `EntityPresetLoader` | 仅有3个entity preset(zombie/skeleton/creeper)，缺少更多常见mob | 低 |
| C4 | `AbilityInteraction` | 可增加更多有意义的synergy对 | 低 |

---

## 二、5轮优化迭代详细计划

### 第1轮：Bug修复与关键校准 (B1-B4, Q3)

**目标**: 修复所有严重Bug和编译错误，确保系统行为正确

1. **B1**: 修复`AbilityBestowal.onTick`幂等性检查
   - 添加与其他creator能力相同的capability检查模式
   - 在onTick开头：检查NBT键不存在时先检查capability，如果已有creator数据则只初始化NBT

2. **B2**: 修复`AbilityInteraction.getActiveCounters`缺失的counter类型处理
   - 添加purifying_touch、heat_shield、elite_bane、piercing counter类型的switch分支
   - purifying_touch: 检查玩家手持或装备是否有净化之触附魔
   - heat_shield: 检查玩家装备是否有热力屏障附魔
   - elite_bane: 检查玩家手持武器是否有精英克星附魔
   - piercing: 始终返回true(穿刺是基础攻击属性)

3. **B3**: 修复`EliteEventHandler`硬编码NBT字符串
   - 替换所有硬编码NBT字符串为NBTKeys常量引用

4. **B4**: 修复`Ability.canCoexistWith`null处理
   - 将`other == null`时返回`false`改为返回`true`

5. **Q3**: 修复`MutualExclusion`注释
   - 更新"20 mutual exclusion pairs"为"19 mutual exclusion pairs"

### 第2轮：NBT键集中化与代码一致性 (Q1-Q2)

**目标**: 统一NBT键引用，消除硬编码字符串

1. **Q1**: 迁移`DynamicStrengthening`的本地KEY_*常量到NBTKeys引用
   - 保持向后兼容（NBT键值不变）
   - 更新DynamicStrengthening中所有本地常量为NBTKeys引用

2. **Q2**: 迁移`EliteAwakening`的本地KEY_*常量到NBTKeys引用
   - 更新EliteAwakening中所有本地常量为NBTKeys引用

3. 检查所有其他文件是否还有硬编码NBT键字符串
   - 在EliteEventHandler、EliteSpawnHandler等文件中搜索

4. 确保NBTKeys包含所有需要的键常量

### 第3轮：Counter/Synergy系统完善 (C1-C2)

**目标**: 完善交互系统，添加缺失的counter和翻译

1. **C1**: 扩展`AbilityInteraction`的counter系统
   - 添加Nexus counter: "净化之触减缓源核滋养频率"
   - 添加Bestowal counter: "精英克星减少赐能成功率"
   - 完善getActiveCounters中的switch处理逻辑

2. **C2**: 补全语言文件
   - 为所有新增counter添加en_us.json和zh_cn.json条目
   - 为所有新增synergy添加翻译条目
   - 检查并补全遗漏的翻译键

3. 添加更多有意义的synergy对
   - Fire + Explosion = "炼狱" — 爆炸伤害点燃所有受影响目标
   - Regen + Immunity = "不朽" — 免疫效果期间再生速度翻倍
   - Web + Immobilize = "死牢" — 禁锢持续时间延长50%

### 第4轮：内容增强与扩展 (C3-C4)

**目标**: 丰富模组内容，提升游戏体验

1. **C3**: 扩展entity preset
   - 添加spider.json: 黑名单=fire(蜘蛛已是火焰免疫), 强制=web
   - 添加enderman.json: 黑名单=web/immobilize(末影人可传送逃脱), 强制=phase_shift
   - 添加blaze.json: 黑名单=fire/poison(烈焰人免疫火焰和中毒)
   - 添加witch.json: 黑名单=poison/corrosion(女巫免疫中毒), 强制=siphon

2. **C4**: 扩展synergy系统
   - 实现第3轮设计的新synergy对
   - 添加新的creator级synergy(造物主能力之间的联动)
   - Nexus + Commander = "蜂巢意志" — 号令范围和效率提升

3. 增强config选项
   - 为新synergy添加配置开关
   - 为dynamic strengthening的参数添加更多config选项

### 第5轮：最终打磨与文档同步

**目标**: 最终打磨所有代码，同步文档，确保系统完整性

1. 代码审查与最终Bug扫描
   - 检查所有修改文件的编译兼容性
   - 验证NBT键唯一性
   - 验证UUID唯一性(AttributeModifier)
   - 检查所有null安全性

2. 文档同步
   - 更新elite-system-update-plan.md中的状态
   - 确保所有设计文档与实际代码一致

3. 性能优化
   - 检查热点路径(AABB查询、capability查询)
   - 确保tick频率合理
   - 优化不必要的每tick操作

4. 最终集成测试
   - 验证所有52个能力的onApply/onTick/onHurt/onDeath/onRemove
   - 验证互斥系统完整性
   - 验证造物主级独占机制
   - 验证C4同化和C7轮回突破例外

---

## 三、执行优先级

1. **第1轮** (Bug修复) — 最高优先级，必须首先完成
2. **第2轮** (NBT集中化) — 高优先级，防止未来键名冲突
3. **第3轮** (Counter/Synergy) — 中优先级，提升游戏深度
4. **第4轮** (内容扩展) — 中优先级，丰富模组内容
5. **第5轮** (最终打磨) — 收尾优先级，确保质量

---

## 四、风险与注意事项

- 所有NBT键值不能改变(向后兼容存档数据)
- UUID不能改变(已有存档中的AttributeModifier引用)
- 互斥规则修改需谨慎(影响生成平衡性)
- 新增counter/synergy需确保不影响现有生成权重
