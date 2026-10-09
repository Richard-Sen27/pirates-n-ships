package com.richardsenger.piratesnships.rpg.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * What exactly a quest is about, as a sum type with a dispatch codec ({@code {"kind": "cargo", ...}}):
 * nothing more than the type ({@link None}: hunts and turn-ins count deeds), an entity type ({@link Kill}), a cargo
 * run ({@link Cargo}, with the contract made on accepting), a buried treasure ({@link Treasure}) or one entity
 * ({@link Victim}, for the captain hunt) or a convoy to escort ({@link Escort}, QST2).
 */
public sealed interface QuestTarget permits QuestTarget.None, QuestTarget.Kill, QuestTarget.Cargo, QuestTarget.Treasure, QuestTarget.Victim,
        QuestTarget.Escort {

    enum Kind implements StringRepresentable {
        NONE, KILL, CARGO, TREASURE, VICTIM, ESCORT;

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        MapCodec<? extends QuestTarget> codec() {
            return switch (this) {
                case NONE -> None.MAP_CODEC;
                case KILL -> Kill.MAP_CODEC;
                case CARGO -> Cargo.MAP_CODEC;
                case TREASURE -> Treasure.MAP_CODEC;
                case VICTIM -> Victim.MAP_CODEC;
                case ESCORT -> Escort.MAP_CODEC;
            };
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    Codec<QuestTarget> CODEC = Kind.CODEC.dispatch("kind", QuestTarget::kind, Kind::codec);

    Kind kind();

    /** No particular target: the quest type says it all. */
    record None() implements QuestTarget {
        public static final None INSTANCE = new None();
        static final MapCodec<None> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override
        public Kind kind() {
            return Kind.NONE;
        }
    }

    /** Entities of {@code entity} type (a shark, the kraken). */
    record Kill(ResourceLocation entity) implements QuestTarget {
        static final MapCodec<Kill> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceLocation.CODEC.fieldOf("entity").forGetter(Kill::entity)
        ).apply(i, Kill::new));

        @Override
        public Kind kind() {
            return Kind.KILL;
        }
    }

    /** {@code quantity} of {@code good} to {@code destination}; {@code contract} is the delivery contract once accepted. */
    record Cargo(ResourceLocation good, int quantity, ResourceLocation destination, Optional<UUID> contract) implements QuestTarget {
        static final MapCodec<Cargo> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceLocation.CODEC.fieldOf("good").forGetter(Cargo::good),
                Codec.INT.fieldOf("quantity").forGetter(Cargo::quantity),
                ResourceLocation.CODEC.fieldOf("destination").forGetter(Cargo::destination),
                UUIDUtil.STRING_CODEC.optionalFieldOf("contract").forGetter(Cargo::contract)
        ).apply(i, Cargo::new));

        public Cargo withContract(UUID id) {
            return new Cargo(good, quantity, destination, Optional.of(id));
        }

        @Override
        public Kind kind() {
            return Kind.CARGO;
        }
    }

    /** The buried treasure at {@code site} of the pirate island {@code port}. */
    record Treasure(ResourceLocation port, BlockPos site) implements QuestTarget {
        static final MapCodec<Treasure> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceLocation.CODEC.fieldOf("port").forGetter(Treasure::port),
                BlockPos.CODEC.fieldOf("site").forGetter(Treasure::site)
        ).apply(i, Treasure::new));

        public Treasure {
            site = site.immutable();
        }

        @Override
        public Kind kind() {
            return Kind.TREASURE;
        }
    }

    /**
     * One entity or ship by id, with a name to show and, for a captain hunt (QST1b), the compass {@code bearing} from
     * the giving port to his island ({@code north}, {@code north_east}, ...; {@link QuestGenerator#bearing}).
     */
    record Victim(UUID id, String name, Optional<String> bearing) implements QuestTarget {
        static final MapCodec<Victim> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(Victim::id),
                Codec.STRING.fieldOf("name").forGetter(Victim::name),
                Codec.STRING.optionalFieldOf("bearing").forGetter(Victim::bearing)
        ).apply(i, Victim::new));

        public Victim(UUID id, String name) {
            this(id, name, Optional.empty());
        }

        @Override
        public Kind kind() {
            return Kind.VICTIM;
        }
    }

    /**
     * A convoy to {@code destination} to sail with (QST2). While offered, {@code voyage} is empty and {@code name} blank:
     * accepting makes the convoy leave the giving port. {@code legs} is the number of legs of its route and
     * {@code lastLeg} the last leg on which the player was seen close by (−1: none yet); the quest's progress counts
     * such legs and its {@code needed} is {@link QuestRules#escortLegsNeeded}. {@code chartingSince} is the game time
     * the quest was accepted while its lane was still being charted (QST2b; empty once the convoy sails).
     */
    record Escort(ResourceLocation destination, Optional<UUID> voyage, String name, int legs, int lastLeg,
                  Optional<Long> chartingSince) implements QuestTarget {
        static final MapCodec<Escort> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceLocation.CODEC.fieldOf("destination").forGetter(Escort::destination),
                UUIDUtil.STRING_CODEC.optionalFieldOf("voyage").forGetter(Escort::voyage),
                Codec.STRING.optionalFieldOf("name", "").forGetter(Escort::name),
                Codec.INT.optionalFieldOf("legs", 0).forGetter(Escort::legs),
                Codec.INT.optionalFieldOf("last_leg", -1).forGetter(Escort::lastLeg),
                Codec.LONG.optionalFieldOf("charting_since").forGetter(Escort::chartingSince)
        ).apply(i, Escort::new));

        /** An offer: no convoy yet. */
        public Escort(ResourceLocation destination) {
            this(destination, Optional.empty(), "", 0, -1, Optional.empty());
        }

        /** Accepted: the convoy {@code voyage} named {@code name} with {@code legs} legs (at least 1). */
        public Escort withConvoy(UUID voyage, String name, int legs) {
            return new Escort(destination, Optional.of(voyage), name, Math.max(1, legs), -1, Optional.empty());
        }

        /** Accepted at game time {@code tick} while the lane is still being charted (QST2b). */
        public Escort charting(long tick) {
            return new Escort(destination, Optional.empty(), "", 0, -1, Optional.of(tick));
        }

        public Escort withLastLeg(int leg) {
            return new Escort(destination, voyage, name, legs, leg, chartingSince);
        }

        /** Whether this escort follows the voyage {@code id}. */
        public boolean follows(UUID id) {
            return voyage.isPresent() && voyage.get().equals(id);
        }

        @Override
        public Kind kind() {
            return Kind.ESCORT;
        }
    }
}
