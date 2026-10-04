package net.bullettrain.xenopixelsmod.network;

import net.bullettrain.xenopixelsmod.RepoRoot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The network protocol version, read out of {@code ModNetwork}.
 *
 * <p>Every change to the wire - a new packet, or a field added to an existing one - has to move
 * this, or an old client and a new server disagree silently about what the bytes mean. Each such
 * change lands with a test asserting the version is <em>at least</em> what that change required.
 *
 * <p>Deliberately a floor rather than an exact match. A test that pins the number breaks on the
 * next unrelated bump, and the choice then is between editing a check that was doing its job and
 * leaving a red suite - which is how these checks get deleted. A floor still catches the failure
 * worth catching: shipping a wire change without touching the version at all.
 */
public final class ProtocolVersion {

    private static final Pattern DECLARATION =
            Pattern.compile("PROTOCOL\\s*=\\s*\"(\\d+)\"");

    private ProtocolVersion() {
    }

    /** The version {@code ModNetwork} currently declares. */
    public static int current() {
        Path network = RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/network",
                "ModNetwork.java");
        String source;
        try {
            source = Files.readString(network, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + network, e);
        }
        Matcher matcher = DECLARATION.matcher(source);
        if (!matcher.find()) {
            throw new IllegalStateException(
                    "no PROTOCOL = \"<number>\" declaration in " + network);
        }
        return Integer.parseInt(matcher.group(1));
    }
}
