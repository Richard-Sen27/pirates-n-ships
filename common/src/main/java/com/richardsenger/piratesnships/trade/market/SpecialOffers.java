package com.richardsenger.piratesnships.trade.market;

import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@link SpecialOffer}s other modules add to port markets (TM1). Register once at mod construction
 * ({@code registerContent}); a second offer with the same id replaces the first.
 */
public final class SpecialOffers {

    private static final Map<ResourceLocation, SpecialOffer> OFFERS = new ConcurrentHashMap<>();

    private SpecialOffers() {
    }

    public static void register(SpecialOffer offer) {
        OFFERS.put(offer.id(), offer);
    }

    public static Optional<SpecialOffer> byId(ResourceLocation id) {
        return Optional.ofNullable(OFFERS.get(id));
    }

    /** The offers ports of {@code kind} list right now, by id. */
    public static List<SpecialOffer> offeredAt(PortKind kind) {
        return OFFERS.values().stream().filter(o -> o.offeredAt(kind))
                .sorted(Comparator.comparing(o -> o.id().toString())).toList();
    }
}
