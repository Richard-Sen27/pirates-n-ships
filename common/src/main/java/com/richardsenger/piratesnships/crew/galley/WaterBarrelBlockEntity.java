package com.richardsenger.piratesnships.crew.galley;

import com.richardsenger.piratesnships.crew.content.CrewContent;
import com.richardsenger.piratesnships.crew.provisions.ProvisionSettings;
import com.richardsenger.piratesnships.crew.provisions.ProvisionStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Holds the water barrel's rations ({@link WaterBarrelRules}). The block state's {@link WaterBarrelBlock#FILL} mirrors
 * it for the model. The rations travel with the item as {@link CrewContent#WATER_RATIONS} when the barrel is broken;
 * an item without that component is a full barrel (crafted or from the creative tab).
 */
public class WaterBarrelBlockEntity extends BlockEntity implements ProvisionContainer {

    private int rations;

    public WaterBarrelBlockEntity(BlockPos pos, BlockState state) {
        super(CrewContent.WATER_BARREL_BLOCK_ENTITY.get(), pos, state);
    }

    public int rations() {
        return rations;
    }

    /** Sets the rations (clamped to the capacity) and updates fill level, comparators and saving. */
    public void setRations(int value, ProvisionSettings s) {
        int clamped = Math.max(0, Math.min(WaterBarrelRules.capacity(s), value));
        if (clamped == rations) {
            return;
        }
        rations = clamped;
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            int fill = WaterBarrelRules.fillLevel(rations, WaterBarrelRules.capacity(s));
            if (state.hasProperty(WaterBarrelBlock.FILL) && state.getValue(WaterBarrelBlock.FILL) != fill) {
                level.setBlock(worldPosition, state.setValue(WaterBarrelBlock.FILL, fill), Block.UPDATE_ALL);
            }
            level.updateNeighbourForOutputSignal(worldPosition, state.getBlock());
        }
    }

    @Override
    public ProvisionStore catchUp(long gameTime, ProvisionSettings s) {
        // water never spoils: nothing to age
        return WaterBarrelRules.store(rations, s);
    }

    @Override
    public ProvisionStore storeAt(long anchorTime, ProvisionSettings s) {
        return WaterBarrelRules.store(rations, s);
    }

    @Override
    public void applyShare(ProvisionPool.Share share, ProvisionStore remaining, long gameTime, ProvisionSettings s) {
        int drunk = share.removed().getOrDefault(WaterBarrelRules.WATER_ID, 0);
        if (drunk > 0) {
            setRations(rations - drunk, s);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        rations = Math.max(0, tag.getInt("rations"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("rations", rations);
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        Integer stored = input.get(CrewContent.WATER_RATIONS.get());
        // no component = a full barrel, the same the provisions classifier counts for the item
        rations = stored != null ? Math.max(0, stored) : -1;
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(CrewContent.WATER_RATIONS.get(), rations);
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("rations");
    }

    /** Resolves the "full" marker from {@link #applyImplicitComponents} once config is at hand (on placement). */
    void resolvePlaced(ProvisionSettings s) {
        int target = rations < 0 ? WaterBarrelRules.capacity(s) : rations;
        rations = 0;
        setRations(target, s);
    }
}
