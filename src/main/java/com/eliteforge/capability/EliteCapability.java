package com.eliteforge.capability;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;

/**
 * Capability interface for attaching elite mob data to living entities.
 * Provides get/set access to the EliteData stored on the entity.
 */
public interface EliteCapability {

    Capability<EliteCapability> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});

    /**
     * Get the elite data attached to this entity.
     *
     * @return the EliteData instance (never null)
     */
    EliteData getEliteData();

    /**
     * Set the elite data for this entity, replacing any existing data.
     *
     * @param data the new EliteData to set (must not be null)
     */
    void setEliteData(EliteData data);

    /**
     * Check if this entity is marked as an elite mob.
     *
     * @return true if the entity is an elite
     */
    boolean isElite();

    /**
     * Set whether this entity is an elite mob.
     *
     * @param elite true to mark as elite, false to remove elite status
     */
    void setElite(boolean elite);

    /**
     * Register this capability with Forge. Called during the RegisterCapabilitiesEvent.
     *
     * @param event the register capabilities event
     */
    static void register(RegisterCapabilitiesEvent event) {
        event.register(EliteCapability.class);
    }
}
