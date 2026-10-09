package net.bullettrain.xenopixelsmod.combat.v3.anim;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;

class V3AnimationResourceTest {
    private static final String NAME = V3AnimationCatalog.PREFIX + "test_occurrence_fire";
    private static final Set<String> BONES = Set.of(
            "root", "waist", "head", "left_arm", "right_arm", "left_leg", "right_leg");

    @Test void namesAndDurationsComeFromAuthoredResourceWithoutGenericFallback() {
        var parsed = V3AnimationCatalog.parse(new StringReader("""
                {"animations":{"%s":{"animation_length":0.31}}}
                """.formatted(NAME)));
        assertEquals(7, parsed.get(NAME));
        assertEquals(Set.of(NAME), parsed.keySet());
        assertThrows(UnsupportedOperationException.class, () -> parsed.clear());
        assertFalse(V3AnimationCatalog.isPlayable("combat.xeno_jab_right_v3"));
        assertFalse(V3AnimationCatalog.isPlayable(null));
        assertEquals(-1, V3AnimationCatalog.durationTicks("combat.xeno_jab_right_v3"));
        assertEquals(-1, V3AnimationCatalog.durationTicks(null));
    }

    @Test void emptyOccurrenceFileAdvertisesNoPlaceholderNames() {
        assertTrue(V3AnimationCatalog.parse(new StringReader("{\"animations\":{}}")).isEmpty());
    }

    @Test void foreignNamesAndInvalidDurationsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> V3AnimationCatalog.parse(new StringReader(
                "{\"animations\":{\"combat.xeno_jab_right_v3\":{\"animation_length\":0.3}}}")));
        String overlong = V3AnimationCatalog.PREFIX + "a".repeat(65 - V3AnimationCatalog.PREFIX.length());
        assertThrows(IllegalArgumentException.class, () -> V3AnimationCatalog.parse(new StringReader(
                "{\"animations\":{\"" + overlong + "\":{\"animation_length\":0.3}}}")));
        String longest = overlong.substring(0, 64);
        assertEquals(Set.of(longest), V3AnimationCatalog.parse(new StringReader(
                "{\"animations\":{\"" + longest + "\":{\"animation_length\":0.3}}}")).keySet());
        for (String length : new String[]{"0", "-1", "601", "1e999", "\"0.3\"", "null"}) {
            assertThrows(IllegalArgumentException.class, () -> V3AnimationCatalog.parse(new StringReader(
                    "{\"animations\":{\"" + NAME + "\":{\"animation_length\":" + length + "}}}")), length);
        }
        assertThrows(IllegalArgumentException.class,
                () -> V3AnimationCatalog.parse(new StringReader("{}")));
    }

    @Test void shippedOccurrenceClipsHaveKnownBonesFiniteFramesAndMatchingMetadata() throws IOException {
        String resource = "/assets/xenopixelsmod/" + V3AnimationCatalog.RESOURCE_PATH;
        try (var stream = V3AnimationResourceTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            JsonObject animations = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("animations");
            assertEquals(animations.keySet(), V3AnimationCatalog.allNames());
            for (var entry : animations.entrySet()) {
                JsonObject clip = entry.getValue().getAsJsonObject();
                double seconds = clip.get("animation_length").getAsDouble();
                assertTrue(V3AnimationCatalog.isPlayable(entry.getKey()));
                assertEquals((int) Math.ceil(seconds * 20 - 1e-6),
                        V3AnimationCatalog.durationTicks(entry.getKey()));
                JsonObject bones = clip.getAsJsonObject("bones");
                assertNotNull(bones, entry.getKey());
                assertFalse(bones.isEmpty(), entry.getKey());
                assertTrue(BONES.containsAll(bones.keySet()), entry.getKey() + " has unknown bones");
                for (var bone : bones.entrySet()) {
                    for (var channel : bone.getValue().getAsJsonObject().entrySet()) {
                        assertTrue(Set.of("rotation", "position", "scale").contains(channel.getKey()),
                                entry.getKey() + " " + channel.getKey());
                        validateChannel(channel.getValue(), seconds, entry.getKey());
                        if (bone.getKey().equals("root") && channel.getKey().equals("position")) {
                            validateNoRootTravel(channel.getValue(), entry.getKey());
                        }
                    }
                }
            }
        }
    }

    private static void validateChannel(JsonElement channel, double duration, String name) {
        if (channel.isJsonObject()) {
            for (var frame : channel.getAsJsonObject().entrySet()) {
                double time = Double.parseDouble(frame.getKey());
                assertTrue(Double.isFinite(time) && time >= 0 && time <= duration + 1e-6, name + " " + time);
                validateVector(frame.getValue(), name);
            }
        } else validateVector(channel, name);
    }

    private static void validateVector(JsonElement value, String name) {
        if (value.isJsonArray()) {
            assertEquals(3, value.getAsJsonArray().size(), name);
            for (JsonElement component : value.getAsJsonArray()) validateVector(component, name);
        } else if (value.isJsonObject()) {
            for (var field : value.getAsJsonObject().entrySet()) {
                if (field.getKey().equals("lerp_mode") || field.getKey().equals("easing")) continue;
                validateVector(field.getValue(), name);
            }
        } else {
            assertTrue(value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(), name);
            assertTrue(Double.isFinite(value.getAsDouble()), name);
        }
    }

    private static void validateNoRootTravel(JsonElement value, String name) {
        if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) validateNoRootTravel(child, name);
        } else if (value.isJsonObject()) {
            for (var field : value.getAsJsonObject().entrySet()) {
                if (field.getKey().equals("lerp_mode") || field.getKey().equals("easing")) continue;
                validateNoRootTravel(field.getValue(), name);
            }
        } else assertEquals(0, value.getAsDouble(), 1e-6, name + " duplicates server root travel");
    }
}
