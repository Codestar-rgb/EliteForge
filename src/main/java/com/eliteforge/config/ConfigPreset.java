package com.eliteforge.config;

import com.eliteforge.EliteForge;
import net.minecraft.network.chat.Component;

/**
 * Config presets — one-shot difficulty profiles that atomically set ~20 server
 * config values to give the server a coherent "feel" without forcing operators
 * to hand-tune each knob.
 * <p>
 * Applied via {@code /eliteforge config preset <name>}. Each preset writes the
 * values to the in-memory {@link EliteForgeConfig} and then saves the spec to
 * disk so the change persists across restarts.
 * <p>
 * Presets are intentionally opinionated. Operators can still tweak individual
 * fields after applying a preset.
 */
public enum ConfigPreset {
    /** Balanced defaults — the shipped config. Moderate spawn rate, level curve,
     *  and ability budget. Good for most survival servers. */
    BALANCED("balanced"),

    /** Hardcore — high spawn chance, fast heat ramp, strong abilities, frequent
     *  creator-tier. For servers that want EliteForge to be the central challenge. */
    HARDCORE("hardcore"),

    /** Casual — low spawn chance, capped levels, weaker abilities, no creator-tier.
     *  For servers that want elites as occasional flavor, not a wall. */
    CASUAL("casual"),

    /** Sandbox — everything maxed, creator-tier common, cheap abilities. For
     *  testing, showcase worlds, or chaos servers. */
    SANDBOX("sandbox"),

    /** Nightmare — extreme spawn rate, max levels, lethal abilities, aggressive
     *  revenge. Not recommended for normal play. */
    NIGHTMARE("nightmare");

    private final String id;

    ConfigPreset(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    /** Human-readable display name for chat feedback. */
    public Component getDisplayName() {
        return Component.translatable("config.eliteforge.preset." + id);
    }

    /** Parse a preset name (case-insensitive). Returns null if no match. */
    public static ConfigPreset byId(String name) {
        if (name == null) return null;
        for (ConfigPreset p : values()) {
            if (p.id.equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    /**
     * Apply this preset's values to the in-memory COMMON + SERVER config specs.
     * Does NOT save to disk — the caller ({@code /eliteforge config preset})
     * handles persistence via {@code SPEC.save()}.
     * <p>
     * Each preset targets a curated subset of "feel" knobs: difficulty mode,
     * spawn chances, max levels, heat gain/decay, ability budget, creator-tier
     * gating, and the revenge threshold. Cosmetic/performance fields (particles,
     * chain links, render distance) are left untouched.
     */
    public void apply() {
        EliteForgeConfig.Common c = EliteForgeConfig.COMMON;
        EliteForgeConfig.Server s = EliteForgeConfig.SERVER;
        switch (this) {
            case BALANCED -> {
                c.difficultyMode.set(DifficultyMode.FORGE);
                c.enableEliteMobs.set(true);
                c.globalSpawnChance.set(0.12);
                c.maxEliteLevel.set(1500);
                c.maxCasualLevel.set(150);
                c.forgeModeSpawnChance.set(0.12);
                c.casualModeSpawnChance.set(0.04);
                s.chunkHeatGainOnEliteSpawn.set(0.5);
                s.chunkHeatGainOnEliteKill.set(2.0);
                s.chunkHeatDecayRate.set(0.05);
                s.playerExperienceGainOnEliteKill.set(1.0);
                s.enableCreatorTier.set(true);
                s.creatorSpawnChanceForge.set(0.001);
                s.enableRevengeSystem.set(true);
                s.revengeKillThreshold.set(15);
                s.bestowalRevertTicks.set(600);
                s.enableDynamicStrengthening.set(true);
                s.enableAwakening.set(true);
            }
            case HARDCORE -> {
                c.difficultyMode.set(DifficultyMode.FORGE);
                c.enableEliteMobs.set(true);
                c.globalSpawnChance.set(0.22);
                c.maxEliteLevel.set(2000);
                c.maxCasualLevel.set(300);
                c.forgeModeSpawnChance.set(0.22);
                c.casualModeSpawnChance.set(0.06);
                s.chunkHeatGainOnEliteSpawn.set(1.0);
                s.chunkHeatGainOnEliteKill.set(4.0);
                s.chunkHeatDecayRate.set(0.03);
                s.playerExperienceGainOnEliteKill.set(2.0);
                s.enableCreatorTier.set(true);
                s.creatorSpawnChanceForge.set(0.003);
                s.enableRevengeSystem.set(true);
                s.revengeKillThreshold.set(10);
                s.bestowalRevertTicks.set(900);
                s.enableDynamicStrengthening.set(true);
                s.enableAwakening.set(true);
            }
            case CASUAL -> {
                c.difficultyMode.set(DifficultyMode.CASUAL);
                c.enableEliteMobs.set(true);
                c.globalSpawnChance.set(0.06);
                c.maxEliteLevel.set(500);
                c.maxCasualLevel.set(100);
                c.forgeModeSpawnChance.set(0.06);
                c.casualModeSpawnChance.set(0.03);
                s.chunkHeatGainOnEliteSpawn.set(0.3);
                s.chunkHeatGainOnEliteKill.set(1.0);
                s.chunkHeatDecayRate.set(0.10);
                s.playerExperienceGainOnEliteKill.set(0.5);
                s.enableCreatorTier.set(false);
                s.creatorSpawnChanceForge.set(0.0);
                s.enableRevengeSystem.set(false);
                s.revengeKillThreshold.set(30);
                s.bestowalRevertTicks.set(300);
                s.enableDynamicStrengthening.set(false);
                s.enableAwakening.set(false);
            }
            case SANDBOX -> {
                c.difficultyMode.set(DifficultyMode.FORGE);
                c.enableEliteMobs.set(true);
                c.globalSpawnChance.set(0.50);
                c.maxEliteLevel.set(1500);
                c.maxCasualLevel.set(150);
                c.forgeModeSpawnChance.set(0.50);
                c.casualModeSpawnChance.set(0.20);
                s.chunkHeatGainOnEliteSpawn.set(2.0);
                s.chunkHeatGainOnEliteKill.set(8.0);
                s.chunkHeatDecayRate.set(0.01);
                s.playerExperienceGainOnEliteKill.set(5.0);
                s.enableCreatorTier.set(true);
                s.creatorSpawnChanceForge.set(0.05);
                s.enableRevengeSystem.set(false);
                // revengeKillThreshold is defineInRange(1, 50) — use the max so SANDBOX
                // effectively disables revenge even if the system is later re-enabled.
                s.revengeKillThreshold.set(50);
                s.bestowalRevertTicks.set(1200);
                s.enableDynamicStrengthening.set(true);
                s.enableAwakening.set(true);
            }
            case NIGHTMARE -> {
                c.difficultyMode.set(DifficultyMode.FORGE);
                c.enableEliteMobs.set(true);
                c.globalSpawnChance.set(0.35);
                c.maxEliteLevel.set(3000);
                c.maxCasualLevel.set(500);
                c.forgeModeSpawnChance.set(0.35);
                c.casualModeSpawnChance.set(0.10);
                s.chunkHeatGainOnEliteSpawn.set(1.5);
                s.chunkHeatGainOnEliteKill.set(6.0);
                s.chunkHeatDecayRate.set(0.02);
                s.playerExperienceGainOnEliteKill.set(3.0);
                s.enableCreatorTier.set(true);
                s.creatorSpawnChanceForge.set(0.008);
                s.enableRevengeSystem.set(true);
                s.revengeKillThreshold.set(5);
                s.bestowalRevertTicks.set(1200);
                s.enableDynamicStrengthening.set(true);
                s.enableAwakening.set(true);
            }
        }
        EliteForge.LOGGER.info("Applied EliteForge config preset: {}", id);
    }
}
