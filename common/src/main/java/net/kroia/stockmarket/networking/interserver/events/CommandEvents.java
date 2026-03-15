package net.kroia.stockmarket.networking.interserver.events;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.kroia.stockmarket.networking.interserver.child.HubConnector;
import net.kroia.stockmarket.networking.interserver.config.ModConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Registers the /hubsend command for testing cross-server string messages.
 *
 * Usage (on a child server):
 *
 *   /hubsend <message>
 *       → sends message to ALL other servers (broadcast)
 *
 *   /hubsend <targetServer> <message>
 *       → sends message to one specific server
 *
 * Examples:
 *   /hubsend Hello from Server A!
 *   /hubsend server_b Hey Server B, can you hear me?
 */
public class CommandEvents {

    public static void register() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {

            // /hubsend <message>  — broadcast to all
            LiteralArgumentBuilder<CommandSourceStack> broadcast =
                    Commands.literal("hubsend")
                            .requires(src -> src.hasPermission(2)) // op level 2
                            .then(Commands.argument("message", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        if (ModConfig.get().isHub) {
                                            ctx.getSource().sendFailure(
                                                    Component.literal("[HubMod] /hubsend is for child servers only."));
                                            return 0;
                                        }

                                        String message     = StringArgumentType.getString(ctx, "message");
                                        String senderName  = ctx.getSource().getTextName();

                                        HubConnector.get().sendString(senderName, message, null);

                                        ctx.getSource().sendSuccess(() ->
                                                        Component.literal("§7[HubMod] Sent to §ball servers§7: §e" + message),
                                                false);
                                        return 1;
                                    })
                            );

            dispatcher.register(broadcast);

            // /hubsendto <targetServer> <message>  — direct to one server
            LiteralArgumentBuilder<CommandSourceStack> direct =
                    Commands.literal("hubsendto")
                            .requires(src -> src.hasPermission(2))
                            .then(Commands.argument("targetServer", StringArgumentType.word())
                                    .then(Commands.argument("message", StringArgumentType.greedyString())
                                            .executes(ctx -> {
                                                if (ModConfig.get().isHub) {
                                                    ctx.getSource().sendFailure(
                                                            Component.literal("[HubMod] /hubsendto is for child servers only."));
                                                    return 0;
                                                }

                                                String target     = StringArgumentType.getString(ctx, "targetServer");
                                                String message    = StringArgumentType.getString(ctx, "message");
                                                String senderName = ctx.getSource().getTextName();

                                                HubConnector.get().sendString(senderName, message, target);

                                                ctx.getSource().sendSuccess(() ->
                                                                Component.literal("§7[HubMod] Sent to §b" + target + "§7: §e" + message),
                                                        false);
                                                return 1;
                                            })
                                    )
                            );

            dispatcher.register(direct);
        });
    }
}