package com.richardsenger.piratesnships.mob.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.mob.village.ResidentPosts.Bed;
import com.richardsenger.piratesnships.mob.village.ResidentPosts.Post;
import com.richardsenger.piratesnships.trade.stall.StallKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;
import java.util.Optional;

/**
 * One villager of a placed village (LIFE1), in world coordinates: the slot it fills (stable per piece and index, so a
 * respawn fills the same one), its role, its workstations in schedule order, its bed, the seats nearest its work and
 * the stall it keeps. Made by {@link VillagePlan}, saved on the villager and in {@link ResidentRegistry}.
 *
 * @param slot  {@code <piece>@<x>,<y>,<z>#<index>}: the piece's name and origin and the resident's index in it
 * @param work  at least one post; the routine's station index counts into it
 * @param seats nearest first; the dusk sit takes the first free one
 */
public record ResidentPlan(String slot, VillageRole role, List<Post> work, Optional<Bed> bed, List<Post> seats,
                           Optional<StallKind> stall) {

    public static final Codec<Post> POST_CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(Post::pos),
            Direction.CODEC.fieldOf("facing").forGetter(Post::facing)
    ).apply(i, Post::new));

    public static final Codec<Bed> BED_CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("foot").forGetter(Bed::foot),
            Direction.CODEC.fieldOf("facing").forGetter(Bed::facing)
    ).apply(i, Bed::new));

    public static final Codec<ResidentPlan> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("slot").forGetter(ResidentPlan::slot),
            VillageRole.CODEC.fieldOf("role").forGetter(ResidentPlan::role),
            POST_CODEC.listOf().fieldOf("work").forGetter(ResidentPlan::work),
            BED_CODEC.optionalFieldOf("bed").forGetter(ResidentPlan::bed),
            POST_CODEC.listOf().optionalFieldOf("seats", List.of()).forGetter(ResidentPlan::seats),
            StallKind.CODEC.optionalFieldOf("stall").forGetter(ResidentPlan::stall)
    ).apply(i, ResidentPlan::new));

    public ResidentPlan {
        work = List.copyOf(work);
        seats = List.copyOf(seats);
        if (work.isEmpty()) throw new IllegalArgumentException("a resident needs a workstation: " + slot);
    }

    /** The station of routine index {@code index}: an index past the last station means the last one. */
    public Post station(int index) {
        return work.get(Math.max(0, Math.min(index, work.size() - 1)));
    }

    /** Where the villager is at home: its first station. */
    public Post home() {
        return work.get(0);
    }

    /** A plan for a villager spawned by hand ({@code /pirates mob spawn villager}, a test): its own spot, nothing else. */
    public static ResidentPlan standalone(VillageRole role, BlockPos at, Direction facing) {
        return new ResidentPlan("standalone@" + at.getX() + "," + at.getY() + "," + at.getZ() + "#0", role,
                List.of(new Post(at.immutable(), facing)), Optional.empty(), List.of(), Optional.ofNullable(role.defaultStall()));
    }
}
