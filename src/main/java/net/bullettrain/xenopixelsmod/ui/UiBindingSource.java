package net.bullettrain.xenopixelsmod.ui;

/**
 * Numbers and text the layout/runtime can ask for. Tests supply a map; the client
 * supplies {@code XenoHudSnapshot} values.
 */
public interface UiBindingSource {
    Double number(String key);

    String text(String key);
}
