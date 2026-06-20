package com.eliteforge.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;

/**
 * Capability provider for EliteCapability. Stores the capability implementation,
 * handles LazyOptional lookup, and manages NBT serialization/deserialization.
 * <p>
 * Registered in AttachCapabilitiesEvent&lt;Entity&gt; for LivingEntity types.
 */
public class EliteCapabilityProvider implements ICapabilitySerializable<CompoundTag> {

    private final EliteCapabilityImpl impl = new EliteCapabilityImpl();
    private final LazyOptional<EliteCapability> lazyOptional = LazyOptional.of(() -> impl);

    /**
     * Default implementation of EliteCapability that wraps an EliteData instance.
     */
    private static class EliteCapabilityImpl implements EliteCapability {
        private EliteData data = new EliteData();

        @Override
        public EliteData getEliteData() {
            return data;
        }

        @Override
        public void setEliteData(EliteData data) {
            if (data == null) {
                throw new IllegalArgumentException("EliteData cannot be null");
            }
            this.data = data;
        }

        @Override
        public boolean isElite() {
            return data.isElite();
        }

        @Override
        public void setElite(boolean elite) {
            data.setElite(elite);
        }
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == EliteCapability.CAPABILITY) {
            return lazyOptional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("EliteData", impl.getEliteData().serializeNBT());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("EliteData")) {
            impl.getEliteData().deserializeNBT(nbt.getCompound("EliteData"));
        }
    }

    /**
     * Invalidate the LazyOptional when the entity is removed or the capability is detached.
     * Should be called from Entity.remove/RemovalReason or equivalent.
     */
    public void invalidate() {
        lazyOptional.invalidate();
    }
}
