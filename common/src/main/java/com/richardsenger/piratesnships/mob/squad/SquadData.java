package com.richardsenger.piratesnships.mob.squad;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

import java.util.List;
import java.util.UUID;

/**
 * What an officer saves of his squad (MOB2): the patrol route, his outpost, the state, the current waypoint, the
 * members, when the next patrol is due (game time; 0 = not scheduled yet) and whether the patrol was ordered by
 * command (an ordered patrol ignores the night). A squad saved while fighting comes back returning.
 */
public record SquadData(List<BlockPos> route, BlockPos outpost, SquadState state, int waypoint, List<UUID> members,
                        long nextPatrolAt, boolean forced) {

    public static final SquadData EMPTY = new SquadData(List.of(), BlockPos.ZERO, SquadState.AT_POST, 0, List.of(), 0L, false);

    public static final Codec<SquadData> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.listOf().fieldOf("route").forGetter(SquadData::route),
            BlockPos.CODEC.fieldOf("outpost").forGetter(SquadData::outpost),
            SquadState.CODEC.fieldOf("state").forGetter(SquadData::state),
            Codec.INT.fieldOf("waypoint").forGetter(SquadData::waypoint),
            UUIDUtil.CODEC.listOf().fieldOf("members").forGetter(SquadData::members),
            Codec.LONG.fieldOf("next_patrol_at").forGetter(SquadData::nextPatrolAt),
            Codec.BOOL.fieldOf("forced").forGetter(SquadData::forced)
    ).apply(i, SquadData::new));

    public SquadData {
        route = List.copyOf(route);
        members = List.copyOf(members);
    }

    /** A fresh squad at its posts with {@code route}. */
    public static SquadData atPost(List<BlockPos> route, BlockPos outpost) {
        return new SquadData(route, outpost, SquadState.AT_POST, 0, List.of(), 0L, false);
    }
}
