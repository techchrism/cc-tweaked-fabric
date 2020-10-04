/*
 * This file is part of ComputerCraft - http://www.computercraft.info
 * Copyright Daniel Ratcliffe, 2011-2020. Do not distribute without permission.
 * Send enquiries to dratcliffe@gmail.com
 */

package dan200.computercraft.shared.command.arguments;

import static dan200.computercraft.shared.command.CommandUtils.suggest;
import static dan200.computercraft.shared.command.CommandUtils.suggestOnServer;
import static dan200.computercraft.shared.command.Exceptions.COMPUTER_ARG_NONE;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.computer.core.ServerComputer;

import net.minecraft.command.arguments.serialize.ArgumentSerializer;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.command.ServerCommandSource;

public final class ComputersArgumentType implements ArgumentType<ComputersArgumentType.ComputersSupplier> {
    private static final ComputersArgumentType MANY = new ComputersArgumentType(false);
    private static final ComputersArgumentType SOME = new ComputersArgumentType(true);

    private static final List<String> EXAMPLES = Arrays.asList("0", "#0", "@Label", "~Advanced");
    private final boolean requireSome;

    private ComputersArgumentType(boolean requireSome) {
        this.requireSome = requireSome;
    }

    public static ComputersArgumentType manyComputers() {
        return MANY;
    }

    public static ComputersArgumentType someComputers() {
        return SOME;
    }

    public static Collection<ServerComputer> getComputersArgument(CommandContext<ServerCommandSource> context, String name) throws CommandSyntaxException {
        return context.getArgument(name, ComputersSupplier.class)
                      .unwrap(context.getSource());
    }

    public static Set<ServerComputer> unwrap(ServerCommandSource source, Collection<ComputersSupplier> suppliers) throws CommandSyntaxException {
        Set<ServerComputer> computers = new HashSet<>();
        for (ComputersSupplier supplier : suppliers) {
            computers.addAll(supplier.unwrap(source));
        }
        return computers;
    }

    @Override
    public ComputersSupplier parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        char kind = reader.peek();
        ComputersSupplier computers;
        if (kind == '@') {
            reader.skip();
            String label = reader.readUnquotedString();
            computers = getComputers(x -> Objects.equals(label, x.getLabel()));
        } else if (kind == '~') {
            reader.skip();
            String family = reader.readUnquotedString();
            computers = getComputers(x -> x.getFamily()
                                           .name()
                                           .equalsIgnoreCase(family));
        } else if (kind == '#') {
            reader.skip();
            int id = reader.readInt();
            computers = getComputers(x -> x.getID() == id);
        } else {
            int instance = reader.readInt();
            computers = s -> {
                ServerComputer computer = ComputerCraft.serverComputerRegistry.get(instance);
                return computer == null ? Collections.emptyList() : Collections.singletonList(computer);
            };
        }

        if (this.requireSome) {
            String selector = reader.getString()
                                    .substring(start, reader.getCursor());
            return source -> {
                Collection<ServerComputer> matched = computers.unwrap(source);
                if (matched.isEmpty()) {
                    throw COMPUTER_ARG_NONE.create(selector);
                }
                return matched;
            };
        } else {
            return computers;
        }
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();

        // We can run this one on the client, for obvious reasons.
        if (remaining.startsWith("~")) {
            return suggest(builder, ComputerFamily.values(), x -> "~" + x.name());
        }

        // Verify we've a command source and we're running on the server
        return suggestOnServer(context, builder, s -> {
            if (remaining.startsWith("@")) {
                suggestComputers(builder, remaining, x -> {
                    String label = x.getLabel();
                    return label == null ? null : "@" + label;
                });
            } else if (remaining.startsWith("#")) {
                suggestComputers(builder, remaining, c -> "#" + c.getID());
            } else {
                suggestComputers(builder, remaining, c -> Integer.toString(c.getInstanceID()));
            }

            return builder.buildFuture();
        });
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    private static void suggestComputers(SuggestionsBuilder builder, String remaining, Function<ServerComputer, String> renderer) {
        remaining = remaining.toLowerCase(Locale.ROOT);
        for (ServerComputer computer : ComputerCraft.serverComputerRegistry.getComputers()) {
            String converted = renderer.apply(computer);
            if (converted != null && converted.toLowerCase(Locale.ROOT)
                                              .startsWith(remaining)) {
                builder.suggest(converted);
            }
        }
    }

    private static ComputersSupplier getComputers(Predicate<ServerComputer> predicate) {
        return s -> Collections.unmodifiableList(ComputerCraft.serverComputerRegistry.getComputers()
                                                                                     .stream()
                                                                                     .filter(predicate)
                                                                                     .collect(Collectors.toList()));
    }

    @FunctionalInterface
    public interface ComputersSupplier {
        Collection<ServerComputer> unwrap(ServerCommandSource source) throws CommandSyntaxException;
    }

    public static class Serializer implements ArgumentSerializer<ComputersArgumentType>
    {

        @Override
        public void toPacket(@Nonnull ComputersArgumentType arg, @Nonnull PacketByteBuf buf) {
            buf.writeBoolean(arg.requireSome);
        }

        @Nonnull
        @Override
        public ComputersArgumentType fromPacket(@Nonnull PacketByteBuf buf) {
            return buf.readBoolean() ? SOME : MANY;
        }

        @Override
        public void toJson(@Nonnull ComputersArgumentType arg, @Nonnull JsonObject json) {
            json.addProperty("requireSome", arg.requireSome);
        }
    }
}
