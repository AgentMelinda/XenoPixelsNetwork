// Usage: fixes the ResolutionException that happens when both My NPCs and CustomNPCs-Unofficial
// are installed together (both bundle an identical, unrelocated nikedemos.markovnames package,
// which NeoForge's per-jar module layer refuses to load twice). Relocates My NPCs' copy to
// espi.mynpcs.shaded.markovnames; run again after updating My NPCs to a new version.
//
// Compile (needs ASM + ASM Commons on the classpath - any recent org.ow2.asm version works;
// this project already resolves them transitively via Mixin, findable under
// ~/.gradle/caches/modules-2/files-2.1/org.ow2.asm/):
//   javac -cp "asm-<ver>.jar;asm-commons-<ver>.jar" relocate_mynpcs_markovnames.java
// Run:
//   java -cp ".;asm-<ver>.jar;asm-commons-<ver>.jar" Relocate <input-mynpcs.jar> <output.jar>
// Then drop <output.jar> into your mods folder in place of the original My NPCs jar.
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

/**
 * Relocates the nikedemos/markovnames package inside a mod jar to a unique package, so it no
 * longer collides with the identical package another mod (CustomNPCs-Unofficial) also bundles.
 * NeoForge's ModLauncher builds a real Java module layer per mod jar, and JPMS forbids two
 * modules from exporting the same package - hence the ResolutionException when both mods are
 * installed together. This is a jar-level fix (like the community "jar-relocator" tool), not a
 * code fix, because the conflict happens during module-layer construction, before any mod code
 * (including mixins) runs.
 */
public class Relocate {
    static final String OLD_PREFIX = "nikedemos/markovnames";
    static final String NEW_PREFIX = "espi/mynpcs/shaded/markovnames";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: Relocate <input.jar> <output.jar>");
            System.exit(1);
        }
        Path input = Paths.get(args[0]);
        Path output = Paths.get(args[1]);

        SimpleRemapper remapper = new SimpleRemapper(buildMappings(input));

        int classesTouched = 0;
        int entriesTotal = 0;
        try (JarFile jarFile = new JarFile(input.toFile());
             JarOutputStream out = new JarOutputStream(new BufferedOutputStream(
                     Files.newOutputStream(output, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)))) {

            Enumeration<JarEntry> entries = jarFile.entries();
            Set<String> writtenNames = new HashSet<>();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                entriesTotal++;
                String name = entry.getName();

                if (entry.isDirectory()) {
                    // Directory entries are optional in a jar; skip them (renamed dirs would be
                    // synthesized implicitly by any reasonable jar tool/classloader).
                    continue;
                }

                byte[] data;
                try (InputStream in = jarFile.getInputStream(entry)) {
                    data = in.readAllBytes();
                }

                String outName = name;
                if (name.endsWith(".class")) {
                    ClassReader reader = new ClassReader(data);
                    ClassWriter writer = new ClassWriter(0);
                    ClassRemapper visitor = new ClassRemapper(writer, remapper);
                    reader.accept(visitor, 0);
                    byte[] remapped = writer.toByteArray();
                    if (!Arrays.equals(remapped, data)) {
                        classesTouched++;
                    }
                    data = remapped;
                    if (name.startsWith(OLD_PREFIX + "/")) {
                        outName = NEW_PREFIX + name.substring(OLD_PREFIX.length());
                    }
                }

                if (!writtenNames.add(outName)) {
                    continue; // duplicate after rename (shouldn't happen, but stay safe)
                }
                JarEntry outEntry = new JarEntry(outName);
                out.putNextEntry(outEntry);
                out.write(data);
                out.closeEntry();
            }
        }

        System.out.println("Entries processed: " + entriesTotal);
        System.out.println("Classes rewritten (relocated or referencing relocated types): " + classesTouched);
        System.out.println("Wrote: " + output.toAbsolutePath());
    }

    /** Builds an old-internal-name -> new-internal-name map for every class in OLD_PREFIX. */
    static Map<String, String> buildMappings(Path input) throws IOException {
        Map<String, String> mappings = new HashMap<>();
        try (JarFile jarFile = new JarFile(input.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!entry.isDirectory() && name.endsWith(".class") && name.startsWith(OLD_PREFIX + "/")) {
                    String oldInternalName = name.substring(0, name.length() - ".class".length());
                    String newInternalName = NEW_PREFIX + oldInternalName.substring(OLD_PREFIX.length());
                    mappings.put(oldInternalName, newInternalName);
                }
            }
        }
        return mappings;
    }
}
