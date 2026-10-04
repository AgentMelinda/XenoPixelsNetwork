package net.bullettrain.xenopixelsmod;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The repository root, for tests that check a file in the tree rather than a value in memory.
 *
 * <p>The test task does not run with the repository as its working directory — ModDevGradle points
 * it at its own run directory — so a plain {@code Path.of("src/main/resources/...")} resolves to
 * nothing. A test that then quietly skips itself when the file "is missing" reports success while
 * checking nothing at all, which is worse than failing: it is the shape a check has when it has
 * stopped working and nobody has noticed.
 *
 * <p>Walking up to the directory holding {@code settings.gradle} gives the same answer whether the
 * suite is run by Gradle, by an IDE, or by hand.
 */
public final class RepoRoot {

    private static final Path ROOT = locate();

    private RepoRoot() {
    }

    /** A path inside the repository, from repository-relative segments. */
    public static Path of(String first, String... more) {
        return ROOT.resolve(Path.of(first, more));
    }

    /** The repository root itself. */
    public static Path get() {
        return ROOT;
    }

    private static Path locate() {
        Path here = Path.of("").toAbsolutePath();
        for (Path candidate = here; candidate != null; candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle"))
                    || Files.isRegularFile(candidate.resolve("settings.gradle.kts"))) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "no settings.gradle above " + here + "; cannot locate the repository root");
    }
}
