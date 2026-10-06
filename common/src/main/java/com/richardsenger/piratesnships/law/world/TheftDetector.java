package com.richardsenger.piratesnships.law.world;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.law.LawConfig;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.crime.CrimeType;
import com.richardsenger.piratesnships.law.crime.CriminalRecord.CrimeResult;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects theft from village containers (docs/design.md §13.1) with {@link TheftRule}. On
 * {@code CONTAINER_OPEN} it remembers the block containers behind the menu and their item counts; on
 * {@code CONTAINER_CLOSE} it compares, checks the village, player-placement and witnesses at that moment, and reports
 * {@link CrimeType#THEFT}. The victim id is derived from the container position, so the repeat window applies per
 * container.
 *
 * <p>Only containers that are block entities count (chests, barrels, double chests, furnaces, ...); the ender chest,
 * entity containers (chest minecarts, donkeys) and the player's own inventory never do. Changes made by others
 * (hoppers, a second player) while the menu is open are attributed to the viewer; accepted as rare.
 */
public final class TheftDetector {

    /** Search radius around the player for the halves of a double chest. */
    private static final int COMPOUND_SEARCH_RADIUS = 8;

    private record Visit(int containerId, ServerLevel level, List<Container> containers, List<BlockPos> positions,
                         Map<Item, Integer> before) {
    }

    private static final Map<UUID, Visit> OPEN = new ConcurrentHashMap<>();

    private TheftDetector() {
    }

    /** Listener for {@code CommonEvents.CONTAINER_OPEN}. */
    public static void onContainerOpen(Player player, AbstractContainerMenu menu) {
        if (!(player.level() instanceof ServerLevel level) || player.isSpectator()) return;
        OPEN.remove(player.getUUID());
        if (!LawConfig.theftParams().enabled()) return;
        List<Container> containers = new ArrayList<>();
        Set<BlockPos> positions = new LinkedHashSet<>();
        for (Container c : menuContainers(menu)) {
            List<BlockPos> found = positionsOf(level, player, c);
            if (found.isEmpty()) continue;
            containers.add(c);
            positions.addAll(found);
        }
        if (containers.isEmpty()) return;
        OPEN.put(player.getUUID(), new Visit(menu.containerId, level, containers, List.copyOf(positions), count(containers)));
    }

    /** Listener for {@code CommonEvents.CONTAINER_CLOSE}. */
    public static void onContainerClose(Player player, AbstractContainerMenu menu) {
        Visit visit = OPEN.remove(player.getUUID());
        if (visit == null || visit.containerId() != menu.containerId || player.level() != visit.level()) return;
        judge(player, visit);
    }

    public static void onLogout(ServerPlayer player) {
        OPEN.remove(player.getUUID());
    }

    public static void onServerStopped() {
        OPEN.clear();
    }

    private static TheftRule.Verdict judge(Player player, Visit visit) {
        TheftRule.Params params = LawConfig.theftParams();
        int removed = TheftRule.netRemoved(visit.before(), count(visit.containers()));
        ServerLevel level = visit.level();
        boolean village = visit.positions().stream()
                .anyMatch(p -> level.structureManager().getStructureWithPieceAt(p, StructureTags.VILLAGE).isValid());
        boolean placed = visit.positions().stream().allMatch(p -> PlacedBlocks.isPlayerPlaced(level, p));
        List<LivingEntity> watchers = potentialWitnesses(level, player, params.witnessRange());
        List<TheftRule.Witness> candidates = watchers.stream()
                .map(w -> new TheftRule.Witness(w.distanceToSqr(player), w.hasLineOfSight(player))).toList();
        int witnesses = TheftRule.countWitnesses(candidates, params);
        TheftRule.Verdict verdict = TheftRule.judge(params, new TheftRule.Visit(village, placed, witnesses, removed));
        if (verdict.isTheft()) {
            CrimeResult result = LawService.reportCrime(player, CrimeType.THEFT, victimId(level, visit.positions().getFirst()));
            if (result.counted()) {
                LivingEntity seenBy = watchers.stream().min(Comparator.comparingDouble(w -> w.distanceToSqr(player))).orElse(null);
                if (seenBy != null) {
                    player.displayClientMessage(Component.translatable(THEFT_SEEN_KEY, seenBy.getDisplayName()), true);
                }
            }
        }
        return verdict;
    }

    /** Action-bar message to the thief. */
    public static final String THEFT_SEEN_KEY = Constants.MOD_ID + ".law.theft_seen";

    /** Stable victim id for a container position (repeat window per container). */
    public static UUID victimId(ServerLevel level, BlockPos pos) {
        String key = "theft:" + level.dimension().location() + ":" + pos.asLong();
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static List<LivingEntity> potentialWitnesses(ServerLevel level, Player thief, double range) {
        if (range <= 0) return List.of();
        return level.getEntitiesOfClass(LivingEntity.class, thief.getBoundingBox().inflate(range), e -> e != thief
                && e.isAlive() && !e.isSleeping() && (LawService.isLawProtected(e) || LawService.isNavy(e)));
    }

    private static Set<Container> menuContainers(AbstractContainerMenu menu) {
        Set<Container> out = new LinkedHashSet<>();
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory)) out.add(slot.container);
        }
        return out;
    }

    private static List<BlockPos> positionsOf(ServerLevel level, Player player, Container c) {
        if (c instanceof BlockEntity be) {
            return be.getLevel() == level ? List.of(be.getBlockPos()) : List.of();
        }
        if (c instanceof CompoundContainer compound) {
            List<BlockPos> out = new ArrayList<>();
            BlockPos center = player.blockPosition();
            for (BlockPos p : BlockPos.betweenClosed(center.offset(-COMPOUND_SEARCH_RADIUS, -COMPOUND_SEARCH_RADIUS, -COMPOUND_SEARCH_RADIUS),
                    center.offset(COMPOUND_SEARCH_RADIUS, COMPOUND_SEARCH_RADIUS, COMPOUND_SEARCH_RADIUS))) {
                if (level.getBlockEntity(p) instanceof Container part && part instanceof BlockEntity && compound.contains(part)) {
                    out.add(p.immutable());
                }
            }
            return out;
        }
        return List.of();
    }

    private static Map<Item, Integer> count(List<Container> containers) {
        Map<Item, Integer> counts = new HashMap<>();
        for (Container c : containers) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (!s.isEmpty()) counts.merge(s.getItem(), s.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    /** Whether {@code player} currently has a tracked container visit (tests). */
    public static boolean isTracking(Player player) {
        return OPEN.containsKey(player.getUUID());
    }
}
