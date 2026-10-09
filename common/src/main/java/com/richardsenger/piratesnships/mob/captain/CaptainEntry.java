package com.richardsenger.piratesnships.mob.captain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Optional;
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
 * @param voyage     the voyage he is at sea on (BOS2, {@code worldsim.captain}); empty while he keeps his post
 */
public record CaptainEntry(UUID id, String name, int generation, boolean alive, long diedDay, ResourceKey<Level> dimension,
                           BlockPos post, Direction facing, Optional<UUID> voyage) {

    public static final Codec<CaptainEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(CaptainEntry::id),
            Codec.STRING.fieldOf("name").forGetter(CaptainEntry::name),
            Codec.INT.optionalFieldOf("generation", 0).forGetter(CaptainEntry::generation),
            Codec.BOOL.fieldOf("alive").forGetter(CaptainEntry::alive),
            Codec.LONG.optionalFieldOf("died_day", 0L).forGetter(CaptainEntry::diedDay),
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(CaptainEntry::dimension),
            BlockPos.CODEC.fieldOf("post").forGetter(CaptainEntry::post),
            Direction.CODEC.fieldOf("facing").forGetter(CaptainEntry::facing),
            UUIDUtil.CODEC.optionalFieldOf("voyage").forGetter(CaptainEntry::voyage)
    ).apply(i, CaptainEntry::new));

    /** A captain at his post (not at sea). */
    public CaptainEntry(UUID id, String name, int generation, boolean alive, long diedDay, ResourceKey<Level> dimension,
                        BlockPos post, Direction facing) {
        this(id, name, generation, alive, diedDay, dimension, post, facing, Optional.empty());
    }

    /** The same captain, lost on {@code day} (no longer at sea). */
    public CaptainEntry dead(long day) {
        return new CaptainEntry(id, name, generation, false, day, dimension, post, facing, Optional.empty());
    }

    /** His successor at the same post. */
    public CaptainEntry successor(UUID newId, String newName) {
        return new CaptainEntry(newId, newName, generation + 1, true, 0L, dimension, post, facing, Optional.empty());
    }

    /** The same captain at sea on {@code voyage}, or back at his post ({@code empty}). */
    public CaptainEntry withVoyage(Optional<UUID> voyage) {
        return new CaptainEntry(id, name, generation, alive, diedDay, dimension, post, facing, voyage);
    }

    /** He is alive and at sea on a voyage (BOS2); his post stands empty. */
    public boolean atSea() {
        return alive && voyage.isPresent();
    }

    /**
     * Whether a successor is due on world day {@code today}: the captain is lost and {@code respawnDays} days have
     * passed since ({@code respawnDays} 0: on the next check).
     */
    public boolean successorDue(long today, int respawnDays) {
        return !alive && today - diedDay >= Math.max(0, respawnDays);
    }
}
