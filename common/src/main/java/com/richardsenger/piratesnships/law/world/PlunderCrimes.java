package com.richardsenger.piratesnships.law.world;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.law.LawConfig;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.crime.CrimeType;
import com.richardsenger.piratesnships.law.crime.CriminalRecord;
import com.richardsenger.piratesnships.trade.exchange.TransactionResult;
import com.richardsenger.piratesnships.trade.net.MarketBackend;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The law's answer to plunder a port noticed (docs/design.md §10.3, §13.1, §13.4). The law module listens to the trade
 * module ({@link MarketBackend#onNoticedPlunder}), never the other way round; every server-side sale path (the market
 * protocol of the harbor master's desk and the market command, and the direct {@code /pirates trade sell} command)
 * reaches it.
 * <ul>
 *   <li><b>Refused plunder</b> (LAW3): a village or navy outpost desk refuses plunder-marked goods
 *       ({@link TransactionResult.Status#PLUNDER_REFUSED}). With {@code law.plunder_notice} on, the harbor master
 *       reports the seller: {@link CrimeType#SELLING_PLUNDER} against the port, its repeat window (one in-game day)
 *       keeping it to the first refusal per player, port and day, and the seller is told.</li>
 * </ul>
 * Before LAW3 a noticed sale that went through was {@link CrimeType#FENCE_PLUNDER}; no port sells noticed plunder any
 * more (the fence never notices, the others refuse), so nothing records that crime now (LAW3b).
 */
public final class PlunderCrimes {

    /** Message to a seller the harbor master reported (LAW3). */
    public static final String REPORTED_KEY = "message." + Constants.MOD_ID + ".law.plunder_reported";

    private PlunderCrimes() {
    }

    /** Registered by {@code LawModule#registerEvents}. */
    public static void register() {
        MarketBackend.onNoticedPlunder(PlunderCrimes::onNoticedSale);
    }

    /** Reports a refused plunder offer as a crime of the seller against the port; other results are ignored. */
    public static void onNoticedSale(MarketBackend.NoticedSale sale) {
        if (sale.result().status() == TransactionResult.Status.PLUNDER_REFUSED) onRefusedPlunder(sale.player(), sale.port());
    }

    /**
     * LAW3: a desk at {@code port} refused {@code seller}'s plunder. With {@code law.plunder_notice} the harbor master
     * reports it as {@link CrimeType#SELLING_PLUNDER}; the seller hears of it when the crime counted (not on a repeat
     * within the day, nor with the criminal score off). Returns the crime's outcome ({@code DISABLED} with the notice
     * off).
     */
    public static CriminalRecord.CrimeOutcome onRefusedPlunder(ServerPlayer seller, ResourceLocation port) {
        if (!LawConfig.PLUNDER_NOTICE.get()) return CriminalRecord.CrimeOutcome.DISABLED;
        CriminalRecord.CrimeResult r = LawService.reportCrime(seller, CrimeType.SELLING_PLUNDER, portVictim(port), port.toString());
        if (r.counted()) seller.sendSystemMessage(Component.translatable(REPORTED_KEY).withStyle(ChatFormatting.RED));
        return r.outcome();
    }

    /** The stable victim id of a port, so the repeat window applies per port. Pure. */
    public static UUID portVictim(ResourceLocation port) {
        return UUID.nameUUIDFromBytes(("pirates_n_ships:port/" + port).getBytes(StandardCharsets.UTF_8));
    }
}
