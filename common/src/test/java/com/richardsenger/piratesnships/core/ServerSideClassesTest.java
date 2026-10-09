package com.richardsenger.piratesnships.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.richardsenger.piratesnships.Constants;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The dedicated-server guard (REL3): no class of {@code common} that a dedicated server may load names a client-only
 * Minecraft class. A dedicated server has no {@code net.minecraft.client} or {@code com.mojang.blaze3d} package, so such
 * a reference throws {@code NoClassDefFoundError} the moment the JVM resolves it (a field or method type during
 * verification, a call when it runs) — "the usual release-day crash", which the GameTest server never sees because it
 * runs on the client's classpath. {@code tools/server_smoke.sh} finds it by running a real dedicated server; this test
 * finds it at build time.
 *
 * <p>Client-side by convention, and therefore skipped: every class in a {@code client} package, the client mixins of
 * {@code pirates_n_ships.mixins.json}, JEI's plugin ({@code compat/jei}, only JEI loads it, and only on the client),
 * classes named {@code Client…} (physical client helpers outside a {@code client} package, e.g. {@code ClientAboard}),
 * the Ponder scenes ({@code almanac/ponder}, registered with Ponder from {@code initClient} only; its module class,
 * which every side loads, is scanned) and {@link #CLIENT_ONLY} (documented as client only). Everything else is scanned: a class its constant pool can
 * resolve (a {@code CONSTANT_Class}: the owner of a field or method it uses, a type it creates, casts to or tests) in a
 * client package fails the test. Mere mentions in descriptors are allowed: the JVM does not load a parameter type to
 * link a method, so {@code ClientEvents.CLIENT_DISCONNECT.register(mc -> X.reset())} in a module's {@code initClient}
 * (a synthetic {@code lambda$initClient$0(Minecraft)}) is safe, as the dedicated-server smoke test confirms. Nested and
 * synthetic classes belong to their outer class.
 */
class ServerSideClassesTest {

    private static final List<String> CLIENT_PACKAGES = List.of("net/minecraft/client/", "com/mojang/blaze3d/");
    private static final String ROOT = "com/richardsenger/piratesnships/";

    /** Client-only classes outside a client package, each documented as such in its own Javadoc. */
    private static final Set<String> CLIENT_ONLY = Set.of(
            ROOT + "audio/music/SeaMusic" // the sea music manager, set up from AudioModule.initClient
    );

    /** Ponder's scenes run on the client only; the module class itself is loaded on both sides by ModModules. */
    private static final String PONDER_SCENES = ROOT + "almanac/ponder/";
    private static final String PONDER_MODULE = PONDER_SCENES + "AlmanacPonderModule";

    @Test
    void noServerSideClassNamesAClientClass() throws IOException {
        Path classes = classesRoot();
        Set<String> clientMixins = clientMixins();
        List<String> failures = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> files = Files.walk(classes)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator) {
                String name = classes.relativize(file).toString().replace('\\', '/');
                name = name.substring(0, name.length() - ".class".length());
                String outer = name.contains("$") ? name.substring(0, name.indexOf('$')) : name;
                if (clientSide(outer, clientMixins)) {
                    continue;
                }
                scanned++;
                Set<String> hits = new HashSet<>();
                for (String referenced : classReferences(Files.readAllBytes(file))) {
                    String element = referenced.replaceFirst("^\\[+L?", "");
                    if (CLIENT_PACKAGES.stream().anyMatch(element::startsWith)) {
                        hits.add(element.replace(";", ""));
                    }
                }
                if (!hits.isEmpty()) {
                    failures.add(name + " -> " + hits.stream().sorted().toList());
                }
            }
        }
        System.out.println("[ServerSideClassesTest] scanned " + scanned + " server-side class(es) in " + classes);
        assertTrue(scanned > 100, "too few classes scanned (" + scanned + "): wrong classes directory " + classes);
        assertTrue(failures.isEmpty(), "Server-side classes that name client-only classes (a NoClassDefFoundError on a "
                + "dedicated server; move the client part into a client package):\n  " + String.join("\n  ", failures));
    }

    /** The rule itself: client packages, client mixins, JEI, Ponder scenes and the Client… prefix are skipped. */
    @Test
    void clientSideRuleSkipsOnlyClientCode() {
        Set<String> mixins = Set.of(ROOT + "mixin/MixinCamera");
        assertTrue(clientSide(ROOT + "ship/hull/client/ShipHud", mixins));
        assertTrue(clientSide(ROOT + "mixin/MixinCamera", mixins));
        assertTrue(clientSide(ROOT + "compat/jei/PiratesJeiPlugin", mixins));
        assertTrue(clientSide(ROOT + "ship/sable/ClientAboard", mixins));
        assertTrue(clientSide(ROOT + "audio/music/SeaMusic", mixins));
        assertTrue(clientSide(ROOT + "almanac/ponder/CrewOrdersScene", mixins));
        assertTrue(!clientSide(ROOT + "almanac/ponder/AlmanacPonderModule", mixins), "loaded on the server too");
        assertTrue(!clientSide(ROOT + "mixin/MixinBoat", mixins));
        assertTrue(!clientSide(ROOT + "ship/ShipModule", mixins));
        assertTrue(!clientSide(ROOT + "clientele/Customer", mixins), "a package merely starting with client is not one");
    }

    private static boolean clientSide(String name, Set<String> clientMixins) {
        String simple = name.substring(name.lastIndexOf('/') + 1);
        return name.contains("/client/") || clientMixins.contains(name) || name.startsWith(ROOT + "compat/jei/")
                || simple.startsWith("Client") || CLIENT_ONLY.contains(name)
                || (name.startsWith(PONDER_SCENES) && !name.equals(PONDER_MODULE));
    }

    /** The classes a class file's constant pool can resolve: its CONSTANT_Class entries (JVMS §4.4). */
    static List<String> classReferences(byte[] bytes) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        if (in.readInt() != 0xCAFEBABE) {
            throw new IOException("not a class file");
        }
        in.readUnsignedShort(); // minor
        in.readUnsignedShort(); // major
        int count = in.readUnsignedShort();
        String[] utf8 = new String[count];
        List<Integer> classNames = new ArrayList<>();
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> utf8[i] = in.readUTF();
                case 7 -> classNames.add(in.readUnsignedShort());
                case 8, 16, 19, 20 -> in.readUnsignedShort();
                case 15 -> in.skipBytes(3);
                case 3, 4, 9, 10, 11, 12, 17, 18 -> in.readInt();
                case 5, 6 -> {
                    in.readLong();
                    i++; // a long or double takes two slots
                }
                default -> throw new IOException("unknown constant pool tag " + tag + " at " + i);
            }
        }
        List<String> names = new ArrayList<>();
        for (int index : classNames) {
            names.add(utf8[index]);
        }
        return names;
    }

    /** The parser reads the pool the JVM resolves, not mere mentions: a lambda's client parameter type is no reference. */
    @Test
    void classReferencesReadsClassEntriesOnly() throws IOException {
        byte[] bytes;
        try (InputStream in = ServerSideClassesTest.class.getResourceAsStream("ServerSideClassesTest.class")) {
            bytes = in.readAllBytes();
        }
        List<String> refs = classReferences(bytes);
        assertTrue(refs.contains("com/richardsenger/piratesnships/Constants"), refs.toString());
        assertTrue(refs.contains("java/io/DataInputStream"), refs.toString());
        assertTrue(refs.stream().noneMatch(r -> r.contains("Mixin")), "the string literal pirates_n_ships.mixins.json is no class");
    }

    private static Set<String> clientMixins() throws IOException {
        try (InputStream in = ServerSideClassesTest.class.getClassLoader().getResourceAsStream("pirates_n_ships.mixins.json")) {
            assertTrue(in != null, "pirates_n_ships.mixins.json is not on the test classpath");
            JsonObject config = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            String pkg = config.get("package").getAsString().replace('.', '/');
            Set<String> names = new HashSet<>();
            if (config.has("client")) {
                for (JsonElement e : config.getAsJsonArray("client")) {
                    names.add(pkg + "/" + e.getAsString().replace('.', '/'));
                }
            }
            return names;
        }
    }

    /** The directory holding common's compiled main classes (build/classes/java/main under Gradle). */
    private static Path classesRoot() {
        try {
            Path location = Path.of(Constants.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            assertTrue(Files.isDirectory(location), "common's classes are not a directory: " + location);
            return location;
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }
}
