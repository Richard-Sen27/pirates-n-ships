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
 * ({@link Victim}, for the later captain and ship hunts).
 */
public sealed interface QuestTarget permits QuestTarget.None, QuestTarget.Kill, QuestTarget.Cargo, QuestTarget.Treasure, QuestTarget.Victim {

    enum Kind implements StringRepresentable {
        NONE, KILL, CARGO, TREASURE, VICTIM;

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        MapCodec<? extends QuestTarget> codec() {
            return switch (this) {
                case NONE -> None.MAP_CODEC;
                case KILL -> Kill.MAP_CODEC;
                case CARGO -> Cargo.MAP_CODEC;
                case TREASURE -> Treasure.MAP_CODEC;
                case VICTIM -> Victim.MAP_CODEC;
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

    /** One entity or ship by id, with a name to show (later packages: captains, ships). */
    record Victim(UUID id, String name) implements QuestTarget {
        static final MapCodec<Victim> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(Victim::id),
                Codec.STRING.fieldOf("name").forGetter(Victim::name)
        ).apply(i, Victim::new));

        @Override
        public Kind kind() {
            return Kind.VICTIM;
        }
    }
}
