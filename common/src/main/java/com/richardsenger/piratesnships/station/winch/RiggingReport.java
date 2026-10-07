package com.richardsenger.piratesnships.station.winch;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.block.CleatBlock;
import com.richardsenger.piratesnships.sailing.block.SailWinchBlock;
import com.richardsenger.piratesnships.sailing.block.YardBlock;
import com.richardsenger.piratesnships.sailing.block.YardBlockEntity;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.sail.SquareSail;
import com.richardsenger.piratesnships.sailing.sail.StayRules;
import com.richardsenger.piratesnships.sailing.sail.TriangularSail;
import com.richardsenger.piratesnships.sailing.sail.TriangularSails;
import com.richardsenger.piratesnships.sailing.sail.YardLinker;
import com.richardsenger.piratesnships.sailing.sail.YardLookup;
import com.richardsenger.piratesnships.sailing.sail.YardRow;
import com.richardsenger.piratesnships.sailing.sail.YardRules;
import com.richardsenger.piratesnships.sailing.sail.YardSails;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntime;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The rigging of one ship as the rules see it right now (Q5): every yard with what it heads or why it heads nothing
 * ({@link YardDiagnosis}), yard rows too long to count, the triangular sails, and what the ship's sailing state
 * ({@link SailingRuntime}) holds. Used by {@code /pirates ship rigging} and by the crew's and the order commands'
 * "no sails" answers, which name the first reason. Positions are shown in world coordinates (as F3 shows them).
 */
public final class RiggingReport {

    static final String KEY = "message." + Constants.MOD_ID + ".rigging.";
    public static final String KEY_HEADER = KEY + "header";
    public static final String KEY_STATE = KEY + "state";
    public static final String KEY_STATE_NONE = KEY + "state_none";
    public static final String KEY_STATE_STALE = KEY + "state_stale";
    public static final String KEY_YARD = KEY + "yard";
    public static final String KEY_HEADS = KEY + "heads";
    public static final String KEY_NO_CLOTH = KEY + "no_cloth";
    public static final String KEY_FOOT = KEY + "foot";
    public static final String KEY_STAY_SAIL = KEY + "stay_sail";
    public static final String KEY_CREW = KEY + "crew";
    public static final String KEY_HINT = KEY + "hint";
    static final String WHY = KEY + "why.";
    public static final String KEY_WHY_NONE = WHY + "none";
    public static final String KEY_WHY_CLEATS = WHY + "cleats";
    public static final String KEY_WHY_TOO_LONG = WHY + "too_long";

    /** One yard: its row, the sail it heads (or null), whether it is the foot of a sail, and the rule's finding. */
    public record Yard(YardRow row, @Nullable SquareSail sail, boolean foot, YardDiagnosis.Finding finding,
                       @Nullable Component blocker, boolean cloth) { }

    /** A run of yard blocks longer than the longest yard: its first block and length. */
    public record LongRun(BlockPos at, int length) { }

    /** A triangular sail by its head cleat. */
    public record Stay(BlockPos head, TriangularSail sail, SailTrim trim) { }

    private final ShipBody ship;
    private final YardRules rules;
    private final List<Yard> yards;
    private final List<LongRun> longRuns;
    private final List<Stay> stays;
    private final int cleats;

    private RiggingReport(ShipBody ship, YardRules rules, List<Yard> yards, List<LongRun> longRuns, List<Stay> stays, int cleats) {
        this.ship = ship;
        this.rules = rules;
        this.yards = yards;
        this.longRuns = longRuns;
        this.stays = stays;
        this.cleats = cleats;
    }

    /** Reads the rigging of {@code ship} from its plot (no sailing state involved). */
    public static RiggingReport of(ServerLevel level, ShipBody ship) {
        YardRules rules = SailingConfig.yardRules();
        StayRules stayRules = SailingConfig.stayRules();
        YardLookup lookup = YardSails.lookup(level);
        Set<YardRow> rows = new LinkedHashSet<>();
        List<LongRun> longRuns = new ArrayList<>();
        Set<BlockPos> inLongRuns = new java.util.HashSet<>();
        List<Stay> stays = new ArrayList<>();
        int cleats = 0;
        for (BlockPos p : ship.plotBlocks()) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof YardBlock) {
                if (rows.stream().anyMatch(r -> r.contains(p.getX(), p.getY(), p.getZ())) || inLongRuns.contains(p)) {
                    continue;
                }
                YardRow r = YardLinker.row(lookup, p.getX(), p.getY(), p.getZ(), rules);
                if (r != null) {
                    rows.add(r);
                } else {
                    int n = YardDiagnosis.runLength(lookup, p.getX(), p.getY(), p.getZ(), 64);
                    longRuns.add(new LongRun(p, n));
                    boolean alongX = s.getValue(YardBlock.AXIS) == net.minecraft.core.Direction.Axis.X;
                    for (int i = -64; i <= 64; i++) {
                        inLongRuns.add(alongX ? p.offset(i, 0, 0) : p.offset(0, 0, i));
                    }
                }
            } else if (s.getBlock() instanceof CleatBlock) {
                cleats++;
                TriangularSail t = TriangularSails.sailHeadedAt(level, p, stayRules);
                if (t != null) stays.add(new Stay(p, t, s.getValue(CleatBlock.TRIM)));
            }
        }
        List<SquareSail> sails = new ArrayList<>();
        for (YardRow r : rows) {
            SquareSail s = YardLinker.sailHeadedBy(lookup, r, rules);
            if (s != null) sails.add(s);
        }
        List<Yard> yards = new ArrayList<>();
        for (YardRow r : rows) {
            SquareSail headed = sails.stream().filter(s -> s.upper().equals(r)).findFirst().orElse(null);
            boolean foot = sails.stream().anyMatch(s -> s.lower().equals(r));
            YardDiagnosis.Finding f = YardDiagnosis.explain(lookup, r, rules);
            Component blocker = f.verdict() == YardDiagnosis.Verdict.BLOCKED
                    ? level.getBlockState(new BlockPos(f.x(), f.y(), f.z())).getBlock().getName() : null;
            BlockPos middle = new BlockPos(r.middleX(), r.y(), r.middleZ());
            boolean cloth = level.getBlockEntity(middle) instanceof YardBlockEntity be && be.geometry() != null;
            yards.add(new Yard(r, headed, foot, f, blocker, cloth));
        }
        yards.sort(Comparator.comparingInt((Yard y) -> -y.row().y()));
        return new RiggingReport(ship, rules, List.copyOf(yards), List.copyOf(longRuns), List.copyOf(stays), cleats);
    }

    public List<Yard> yards() {
        return yards;
    }

    public List<Stay> stays() {
        return stays;
    }

    public List<LongRun> longRuns() {
        return longRuns;
    }

    /** Square and triangular sails the rules find now. */
    public int sails() {
        return (int) yards.stream().filter(y -> y.sail() != null).count() + stays.size();
    }

    /** World block position of a plot position of this ship. */
    BlockPos world(BlockPos plot) {
        return BlockPos.containing(ship.toWorld(Vec3.atCenterOf(plot)));
    }

    private Component at(BlockPos plot) {
        BlockPos w = world(plot);
        return Component.literal(w.getX() + " " + w.getY() + " " + w.getZ());
    }

    private Component at(YardRow r) {
        return at(new BlockPos(r.middleX(), r.y(), r.middleZ()));
    }

    /**
     * Why the ship has no sail, as the first thing to fix: the highest yard that neither heads a sail nor is the foot of
     * one, else a yard row that is too long, else cleats without a sail, else "no yards or stays". Null when the ship
     * has a sail.
     */
    public @Nullable Component problem() {
        if (sails() > 0) {
            return null;
        }
        for (Yard y : yards) {
            if (y.sail() == null && !y.foot()) {
                return why(y);
            }
        }
        if (!longRuns.isEmpty()) {
            LongRun l = longRuns.get(0);
            return Component.translatable(KEY_WHY_TOO_LONG, at(l.at()), l.length(), rules.maxLength());
        }
        if (cleats > 0) {
            return Component.translatable(KEY_WHY_CLEATS, cleats);
        }
        return Component.translatable(KEY_WHY_NONE);
    }

    /** Why the yard {@code y} heads no sail (or that it heads one). */
    Component why(Yard y) {
        YardDiagnosis.Finding f = y.finding();
        Component here = at(y.row());
        BlockPos cell = new BlockPos(f.x(), f.y(), f.z());
        return switch (f.verdict()) {
            case HEADS_SAIL -> Component.translatable(KEY_HEADS, f.distance(), fmt(y.sail() == null ? 0 : y.sail().area()), "", "");
            case NOTHING_BELOW -> Component.translatable(WHY + "nothing_below", here, rules.minGap(), rules.maxGap());
            case BLOCKED -> Component.translatable(WHY + "blocked", here,
                    y.blocker() == null ? Component.literal("a block") : y.blocker(), f.distance());
            case TOO_CLOSE -> Component.translatable(WHY + "too_close", here, f.distance(), rules.minGap(), rules.maxGap());
            case OTHER_AXIS -> Component.translatable(WHY + "other_axis", here, at(cell));
            case OFF_MIDDLE -> Component.translatable(WHY + "off_middle", here, at(cell));
            case LOWER_TOO_LONG -> Component.translatable(WHY + "lower_too_long", here, rules.maxLength());
        };
    }

    /**
     * The command's lines: the sailing state before and after re-reading ({@code runtimeBefore}: the sail count the
     * ship's sailing state held, -1 for none), every yard, every long run, every triangular sail, the crew.
     */
    public List<Component> lines(ServerLevel level, Component shipName, int runtimeBefore, @Nullable SailingRuntime runtimeAfter) {
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable(KEY_HEADER, shipName, sails(), yards.size(), stays.size()));
        if (runtimeAfter == null) {
            out.add(Component.translatable(KEY_STATE_NONE));
        } else {
            out.add(Component.translatable(KEY_STATE, runtimeAfter.sailCount(), runtimeAfter.unfurledCount()));
            if (runtimeBefore >= 0 && runtimeBefore != runtimeAfter.sailCount()) {
                out.add(Component.translatable(KEY_STATE_STALE, runtimeBefore, runtimeAfter.sailCount()));
            }
        }
        for (Yard y : yards) {
            Component status;
            if (y.sail() != null) {
                BlockPos head = new BlockPos(y.row().middleX(), y.row().y(), y.row().middleZ());
                SailTrim trim = level.getBlockState(head).getBlock() instanceof YardBlock ? level.getBlockState(head).getValue(YardBlock.TRIM) : SailTrim.FURLED;
                status = Component.translatable(KEY_HEADS, y.sail().drop(), fmt(y.sail().area()), Component.translatable(SailWinchBlock.trimKey(trim)),
                        y.cloth() ? Component.empty() : Component.translatable(KEY_NO_CLOTH));
            } else if (y.foot()) {
                status = Component.translatable(KEY_FOOT);
            } else {
                status = why(y);
            }
            out.add(Component.translatable(KEY_YARD, at(y.row()), y.row().length(), y.row().alongX() ? "x" : "z", status));
        }
        for (LongRun l : longRuns) {
            out.add(Component.translatable(KEY_WHY_TOO_LONG, at(l.at()), l.length(), rules.maxLength()));
        }
        for (Stay s : stays) {
            out.add(Component.translatable(KEY_STAY_SAIL, at(s.head()), fmt(s.sail().area()), Component.translatable(SailWinchBlock.trimKey(s.trim()))));
        }
        if (sails() == 0 && stays.isEmpty() && cleats > 0) {
            out.add(Component.translatable(KEY_WHY_CLEATS, cleats));
        }
        if (yards.isEmpty() && longRuns.isEmpty() && cleats == 0) {
            out.add(Component.translatable(KEY_WHY_NONE));
        }
        int[] crew = crew(level, ship);
        out.add(Component.translatable(KEY_CREW, crew[0], crew[1]));
        return out;
    }

    /** {assigned to a station of the ship, free on its deck}: the crew the ship's orders reach. */
    public static int[] crew(ServerLevel level, ShipBody ship) {
        int assigned = CrewStations.crewOf(level, ship.id()).size();
        int free = 0;
        for (CrewMember c : level.getEntitiesOfClass(CrewMember.class, CrewStations.worldBox(ship, 4), c -> c.isAlive() && c.assignment() == null)) {
            ShipBody on = CaptainsWhistleItem.shipOf(level, c);
            if (on != null && on.id().equals(ship.id())) free++;
        }
        return new int[] {assigned, free};
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /** The English lines (datagen). */
    public static void lang(LangBuilder lang) {
        lang.add(KEY_HEADER, "Rigging of %s: %s sails (%s yards, %s triangular sails)")
                .add(KEY_STATE, "Sailing state: %s sails, %s set")
                .add(KEY_STATE_NONE, "Sailing state: none (this ship was not assembled at a helm)")
                .add(KEY_STATE_STALE, "The sailing state had %s sails and missed some; it is up to date now with %s")
                .add(KEY_YARD, "Yard at %s (%s blocks along %s): %s")
                .add(KEY_HEADS, "heads a sail %s blocks deep, area %s, %s%s")
                .add(KEY_NO_CLOTH, " (cloth not drawn yet)")
                .add(KEY_FOOT, "foot of the sail above")
                .add(KEY_STAY_SAIL, "Triangular sail headed at %s: area %s, %s")
                .add(KEY_CREW, "Crew aboard: %s at stations, %s free on deck")
                .add(KEY_HINT, "/pirates ship rigging shows every yard and why it carries no sail")
                .add(KEY_WHY_NONE, "there are no yards or rope stays on this ship")
                .add(KEY_WHY_CLEATS, "%s cleats but no triangular sail: rig a rope from a high cleat down to a lower one, and put a third cleat straight below the high one")
                .add(KEY_WHY_TOO_LONG, "the yard at %s is %s blocks long, longer than %s")
                .add(WHY + "nothing_below", "the yard at %s has no second yard %s to %s blocks straight below its middle block")
                .add(WHY + "blocked", "the yard at %s has %s in its mast column %s blocks down; only air, logs and wooden fences may be between two yards")
                .add(WHY + "too_close", "the yard at %s has a yard only %s blocks below it; yards must be %s to %s blocks apart")
                .add(WHY + "other_axis", "the yard at %s and the yard below it at %s run along different axes")
                .add(WHY + "off_middle", "the yard below the one at %s is not centered on the same mast column (at %s); center both yards on the mast, with odd lengths")
                .add(WHY + "lower_too_long", "the yard below the one at %s is longer than %s blocks");
    }
}
