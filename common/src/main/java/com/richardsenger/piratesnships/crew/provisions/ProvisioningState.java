package com.richardsenger.piratesnships.crew.provisions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The crew's side of provisioning for one ship, kept next to the {@link ProvisionStore} (in ship data). All times are
 * in ticks; they are doubles because a shortage can start in the middle of a tick interval.
 *
 * @param foodCredit            nutrition already taken out of the store but not yet "used up" by the crew. Units
 *                              are eaten whole, so the leftover of the last unit carries over here.
 * @param waterCredit           the same for water rations
 * @param rumCredit             the same for rum rations
 * @param hungryTicks           how long the crew has been without food, continuously (0 when fed)
 * @param thirstyTicks          how long the crew has been without water, continuously (0 when watered)
 * @param ticksSinceAntiScurvy  time since the crew last ate anti-scurvy food (the scurvy clock)
 * @param drunkTicks            remaining time of the "too much rum" work-speed penalty
 */
public record ProvisioningState(double foodCredit, double waterCredit, double rumCredit, double hungryTicks,
                                double thirstyTicks, double ticksSinceAntiScurvy, double drunkTicks) {

    public static final ProvisioningState INITIAL = new ProvisioningState(0, 0, 0, 0, 0, 0, 0);

    public static final Codec<ProvisioningState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("food_credit", 0.0).forGetter(ProvisioningState::foodCredit),
            Codec.DOUBLE.optionalFieldOf("water_credit", 0.0).forGetter(ProvisioningState::waterCredit),
            Codec.DOUBLE.optionalFieldOf("rum_credit", 0.0).forGetter(ProvisioningState::rumCredit),
            Codec.DOUBLE.optionalFieldOf("hungry", 0.0).forGetter(ProvisioningState::hungryTicks),
            Codec.DOUBLE.optionalFieldOf("thirsty", 0.0).forGetter(ProvisioningState::thirstyTicks),
            Codec.DOUBLE.optionalFieldOf("since_anti_scurvy", 0.0).forGetter(ProvisioningState::ticksSinceAntiScurvy),
            Codec.DOUBLE.optionalFieldOf("drunk", 0.0).forGetter(ProvisioningState::drunkTicks)
    ).apply(i, ProvisioningState::new));
}
