package net.bullettrain.xenopixelsmod.mixinconfig;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * An accessor or invoker mixin that ordinary code casts to must be listed in a mixin config.
 * Mixin refuses to load an unlisted one the first time it is touched, which crashes the game at
 * that moment rather than at startup: a V3 left tap did exactly that through
 * {@code MinecraftAttackInvoker} on 2026-10-07.
 */
class ReferencedMixinsRegisteredTest {
    private static final Path ROOT = repositoryRoot();
    private static final Path SOURCES = ROOT.resolve("src/main/java");
    private static final Path MIXINS = SOURCES.resolve("net/bullettrain/xenopixelsmod/mixin");

    /** The test JVM does not start in the repository root, so find it from wherever it did start. */
    private static Path repositoryRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("src/main/java/net/bullettrain"))) dir = dir.getParent();
        if (dir == null) throw new IllegalStateException("Repository root not found from " + Path.of("").toAbsolutePath());
        return dir;
    }
    private static final Pattern IMPORT =
            Pattern.compile("import\\s+net\\.bullettrain\\.xenopixelsmod\\.mixin\\.([\\w.]+);");

    @Test void everyMixinImportedByOrdinaryCodeIsInAMixinConfig() throws IOException {
        String configs = Files.readString(ROOT.resolve("src/main/resources/xenopixelsmod.mixins.json"))
                + Files.readString(ROOT.resolve("src/main/resources/xenopixelsmod.compat.mixins.json"));
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                if (file.startsWith(MIXINS)) continue;
                Matcher matcher = IMPORT.matcher(Files.readString(file));
                while (matcher.find()) {
                    String name = matcher.group(1);
                    Path source = MIXINS.resolve(name.replace('.', '/') + ".java");
                    if (!Files.exists(source) || !Files.readString(source).contains("@Mixin(")) continue;
                    if (!configs.contains("\"" + name + "\"")) missing.add(name + " (used by " + file.getFileName() + ")");
                }
            }
        }
        assertTrue(missing.isEmpty(), "Mixins used by code but absent from every mixin config: " + missing);
    }
}
