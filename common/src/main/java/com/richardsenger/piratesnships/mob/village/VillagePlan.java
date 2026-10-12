package com.richardsenger.piratesnships.mob.village;

import com.richardsenger.piratesnships.mob.village.ResidentPosts.Bed;
import com.richardsenger.piratesnships.mob.village.ResidentPosts.Post;
import com.richardsenger.piratesnships.mob.village.ResidentPosts.Slot;
import com.richardsenger.piratesnships.world.outpost.GarrisonPosts;
import com.richardsenger.piratesnships.world.outpost.GarrisonPosts.PlacedPiece;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The people of a placed seafarer village (LIFE1), pure: from the village's pieces (name, origin, rotation; the
 * jigsaw's order) to one {@link ResidentPlan} per resident of {@link ResidentPosts}, in world coordinates. Every chunk
 * of the village computes the same plan, so each resident is placed once, by the chunk that holds its first station.
 *
 * <ul>
 *   <li><b>Stations.</b> The slot's posts, turned with the piece. The fishwife's second station is the fish market's
 *       counter nearest her rack (her rack again in a village without one); the dockhand's second is the pier post
 *       nearest the warehouse (its second crate post without a pier).</li>
 *   <li><b>Beds.</b> A resident whose slot sleeps at home takes its own piece's bed of the same index first; then the
 *       others take the free bed nearest their first station, in slot order. Without a free bed a resident has none
 *       and stays at home at night.</li>
 *   <li><b>Seats.</b> The {@link #SEATS_PER_RESIDENT} seats nearest its first station, the tavern's and the inn's
 *       counted {@link #TAVERN_PULL} blocks nearer (the dusk is spent there when one is near).</li>
 * </ul>
 */
public final class VillagePlan {

    public static final int SEATS_PER_RESIDENT = 4;
    /** Blocks a tavern or inn seat counts as nearer than it is: within this distance it comes before any other seat. */
    public static final int TAVERN_PULL = 24;

    private VillagePlan() {
    }

    private record Placed(PlacedPiece piece, int pieceIndex) {
    }

    private record BedRef(String piece, BlockPos origin, int index, Bed bed) {
    }

    private record SeatRef(Post post, boolean tavern) {
    }

    /** The slot id of resident {@code index} of {@code piece}. */
    public static String slotId(PlacedPiece piece, int index) {
        BlockPos o = piece.origin();
        return piece.name() + "@" + o.getX() + "," + o.getY() + "," + o.getZ() + "#" + index;
    }

    /** {@code local} of {@code piece} in the world, turned with it. */
    public static Post toWorld(PlacedPiece piece, Post local) {
        return new Post(GarrisonPosts.toWorld(piece, local.pos()), piece.rotation().rotate(local.facing()));
    }

    public static Bed toWorld(PlacedPiece piece, Bed local) {
        return new Bed(GarrisonPosts.toWorld(piece, local.foot()), piece.rotation().rotate(local.facing()));
    }

    /** The plan of every resident of a village made of {@code pieces}. */
    public static List<ResidentPlan> plan(List<PlacedPiece> pieces) {
        List<Post> fishHelpers = new ArrayList<>();
        List<Post> pierPosts = new ArrayList<>();
        List<SeatRef> seats = new ArrayList<>();
        List<BedRef> beds = new ArrayList<>();
        for (PlacedPiece p : pieces) {
            String name = p.name();
            if (ResidentPosts.FISH_MARKET.equals(name)) fishHelpers.add(toWorld(p, ResidentPosts.FISH_MARKET_HELPER));
            if (ResidentPosts.PIER.equals(name)) ResidentPosts.PIER_POSTS.forEach(post -> pierPosts.add(toWorld(p, post)));
            boolean tavern = ResidentPosts.TAVERN.equals(name) || ResidentPosts.INN.equals(name);
            ResidentPosts.seatsOf(name).forEach(s -> seats.add(new SeatRef(toWorld(p, s), tavern)));
            List<Bed> local = ResidentPosts.bedsOf(name);
            for (int i = 0; i < local.size(); i++) beds.add(new BedRef(name, p.origin(), i, toWorld(p, local.get(i))));
        }

        // the residents and their stations, in the pieces' order
        record Draft(PlacedPiece piece, int index, Slot slot, List<Post> work) {
        }
        List<Draft> drafts = new ArrayList<>();
        for (PlacedPiece p : pieces) {
            List<Slot> slots = ResidentPosts.residentsOf(p.name());
            for (int i = 0; i < slots.size(); i++) {
                Slot slot = slots.get(i);
                List<Post> work = new ArrayList<>(slot.work().stream().map(w -> toWorld(p, w)).toList());
                if (slot.role() == VillageRole.FISHWIFE) {
                    work.add(nearest(work.get(0).pos(), fishHelpers).orElse(work.get(0)));
                } else if (slot.role() == VillageRole.DOCKHAND) {
                    Optional<Post> pier = nearest(work.get(0).pos(), pierPosts);
                    if (pier.isPresent()) work.set(Math.min(1, work.size() - 1), pier.get());
                    if (work.size() < 2 && pier.isPresent()) work.add(pier.get());
                }
                drafts.add(new Draft(p, i, slot, work));
            }
        }

        // beds: own beds first, then the nearest free one
        Set<BedRef> taken = new HashSet<>();
        Bed[] chosen = new Bed[drafts.size()];
        for (int d = 0; d < drafts.size(); d++) {
            Draft draft = drafts.get(d);
            if (!draft.slot().ownBed()) continue;
            for (BedRef b : beds) {
                if (b.piece().equals(draft.piece().name()) && b.origin().equals(draft.piece().origin()) && b.index() == draft.index()
                        && taken.add(b)) {
                    chosen[d] = b.bed();
                    break;
                }
            }
        }
        for (int d = 0; d < drafts.size(); d++) {
            if (chosen[d] != null) continue;
            BlockPos from = drafts.get(d).work().get(0).pos();
            BedRef best = null;
            for (BedRef b : beds) {
                if (taken.contains(b)) continue;
                if (best == null || b.bed().foot().distSqr(from) < best.bed().foot().distSqr(from)) best = b;
            }
            if (best != null) {
                taken.add(best);
                chosen[d] = best.bed();
            }
        }

        List<ResidentPlan> out = new ArrayList<>();
        for (int d = 0; d < drafts.size(); d++) {
            Draft draft = drafts.get(d);
            BlockPos from = draft.work().get(0).pos();
            List<Post> near = seats.stream()
                    .sorted(Comparator.comparingDouble((SeatRef s) -> seatDistance(s, from)))
                    .limit(SEATS_PER_RESIDENT).map(SeatRef::post).toList();
            out.add(new ResidentPlan(slotId(draft.piece(), draft.index()), draft.slot().role(), draft.work(),
                    Optional.ofNullable(chosen[d]), near, Optional.ofNullable(draft.slot().stall())));
        }
        return out;
    }

    private static double seatDistance(SeatRef seat, BlockPos from) {
        double d = Math.sqrt(seat.post().pos().distSqr(from));
        return seat.tavern() ? Math.max(0, d - TAVERN_PULL) : d;
    }

    private static Optional<Post> nearest(BlockPos from, List<Post> posts) {
        return posts.stream().min(Comparator.comparingDouble(p -> p.pos().distSqr(from)));
    }
}
