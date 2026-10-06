package com.richardsenger.piratesnships.law.bounty;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;
import java.util.UUID;

/** Who a bounty is on: a player or an NPC, by UUID, with the display name shown on notice boards. */
public record BountyTarget(UUID id, String name, Kind kind) {

    public enum Kind implements StringRepresentable {
        PLAYER, NPC;

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final Codec<BountyTarget> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(BountyTarget::id),
            Codec.STRING.fieldOf("name").forGetter(BountyTarget::name),
            Kind.CODEC.fieldOf("kind").forGetter(BountyTarget::kind)
    ).apply(i, BountyTarget::new));

    public static BountyTarget player(UUID id, String name) {
        return new BountyTarget(id, name, Kind.PLAYER);
    }

    public static BountyTarget npc(UUID id, String name) {
        return new BountyTarget(id, name, Kind.NPC);
    }
}
