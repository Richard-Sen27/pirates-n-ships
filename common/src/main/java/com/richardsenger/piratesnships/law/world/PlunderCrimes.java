package com.richardsenger.piratesnships.law.world;

import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.crime.CrimeType;
import com.richardsenger.piratesnships.trade.net.MarketBackend;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The law's answer to plunder sold in a port that noticed it (docs/design.md §10.3, §13.1): every noticed sale made
 * through the market protocol (harbor master's desk, or a market opened by command) is reported as
 * {@link CrimeType#FENCE_PLUNDER} against the port. The law module listens to the trade module
 * ({@link MarketBackend#onNoticedPlunder}), never the other way round. Toggles, severity and the repeat window are the
 * law module's own ({@code law.criminal_score_enabled}, {@code law.severity.fence_plunder},
 * {@code law.repeat_cooldown_seconds.fence_plunder}); whether a sale is noticed at all is the trade module's
 * ({@code navy_notice_chance}, {@code village_notice_chance}).
 */
public final class PlunderCrimes {

    private PlunderCrimes() {
    }

    /** Registered by {@code LawModule#registerEvents}. */
    public static void register() {
        MarketBackend.onNoticedPlunder(PlunderCrimes::onNoticedSale);
    }

    /** Reports the noticed sale as a crime of the seller against the port. */
    public static void onNoticedSale(MarketBackend.NoticedSale sale) {
        LawService.reportCrime(sale.player(), CrimeType.FENCE_PLUNDER, portVictim(sale.port()), sale.port().toString());
    }

    /** The stable victim id of a port, so the repeat window applies per port. Pure. */
    public static UUID portVictim(ResourceLocation port) {
        return UUID.nameUUIDFromBytes(("pirates_n_ships:port/" + port).getBytes(StandardCharsets.UTF_8));
    }
}
