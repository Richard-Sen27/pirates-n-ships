package com.richardsenger.piratesnships.combat.melee.net;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.melee.rules.AttackKind;
import com.richardsenger.piratesnships.combat.melee.rules.CombatState;
import com.richardsenger.piratesnships.combat.melee.rules.Phase;
import com.richardsenger.piratesnships.combat.melee.rules.Refusal;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

/**
 * Server → client: the melee state of one entity, sent by {@link MeleeStateSync} when it changes in a way other
 * clients need (phase, attack, guard, riposte, lockout) to the entity's trackers and itself, and with stamina to the
 * owning player only (throttled), plus a {@code refusal} to the owner when an action was refused.
 *
 * @param entityId      the entity
 * @param phase         current phase
 * @param attack        attack of an attack phase, else {@code null}
 * @param elapsed       ticks spent in the phase when sent
 * @param duration      phase length in ticks (0 = open-ended: idle, guarding); for {@link Phase#STAGGERED} the stagger
 * @param guardHeld     the guard input is held
 * @param riposteAttack the current attack is a riposte
 * @param riposteTicks  remaining riposte window (0 = none)
 * @param lockoutTicks  remaining parry lockout (0 = none)
 * @param stamina       current stamina; only meaningful when {@link #hasStamina()}
 * @param maxStamina    full stamina; {@code 0} = this copy carries no stamina (sent to other players)
 * @param refusal       {@link Refusal#NONE}, or why the owner's last action was refused (owner copy only)
 * @param serverTick    server tick count when sent, for interpolation and ordering
 */
public record MeleeStatePayload(int entityId, Phase phase, @Nullable AttackKind attack, int elapsed, int duration,
                                boolean guardHeld, boolean riposteAttack, int riposteTicks, int lockoutTicks,
                                float stamina, float maxStamina, Refusal refusal, int serverTick)
        implements CustomPacketPayload {

    public static final Type<MeleeStatePayload> TYPE = new Type<>(Constants.id("melee_state"));

    private static final Phase[] PHASES = Phase.values();
    private static final AttackKind[] ATTACKS = AttackKind.values();
    private static final Refusal[] REFUSALS = Refusal.values();

    public static final StreamCodec<ByteBuf, MeleeStatePayload> CODEC = StreamCodec.of(MeleeStatePayload::write, MeleeStatePayload::read);

    /** The observer copy: everything but stamina. */
    public static MeleeStatePayload observed(int entityId, CombatState s, int serverTick) {
        return new MeleeStatePayload(entityId, s.phase(), s.attack(), s.elapsed(), s.duration(), s.guardHeld(),
                s.riposteAttack(), s.riposteTicks(), s.lockoutTicks(), 0f, 0f, Refusal.NONE, serverTick);
    }

    /** The owner copy: with stamina and, after a refused action, the refusal. */
    public static MeleeStatePayload owned(int entityId, CombatState s, float maxStamina, Refusal refusal, int serverTick) {
        return new MeleeStatePayload(entityId, s.phase(), s.attack(), s.elapsed(), s.duration(), s.guardHeld(),
                s.riposteAttack(), s.riposteTicks(), s.lockoutTicks(), s.stamina(), maxStamina, refusal, serverTick);
    }

    public boolean hasStamina() {
        return maxStamina > 0f;
    }

    /** Remaining stagger when sent (0 unless staggered). */
    public int staggerTicks() {
        return phase == Phase.STAGGERED ? Math.max(0, duration - elapsed) : 0;
    }

    @Override
    public Type<MeleeStatePayload> type() {
        return TYPE;
    }

    private static void write(ByteBuf buf, MeleeStatePayload p) {
        ByteBufCodecs.VAR_INT.encode(buf, p.entityId);
        buf.writeByte(p.phase.ordinal());
        buf.writeByte(p.attack == null ? -1 : p.attack.ordinal());
        ByteBufCodecs.VAR_INT.encode(buf, p.elapsed);
        ByteBufCodecs.VAR_INT.encode(buf, p.duration);
        buf.writeByte((p.guardHeld ? 1 : 0) | (p.riposteAttack ? 2 : 0));
        ByteBufCodecs.VAR_INT.encode(buf, p.riposteTicks);
        ByteBufCodecs.VAR_INT.encode(buf, p.lockoutTicks);
        buf.writeFloat(p.stamina);
        buf.writeFloat(p.maxStamina);
        buf.writeByte(p.refusal.ordinal());
        ByteBufCodecs.VAR_INT.encode(buf, p.serverTick);
    }

    private static MeleeStatePayload read(ByteBuf buf) {
        int entityId = ByteBufCodecs.VAR_INT.decode(buf);
        Phase phase = byOrdinal(PHASES, buf.readByte(), Phase.IDLE);
        AttackKind attack = byOrdinal(ATTACKS, buf.readByte(), null);
        int elapsed = ByteBufCodecs.VAR_INT.decode(buf);
        int duration = ByteBufCodecs.VAR_INT.decode(buf);
        int flags = buf.readByte();
        int riposte = ByteBufCodecs.VAR_INT.decode(buf);
        int lockout = ByteBufCodecs.VAR_INT.decode(buf);
        float stamina = buf.readFloat();
        float max = buf.readFloat();
        Refusal refusal = byOrdinal(REFUSALS, buf.readByte(), Refusal.NONE);
        int tick = ByteBufCodecs.VAR_INT.decode(buf);
        return new MeleeStatePayload(entityId, phase, attack, elapsed, duration, (flags & 1) != 0, (flags & 2) != 0,
                riposte, lockout, stamina, max, refusal, tick);
    }

    private static <E> E byOrdinal(E[] values, int ordinal, E fallback) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : fallback;
    }
}
