package com.richardsenger.piratesnships.worldsim.materialize;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.cannon.CannonBlock;
import com.richardsenger.piratesnships.combat.cannon.CannonConfig;
import com.richardsenger.piratesnships.combat.cannon.CannonRules;
import com.richardsenger.piratesnships.combat.cannon.CannonService;
import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.Stations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The guns of a materialised voyage (WS4c, design.md §8.2, §10.4). A navy or pirate ship appears with
 * {@code world_simulation.materialize.cannon_rounds} rounds of powder and shot per cannon in its shot lockers and, with
 * {@code guns_start_loaded}, its cannons loaded; a merchant gets nothing ({@link GunStocking#stocks}). A <b>shot
 * locker</b> is a block of {@link #SHOT_LOCKERS} (vanilla barrels and chests) with a container, within the gun crews'
 * {@code cannons.crew.supply_range} of a cannon: each cannon's rounds go into its nearest locker
 * ({@link GunStocking#lockerOf}). Our cargo containers are never lockers, so the ammunition is not cargo:
 * dematerialisation reads only the cargo containers and ignores it, and a ship that appears again is stocked afresh.
 *
 * <p>A loaded gun has no {@code LOAD} work, so the job board never posts it for free hands; {@link #manLoaded} seats
 * the voyage's free crew at the unmanned loaded guns directly, those bearing on the quarry first (WS4b's chase).
 */
public final class VoyageGuns {

    /** Blocks whose container is a shot locker of a voyage ship (datagen: barrel, chest, trapped chest). */
    public static final TagKey<Block> SHOT_LOCKERS = TagKey.create(Registries.BLOCK, Constants.id("shot_lockers"));

    /** What {@link #stock} did: guns found, lockers used, rounds put in, guns loaded, guns without a locker in reach. */
    public record Stocked(int guns, int lockers, int rounds, int loaded, int unsupplied) {
        public static final Stocked NONE = new Stocked(0, 0, 0, 0, 0);
    }

    private VoyageGuns() {
    }

    /** The plot positions of the cannon masters of {@code ship}, in a fixed order. */
    public static List<BlockPos> guns(ServerLevel level, ShipBody ship) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockEntity be : ship.plotBlockEntities()) {
            if (CannonBlock.isMaster(level.getBlockState(be.getBlockPos()))) out.add(be.getBlockPos().immutable());
        }
        out.sort(Comparator.comparingLong(BlockPos::asLong));
        return out;
    }

    /** The plot positions of every shot locker block with a container on {@code ship} (in reach of a gun or not). */
    public static List<BlockPos> lockers(ServerLevel level, ShipBody ship) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockEntity be : ship.plotBlockEntities()) {
            if (be instanceof Container && level.getBlockState(be.getBlockPos()).is(SHOT_LOCKERS)) out.add(be.getBlockPos().immutable());
        }
        out.sort(Comparator.comparingLong(BlockPos::asLong));
        return out;
    }

    /** For each of {@link #guns}, the index into {@link #lockers} of the locker its crew is supplied from, or -1. */
    public static int[] lockerOf(List<BlockPos> guns, List<BlockPos> lockers) {
        return GunStocking.lockerOf(guns, lockers, CannonConfig.CREW_SUPPLY_RANGE.get());
    }

    /**
     * Stocks the shot lockers of the freshly placed {@code ship} of a {@code faction} voyage and loads its guns (see
     * the class comment). Items that do not fit are dropped from the count and logged. Does nothing for a merchant.
     */
    public static Stocked stock(ServerLevel level, ShipBody ship, Faction faction) {
        if (!GunStocking.stocks(faction)) return Stocked.NONE;
        List<BlockPos> guns = guns(level, ship);
        if (guns.isEmpty()) return Stocked.NONE;
        List<BlockPos> lockers = lockers(level, ship);
        int[] lockerOf = lockerOf(guns, lockers);
        int[] rounds = GunStocking.roundsPerLocker(lockerOf, lockers.size(), MaterializeConfig.CANNON_ROUNDS.get());
        int put = 0;
        int used = 0;
        for (int l = 0; l < lockers.size(); l++) {
            if (rounds[l] <= 0 || !(level.getBlockEntity(lockers.get(l)) instanceof Container c)) continue;
            used++;
            int powder = insert(c, Items.GUNPOWDER, rounds[l]);
            int shot = insert(c, CombatContent.CANNONBALL.get(), rounds[l]);
            if (powder < rounds[l] || shot < rounds[l]) {
                Constants.LOG.warn("Shot locker at {} took {} powder and {} shot of {} rounds", lockers.get(l), powder, shot, rounds[l]);
            }
            put += Math.min(powder, shot);
        }
        int unsupplied = 0;
        for (int l : lockerOf) {
            if (l == GunStocking.NO_LOCKER) unsupplied++;
        }
        int loaded = 0;
        if (MaterializeConfig.GUNS_START_LOADED.get()) {
            for (BlockPos g : guns) {
                if (load(level, g)) loaded++;
            }
        }
        return new Stocked(guns.size(), used, put, loaded, unsupplied);
    }

    /** Puts gunpowder and then a ball into the empty gun at {@code gun}; true when it is loaded afterwards. */
    private static boolean load(ServerLevel level, BlockPos gun) {
        CannonService.load(level, gun, null, new ItemStack(Items.GUNPOWDER));
        CannonService.load(level, gun, null, new ItemStack(CombatContent.CANNONBALL.get()));
        return loaded(level.getBlockState(gun));
    }

    private static boolean loaded(BlockState state) {
        return state.getBlock() instanceof CannonBlock && CannonRules.canFire(state.getValue(CannonBlock.LOAD));
    }

    /** Puts {@code count} of {@code item} into {@code c}: onto matching stacks first, then into empty slots; returns how many fit. */
    static int insert(Container c, Item item, int count) {
        int left = count;
        int max = Math.min(c.getMaxStackSize(), item.getDefaultMaxStackSize());
        for (int i = 0; i < c.getContainerSize() && left > 0; i++) {
            ItemStack s = c.getItem(i);
            if (s.is(item) && s.getCount() < max) {
                int n = Math.min(left, max - s.getCount());
                s.grow(n);
                left -= n;
            }
        }
        for (int i = 0; i < c.getContainerSize() && left > 0; i++) {
            if (c.getItem(i).isEmpty()) {
                int n = Math.min(left, max);
                c.setItem(i, new ItemStack(item, n));
                left -= n;
            }
        }
        c.setChanged();
        return count - left;
    }

    /**
     * Seats the free crew of {@code voyage} (alive, aboard, at no station) at the unmanned loaded guns of
     * {@code ship}, the guns whose muzzles bear most nearly on ({@code tx}, {@code tz}) first, each the free hand
     * nearest to it. Returns how many were seated.
     */
    public static int manLoaded(ServerLevel level, ShipBody ship, UUID voyage, double tx, double tz) {
        if (!CannonConfig.ENABLED.get()) return 0;
        List<CrewMember> free = new ArrayList<>();
        for (LivingEntity e : VoyageCrew.alive(level, ship, voyage, VoyageCrew.CREW_TAG)) {
            if (e instanceof CrewMember c && c.assignment() == null) free.add(c);
        }
        if (free.isEmpty()) return 0;
        List<BlockPos> open = new ArrayList<>();
        List<GunStocking.Gun> view = new ArrayList<>();
        for (BlockPos g : guns(level, ship)) {
            BlockState state = level.getBlockState(g);
            StationRef ref = Stations.at(level, g);
            if (!loaded(state) || ref == null || Stations.isManned(ref)) continue;
            Direction facing = state.getValue(CannonBlock.FACING);
            Vec3 at = ship.toWorld(Vec3.atCenterOf(g));
            Vec3 ahead = ship.toWorld(Vec3.atCenterOf(g).add(facing.getStepX(), 0, facing.getStepZ()));
            open.add(g);
            view.add(new GunStocking.Gun(at.x, at.z, ahead.x - at.x, ahead.z - at.z));
        }
        int seated = 0;
        for (int i : GunStocking.manningOrder(view, tx, tz)) {
            if (free.isEmpty()) break;
            GunStocking.Gun g = view.get(i);
            CrewMember nearest = free.stream().min(Comparator.comparingDouble(c -> Math.hypot(c.getX() - g.x(), c.getZ() - g.z()))).orElseThrow();
            if (CrewStations.assign(level, nearest, open.get(i), false) == CrewStations.AssignResult.ASSIGNED) {
                free.remove(nearest);
                seated++;
            }
        }
        return seated;
    }

    /** Whether the cannon at {@code gun} is loaded (powder and ball). */
    public static boolean isLoaded(ServerLevel level, BlockPos gun) {
        return loaded(level.getBlockState(gun));
    }

    /** What the gun at {@code gun} holds; for test messages. */
    static String describe(ServerLevel level, BlockPos gun) {
        BlockState s = level.getBlockState(gun);
        return s.getBlock() instanceof CannonBlock ? s.getValue(CannonBlock.LOAD).getSerializedName() : "no cannon";
    }
}
