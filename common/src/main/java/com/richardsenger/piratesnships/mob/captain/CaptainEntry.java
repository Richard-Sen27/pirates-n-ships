package com.richardsenger.piratesnships.mob.captain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * The captain of one pirate island in {@link CaptainRegistry} (BOS1): who he is, whether he lives, and where his post
 * is (so a successor can take it).
 *
 * @param id         the captain entity's UUID (also the target of his bounty)
 * @param name       his name ({@link CaptainNames})
 * @param generation 0 for the island's first captain, +1 per successor
 * @param alive      false once he died, was captured and handed over, or was removed
 * @param diedDay    the world day ({@code overworld day time / 24000}) he was lost on; meaningless while alive
 * @param dimension  the level of his post
 * @param post       the block he stands in
 * @param facing     the direction he faces at his post
 */
public record CaptainEntry(UUID id, String name, int generation, boolean alive, long diedDay, ResourceKey<Level> dimension,
                           BlockPos post, Direction facing) {

    public static final Codec<CaptainEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(CaptainEntry::id),
            Codec.STRING.fieldOf("name").forGetter(CaptainEntry::name),
            Codec.INT.optionalFieldOf("generation", 0).forGetter(CaptainEntry::generation),
            Codec.BOOL.fieldOf("alive").forGetter(CaptainEntry::alive),
            Codec.LONG.optionalFieldOf("died_day", 0L).forGetter(CaptainEntry::diedDay),
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(CaptainEntry::dimension),
            BlockPos.CODEC.fieldOf("post").forGetter(CaptainEntry::post),
            Direction.CODEC.fieldOf("facing").forGetter(CaptainEntry::facing)
    ).apply(i, CaptainEntry::new));

    /** The same captain, lost on {@code day}. */
    public CaptainEntry dead(long day) {
        return new CaptainEntry(id, name, generation, false, day, dimension, post, facing);
    }

    /** His successor at the same post. */
    public CaptainEntry successor(UUID newId, String newName) {
        return new CaptainEntry(newId, newName, generation + 1, true, 0L, dimension, post, facing);
    }

    /**
     * Whether a successor is due on world day {@code today}: the captain is lost and {@code respawnDays} days have
     * passed since ({@code respawnDays} 0: on the next check).
     */
    public boolean successorDue(long today, int respawnDays) {
        return !alive && today - diedDay >= Math.max(0, respawnDays);
    }
}
