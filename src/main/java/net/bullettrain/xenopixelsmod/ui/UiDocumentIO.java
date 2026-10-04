package net.bullettrain.xenopixelsmod.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.List;

/** Gson load/save. Parse failures become error strings. */
public final class UiDocumentIO {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private UiDocumentIO() {
    }

    public static String toJson(UiDocument document) {
        return GSON.toJson(document == null ? new UiDocument() : document);
    }

    public static UiLoadResult fromJson(String json) {
        List<String> errors = new ArrayList<>();
        if (json == null || json.isBlank()) {
            errors.add("empty json");
            return new UiLoadResult(null, errors);
        }
        try {
            UiDocument document = GSON.fromJson(json, UiDocument.class);
            errors.addAll(UiDocumentValidator.validate(document));
            return new UiLoadResult(document, errors);
        } catch (JsonParseException ex) {
            errors.add("invalid json");
            return new UiLoadResult(null, errors);
        }
    }

    public record UiLoadResult(UiDocument document, List<String> errors) {
        public boolean ok() {
            return document != null && errors.isEmpty();
        }
    }
}
