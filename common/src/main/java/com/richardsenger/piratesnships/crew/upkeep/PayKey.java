package com.richardsenger.piratesnships.crew.upkeep;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Where a wage comes from (CRW2, docs/design.md §7.3): a container aboard ({@link Container}, a plot position from
 * {@link ShipCoins#scan}) or the doubloons the ship's owner carries ({@link Wallet}). The key type of the wage sources
 * that {@link ShipDayTick} hands to {@link WageRules}. Pure.
 * <p>
 * The owner's wallet is the farthest source ({@link #WALLET_DISTANCE}): {@link WageRules} empties every container aboard
 * before it touches the wallet.
 */
public sealed interface PayKey {

    /** The wallet's distance from the helm: farther than any container, so the coins aboard always pay first. */
    double WALLET_DISTANCE = Double.MAX_VALUE;

    /** A container aboard at plot position {@code pos}. */
    record Container(BlockPos pos) implements PayKey {
        public Container {
            Objects.requireNonNull(pos, "pos");
        }
    }

    /** The doubloons player {@code owner} carries. */
    record Wallet(UUID owner) implements PayKey {
        public Wallet {
            Objects.requireNonNull(owner, "owner");
        }
    }

    /**
     * The wage sources of one ship: its containers ({@link ShipCoins#scan}, distance to the helm) and, when
     * {@code owner} is not null and carries coins, the owner's wallet last.
     */
    static List<WageRules.Source<PayKey>> sources(List<WageRules.Source<BlockPos>> containers, @Nullable UUID owner, long walletCoins) {
        List<WageRules.Source<PayKey>> out = new ArrayList<>(containers.size() + 1);
        for (WageRules.Source<BlockPos> s : containers) {
            out.add(new WageRules.Source<>(new Container(s.key()), s.distance(), s.coins()));
        }
        if (owner != null && walletCoins > 0) {
            out.add(new WageRules.Source<>(new Wallet(owner), WALLET_DISTANCE, walletCoins));
        }
        return out;
    }

    /** The part of {@code payment} that comes out of containers aboard, for {@link ShipCoins#take}. */
    static WageRules.Payment<BlockPos> containers(WageRules.Payment<PayKey> payment) {
        Map<BlockPos, Long> takes = new LinkedHashMap<>();
        long coins = 0;
        for (Map.Entry<PayKey, Long> e : payment.takes().entrySet()) {
            if (e.getKey() instanceof Container c) {
                takes.merge(c.pos(), e.getValue(), Long::sum);
                coins += e.getValue();
            }
        }
        return new WageRules.Payment<>(payment.paid(), payment.unpaid(), coins, takes);
    }

    /** Doubloons {@code payment} takes out of the owner's wallet (0 when it does not touch it). */
    static long walletCoins(WageRules.Payment<PayKey> payment) {
        long n = 0;
        for (Map.Entry<PayKey, Long> e : payment.takes().entrySet()) {
            if (e.getKey() instanceof Wallet) n += e.getValue();
        }
        return n;
    }
}
