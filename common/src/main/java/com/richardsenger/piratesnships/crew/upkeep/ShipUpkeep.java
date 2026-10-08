package com.richardsenger.piratesnships.crew.upkeep;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.crew.provisions.ProvisioningState;

/**
 * A ship's upkeep state between two dawns (CR2), kept in {@link ShipUpkeepData}. Immutable.
 *
 * @param provisioning  the crew's side of the provisions rules (credits, hunger, thirst, scurvy clock, drunk timer)
 * @param workSpeed     the work-speed factor of the last day tick, multiplied into station work until the next one
 * @param scurvy        the crew had scurvy at the last day tick
 * @param hungry        the crew was out of food at the last day tick
 * @param thirsty       the crew was out of water at the last day tick
 * @param lowMoraleDays consecutive dawns with the ship's average morale below {@code mutiny_below} (mutiny counter)
 * @param lastPay       the pay result of the last day tick
 */
public record ShipUpkeep(ProvisioningState provisioning, double workSpeed, boolean scurvy, boolean hungry, boolean thirsty,
                         int lowMoraleDays, PayRecord lastPay) {

    public static final ShipUpkeep INITIAL = new ShipUpkeep(ProvisioningState.INITIAL, 1.0, false, false, false, 0, PayRecord.NONE);

    /**
     * The pay of one day: {@code wagesOn} false when wages were off (or no day tick ran yet).
     *
     * @param paid        members paid in full
     * @param unpaid      members not paid
     * @param coins       doubloons taken in all
     * @param walletCoins of these, doubloons taken from the owner's wallet (CRW2, {@code crew.wages.from_wallet}); the
     *                    rest came from containers aboard
     */
    public record PayRecord(boolean wagesOn, int paid, int unpaid, long coins, long walletCoins) {
        public static final PayRecord NONE = new PayRecord(false, 0, 0, 0, 0);

        /** A pay record with every coin from containers aboard. */
        public PayRecord(boolean wagesOn, int paid, int unpaid, long coins) {
            this(wagesOn, paid, unpaid, coins, 0);
        }

        /** Doubloons taken from containers aboard. */
        public long shipCoins() {
            return coins - walletCoins;
        }

        public static final Codec<PayRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("wages_on", false).forGetter(PayRecord::wagesOn),
                Codec.INT.optionalFieldOf("paid", 0).forGetter(PayRecord::paid),
                Codec.INT.optionalFieldOf("unpaid", 0).forGetter(PayRecord::unpaid),
                Codec.LONG.optionalFieldOf("coins", 0L).forGetter(PayRecord::coins),
                Codec.LONG.optionalFieldOf("wallet_coins", 0L).forGetter(PayRecord::walletCoins)
        ).apply(i, PayRecord::new));
    }

    public static final Codec<ShipUpkeep> CODEC = RecordCodecBuilder.create(i -> i.group(
            ProvisioningState.CODEC.optionalFieldOf("provisioning", ProvisioningState.INITIAL).forGetter(ShipUpkeep::provisioning),
            Codec.DOUBLE.optionalFieldOf("work_speed", 1.0).forGetter(ShipUpkeep::workSpeed),
            Codec.BOOL.optionalFieldOf("scurvy", false).forGetter(ShipUpkeep::scurvy),
            Codec.BOOL.optionalFieldOf("hungry", false).forGetter(ShipUpkeep::hungry),
            Codec.BOOL.optionalFieldOf("thirsty", false).forGetter(ShipUpkeep::thirsty),
            Codec.INT.optionalFieldOf("low_morale_days", 0).forGetter(ShipUpkeep::lowMoraleDays),
            PayRecord.CODEC.optionalFieldOf("last_pay", PayRecord.NONE).forGetter(ShipUpkeep::lastPay)
    ).apply(i, ShipUpkeep::new));
}
