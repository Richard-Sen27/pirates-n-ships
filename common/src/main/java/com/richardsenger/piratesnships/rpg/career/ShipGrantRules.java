package com.richardsenger.piratesnships.rpg.career;

import com.mojang.serialization.Codec;
import com.richardsenger.piratesnships.trade.market.PortKind;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The pure rules of the ship grants (docs/design.md §15, SHP1): fighting ships come by rank or by capture, never from
 * the shipwright. A navy officer reaching {@code careers.ship_grants.navy_rank} (Captain) is granted a navy sloop, a
 * pirate reaching {@code infamy_rank} (Dread Captain) a pirate sloop, once per player and ladder; the grant is a
 * {@link ShipCommission} item redeemed at a port of the ladder's kind. No world access and no config reads;
 * {@link ShipGrants} feeds them the config ({@link Params}) and the player's ledger (the promotion gifts' list,
 * {@link CareerAttachments#GIFTS}, which holds {@link #grantKey} and {@link #redeemedKey} too).
 */
public final class ShipGrantRules {

    /** The two ladders a ship is granted on; each has its port kind (where it is redeemed) and its flag. */
    public enum Ladder implements StringRepresentable {
        NAVY("navy", PortKind.NAVY_OUTPOST),
        PIRATES("pirates", PortKind.PIRATE_ISLAND);

        public static final Codec<Ladder> CODEC = StringRepresentable.fromEnum(Ladder::values);

        private final String id;
        private final PortKind port;

        Ladder(String id, PortKind port) {
            this.id = id;
            this.port = port;
        }

        public String id() {
            return id;
        }

        /** The kind of port whose officer or desk delivers this ladder's ship. */
        public PortKind port() {
            return port;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    /**
     * The grants' config.
     *
     * @param enabled        {@code careers.ship_grants.enabled} (and {@code careers.enabled})
     * @param navyRank       the navy rank whose first reaching grants the navy ship
     * @param infamyRank     the infamy rank whose first reaching grants the pirate ship
     * @param navyTemplate   the ship template of the navy grant
     * @param pirateTemplate the ship template of the pirate grant
     */
    public record Params(boolean enabled, NavyRank navyRank, InfamyRank infamyRank, ResourceLocation navyTemplate,
                         ResourceLocation pirateTemplate) {

        public static final NavyRank DEFAULT_NAVY_RANK = NavyRank.CAPTAIN;
        public static final InfamyRank DEFAULT_INFAMY_RANK = InfamyRank.DREAD_CAPTAIN;
        public static final ResourceLocation DEFAULT_NAVY_TEMPLATE = ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "navy_sloop_armed");
        public static final ResourceLocation DEFAULT_PIRATE_TEMPLATE = ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "pirate_sloop_armed");

        /** The shipped defaults: a Captain gets the armed navy sloop, a Dread Captain the armed pirate sloop. */
        public static Params defaults() {
            return new Params(true, DEFAULT_NAVY_RANK, DEFAULT_INFAMY_RANK, DEFAULT_NAVY_TEMPLATE, DEFAULT_PIRATE_TEMPLATE);
        }

        /** The template granted on {@code ladder}. */
        public ResourceLocation template(Ladder ladder) {
            return ladder == Ladder.NAVY ? navyTemplate : pirateTemplate;
        }
    }

    /** Why a commission is not redeemed (or {@link #OK}). */
    public enum Verdict { OK, DISABLED, NOT_YOURS, ALREADY_REDEEMED, WRONG_PORT, RANK_LOST }

    static final List<String> NAVY_NAMES = List.of("Resolute", "Vigilant", "Steadfast", "Dauntless", "Guardian",
            "Intrepid", "Sentinel", "Valiant");
    static final List<String> PIRATE_NAMES = List.of("Black Gull", "Sea Wolf", "Crimson Tide", "Rusty Cutlass",
            "Widow's Revenge", "Salty Dog", "Grinning Skull", "Night Shark");

    private ShipGrantRules() {
    }

    /** The ledger key of a ladder's grant (the commission was handed out). */
    public static String grantKey(Ladder ladder) {
        return "ship." + ladder.id();
    }

    /** The ledger key of a redeemed commission (the ship was delivered). */
    public static String redeemedKey(Ladder ladder) {
        return "ship." + ladder.id() + ".redeemed";
    }

    /** Whether {@code record} holds the rank {@code ladder}'s grant needs (a navy rank only counts in service). */
    public static boolean holdsRank(CareerRecord record, Ladder ladder, Params p) {
        return ladder == Ladder.NAVY ? CareerRewardRules.serves(record, p.navyRank()) : record.infamy().atLeast(p.infamyRank());
    }

    /**
     * The ladders whose commission is due for a career now at {@code after}: the grant's rank is held and the ledger
     * has no grant of that ladder yet, so each ladder grants once per player, whatever resigning, enlisting again or a
     * later rank does. A player who already held the rank before the grants existed receives it at the next change
     * of the career. Nothing while disabled.
     */
    public static List<Ladder> grantsDue(CareerRecord after, Set<String> ledger, Params p) {
        List<Ladder> out = new ArrayList<>();
        if (!p.enabled()) return out;
        for (Ladder l : Ladder.values()) {
            if (holdsRank(after, l, p) && !ledger.contains(grantKey(l))) out.add(l);
        }
        return out;
    }

    /**
     * Whether {@code player} may redeem {@code commission} at a port of kind {@code port}: the grants are on, the
     * commission names the player, this ladder's ship was not delivered to them yet, the port is of the ladder's kind
     * and the player still holds the rank (a deserter's navy commission is void).
     */
    public static Verdict redeem(ShipCommission commission, UUID player, PortKind port, CareerRecord record, Set<String> ledger,
                                 Params p) {
        if (!p.enabled()) return Verdict.DISABLED;
        if (!commission.owner().equals(player)) return Verdict.NOT_YOURS;
        if (ledger.contains(redeemedKey(commission.ladder()))) return Verdict.ALREADY_REDEEMED;
        if (port != commission.ladder().port()) return Verdict.WRONG_PORT;
        if (!holdsRank(record, commission.ladder(), p)) return Verdict.RANK_LOST;
        return Verdict.OK;
    }

    /** The bare name of a granted ship, picked by {@code seed} (the player) from the ladder's list. */
    public static String baseName(Ladder ladder, UUID seed) {
        List<String> names = ladder == Ladder.NAVY ? NAVY_NAMES : PIRATE_NAMES;
        return names.get(Math.floorMod(seed.hashCode(), names.size()));
    }
}
