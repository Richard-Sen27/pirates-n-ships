package com.richardsenger.piratesnships.crew.upkeep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** The order of the wage sources (CRW2): containers aboard nearest the helm first, the owner's wallet last. */
class PayKeyTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final BlockPos NEAR = new BlockPos(1, 0, 0);
    private static final BlockPos FAR = new BlockPos(9, 0, 0);

    private static WageRules.Source<BlockPos> chest(BlockPos pos, double distance, long coins) {
        return new WageRules.Source<>(pos, distance, coins);
    }

    @Test
    void walletComesAfterEveryContainer() {
        List<WageRules.Source<PayKey>> sources = PayKey.sources(List.of(chest(FAR, 1000, 3), chest(NEAR, 1, 2)), OWNER, 50);
        assertEquals(3, sources.size());
        assertEquals(new PayKey.Wallet(OWNER), sources.get(2).key());
        assertEquals(PayKey.WALLET_DISTANCE, sources.get(2).distance());
        WageRules.Payment<PayKey> p = WageRules.pay(4, 2, sources);
        assertEquals(4, p.paid());
        assertEquals(List.of(new PayKey.Container(NEAR), new PayKey.Container(FAR), new PayKey.Wallet(OWNER)),
                List.copyOf(p.takes().keySet()), "near chest, far chest, then the wallet");
        assertEquals(3, PayKey.walletCoins(p));
        WageRules.Payment<BlockPos> aboard = PayKey.containers(p);
        assertEquals(Map.of(NEAR, 2L, FAR, 3L), aboard.takes());
        assertEquals(5, aboard.coinsTaken());
    }

    @Test
    void enoughAboardLeavesTheWalletAlone() {
        WageRules.Payment<PayKey> p = WageRules.pay(2, 2, PayKey.sources(List.of(chest(FAR, 1000, 10)), OWNER, 50));
        assertEquals(2, p.paid());
        assertEquals(0, PayKey.walletCoins(p));
        assertEquals(Map.of(FAR, 4L), PayKey.containers(p).takes());
    }

    @Test
    void emptyChestsPayFromTheWalletAlone() {
        WageRules.Payment<PayKey> p = WageRules.pay(2, 2, PayKey.sources(List.of(), OWNER, 3));
        assertEquals(1, p.paid(), "3 coins pay one wage of 2");
        assertEquals(1, p.unpaid());
        assertEquals(2, PayKey.walletCoins(p));
        assertTrue(PayKey.containers(p).takes().isEmpty());
    }

    @Test
    void noOwnerOrEmptyWalletAddsNoSource() {
        assertEquals(1, PayKey.sources(List.of(chest(NEAR, 1, 2)), null, 50).size());
        assertEquals(1, PayKey.sources(List.of(chest(NEAR, 1, 2)), OWNER, 0).size());
        WageRules.Payment<PayKey> p = WageRules.pay(2, 2, PayKey.sources(List.of(chest(NEAR, 1, 2)), null, 50));
        assertEquals(1, p.unpaid());
        assertEquals(0, PayKey.walletCoins(p));
    }

    @Test
    void walletIsFartherThanAnyHelmDistance() {
        List<WageRules.Source<PayKey>> sources = PayKey.sources(List.of(chest(FAR, 1e300, 2)), OWNER, 2);
        WageRules.Payment<PayKey> p = WageRules.pay(1, 2, sources);
        assertEquals(Map.of(new PayKey.Container(FAR), 2L), p.takes());
    }

    @Test
    void payRecordKeepsTheWalletShare() {
        ShipUpkeep.PayRecord r = new ShipUpkeep.PayRecord(true, 2, 0, 4, 1);
        assertEquals(3, r.shipCoins());
        assertEquals(new ShipUpkeep.PayRecord(true, 2, 0, 4, 0), new ShipUpkeep.PayRecord(true, 2, 0, 4));
    }
}
