package net.bullettrain.xenopixelsmod.mixin.compat.mynpcs;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.npc.clone.NpcCloneConverter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Keeps CustomNPCs NPCs already placed in a world alive after the move to My NPCs.
 *
 * <p>Those NPCs are stored in chunks as entities with {@code id: customnpcs:customnpc}. With
 * CustomNPCs uninstalled vanilla cannot resolve that id: it logs "Skipping Entity with id" and
 * <b>discards the entity</b>, and the loss becomes permanent the moment that chunk saves again.
 * Rewriting the id before the lookup lets My NPCs load them instead, and the chunk then saves them
 * under the new id, so each chunk migrates once and stays migrated.
 *
 * <p>{@code EntityType.by} is the single point every load path funnels through to turn a saved id
 * into a type, which is why the hook is here rather than spread across the chunk and passenger
 * paths.
 *
 * <p>Inert unless My NPCs is the NPC mod in play: the package gate already requires {@code mynpcs},
 * and with CustomNPCs also installed the original id resolves on its own and nothing needs doing.
 */
@Mixin(EntityType.class)
public abstract class EntityTypeNpcMigrationMixin {

    @Inject(method = "by(Lnet/minecraft/nbt/CompoundTag;)Ljava/util/Optional;",
            at = @At("HEAD"), require = 0)
    private static void xenopixels$migrateCustomNpcsEntity(
            CompoundTag tag, CallbackInfoReturnable<Optional<EntityType<?>>> cir) {
        if (tag == null || !NpcCloneConverter.isCustomNpcsClone(tag)) {
            return;
        }
        if (ModList.get().isLoaded("customnpcs")) {
            // Both installed: CustomNPCs can load its own entity, so leave it be.
            return;
        }
        if (NpcCloneConverter.migrateEntityInPlace(tag)) {
            // Worth a line each: this is a one-way change to somebody's world, and the count in the
            // log is how anyone later answers "did my NPCs survive the move?".
            XenoPixelsMod.LOGGER.info("Migrated an in-world CustomNPCs NPC to My NPCs");
        }
    }
}
