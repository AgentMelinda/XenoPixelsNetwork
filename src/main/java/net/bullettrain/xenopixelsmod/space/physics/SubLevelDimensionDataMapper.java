package net.bullettrain.xenopixelsmod.space.physics;

import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/** Reindexes Sable's serialized section-array keys when a body moves between dimension height ranges. */
public final class SubLevelDimensionDataMapper {
    private SubLevelDimensionDataMapper() {}

    /**
     * Sable 2.0.3 writes chunk section-array indices in the plot tag, then uses those values directly
     * against the destination level's section array. Convert each source index through its absolute
     * section Y so the same block-space section is selected in the destination dimension.
     *
     * @throws IllegalArgumentException if the source tag is malformed or any section lies outside
     *                                  the destination level's vertical section range
     */
    public static SubLevelData mapToDestination(SubLevelData sourceData, ServerLevel sourceLevel,
                                                ServerLevel destinationLevel) {
        CompoundTag fullTag = sourceData.fullTag().copy();
        if (!fullTag.contains("plot", CompoundTag.TAG_COMPOUND))
            throw new IllegalArgumentException("serialized Sable body is missing plot data");

        CompoundTag plot = fullTag.getCompound("plot");
        if (!plot.contains("chunks", CompoundTag.TAG_COMPOUND))
            throw new IllegalArgumentException("serialized Sable plot is missing chunks");

        CompoundTag chunks = plot.getCompound("chunks");
        for (String chunkKey : chunks.getAllKeys()) {
            CompoundTag chunk = chunks.getCompound(chunkKey);
            if (!chunk.contains("sections", CompoundTag.TAG_COMPOUND)) continue;

            CompoundTag sourceSections = chunk.getCompound("sections");
            CompoundTag mappedSections = new CompoundTag();
            for (String sectionKey : sourceSections.getAllKeys()) {
                final int sourceIndex;
                try {
                    sourceIndex = Integer.parseInt(sectionKey);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("invalid Sable section index: " + sectionKey, exception);
                }
                if (sourceIndex < 0 || sourceIndex >= sourceLevel.getSectionsCount())
                    throw new IllegalArgumentException("Sable section index " + sourceIndex
                            + " is outside source section count " + sourceLevel.getSectionsCount());

                int absoluteSectionY = sourceLevel.getSectionYFromSectionIndex(sourceIndex);
                int destinationIndex = destinationLevel.getSectionIndex(absoluteSectionY * 16);
                if (destinationIndex < 0 || destinationIndex >= destinationLevel.getSectionsCount()
                        || destinationLevel.getSectionYFromSectionIndex(destinationIndex) != absoluteSectionY)
                    throw new IllegalArgumentException("Sable section Y " + absoluteSectionY
                            + " is outside destination range [" + destinationLevel.getMinBuildHeight() + ", "
                            + destinationLevel.getMaxBuildHeight() + ")");

                String mappedKey = Integer.toString(destinationIndex);
                if (mappedSections.contains(mappedKey))
                    throw new IllegalArgumentException("multiple source sections map to destination index " + destinationIndex);
                CompoundTag section = sourceSections.getCompound(sectionKey);
                // Light arrays are dimension/section-position dependent. Let the destination light engine
                // rebuild them after ServerLevelPlot.load instead of attaching source-dimension lighting.
                section.remove("BlockLight");
                section.remove("SkyLight");
                mappedSections.put(mappedKey, section);
            }
            chunk.put("sections", mappedSections);
            // Heightmap bit width depends on the destination build height; Sable's loader primes missing maps.
            chunk.remove("heightmaps");
            chunk.putBoolean("isLightOn", false);
        }

        return new SubLevelData(sourceData.uuid(), sourceData.bounds(), sourceData.pose(),
                sourceData.dependencies(), fullTag);
    }
}
