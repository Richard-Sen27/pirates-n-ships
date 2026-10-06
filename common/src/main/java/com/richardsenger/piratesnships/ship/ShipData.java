package com.richardsenger.piratesnships.ship;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
 * @param flag      flag / faction id, empty for none (placeholder until the faction module exists)
 * @param dimension the level the ship lives in
 */
public record ShipData(UUID id, String name, Optional<UUID> owner, List<UUID> crew, String flag, ResourceLocation dimension) {

    public static final Codec<ShipData> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(ShipData::id),
            Codec.STRING.optionalFieldOf("name", "").forGetter(ShipData::name),
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(ShipData::owner),
            UUIDUtil.CODEC.listOf().optionalFieldOf("crew", List.of()).forGetter(ShipData::crew),
            Codec.STRING.optionalFieldOf("flag", "").forGetter(ShipData::flag),
            ResourceLocation.CODEC.fieldOf("dimension").forGetter(ShipData::dimension)
    ).apply(i, ShipData::new));

    public ShipData {
        crew = List.copyOf(crew);
    }

    public static ShipData create(UUID id, Optional<UUID> owner, ResourceLocation dimension) {
        return new ShipData(id, "", owner, List.of(), "", dimension);
    }

    public ShipData withName(String newName) {
        return new ShipData(id, newName, owner, crew, flag, dimension);
    }
}
