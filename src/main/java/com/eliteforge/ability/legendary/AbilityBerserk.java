package com.eliteforge.ability.legendary;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import com.eliteforge.util.NBTKeys;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * AbilityBerserk (涅槃) - Revive on death with a portion of health.
 * 
 * When killed: revive with (10% + level * 10%) health.
 * On revival: gain Speed II + Strength II for 10s.
 * Can only revive once per fight.
 * Explosion + totem particles.
 */
public class AbilityBerserk extends Ability {

    public AbilityBerserk() {
        super(
            new ResourceLocation("eliteforge", "berserk"),
            AbilityCategory.LEGENDARY,
            5.0f
        );
    }

    @Override
    public void onApply(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        entity.getPersistentData().putBoolean(NBTKeys.BERSERK_REVIVED, false);
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        // Berserk triggers on death
    }

    /**
     * H2 fix: This method is intentionally a no-op.
     * <p>
     * Previously, this method contained revival logic (mark BERSERK_REVIVED,
     * apply buffs, spawn particles). However, it was never actually called
     * because EliteEventHandler.onLivingDeath() handles Berserk revival
     * inline (it needs to cancel the LivingDeathEvent, which requires access
     * to the event object that onDeath() doesn't receive).
     * <p>
     * The EventHandler:
     * 1. Checks for Berserk ability + not-yet-revived flag
     * 2. Cancels the death event
     * 3. Heals the entity
     * 4. Applies Speed II + Strength II buffs
     * 5. Spawns particles + plays sound
     * 6. Returns early (skipping dispatchAbilityDeath for other abilities —
     *    this is intentional: if the entity revived, it didn't truly die, so
     *    other abilities' onDeath should not fire)
     * <p>
     * This method is kept as an explicit no-op with documentation rather than
     * removed entirely, so that future developers don't try to add revival
     * logic here (it won't work — use EliteEventHandler instead).
     *
     * @param entity the entity that "died"
     * @param level  the ability level
     */
    @Override
    public void onDeath(LivingEntity entity, int level) {
        // Intentionally empty — see Javadoc above.
        // Berserk revival is handled by EliteEventHandler.onLivingDeath().
    }

    @Override
    public void onPlayerKill(LivingEntity entity, net.minecraft.world.entity.player.Player player, int level) {
        // Reset revived flag for next engagement (if somehow applicable)
    }
}
