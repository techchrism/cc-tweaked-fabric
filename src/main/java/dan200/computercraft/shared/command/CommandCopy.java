/*
 * This file is part of ComputerCraft - http://www.computercraft.info
 * Copyright Daniel Ratcliffe, 2011-2020. Do not distribute without permission.
 * Send enquiries to dratcliffe@gmail.com
 */

package dan200.computercraft.shared.command;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.client.MinecraftClient;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment (EnvType.CLIENT)
public final class CommandCopy {
    private static final String PREFIX = "/computercraft copy ";

    private CommandCopy() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> registry) {
        registry.register(literal("computercraft").then(literal("copy"))
                                                  .then(argument("message", StringArgumentType.greedyString()))
                                                  .executes(context -> {
                                                      MinecraftClient.getInstance().keyboard.setClipboard(context.getArgument("message", String.class));
                                                      return 1;
                                                  }));
    }

    public static boolean onClientSendMessage(String message) {
        // Emulate the command on the client side
        if (message.startsWith(PREFIX)) {
            MinecraftClient.getInstance().keyboard.setClipboard(message.substring(PREFIX.length()));
            return true;
        }
        return false;
    }

    public static Text createCopyText(String text) {
        return new LiteralText(text).fillStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, PREFIX + text))
                                                          .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                                                                         new TranslatableText("gui.computercraft.tooltip.copy"))));
    }
}
