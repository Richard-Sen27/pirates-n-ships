package com.richardsenger.piratesnships.combat.cannon;

import com.richardsenger.piratesnships.combat.cannon.CannonStation.CannonOrder;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.StationState;
import com.richardsenger.piratesnships.station.Stations;
import com.richardsenger.piratesnships.combat.cannon.npc.Gunnery;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Crew loading of cannons and swivel guns (C9, docs/design.md §6, §8.2), shared by {@link CannonStation} and
 * {@link SwivelStation}. A crew member at a gun takes gunpowder and the gun's ammo from the {@link CrewSupply} around
 * it and puts them in through the same service calls a player's load uses ({@link CannonService#load},
 * {@link SwivelService#load}), so block state, recorded shot and sounds are the same. {@link CannonOrder#LOAD} is the
 * order; with {@code cannons.crew.auto_reload} the crew loads again right after its own shot, and a "Fire!" given while
 * it loads waits for the load and then fires. Rules: {@link CrewSupplyRules}. CAN3: a cannon takes the first shot of
 * {@link #shotPreference} its supply holds.
 */
final class CrewLoading {

    /** Stations whose running "Fire!" was given during a load: the shot comes after the load. Transient. */
    private static final Set<StationRef> FIRE_AFTER_LOAD = ConcurrentHashMap.newKeySet();

    private static final Predicate<ItemStack> POWDER = CannonBlock::isPowder;

    private CrewLoading() {
    }

    /** One crewed gun as the load sees it: where it is, what is in it, and how its own service loads it. */
    interface Gun {
        BlockPos pos();

        CannonLoad load();

        long reloadLeft();

        int ammoCount();

        Predicate<ItemStack> ammo();

        int workTicks();

        /** Puts {@code stack} in through the gun's service as a crew member (no player); true when it went in. */
        boolean put(ItemStack stack, boolean powder);

        /**
         * The ammo this load takes from {@code supply} ({@code n} items): by default any {@link #ammo()}; a cannon picks
         * its shot by preference (CAN3).
         */
        default Predicate<ItemStack> ammoIn(CrewSupply supply, int n) {
            return ammo();
        }
    }

    static boolean enabled() {
        return CannonConfig.CREW_ENABLED.get();
    }

    /** The cannon of {@code station} (either half resolves to the master), or null. */
    static @Nullable Gun cannon(ServerLevel level, StationRef station) {
        BlockPos pos = CannonService.master(level, station.pos());
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CannonBlock) || !(level.getBlockEntity(pos) instanceof CannonBlockEntity be)) return null;
        CannonLoad load = state.getValue(CannonBlock.LOAD);
        long left = CannonRules.reloadLeft(be.reloadUntil(), level.getGameTime());
        return new Gun() {
            public BlockPos pos() { return pos; }
            public CannonLoad load() { return load; }
            public long reloadLeft() { return left; }
            public int ammoCount() { return 1; }
            public Predicate<ItemStack> ammo() { return CrewLoading::allowedShot; }
            public int workTicks() { return CannonConfig.CREW_LOAD_TICKS.get(); }

            public Predicate<ItemStack> ammoIn(CrewSupply supply, int n) {
                ShotKind k = ShotRules.pick(shotPreference(station), kind -> supply.has(of(kind), n));
                return k == null ? ammo() : of(k);
            }

            public boolean put(ItemStack stack, boolean powder) {
                CannonService.Outcome o = CannonService.load(level, pos, null, stack).outcome();
                return o == (powder ? CannonService.Outcome.POWDER_IN : CannonService.Outcome.BALL_IN);
            }
        };
    }

    /** The swivel gun of {@code station}, or null. */
    static @Nullable Gun swivel(ServerLevel level, StationRef station) {
        BlockPos pos = station.pos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SwivelGunBlock) || !(level.getBlockEntity(pos) instanceof SwivelGunBlockEntity be)) return null;
        CannonLoad load = state.getValue(SwivelGunBlock.LOAD);
        long left = CannonRules.reloadLeft(be.reloadUntil(), level.getGameTime());
        return new Gun() {
            public BlockPos pos() { return pos; }
            public CannonLoad load() { return load; }
            public long reloadLeft() { return left; }
            public int ammoCount() { return CannonConfig.SWIVEL_AMMO_COUNT.get(); }
            public Predicate<ItemStack> ammo() { return SwivelService::isAmmo; }
            public int workTicks() { return CannonConfig.CREW_SWIVEL_LOAD_TICKS.get(); }

            public boolean put(ItemStack stack, boolean powder) {
                SwivelService.Outcome o = SwivelService.load(level, pos, null, stack).outcome();
                return o == (powder ? SwivelService.Outcome.POWDER_IN : SwivelService.Outcome.SHOT_IN);
            }
        };
    }

    /** A shot whose server toggle is on (CAN3). */
    private static boolean allowedShot(ItemStack stack) {
        ShotKind k = CannonBlock.shotOf(stack);
        return k != null && CannonConfig.allowed(k);
    }

    private static Predicate<ItemStack> of(ShotKind kind) {
        return stack -> CannonBlock.shotOf(stack) == kind;
    }

    /**
     * The order the crew at the cannon {@code station} reaches for shot in (CAN3): the shot its gunnery wants while the
     * ship's crews fire by themselves ({@link Gunnery#wantedShot}, then ball and chain shot), else the
     * {@code cannons.crew.load_preference} (then ball, chain shot, grapeshot).
     */
    static List<ShotKind> shotPreference(StationRef station) {
        ShotKind wanted = Gunnery.wantedShot(station);
        return wanted != null ? ShotRules.gunneryPreference(wanted, CannonConfig::allowed)
                : ShotRules.crewPreference(CannonConfig.CREW_LOAD_PREFERENCE.get(), CannonConfig::allowed);
    }

    private static CrewSupply supply(ServerLevel level, StationRef station, Gun gun) {
        return CrewSupply.around(level, station.ship(), gun.pos(), CannonConfig.CREW_SUPPLY_RANGE.get());
    }

    private static boolean supplied(CrewSupply supply, Gun gun, CrewSupplyRules.Need need) {
        return supply.has(POWDER, need.powder()) && supply.has(gun.ammoIn(supply, need.ammo()), need.ammo());
    }

    private static @Nullable StationState<Object> state(StationRef station) {
        return Stations.state(station);
    }

    /**
     * Work ticks of {@link CannonOrder#LOAD}: -1 when crew loading is off or the supply lacks powder or ammo, 0 when
     * the gun is loaded, else the load time (at least the rest of the reload cooldown). A second "Load!" during a load
     * keeps its progress.
     */
    static int loadTicks(ServerLevel level, StationRef station, Gun gun) {
        if (!enabled()) return -1;
        CrewSupplyRules.Need need = CrewSupplyRules.need(gun.load(), gun.ammoCount());
        if (need.nothing()) return 0;
        if (!supplied(supply(level, station, gun), gun, need)) return -1;
        StationState<Object> st = state(station);
        if (st != null && st.order() == CannonOrder.LOAD) return st.remaining();
        return CrewSupplyRules.loadTicks(gun.workTicks(), gun.reloadLeft());
    }

    /**
     * Work ticks of {@link CannonOrder#FIRE}: the fuse at a loaded gun; the rest of the load and then the fuse when the
     * crew is loading (crew loading on); 0 (not loaded) otherwise.
     */
    static int fireTicks(StationRef station, Gun gun) {
        if (CannonRules.canFire(gun.load())) {
            FIRE_AFTER_LOAD.remove(station);
            return CannonStation.FUSE_TICKS;
        }
        StationState<Object> st = state(station);
        if (enabled() && st != null && st.order() == CannonOrder.LOAD) {
            FIRE_AFTER_LOAD.add(station);
            return CrewSupplyRules.fireAfterLoad(st.remaining(), CannonStation.FUSE_TICKS);
        }
        FIRE_AFTER_LOAD.remove(station);
        return 0;
    }

    /**
     * Loads {@code gun} from the supply: checks that the supply covers the whole load first (nothing goes in
     * otherwise), then puts in the powder (if the gun has none yet) and the ammo through the gun's service and takes
     * each from the supply once it went in. False when nothing could be loaded.
     */
    static boolean load(ServerLevel level, StationRef station, Gun gun) {
        if (!enabled()) return false;
        CrewSupplyRules.Need need = CrewSupplyRules.need(gun.load(), gun.ammoCount());
        if (need.nothing()) return false;
        CrewSupply supply = supply(level, station, gun);
        if (!supplied(supply, gun, need)) return false;
        if (need.powder() > 0) {
            ItemStack powder = supply.peek(POWDER, need.powder());
            if (powder == null || !gun.put(powder, true)) return false;
            supply.take(POWDER, need.powder());
        }
        Predicate<ItemStack> which = gun.ammoIn(supply, need.ammo());
        ItemStack ammo = supply.peek(which, need.ammo());
        if (ammo == null || !gun.put(ammo, false)) return false;
        supply.take(which, need.ammo());
        return true;
    }

    /** Whether the "Fire!" that just finished at {@code station} was given during a load (and must load first). */
    static boolean firesAfterLoad(StationRef station) {
        return FIRE_AFTER_LOAD.remove(station);
    }

    /** After the crew's shot: with auto reload on, the crew starts loading again (quietly; nothing when unsupplied). */
    static void afterShot(ServerLevel level, StationRef station, boolean fired) {
        if (CrewSupplyRules.reloadAfterShot(enabled(), CannonConfig.CREW_AUTO_RELOAD.get(), fired)) {
            Stations.order(level, station, CannonOrder.LOAD);
        }
    }
}
