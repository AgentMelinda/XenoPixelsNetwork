package net.bullettrain.xenopixelsmod.client.npc;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The appearance screen, reordered to the reference.
 *
 * <p>Every field was already there. They sat in one long list under headings of our own invention -
 * Mode, Identity, Parts, Colours, Hair, Options - rather than in the reference's three sub-tabs, and
 * the body-type cycler showed a bare index instead of {@code n / total}. Same controls, wrong shape.
 *
 * <p>Reference, read off screenshot 47 in {@code XenoMyNpcsGuiOrderImages}:
 *
 * <pre>
 * [ Body ] [ Face ] [ Hair / Aura ]
 * Appearance Mode / Gender / Tail / Class / Body type / Bust scale
 * Body color 1-3 / Tail color / Tail: Race
 * </pre>
 *
 * <p>The screen needs a client to build, so the order is read out of its source.
 */
class XenoNpcAppearanceOrderTest {

    private static final Path SCREEN = RepoRoot.of(
            "src/main/java/net/bullettrain/xenopixelsmod/client/npc/XenoNpcAppearanceScreen.java");

    /** Labels passed to a row helper, in source order, inside one method. */
    private static List<String> labelsIn(String method) throws IOException {
        String source = Files.readString(SCREEN, StandardCharsets.UTF_8);
        int start = source.indexOf("private List<EditorRow> " + method + "()");
        assertTrue(start >= 0, method + " should exist");
        int end = source.indexOf("\n    private ", start + 1);
        String body = source.substring(start, end < 0 ? source.length() : end);

        List<String> labels = new ArrayList<>();
        // colorField is the swatch+picker row from the 2026-09-26 colour pass; it still carries
        // the same label contract as a plain field.
        Matcher m = Pattern.compile(
                "(?:cycle|field|colorField|toggle|floatField|stepper|partRow)\\(\"([^\"]+)\"")
                .matcher(body);
        while (m.find()) {
            labels.add(m.group(1));
        }
        return labels;
    }

    @Test
    void theBodyTabFollowsTheReferenceOrder() throws IOException {
        assertEquals(List.of(
                "Appearance Mode",
                "Gender",
                "Tail",
                "Class",
                "Body type",
                "Bust scale",
                "Body color 1",
                "Body color 2",
                "Body color 3",
                "Tail color",
                "Tail: Race"), labelsIn("bodyRows"));
    }

    @Test
    void theFaceTabHoldsTheHeadPartsAndTheirColours() throws IOException {
        List<String> face = labelsIn("faceRows");
        assertEquals(List.of("Eyes", "Eyebrows", "Nose", "Mouth", "Tattoo",
                "Eye color 1", "Eye color 2"), face);
    }

    @Test
    void theHairTabHoldsTheHairRows() throws IOException {
        List<String> hair = labelsIn("hairRows");
        assertTrue(hair.contains("Hair on"), hair.toString());
        assertTrue(hair.contains("Hair style"), "the cycler that went missing once before");
        assertTrue(hair.contains("Hair color"), hair.toString());
        assertTrue(hair.contains("Hair code"), hair.toString());
    }

    @Test
    void noFieldWasLostInTheReshuffle() throws IOException {
        // The risk in a reorder is a row quietly failing to land anywhere. Every control the old
        // single list carried should still be on exactly one tab.
        List<String> all = new ArrayList<>();
        all.addAll(labelsIn("bodyRows"));
        all.addAll(labelsIn("faceRows"));
        all.addAll(labelsIn("hairRows"));

        for (String expected : new String[]{"Gender", "Class", "Body type", "Bust scale",
                "Body color 1", "Body color 2", "Body color 3", "Eye color 1", "Eye color 2",
                "Tail color", "Hair on", "Hair style", "Hair color", "Hair code", "Base hair",
                "Head bone", "Eyes", "Nose", "Mouth", "Tattoo"}) {
            assertEquals(1, all.stream().filter(expected::equals).count(),
                    expected + " should appear on exactly one sub-tab");
        }
    }

    @Test
    void theThreeSubTabsAreNamedAsTheReferenceNamesThem() throws IOException {
        String source = Files.readString(SCREEN, StandardCharsets.UTF_8);
        assertTrue(source.contains("BODY(\"Body\")"), "Body");
        assertTrue(source.contains("FACE(\"Face\")"), "Face");
        assertTrue(source.contains("HAIR(\"Hair / Aura\")"), "Hair / Aura, not just Hair");
    }

    @Test
    void switchingSubTabReturnsToTheFirstPage() throws IOException {
        // Page 3 of Body means nothing on Face; landing there would look like an empty screen.
        String source = Files.readString(SCREEN, StandardCharsets.UTF_8);
        int handler = source.indexOf("if (section != target) {");
        assertTrue(handler >= 0, "switching should be guarded");
        String body = source.substring(handler, Math.min(source.length(), handler + 220));
        assertTrue(body.contains("page = 0"), "and should reset the page");
    }

    @Test
    void theBodyTypeCyclerShowsHowManyThereAre() throws IOException {
        // The reference reads "< 2 / 2 >". A bare index says nothing about whether more exist.
        String source = Files.readString(SCREEN, StandardCharsets.UTF_8);
        int partRow = source.indexOf("private EditorRow partRow(");
        assertTrue(partRow >= 0, "partRow should exist");
        String body = source.substring(partRow, Math.min(source.length(), partRow + 900));
        assertTrue(body.contains("\" / \" + max"), "labels should carry the total");
    }
}
