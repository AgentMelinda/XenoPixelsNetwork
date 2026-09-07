package net.bullettrain.xenopixelsmod.block.custom.copycat;

/**
 * Marker on the copycat wing blocks (texture-inheriting variants of the wing control surfaces), so
 * the Panel Configurator, the Create wrench handler and the wing renderer can {@code instanceof}-test
 * for "this is a copycat wing" without listing every class.
 */
public interface CopycatWing {
}
