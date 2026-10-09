package com.richardsenger.piratesnships.platform;

import com.richardsenger.piratesnships.crew.hammock.HammockBlock;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Fabric side of {@link HammockBlock}'s loader extension methods, which override NeoForge's {@code IBlockExtension}
 * there and are plain methods here: Fabric API's sleep events ask them instead. Wiring only, the answers come from the
 * common block.
 * <ul>
 *   <li>{@code isBed} through {@code ALLOW_BED}: without it vanilla's bed check wakes a hammock sleeper every tick.</li>
 *   <li>{@code setBedOccupied} through {@code SET_BED_OCCUPATION_STATE} (a hammock has no {@code OCCUPIED}).</li>
 *   <li>the sleeping direction through {@code MODIFY_SLEEPING_DIRECTION}: the block's {@code FACING}, NeoForge's
 *       default {@code getBedDirection}, so clients draw the sleeper lying along the hammock.</li>
 * </ul>
 * {@code getRespawnPosition} (an unforced respawn point at a hammock on land) has no Fabric API hook: on Fabric such a
 * respawn point is not found (docs/fabric.md). On a ship Sable answers before it is asked anyway.
 */
final class FabricHammockBeds {

    private FabricHammockBeds() {
    }

    static void attach() {
        EntitySleepEvents.ALLOW_BED.register((entity, pos, state, vanilla) ->
                state.getBlock() instanceof HammockBlock h && h.isBed(state, entity.level(), pos, entity)
                        ? InteractionResult.SUCCESS : InteractionResult.PASS);
        EntitySleepEvents.SET_BED_OCCUPATION_STATE.register((entity, pos, state, occupied) -> {
            if (!(state.getBlock() instanceof HammockBlock h)) return false;
            h.setBedOccupied(state, entity.level(), pos, entity, occupied);
            return true;
        });
        EntitySleepEvents.MODIFY_SLEEPING_DIRECTION.register((entity, pos, direction) -> {
            BlockState state = entity.level().getBlockState(pos);
            return state.getBlock() instanceof HammockBlock ? state.getValue(HorizontalDirectionalBlock.FACING) : direction;
        });
    }
}
