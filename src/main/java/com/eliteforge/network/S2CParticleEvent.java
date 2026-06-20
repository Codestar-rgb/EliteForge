package com.eliteforge.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

/**
 * Packet for spawning particles on the client.
 * Sent from the server to create visual effects at specific positions.
 *
 * Supports all vanilla particle types and allows specifying:
 * - Position (x, y, z)
 * - Particle type (by registry ID)
 * - Count (number of particles)
 * - Spread (random offset range)
 * - Speed (particle velocity)
 * - Extra data (for special particles like note/dust)
 */
public class S2CParticleEvent {

    private double x;
    private double y;
    private double z;
    private String particleTypeId;
    private int count;
    private double spread;
    private double speed;

    /**
     * Create a simple particle event with default spread and speed.
     *
     * @param x              X position
     * @param y              Y position
     * @param z              Z position
     * @param particleTypeId The registry ID of the particle type
     * @param count          Number of particles to spawn
     */
    public S2CParticleEvent(double x, double y, double z, String particleTypeId, int count) {
        this(x, y, z, particleTypeId, count, 0.5, 0.1);
    }

    /**
     * Create a particle event with custom spread and speed.
     *
     * @param x              X position
     * @param y              Y position
     * @param z              Z position
     * @param particleTypeId The registry ID of the particle type
     * @param count          Number of particles to spawn
     * @param spread         Random offset range (0.0 = exact position)
     * @param speed          Particle velocity (0.0 = stationary)
     */
    public S2CParticleEvent(double x, double y, double z, String particleTypeId, int count, double spread, double speed) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.particleTypeId = particleTypeId;
        this.count = count;
        this.spread = spread;
        this.speed = speed;
    }

    /**
     * Decode a packet from the network buffer.
     */
    public S2CParticleEvent(FriendlyByteBuf buf) {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.particleTypeId = buf.readUtf(256);
        this.count = buf.readVarInt();
        this.spread = buf.readDouble();
        this.speed = buf.readDouble();
    }

    /**
     * Encode the packet to the network buffer.
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeUtf(particleTypeId, 256);
        buf.writeVarInt(count);
        buf.writeDouble(spread);
        buf.writeDouble(speed);
    }

    /**
     * Handle the packet on the client side.
     * Spawns the specified particles at the given position.
     */
    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Level level = Minecraft.getInstance().level;
            if (level == null) return;

            // Cap particle count to prevent client lag from malicious or oversized packets.
            // 200 particles is more than enough for any visual effect; higher values would
            // cause frame drops on lower-end clients.
            int effectiveCount = Math.min(200, Math.max(1, count));

            // Resolve the particle type from the registry ID
            ParticleType<?> particleType = ForgeRegistries.PARTICLE_TYPES.getValue(
                    new net.minecraft.resources.ResourceLocation(particleTypeId));

            if (particleType instanceof ParticleOptions particleOptions) {
                level.addAlwaysVisibleParticle(
                        particleOptions,
                        true,
                        x, y, z,
                        spread * (level.random.nextDouble() - 0.5),
                        spread * (level.random.nextDouble() - 0.5),
                        spread * (level.random.nextDouble() - 0.5)
                );

                // Spawn additional particles based on count
                for (int i = 1; i < effectiveCount; i++) {
                    double offsetX = x + (level.random.nextDouble() - 0.5) * spread * 2;
                    double offsetY = y + (level.random.nextDouble() - 0.5) * spread * 2;
                    double offsetZ = z + (level.random.nextDouble() - 0.5) * spread * 2;

                    level.addAlwaysVisibleParticle(
                            particleOptions,
                            true,
                            offsetX, offsetY, offsetZ,
                            speed * (level.random.nextDouble() - 0.5),
                            speed * (level.random.nextDouble() - 0.5),
                            speed * (level.random.nextDouble() - 0.5)
                    );
                }
            } else {
                // Fallback to basic particle types by ID
                ParticleOptions fallback = resolveFallbackParticle(particleTypeId);
                if (fallback != null) {
                    for (int i = 0; i < effectiveCount; i++) {
                        double offsetX = x + (level.random.nextDouble() - 0.5) * spread * 2;
                        double offsetY = y + (level.random.nextDouble() - 0.5) * spread * 2;
                        double offsetZ = z + (level.random.nextDouble() - 0.5) * spread * 2;
                        level.addAlwaysVisibleParticle(
                                fallback, true,
                                offsetX, offsetY, offsetZ,
                                speed * (level.random.nextDouble() - 0.5),
                                speed * (level.random.nextDouble() - 0.5),
                                speed * (level.random.nextDouble() - 0.5)
                        );
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }

    /**
     * Resolve common particle types by their string ID as a fallback.
     */
    private static ParticleOptions resolveFallbackParticle(String typeId) {
        return switch (typeId) {
            case "minecraft:flame" -> ParticleTypes.FLAME;
            case "minecraft:smoke" -> ParticleTypes.SMOKE;
            case "minecraft:enchant" -> ParticleTypes.ENCHANT;
            case "minecraft:witch" -> ParticleTypes.WITCH;
            case "minecraft:angry_villager" -> ParticleTypes.ANGRY_VILLAGER;
            case "minecraft:happy_villager" -> ParticleTypes.HAPPY_VILLAGER;
            case "minecraft:heart" -> ParticleTypes.HEART;
            case "minecraft:crit" -> ParticleTypes.CRIT;
            case "minecraft:magic_crit" -> ParticleTypes.CRIT;
            case "minecraft:portal" -> ParticleTypes.PORTAL;
            case "minecraft:dragon_breath" -> ParticleTypes.DRAGON_BREATH;
            case "minecraft:totem_of_undying" -> ParticleTypes.TOTEM_OF_UNDYING;
            case "minecraft:flash" -> ParticleTypes.FLASH;
            case "minecraft:snowflake" -> ParticleTypes.SNOWFLAKE;
            case "minecraft:campfire_cosy_smoke" -> ParticleTypes.CAMPFIRE_COSY_SMOKE;
            case "minecraft:lava" -> ParticleTypes.LAVA;
            default -> null;
        };
    }

    // ========================================================================
    // Getters
    // ========================================================================

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public String getParticleTypeId() {
        return particleTypeId;
    }

    public int getCount() {
        return count;
    }

    public double getSpread() {
        return spread;
    }

    public double getSpeed() {
        return speed;
    }

    // ========================================================================
    // Factory Methods for Common Particles
    // ========================================================================

    /**
     * Create a particle event for elite spawn effects.
     */
    public static S2CParticleEvent eliteSpawn(double x, double y, double z) {
        return new S2CParticleEvent(x, y + 1.0, z, "minecraft:witch", 20, 1.0, 0.2);
    }

    /**
     * Create a particle event for elite death effects.
     */
    public static S2CParticleEvent eliteDeath(double x, double y, double z) {
        return new S2CParticleEvent(x, y + 1.0, z, "minecraft:totem_of_undying", 30, 1.5, 0.3);
    }

    /**
     * Create a particle event for quenching effects.
     */
    public static S2CParticleEvent quench(double x, double y, double z) {
        return new S2CParticleEvent(x, y + 0.5, z, "minecraft:snowflake", 15, 0.5, 0.1);
    }

    /**
     * Create a particle event for forging effects.
     */
    public static S2CParticleEvent forging(double x, double y, double z) {
        return new S2CParticleEvent(x, y + 1.0, z, "minecraft:flame", 10, 0.3, 0.05);
    }

    /**
     * Create a particle event for tempering effects.
     */
    public static S2CParticleEvent tempering(double x, double y, double z) {
        return new S2CParticleEvent(x, y + 1.0, z, "minecraft:enchant", 25, 1.0, 0.2);
    }

    /**
     * Create a particle event for ability activation effects.
     */
    public static S2CParticleEvent abilityActivation(double x, double y, double z, String abilityCategory) {
        String particleId = switch (abilityCategory) {
            case "attack" -> "minecraft:flame";
            case "defense" -> "minecraft:happy_villager";
            case "control" -> "minecraft:portal";
            case "legendary" -> "minecraft:dragon_breath";
            default -> "minecraft:enchant";
        };
        return new S2CParticleEvent(x, y + 1.0, z, particleId, 15, 0.8, 0.15);
    }
}
