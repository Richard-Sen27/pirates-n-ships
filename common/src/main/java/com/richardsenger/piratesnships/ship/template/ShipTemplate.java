package com.richardsenger.piratesnships.ship.template;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A prebuilt ship: a structure template (converted from a schematic by {@code tools/schem_to_structure.py}) and what
 * the game needs to know to put it on the water. Datapack file:
 * {@code data/<ns>/pirates_n_ships/ship_template/<name>.json}.
 *
 * <pre>{@code
 * {
 *   "structure": "pirates_n_ships:ships/starter_sloop",   // data/<ns>/structure/<path>.nbt
 *   "name": "ship_template.pirates_n_ships.starter_sloop", // translation key
 *   "helm": [4, 8, 22],    // optional: template position of the helm; missing = the first helm block in the template
 *   "waterline": 2,        // optional: the template row that sits at the water surface (top water block);
 *                          //           missing = helm y - 1, or 0 for a template without a helm
 *   "bow": "north",        // optional: where the bow points inside the template (default north)
 *   "price": 400           // optional: doubloons at a shipwright (later), default 0
 * }
 * }</pre>
 */
public record ShipTemplate(ResourceLocation structure, String name, Optional<BlockPos> helm, Optional<Integer> waterline,
                           Direction bow, int price) {

    private static final Codec<Direction> HORIZONTAL = Direction.CODEC.comapFlatMap(
            d -> d.getAxis().isHorizontal() ? DataResult.success(d) : DataResult.error(() -> "bow must be horizontal: " + d),
            d -> d);

    public static final Codec<ShipTemplate> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("structure").forGetter(ShipTemplate::structure),
            Codec.STRING.fieldOf("name").forGetter(ShipTemplate::name),
            BlockPos.CODEC.optionalFieldOf("helm").forGetter(ShipTemplate::helm),
            Codec.intRange(0, 4096).optionalFieldOf("waterline").forGetter(ShipTemplate::waterline),
            HORIZONTAL.optionalFieldOf("bow", Direction.NORTH).forGetter(ShipTemplate::bow),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("price", 0).forGetter(ShipTemplate::price)
    ).apply(i, ShipTemplate::new));

    /** The waterline row: the explicit one, else one row below the helm, else the bottom row. */
    public int waterlineFor(@Nullable BlockPos resolvedHelm) {
        return waterline.orElseGet(() -> resolvedHelm == null ? 0 : Math.max(0, resolvedHelm.getY() - 1));
    }
}
