package com.eliteforge.network;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityRegistry;
import com.eliteforge.capability.EliteCapabilityStorage;
import com.eliteforge.capability.EliteData;
import com.eliteforge.quality.QualityTier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Packet for syncing elite entity data from server to client.
 * Sends the entity ID, abilities with their levels, and quality tier
 * so the client can render name plates, ability icons, and particles.
 *
 * This packet is sent whenever an elite's data changes or when a player
 * starts tracking an elite entity.
 *
 * Client-side data is stored ONLY in EliteCapabilityStorage.
 */
public class S2CEliteDataSync {

    private int entityId;
    private Map<String, Integer> abilities;
    private int qualityTierOrdinal;
    private int eliteLevel;
    // Explicit isElite flag — the server's authoritative answer to "is this an elite?".
    // Previously the client inferred it from `!abilities.isEmpty() || eliteLevel > 0`, which
    // broke for Necromancy summons (not elite, but level defaults to 1) and for reverted
    // bestowed elites (level clamps to >=1).
    private boolean isElite;
    // Creator-tier fields
    private boolean isCreatorEntity;
    private String creatorAbilityId;
    private int creatorAbilityLevel;
    // Owner-link UUIDs for purple chain rendering between a summon/minion and its owner.
    // summonerUUID: Necromancy undead / Clone clones → their caster.
    // commanderUUID / nexusSourceUUID / bestowerUUID: existing elites linked to a creator.
    private UUID summonerUUID;
    private UUID commanderUUID;
    private UUID nexusSourceUUID;
    private UUID bestowerUUID;

    /**
     * Create a new sync packet for an elite entity.
     *
     * @param entityId    The entity ID of the elite
     * @param abilities   Map of ability IDs to their levels
     * @param qualityTier The quality tier of the elite
     * @param eliteLevel  The elite's level
     */
    public S2CEliteDataSync(int entityId, Map<String, Integer> abilities, QualityTier qualityTier, int eliteLevel) {
        this(entityId, abilities, qualityTier, eliteLevel, false, null, 0);
    }

    /**
     * Create a new sync packet for an elite entity with creator-tier data.
     *
     * @param entityId          The entity ID of the elite
     * @param abilities         Map of ability IDs to their levels
     * @param qualityTier       The quality tier of the elite
     * @param eliteLevel        The elite's level
     * @param isCreatorEntity   Whether this is a creator-tier entity
     * @param creatorAbilityId  The creator ability ID, or null
     * @param creatorAbilityLevel The creator ability level
     */
    public S2CEliteDataSync(int entityId, Map<String, Integer> abilities, QualityTier qualityTier, int eliteLevel,
                           boolean isCreatorEntity, String creatorAbilityId, int creatorAbilityLevel) {
        this(entityId, abilities, qualityTier, eliteLevel, isCreatorEntity, creatorAbilityId, creatorAbilityLevel,
                null, null, null, null);
    }

    /**
     * Full constructor including owner-link UUIDs used for purple chain rendering.
     *
     * @param entityId          The entity ID of the elite
     * @param abilities         Map of ability IDs to their levels
     * @param qualityTier       The quality tier of the elite
     * @param eliteLevel        The elite's level
     * @param isCreatorEntity   Whether this is a creator-tier entity
     * @param creatorAbilityId  The creator ability ID, or null
     * @param creatorAbilityLevel The creator ability level
     * @param summonerUUID      UUID of the entity that summoned this one, or null
     * @param commanderUUID     UUID of the commander entity, or null
     * @param nexusSourceUUID   UUID of the Nexus source entity, or null
     * @param bestowerUUID      UUID of the bestower entity, or null
     */
    public S2CEliteDataSync(int entityId, Map<String, Integer> abilities, QualityTier qualityTier, int eliteLevel,
                           boolean isCreatorEntity, String creatorAbilityId, int creatorAbilityLevel,
                           UUID summonerUUID, UUID commanderUUID, UUID nexusSourceUUID, UUID bestowerUUID) {
        this(entityId, abilities, qualityTier, eliteLevel, /*isElite*/ !abilities.isEmpty() || eliteLevel > 0,
                isCreatorEntity, creatorAbilityId, creatorAbilityLevel,
                summonerUUID, commanderUUID, nexusSourceUUID, bestowerUUID);
    }

    /**
     * Authoritative constructor carrying an explicit {@code isElite} flag. Used by
     * {@link EliteCapabilitySync} to sync non-elite summons (Necromancy undead) whose
     * level defaults to 1 — the legacy heuristic would have wrongly marked them elite.
     *
     * @param isElite           The server's authoritative is-elite flag
     */
    public S2CEliteDataSync(int entityId, Map<String, Integer> abilities, QualityTier qualityTier, int eliteLevel,
                           boolean isElite, boolean isCreatorEntity, String creatorAbilityId, int creatorAbilityLevel,
                           UUID summonerUUID, UUID commanderUUID, UUID nexusSourceUUID, UUID bestowerUUID) {
        this.entityId = entityId;
        this.abilities = abilities == null ? new java.util.HashMap<>() : new java.util.HashMap<>(abilities);
        this.qualityTierOrdinal = qualityTier.ordinal();
        this.eliteLevel = eliteLevel;
        this.isElite = isElite;
        this.isCreatorEntity = isCreatorEntity;
        this.creatorAbilityId = creatorAbilityId;
        this.creatorAbilityLevel = creatorAbilityLevel;
        this.summonerUUID = summonerUUID;
        this.commanderUUID = commanderUUID;
        this.nexusSourceUUID = nexusSourceUUID;
        this.bestowerUUID = bestowerUUID;
    }

    /**
     * Decode a packet from the network buffer.
     */
    public S2CEliteDataSync(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
        this.eliteLevel = buf.readVarInt();
        this.qualityTierOrdinal = buf.readVarInt();
        this.isElite = buf.readBoolean();
        this.isCreatorEntity = buf.readBoolean();

        int abilityCount = buf.readVarInt();
        this.abilities = new HashMap<>(abilityCount);
        for (int i = 0; i < abilityCount; i++) {
            String abilityId = buf.readUtf(64);
            int level = buf.readVarInt();
            this.abilities.put(abilityId, level);
        }

        // Creator ability data
        if (isCreatorEntity) {
            String readId = buf.readUtf(64);
            this.creatorAbilityId = readId.isEmpty() ? null : readId;
            this.creatorAbilityLevel = buf.readVarInt();
        } else {
            this.creatorAbilityId = null;
            this.creatorAbilityLevel = 0;
        }

        // Owner-link UUIDs for purple chain rendering (each prefixed with a presence flag)
        this.summonerUUID = buf.readBoolean() ? buf.readUUID() : null;
        this.commanderUUID = buf.readBoolean() ? buf.readUUID() : null;
        this.nexusSourceUUID = buf.readBoolean() ? buf.readUUID() : null;
        this.bestowerUUID = buf.readBoolean() ? buf.readUUID() : null;
    }

    /**
     * Encode the packet to the network buffer.
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(eliteLevel);
        buf.writeVarInt(qualityTierOrdinal);
        buf.writeBoolean(isElite);
        buf.writeBoolean(isCreatorEntity);
        buf.writeVarInt(abilities.size());
        for (Map.Entry<String, Integer> entry : abilities.entrySet()) {
            buf.writeUtf(entry.getKey(), 64);
            buf.writeVarInt(entry.getValue());
        }
        // Creator ability data - always write when isCreatorEntity is true
        // so decode can read consistently. Use empty string for null creatorAbilityId.
        if (isCreatorEntity) {
            buf.writeUtf(creatorAbilityId != null ? creatorAbilityId : "", 64);
            buf.writeVarInt(creatorAbilityLevel);
        }

        // Owner-link UUIDs for purple chain rendering (each prefixed with a presence flag)
        buf.writeBoolean(summonerUUID != null);
        if (summonerUUID != null) buf.writeUUID(summonerUUID);
        buf.writeBoolean(commanderUUID != null);
        if (commanderUUID != null) buf.writeUUID(commanderUUID);
        buf.writeBoolean(nexusSourceUUID != null);
        if (nexusSourceUUID != null) buf.writeUUID(nexusSourceUUID);
        buf.writeBoolean(bestowerUUID != null);
        if (bestowerUUID != null) buf.writeUUID(bestowerUUID);
    }

    /**
     * Handle the packet on the client side.
     * Stores the elite data ONLY in EliteCapabilityStorage for rendering.
     */
    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // Build EliteData from packet data and store in EliteCapabilityStorage
            EliteData data = new EliteData();
            data.setElite(isElite);
            data.setLevel(eliteLevel);
            data.setQualityTier(getQualityTier());

            // Creator-tier data
            data.setCreatorEntity(isCreatorEntity);
            if (isCreatorEntity && creatorAbilityId != null) {
                data.setCreatorAbilityId(creatorAbilityId);
                data.setCreatorAbilityLevel(creatorAbilityLevel);
            }

            // Owner-link UUIDs for purple chain rendering
            if (summonerUUID != null) data.setSummonerUUID(summonerUUID);
            if (commanderUUID != null) data.setCommanderUUID(commanderUUID);
            if (nexusSourceUUID != null) data.setNexusSourceUUID(nexusSourceUUID);
            if (bestowerUUID != null) data.setBestowerUUID(bestowerUUID);

            for (Map.Entry<String, Integer> entry : abilities.entrySet()) {
                if (!entry.getKey().isEmpty() && entry.getValue() > 0) {
                    data.addAbility(entry.getKey(), entry.getValue());
                }
            }

            // Store ONLY in EliteCapabilityStorage (not in entity persistent data)
            EliteCapabilityStorage.updateEliteData(entityId, data);
        });
        context.setPacketHandled(true);
    }

    // ========================================================================
    // Getters
    // ========================================================================

    public int getEntityId() {
        return entityId;
    }

    public Map<String, Integer> getAbilities() {
        return abilities;
    }

    public QualityTier getQualityTier() {
        QualityTier[] tiers = QualityTier.values();
        if (qualityTierOrdinal >= 0 && qualityTierOrdinal < tiers.length) {
            return tiers[qualityTierOrdinal];
        }
        return QualityTier.NORMAL;
    }

    public int getEliteLevel() {
        return eliteLevel;
    }

    public boolean isElite() {
        return isElite;
    }

    public boolean isCreatorEntity() {
        return isCreatorEntity;
    }

    public String getCreatorAbilityId() {
        return creatorAbilityId;
    }

    public int getCreatorAbilityLevel() {
        return creatorAbilityLevel;
    }

    public UUID getSummonerUUID() {
        return summonerUUID;
    }

    public UUID getCommanderUUID() {
        return commanderUUID;
    }

    public UUID getNexusSourceUUID() {
        return nexusSourceUUID;
    }

    public UUID getBestowerUUID() {
        return bestowerUUID;
    }

    /**
     * Get the abilities as a list of Ability instances with their levels.
     * Uses AbilityRegistry for lookup instead of the removed Ability.byId().
     */
    public List<Map.Entry<Ability, Integer>> getAbilityList() {
        List<Map.Entry<Ability, Integer>> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : abilities.entrySet()) {
            Ability ability = AbilityRegistry.getAbility(entry.getKey());
            if (ability != null) {
                result.add(Map.entry(ability, entry.getValue()));
            }
        }
        return result;
    }
}
