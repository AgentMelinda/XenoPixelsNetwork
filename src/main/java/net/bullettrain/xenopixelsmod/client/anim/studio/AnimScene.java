package net.bullettrain.xenopixelsmod.client.anim.studio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * One studio scene: ordered clips on a dummy actor. JSON only — no Minecraft types.
 */
public final class AnimScene {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String name = "scene";
    public String actor = "training_dummy";
    public final List<String> clips = new ArrayList<>();

    public AnimScene() {}

    public AnimScene(String name) {
        this.name = name == null || name.isBlank() ? "scene" : name;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static AnimScene fromJson(String json) {
        if (json == null || json.isBlank()) return new AnimScene();
        AnimScene scene = GSON.fromJson(json, AnimScene.class);
        return scene == null ? new AnimScene() : scene;
    }
}
