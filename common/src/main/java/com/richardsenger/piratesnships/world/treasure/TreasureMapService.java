package com.richardsenger.piratesnships.world.treasure;

import com.richardsenger.piratesnships.chart.ChartConfig;
import com.richardsenger.piratesnships.world.WorldConfig;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.world.port.TreasureSite;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Server side of the treasure maps (TM1, design.md §10.1): binding a blank map, the found state, and marking a site
 * looted when its chest is opened. Server thread only.
 */
public final class TreasureMapService {

    /** A chest this close (blocks, on every axis) to a site is the site's chest. */
    public static final int CHEST_REACH = 2;
    /** Sites farther than this (blocks, on every axis) from the player who opens a container are not checked. */
    static final int PLAYER_REACH = 8;

    private TreasureMapService() {
    }

    /** The map's component, or {@code null} for a blank map (or another item). */
    public static TreasureMapData data(ItemStack stack) {
        return stack.get(TreasureMapContent.TREASURE_MAP_DATA.get());
    }

    /** {@link TreasureMapItem#use} on the server. */
    public static InteractionResultHolder<ItemStack> use(ServerLevel level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        TreasureMapData data = data(stack);
        if (data != null) {
            refresh(stack, level.getServer());
            data = data(stack);
            Component msg = data.found() ? Component.translatable(TreasureMapText.FOUND)
                    : TreasureMapText.bearing(TreasureBearing.of(player.getX(), player.getZ(), data.site().getX() + 0.5, data.site().getZ() + 0.5));
            player.displayClientMessage(msg, true);
            return InteractionResultHolder.success(stack);
        }
        if (!WorldConfig.TREASURE_MAPS_ENABLED.get()) {
            player.displayClientMessage(Component.translatable(TreasureMapText.DISABLED), true);
            return InteractionResultHolder.fail(stack);
        }
        Optional<TreasureBinding.Choice> choice = TreasureBinding.choose(PortRegistry.get(level.getServer()).index().all(),
                level.dimension(), player.blockPosition(), WorldConfig.TREASURE_MAP_SEARCH_RADIUS.get());
        if (choice.isEmpty()) {
            player.displayClientMessage(Component.translatable(TreasureMapText.NONE), true);
            return InteractionResultHolder.fail(stack);
        }
        ItemStack bound = boundMap(level, choice.get().port(), choice.get().site());
        player.displayClientMessage(TreasureMapText.bearing(TreasureBearing.of(player.getX(), player.getZ(),
                choice.get().site().pos().getX() + 0.5, choice.get().site().pos().getZ() + 0.5)), true);
        if (stack.getCount() == 1) return InteractionResultHolder.success(bound);
        // one map of a stack of blanks binds; the rest stay blank
        stack.shrink(1);
        if (!player.getInventory().add(bound)) player.drop(bound, false);
        return InteractionResultHolder.success(stack);
    }

    /** A new map bound to {@code site} of {@code port}, its picture sampled now from the loaded chunks of {@code level}. */
    public static ItemStack boundMap(ServerLevel level, Port port, TreasureSite site) {
        int cellBlocks = ChartConfig.CELL_BLOCKS.get();
        byte[] cells = TreasureRaster.sample(level, site.pos(), cellBlocks, ChartConfig.SHALLOW_DEPTH.get());
        TreasureMapData data = new TreasureMapData(port.id(), site.pos(), site.looted(), cellBlocks,
                TreasureMapData.originCell(site.pos().getX(), cellBlocks), TreasureMapData.originCell(site.pos().getZ(), cellBlocks), cells);
        ItemStack stack = new ItemStack(TreasureMapContent.TREASURE_MAP.get());
        stack.set(TreasureMapContent.TREASURE_MAP_DATA.get(), data);
        return stack;
    }

    /** Marks a bound map found once its site is looted; true if it changed. */
    public static boolean refresh(ItemStack stack, MinecraftServer server) {
        TreasureMapData data = data(stack);
        if (data == null || data.found()) return false;
        if (!TreasureBinding.found(PortRegistry.get(server).index().byId(data.port()), data.site())) return false;
        stack.set(TreasureMapContent.TREASURE_MAP_DATA.get(), data.withFound(true));
        return true;
    }

    /**
     * {@code CommonEvents.CONTAINER_OPEN}: a container opened at a treasure site (a block container within
     * {@link #CHEST_REACH} of an unlooted site) marks the site looted on its port; the player is told and their maps
     * of it turn found at once (everyone else's within a second, as they tick).
     */
    public static void onContainerOpen(Player player, AbstractContainerMenu menu) {
        if (!(player.level() instanceof ServerLevel level) || player.isSpectator()) return;
        Set<Container> opened = menuContainers(menu);
        if (opened.isEmpty()) return;
        PortRegistry registry = PortRegistry.get(level.getServer());
        BlockPos at = player.blockPosition();
        boolean any = false;
        for (Port port : registry.index().all()) {
            if (!port.dimension().equals(level.dimension()) || !TreasureBinding.hasUnlooted(port)) continue;
            List<BlockPos> chests = new ArrayList<>();
            for (TreasureSite site : port.treasures()) {
                if (site.looted() || !TreasureBinding.atSite(site.pos(), at, PLAYER_REACH)) continue;
                chests.addAll(openedNear(level, site.pos(), opened));
            }
            if (chests.isEmpty()) continue;
            Optional<Port> looted = TreasureBinding.markLooted(port, chests, CHEST_REACH);
            if (looted.isPresent()) {
                replace(registry, looted.get());
                any = true;
            }
        }
        if (!any) return;
        boolean told = false;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (refresh(inv.getItem(i), level.getServer())) told = true;
        }
        if (told || WorldConfig.TREASURE_MAPS_ENABLED.get()) {
            player.displayClientMessage(Component.translatable(TreasureMapText.FOUND), true);
        }
    }

    /** Replaces the port's record in the registry (the registry keeps one record per id). */
    public static void replace(PortRegistry registry, Port port) {
        registry.remove(port.id());
        registry.add(port);
    }

    /** Block containers within {@link #CHEST_REACH} of {@code site} that are (part of) one of {@code opened}. */
    private static List<BlockPos> openedNear(ServerLevel level, BlockPos site, Set<Container> opened) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(site.offset(-CHEST_REACH, -CHEST_REACH, -CHEST_REACH),
                site.offset(CHEST_REACH, CHEST_REACH, CHEST_REACH))) {
            if (!level.isLoaded(p) || !(level.getBlockEntity(p) instanceof BlockEntity be) || !(be instanceof Container c)) continue;
            for (Container o : opened) {
                if (o == c || o instanceof CompoundContainer compound && compound.contains(c)) {
                    out.add(p.immutable());
                    break;
                }
            }
        }
        return out;
    }

    private static Set<Container> menuContainers(AbstractContainerMenu menu) {
        Set<Container> out = new LinkedHashSet<>();
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory)) out.add(slot.container);
        }
        return out;
    }
}
