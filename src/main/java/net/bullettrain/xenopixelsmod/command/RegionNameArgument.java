package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/** Local region names and namespaced dimensional names, with optional Brigadier quoting. */
public final class RegionNameArgument implements ArgumentType<String> {
    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && StringReader.isQuotedStringStart(reader.peek())) return reader.readString();
        int start = reader.getCursor();
        while (reader.canRead() && !Character.isWhitespace(reader.peek())) reader.skip();
        return reader.getString().substring(start, reader.getCursor());
    }
}
