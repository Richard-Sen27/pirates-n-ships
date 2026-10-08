package com.richardsenger.piratesnships.crew.upkeep;

import com.richardsenger.piratesnships.crew.galley.ProvisionContainer;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.trade.cargo.CargoContainerBlockEntity;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The doubloons aboard a ship (CR2, docs/design.md §7.3): cargo crates and barrels holding doubloons and every other
 * container block (chests, barrels, ...) in the ship's plot except provisions containers, each with its distance to the
 * helm (to the plot center without a helm). Wages ({@link ShipDayTick}) and other charges on a ship take coins nearest
 * the helm first ({@link WageRules}).
 */
public final class ShipCoins {

    private ShipCoins() {
    }

    /** The ship's coin sources (plot positions holding at least one doubloon), in plot block order. */
    public static List<WageRules.Source<BlockPos>> scan(ServerLevel level, ShipBody ship) {
        List<BlockPos> blocks = ship.plotBlocks();
        Vec3 helm = helm(level, ship, blocks);
        List<WageRules.Source<BlockPos>> coins = new ArrayList<>();
        for (BlockPos p : blocks) {
            BlockEntity be = level.getBlockEntity(p);
            if (be == null) continue;
            long n = coinsIn(be);
            if (n > 0) coins.add(new WageRules.Source<>(p.immutable(), Vec3.atCenterOf(p).distanceTo(helm), n));
        }
        return coins;
    }

    /** All doubloons aboard. */
    public static long total(ServerLevel level, ShipBody ship) {
        long n = 0;
        for (WageRules.Source<BlockPos> s : scan(level, ship)) n += s.coins();
        return n;
    }

    /**
     * Takes {@code amount} doubloons from the ship, nearest the helm first, when it holds that many; otherwise takes
     * nothing. An amount of 0 or less takes nothing and succeeds.
     *
     * @return whether the amount was taken
     */
    public static boolean take(ServerLevel level, ShipBody ship, int amount) {
        if (amount <= 0) return true;
        WageRules.Payment<BlockPos> payment = WageRules.pay(1, amount, scan(level, ship));
        if (payment.paid() < 1) return false;
        take(level, payment);
        return true;
    }

    /** Takes the coins a planned {@code payment} over {@link #scan} sources says to take out of each source. */
    public static void take(ServerLevel level, WageRules.Payment<BlockPos> payment) {
        for (Map.Entry<BlockPos, Long> e : payment.takes().entrySet()) {
            BlockEntity be = level.getBlockEntity(e.getKey());
            int n = (int) Math.min(Integer.MAX_VALUE, e.getValue());
            if (be instanceof CargoContainerBlockEntity cargo) {
                cargo.extract(n);
            } else if (be instanceof Container c) {
                Wallet.take(c, n);
            }
        }
    }

    /** Doubloons in a block entity that may pay (never a provisions container). */
    public static long coinsIn(BlockEntity be) {
        if (be instanceof ProvisionContainer) return 0;
        if (be instanceof CargoContainerBlockEntity cargo) {
            return Wallet.isCoin(cargo.heldKind()) ? cargo.count() : 0;
        }
        return be instanceof Container c ? Wallet.count(c) : 0;
    }

    /** The helm's center (the first helm block in {@code blocks}), or the plot center without a helm. */
    private static Vec3 helm(ServerLevel level, ShipBody ship, List<BlockPos> blocks) {
        for (BlockPos p : blocks) {
            if (level.getBlockState(p).is(AssemblyContent.HELM.get())) {
                return Vec3.atCenterOf(p);
            }
        }
        BlockPos[] b = ship.plotBounds();
        return Vec3.atLowerCornerOf(b[0]).add(Vec3.atLowerCornerOf(b[1]).add(1, 1, 1)).scale(0.5);
    }
}
