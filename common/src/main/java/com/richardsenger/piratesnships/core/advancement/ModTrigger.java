package com.richardsenger.piratesnships.core.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * One criterion trigger of the mod (ADV1, {@code docs/advancements.md}): a mod event family such as
 * {@code pirates_n_ships:ship_assembled} or {@code pirates_n_ships:port_visited}. Every family shares this shape, so
 * one class is registered once per family ({@link ModTriggers}): the event carries an optional {@code kind} (a port
 * kind, a rank, a template id) and a number ({@code value}: blocks of a ship, hands aboard, doubloons earned in all).
 *
 * <pre>{@code
 * {"trigger": "pirates_n_ships:ship_assembled", "conditions": {"value": {"min": 500}}}
 * {"trigger": "pirates_n_ships:port_visited", "conditions": {"kind": "navy_outpost"}}
 * }</pre>
 *
 * Fired only through {@link ModAdvancements}, which honours {@code advancements.enabled}.
 */
public final class ModTrigger extends SimpleCriterionTrigger<ModTrigger.Instance> {

    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    /** Tells every listening criterion of {@code player} about one event of this family. */
    void fire(ServerPlayer player, String kind, int value) {
        trigger(player, instance -> instance.matches(kind, value));
    }

    /** A criterion of this family: any event. */
    public Criterion<Instance> any() {
        return createCriterion(new Instance(Optional.empty(), Optional.empty(), MinMaxBounds.Ints.ANY));
    }

    /** A criterion of this family: an event of {@code kind}. */
    public Criterion<Instance> kind(String kind) {
        return createCriterion(new Instance(Optional.empty(), Optional.of(kind), MinMaxBounds.Ints.ANY));
    }

    /** A criterion of this family: an event whose value is at least {@code min}. */
    public Criterion<Instance> atLeast(int min) {
        return createCriterion(new Instance(Optional.empty(), Optional.empty(), MinMaxBounds.Ints.atLeast(min)));
    }

    /**
     * The conditions of one criterion.
     *
     * @param kind  when present, the event's kind must equal it
     * @param value bounds the event's number must fall in ({@code ANY}: every number)
     */
    public record Instance(Optional<ContextAwarePredicate> player, Optional<String> kind, MinMaxBounds.Ints value)
            implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                Codec.STRING.optionalFieldOf("kind").forGetter(Instance::kind),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("value", MinMaxBounds.Ints.ANY).forGetter(Instance::value)
        ).apply(i, Instance::new));

        /** Pure: does an event of {@code eventKind} worth {@code eventValue} satisfy this criterion? */
        public boolean matches(String eventKind, int eventValue) {
            if (kind.isPresent() && !kind.get().equals(eventKind)) return false;
            return value.matches(eventValue);
        }
    }
}
