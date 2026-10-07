package com.richardsenger.piratesnships.ship;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.ship.decor.flag.FlagReading;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

/**
 * The persistent record of one ship (docs/design.md §4.1), keyed on its Sable sub-level UUID. Immutable; change it with
 * the {@code with…} methods and put it back into {@link ShipRegistry}.
 *
 * @param id        the sub-level UUID
 * @param name      display name, empty if unnamed
 * @param owner     the player who assembled it
 * @param crew      crew member UUIDs (empty until the crew module exists)
 * @param flag      the flag the ship shows (FL2, docs/design.md §4.7): the winning flagpole's reading
 *                  ({@code ship.decor.flag.FlagSelection}), kept up to date by {@code ShipAllegiance}; a wreck shows
 *                  {@link FlagReading#NO_FLAG}. Saved as a string ({@link FlagReading#STRING_CODEC}).
 * @param dimension the level the ship lives in
 * @param blownCoverUntil game time (overworld) until which the navy has seen through the ship's colours (FL2): it
 *                  treats the ship as hostile whatever it flies; 0 = never caught
 */
public record ShipData(UUID id, String name, Optional<UUID> owner, List<UUID> crew, FlagReading flag, ResourceLocation dimension,
                       long blownCoverUntil) {

    public static final Codec<ShipData> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(ShipData::id),
            Codec.STRING.optionalFieldOf("name", "").forGetter(ShipData::name),
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(ShipData::owner),
            UUIDUtil.CODEC.listOf().optionalFieldOf("crew", List.of()).forGetter(ShipData::crew),
            FlagReading.STRING_CODEC.optionalFieldOf("flag", FlagReading.NO_FLAG).forGetter(ShipData::flag),
            ResourceLocation.CODEC.fieldOf("dimension").forGetter(ShipData::dimension),
            Codec.LONG.optionalFieldOf("blown_cover_until", 0L).forGetter(ShipData::blownCoverUntil)
    ).apply(i, ShipData::new));

    public ShipData {
        crew = List.copyOf(crew);
    }

    /** A record whose cover was never blown. */
    public ShipData(UUID id, String name, Optional<UUID> owner, List<UUID> crew, FlagReading flag, ResourceLocation dimension) {
        this(id, name, owner, crew, flag, dimension, 0L);
    }

    public static ShipData create(UUID id, Optional<UUID> owner, ResourceLocation dimension) {
        return new ShipData(id, "", owner, List.of(), FlagReading.NO_FLAG, dimension);
    }

    public ShipData withName(String newName) {
        return new ShipData(id, newName, owner, crew, flag, dimension, blownCoverUntil);
    }

    public ShipData withFlag(FlagReading newFlag) {
        return new ShipData(id, name, owner, crew, newFlag, dimension, blownCoverUntil);
    }

    public ShipData withBlownCoverUntil(long until) {
        return new ShipData(id, name, owner, crew, flag, dimension, until);
    }

    /** The navy has seen through the ship's colours and still remembers it at {@code now} (overworld game time). */
    public boolean coverBlown(long now) {
        return now < blownCoverUntil;
    }

    /** The same ship with another owner, or none (a mutiny takes the ship from its owner, CR2). */
    public ShipData withOwner(Optional<UUID> newOwner) {
        return new ShipData(id, name, newOwner, crew, flag, dimension);
    }
}
