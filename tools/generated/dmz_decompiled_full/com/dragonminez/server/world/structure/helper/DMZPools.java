package com.dragonminez.server.world.structure.helper;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool.Projection;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

public class DMZPools {
   public static final ResourceKey<StructureTemplatePool> GOKU_HOUSE = createKey("goku_house");
   public static final ResourceKey<StructureTemplatePool> ROSHI_HOUSE = createKey("roshi_house");
   public static final ResourceKey<StructureTemplatePool> TIMECHAMBER = createKey("timechamber");
   public static final ResourceKey<StructureTemplatePool> ELDER_GURU = createKey("elder_guru");
   public static final ResourceKey<StructureTemplatePool> KAMILOOKOUT = createKey("kamilookout");
   public static final ResourceKey<StructureTemplatePool> GERO_LAB = createKey("gero_lab");
   public static final ResourceKey<StructureTemplatePool> GERO_LAB_STAIRS = createKey("gero_lab/stairs");
   public static final ResourceKey<StructureTemplatePool> GERO_LAB_UNDERGROUND = createKey("gero_lab/underground");
   public static final ResourceKey<StructureTemplatePool> BABIDI = createKey("babidi");
   public static final ResourceKey<StructureTemplatePool> BABIDI_TOP = createKey("babidi/top");
   public static final ResourceKey<StructureTemplatePool> BABIDI_BOTTOM = createKey("babidi/bottom");
   public static final ResourceKey<StructureTemplatePool> CELL_ARENA = createKey("cell_arena");
   public static final ResourceKey<StructureTemplatePool> FRIEZA_SHIP = createKey("frieza_ship");
   public static final ResourceKey<StructureTemplatePool> PICCOLO_HOUSE = createKey("piccolo_house");
   public static final ResourceKey<StructureTemplatePool> OLDKAI_PILLAR = createKey("oldkai_pillar");
   public static final ResourceKey<StructureTemplatePool> YAMCHA_HOUSE = createKey("yamcha_house");
   public static final ResourceKey<StructureTemplatePool> TRUNKS_SHIP = createKey("trunks_ship");
   public static final ResourceKey<StructureTemplatePool> VEGETA_POD = createKey("vegeta_pod");

   public static void bootstrap(BootstrapContext<StructureTemplatePool> context) {
      Holder<StructureTemplatePool> empty = context.lookup(Registries.TEMPLATE_POOL).getOrThrow(Pools.EMPTY);
      HolderGetter<StructureProcessorList> processors = context.lookup(Registries.PROCESSOR_LIST);
      Holder<StructureProcessorList> foundation = processors.getOrThrow(DMZProcessorLists.FOUNDATION);
      context.register(
         GOKU_HOUSE,
         new StructureTemplatePool(empty, ImmutableList.of(Pair.of(StructurePoolElement.single("dragonminez:goku_house", foundation), 1)), Projection.RIGID)
      );
      context.register(
         ROSHI_HOUSE,
         new StructureTemplatePool(empty, ImmutableList.of(Pair.of(StructurePoolElement.single("dragonminez:roshi_house", foundation), 1)), Projection.RIGID)
      );
      context.register(
         TIMECHAMBER, new StructureTemplatePool(empty, ImmutableList.of(Pair.of(StructurePoolElement.single("dragonminez:timechamber"), 1)), Projection.RIGID)
      );
      context.register(
         ELDER_GURU, new StructureTemplatePool(empty, ImmutableList.of(Pair.of(StructurePoolElement.single("dragonminez:elder_guru"), 1)), Projection.RIGID)
      );
      context.register(
         KAMILOOKOUT, new StructureTemplatePool(empty, ImmutableList.of(Pair.of(StructurePoolElement.single("dragonminez:kamilookout"), 1)), Projection.RIGID)
      );
      context.register(
         GERO_LAB,
         new StructureTemplatePool(empty, ImmutableList.of(Pair.of(StructurePoolElement.single("dragonminez:gero_lab_surface"), 1)), Projection.RIGID)
      );
      context.register(
         GERO_LAB_STAIRS,
         new StructureTemplatePool(empty, ImmutableList.of(Pair.of(StructurePoolElement.single("dragonminez:gero_lab_stairs"), 1)), Projection.RIGID)
      );
      context.register(
         GERO_LAB_UNDERGROUND,
         new StructureTemplatePool(empty, ImmutableList.of(Pair.of(StructurePoolElement.single("dragonminez:gero_lab_underground"), 1)), Projection.RIGID)
      );
      context.register(
         BABIDI, new StructureTemplatePool(empty, ImmutableList.of(Pair.of(StructurePoolElement.single("dragonminez:babidi_surface"), 1)), Projection.RIGID)
      );
      context.register(
         BABIDI_TOP, new StructureTemplatePool(empty, ImmutableList.of(Pair.of(StructurePoolElement.single("dragonminez:babidi_top"), 1)), Projection.RIGID)
      );
      context.register(
         BABIDI_BOTTOM,
         new StructureTemplatePool(empty, ImmutableList.of(Pair.of(StructurePoolElement.single("dragonminez:babidi_bottom"), 1)), Projection.RIGID)
      );
      context.register(
         CELL_ARENA,
         new StructureTemplatePool(empty, ImmutableList.of(Pair.of(StructurePoolElement.single("dragonminez:cell_arena", foundation), 1)), Projection.RIGID)
      );
      context.register(
         FRIEZA_SHIP, new StructureTemplatePool(empty, ImmutableList.of(Pair.of(StructurePoolElement.single("dragonminez:frieza_ship"), 1)), Projection.RIGID)
      );
      context.register(
         PICCOLO_HOUSE,
         new StructureTemplatePool(empty, ImmutableList.of(Pair.of(StructurePoolElement.single("dragonminez:piccolo_house", foundation), 1)), Projection.RIGID)
      );
      context.register(
         OLDKAI_PILLAR,
         new StructureTemplatePool(empty, ImmutableList.of(Pair.of(StructurePoolElement.single("dragonminez:oldkai_pillar", foundation), 1)), Projection.RIGID)
      );
      context.register(
         YAMCHA_HOUSE,
         new StructureTemplatePool(empty, ImmutableList.of(Pair.of(StructurePoolElement.single("dragonminez:yamcha_house", foundation), 1)), Projection.RIGID)
      );
      context.register(
         TRUNKS_SHIP, new StructureTemplatePool(empty, ImmutableList.of(Pair.of(StructurePoolElement.single("dragonminez:trunks_ship"), 1)), Projection.RIGID)
      );
      context.register(
         VEGETA_POD,
         new StructureTemplatePool(empty, ImmutableList.of(Pair.of(StructurePoolElement.single("dragonminez:vegeta_pod", foundation), 1)), Projection.RIGID)
      );
   }

   private static ResourceKey<StructureTemplatePool> createKey(String name) {
      return ResourceKey.create(Registries.TEMPLATE_POOL, ResourceLocation.fromNamespaceAndPath("dragonminez", name));
   }
}
