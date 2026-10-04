package net.bullettrain.xenopixelsmod.npc.brain.v6;

public record XenoNpcDecision(XenoNpcActionKind kind, int priority, long cooldownTicks) {
}