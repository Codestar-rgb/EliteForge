package com.eliteforge.util;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Illusioner;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Stray;
import net.minecraft.world.item.Items;

/**
 * Utility class providing common helper methods for working with Minecraft mobs.
 * <p>
 * Centralizes mob-related checks that are used across multiple systems
 * (e.g., ability logic, spawn ecosystem) to avoid duplication.
 */
public final class MobHelper {

    private MobHelper() {
        // Utility class - no instantiation
    }

    /**
     * Checks whether the given mob is a ranged attacker.
     * <p>
     * A mob is considered ranged if it holds a ranged weapon in its main hand
     * (bow, crossbow, or trident) or if it is an entity type known to perform
     * ranged attacks (Skeleton, Stray, Pillager, Illusioner, Ghast).
     *
     * @param mob the mob to check
     * @return {@code true} if the mob is classified as a ranged attacker
     */
    public static boolean isRangedMob(Mob mob) {
        var mainHandItem = mob.getMainHandItem();
        return mainHandItem.is(Items.BOW)
                || mainHandItem.is(Items.CROSSBOW)
                || mainHandItem.is(Items.TRIDENT)
                || mob instanceof Skeleton
                || mob instanceof Stray
                || mob instanceof Pillager
                || mob instanceof Illusioner
                || mob instanceof Ghast;
    }
}
