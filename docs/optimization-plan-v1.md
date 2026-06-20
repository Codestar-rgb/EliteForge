# EliteForge v3 — 5轮全面优化迭代计划

## 代码分析发现

### 🔴 关键Bug与不一致
1. **Health Modifier不一致**: EliteSpawnHandler用MULTIPLY_BASE, EliteRevenge用ADDITION, EliteAwakening用ADDITION — 同一功能（神话级血量翻倍）使用了3种不同方式
2. **TRACKED_ELITES内存泄漏**: Entity死亡/移除时未从TRACKED_ELITES清理
3. **EliteRevenge squad不检查互斥**: spawnEliteSquad中随机生成能力时不检查MutualExclusion
4. **Assimilate invuln timer双管理风险**: onTick说"do NOT decrement"但仅靠注释约束
5. **Bestowal reversion timer处理分散**: 在EliteEventHandler.tickCreatorNbtTimers中处理但缺少完整弱化效果

### 🟡 机制完善
6. **QualityTier.MYTHIC的weight=0**: 设计文档提到0.1但代码为0（canRollNaturally=false合理）
7. **AbilityManager @Deprecated残留**: 仍存在且可能造成混淆
8. **造物主能力描述使用Unicode中间点(·)**: en_us.json用"·"而非英文习惯
9. **缺少造物主级协同/反制条目**: AbilityInteraction无creator相关条目
10. **Dominion区域20%精英转化率未实现**: 设计文档提到但代码中缺少

### 🟢 优化机会
11. **Duplicate DifficultyManager实例**: EliteSpawnHandler和EliteEventHandler各创建一个
12. **NBT key常量分散**: 无集中管理
13. **Scorched Earth zone无清理机制**: 可能产生孤立区域数据
14. **Creator ability maxAbilities交互**: 同化/轮回增加能力时未充分考虑maxAbilities限制

---

## 第1轮: 关键Bug修复与一致性校准
- 修复Health Modifier操作不一致(统一为MULTIPLY_BASE)
- 修复TRACKED_ELITES内存泄漏
- 修复EliteRevenge squad互斥检查缺失
- 修复EliteRevenge.spawnCreatorElite health modifier不一致
- 强化Assimilate invuln timer安全机制

## 第2轮: 机制完善与造物主级系统一致性
- 实现Dominion区域精英转化率(20%)
- 完善Bestowal reversion弱化效果
- 添加Scorched Earth zone清理机制
- 完善creator entity maxAbilities交互
- 清理AbilityManager @Deprecated残留

## 第3轮: 语言文件完善与协同/反制系统扩展
- 修复en_us.json造物主能力名称(使用英文命名惯例)
- 添加造物主级协同条目到AbilityInteraction
- 添加造物主级反制条目到AbilityInteraction
- 完善creator tier相关tooltip和message条目
- 添加缺失的quality.mythic相关描述

## 第4轮: 性能优化与代码质量提升
- 消除Duplicate DifficultyManager实例
- 集中NBT key常量到NBTKeys类
- 优化TRACKED_ELITES清理策略
- 优化creator ability onTick中的NBT missing check模式
- 改进Scorched Earth zone tick性能

## 第5轮: 最终精磨与全系统校验
- 全面检查所有creator ability生命周期正确性
- 校验所有互斥规则完整性
- 校验所有属性修饰符UUID唯一性
- 同步更新设计文档
- 最终代码质量审查
