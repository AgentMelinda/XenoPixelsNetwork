package net.bullettrain.xenopixelsmod.command;

import net.bullettrain.xenopixelsmod.compat.npc.NpcTypes;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.npc.NpcXenoScriptApi;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.fml.loading.FMLLoader;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipFile;

/** Runtime catalog of the CustomNPCs scripting API actually installed on the server. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class CustomNpcScriptApiCommands {
    private static final int PAGE_SIZE = 12;
    private CustomNpcScriptApiCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        var tree = Commands.literal("scriptapi")
                .then(Commands.literal("globals").executes(c -> page(c.getSource(), "", 1, true))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(c -> page(c.getSource(), "", IntegerArgumentType.getInteger(c, "page"), true))))
                .then(Commands.literal("all").executes(c -> page(c.getSource(), "", 1, false))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(c -> page(c.getSource(), "", IntegerArgumentType.getInteger(c, "page"), false))))
                .then(Commands.literal("search").then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(c -> page(c.getSource(), StringArgumentType.getString(c, "text"), 1, false))))
                .then(Commands.literal("show").then(Commands.argument("class", StringArgumentType.greedyString())
                        .executes(c -> show(c.getSource(), StringArgumentType.getString(c, "class"), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(c -> show(c.getSource(), StringArgumentType.getString(c, "class"), IntegerArgumentType.getInteger(c, "page"))))))
                .then(Commands.literal("dump").requires(s -> s.hasPermission(2)).executes(c -> dump(c.getSource())));
        d.register(Commands.literal("xenopixels").then(tree));
    }

    private static int page(CommandSourceStack source, String query, int page, boolean globalsOnly) {
        List<String> rows = new ArrayList<>();
        if (globalsOnly || query.isBlank()) rows.add("global XenoPixels (XenoPixels API v" + NpcXenoScriptApi.INSTANCE.getVersion() + ")");
        if (!globalsOnly) rows.addAll(classes(query));
        int pages = Math.max(1, (rows.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int from = Math.min(rows.size(), (page - 1) * PAGE_SIZE);
        int to = Math.min(rows.size(), from + PAGE_SIZE);
        source.sendSuccess(() -> Component.literal("CustomNPCs scripting API " + page + "/" + pages), false);
        for (int i = from; i < to; i++) {
            String row = rows.get(i);
            source.sendSuccess(() -> Component.literal(row), false);
        }
        return to - from;
    }

    /** The NPC mod whose jar the catalog is read from, and the API path inside it. */
    private record ApiJar(String modId, String apiPath) {}

    private static final ApiJar[] API_JARS = {
            new ApiJar("mynpcs", "espi/mynpcs/api/"),
            new ApiJar("customnpcs", "noppes/npcs/api/"),
    };

    private static List<String> classes(String query) {
        for (ApiJar candidate : API_JARS) {
            List<String> found = classes(query, candidate);
            if (!found.isEmpty()) return found;
        }
        return new ArrayList<>();
    }

    private static List<String> classes(String query, ApiJar source) {
        List<String> out = new ArrayList<>();
        try {
            var info = FMLLoader.getLoadingModList().getModFileById(source.modId());
            if (info == null) return out;
            Path jar = info.getFile().getFilePath();
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                zip.stream().filter(e -> e.getName().startsWith(source.apiPath()) && e.getName().endsWith(".class") && !e.getName().contains("$"))
                        .map(e -> e.getName().substring(0, e.getName().length() - 6).replace('/', '.'))
                        .sorted().forEach(name -> {
                            if (query.isBlank() || name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) out.add("class " + name);
                        });
            }
        } catch (IOException | RuntimeException ignored) {
            // The command remains usable for globals if a production mod loader hides the jar path.
        }
        return out;
    }

    private static int dump(CommandSourceStack source) {
        Path file = source.getServer().getFile("logs/xenopixels-customnpcs-script-api.md");
        try {
            Files.createDirectories(file.getParent());
            List<String> rows = classes("");
            Files.writeString(file, "# CustomNPCs scripting API\n\n" + String.join("\n", rows) + "\n\nGlobal: `XenoPixels`\n");
            source.sendSuccess(() -> Component.literal("Wrote API catalog to " + file), true);
            return rows.size();
        } catch (IOException e) {
            source.sendFailure(Component.literal("Could not write API catalog: " + e.getMessage()));
            return 0;
        }
    }

    private static int show(CommandSourceStack source, String requested, int page) {
        // A bare name such as "entity.IPlayer" is looked up under whichever NPC mod is installed;
        // a fully-qualified one is taken as given, so either spelling still works if someone has it
        // written down from before the fork.
        String name = requested.startsWith("noppes.") || requested.startsWith("espi.")
                ? requested : null;
        List<String> rows = new ArrayList<>();
        try {
            Class<?> type = name == null
                    ? NpcTypes.find("api." + requested)
                    : Class.forName(name, false, Thread.currentThread().getContextClassLoader());
            if (type == null) {
                source.sendFailure(Component.literal("No NPC API class named " + requested));
                return 0;
            }
            for (var field : type.getFields()) {
                if (Modifier.isPublic(field.getModifiers())) rows.add("field " + field.getName() + " : " + field.getType().getTypeName());
            }
            for (var method : type.getMethods()) {
                if (Modifier.isPublic(method.getModifiers())) {
                    rows.add("method " + method.getName() + "(" + java.util.Arrays.stream(method.getParameterTypes()).map(Class::getTypeName).reduce((a,b) -> a + ", " + b).orElse("") + ") : " + method.getReturnType().getTypeName());
                }
            }
            rows.sort(String::compareTo);
        } catch (ReflectiveOperationException | LinkageError e) {
            source.sendFailure(Component.literal("Unknown CustomNPCs API class: " + requested));
            return 0;
        }
        int pages = Math.max(1, (rows.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int from = Math.min(rows.size(), (page - 1) * PAGE_SIZE), to = Math.min(rows.size(), from + PAGE_SIZE);
        source.sendSuccess(() -> Component.literal(name + " " + page + "/" + pages), false);
        for (int i = from; i < to; i++) { String row = rows.get(i); source.sendSuccess(() -> Component.literal(row), false); }
        return to - from;
    }
}
