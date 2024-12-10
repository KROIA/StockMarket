package net.kroia.stockmarket.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.bank.MoneyBank;
import net.kroia.stockmarket.bank.ServerBank;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.apache.logging.log4j.core.jmx.Server;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public class ModCommands {
    // Method to register commands
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        /*dispatcher.register(Commands.literal("mycommand")
                .then(Commands.literal("subcommand")
                        .executes(ModCommands::runSubCommand))
                .executes(ModCommands::runMainCommand));*/

        //dispatcher.register(Commands.literal("money")
        //        .executes(ModCommands::command_money));

        /*dispatcher.register(Commands.literal("money")
                .then(Commands.literal("add").executes(ModCommands::command_money_add))
                .executes(ModCommands::command_money));*/

        /*
        dispatcher.register(
                Commands.literal("money")
                        .then(Commands.literal("add")
                                .requires(source -> source.hasPermission(2)) // Admin-only for adding money
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(ModCommands::addMoneyToSelf)) // Add to self
                                .then(Commands.argument("target", StringArgumentType.string()) // Add to others
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                .executes(ModCommands::addMoneyToUser))))
                        .executes(ModCommands::getMoney) // Show balance
        );

        */

        dispatcher.register(
                Commands.literal("money")
                        .then(Commands.literal("add")
                                .requires(source -> source.hasPermission(2)) // Admin-only for adding money
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(context -> {
                                            CommandSourceStack source = context.getSource();
                                            ServerPlayer player = source.getPlayerOrException();

                                            // Get arguments
                                            String username = player.getName().getString();
                                            int amount = IntegerArgumentType.getInteger(context, "amount");

                                            // Execute the command on the server
                                            return executeAddMoney(player, username, amount);
                                        })) // Add to self
                                .then(Commands.argument("username", StringArgumentType.string())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                .executes(context -> {
                                                    CommandSourceStack source = context.getSource();
                                                    ServerPlayer player = source.getPlayerOrException();

                                                    // Get arguments
                                                    String username = StringArgumentType.getString(context, "username");
                                                    int amount = IntegerArgumentType.getInteger(context, "amount");

                                                    // Execute the command on the server
                                                    return executeAddMoney(player, username, amount);
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("send")
                                .then(Commands.argument("username", StringArgumentType.string())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                .executes(context -> {
                                                    CommandSourceStack source = context.getSource();
                                                    ServerPlayer player = source.getPlayerOrException();

                                                    // Get arguments
                                                    String fromPlayer = player.getName().getString();
                                                    String toPlayer = StringArgumentType.getString(context, "username");
                                                    int amount = IntegerArgumentType.getInteger(context, "amount");

                                                    // Execute the command on the server
                                                    return executeAddMoneySend(player,fromPlayer, toPlayer, amount);
                                                })
                                        )
                                )
                        )
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            ServerPlayer player = source.getPlayerOrException();

                            // Execute the balance command on the server
                            return showBalance(player);
                        })

        );
    }

    private static int executeAddMoney(ServerPlayer executor, String username, int amount) {
        // Server-side logic for adding money
        ServerPlayer targetPlayer = executor.getServer().getPlayerList().getPlayerByName(username);
        if(targetPlayer == null) {
            executor.sendSystemMessage(
                    Component.literal("Player " + username + " not found.")
            );
            return Command.SINGLE_SUCCESS;
        }
        ServerBank.getBank(targetPlayer.getUUID()).deposit(amount); // Example call
        executor.sendSystemMessage(
                Component.literal("Added " + amount + " to " + username + "'s account!")
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int executeAddMoneySend(ServerPlayer executor, String fromUser, String toUser, int amount) {
        // Server-side logic for adding money
        ServerPlayer from = executor.getServer().getPlayerList().getPlayerByName(fromUser);
        ServerPlayer to = executor.getServer().getPlayerList().getPlayerByName(toUser);
        if(from == null) {
            executor.sendSystemMessage(
                    Component.literal("Player " + fromUser + " not found.")
            );
            return Command.SINGLE_SUCCESS;
        }
        if(to == null) {
            executor.sendSystemMessage(
                    Component.literal("Player " + toUser + " not found.")
            );
            return Command.SINGLE_SUCCESS;
        }
        MoneyBank fromBank = ServerBank.getBank(from.getUUID());
        MoneyBank toBank = ServerBank.getBank(to.getUUID());

        if(fromBank.transfer(amount, toBank))
        {
            StockMarketMod.printToClientConsole(from, "Transfered " + amount + "$ from " + fromUser + " to " + toUser + "'s account!");
            StockMarketMod.printToClientConsole(to, "Received " + amount + "$ from " + fromUser + "'s account!");
        }
        else {
            if(fromBank.getBalance() < amount)
                StockMarketMod.printToClientConsole(from, "You don't have enough money to transfer " + amount + "$!");
            else
                StockMarketMod.printToClientConsole(from, "Failed to transfer " + amount + "$ to " + toUser + "'s account!");
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int showBalance(ServerPlayer player) {
        // Server-side logic for showing the balance
        long balance = ServerBank.getBank(player.getUUID()).getBalance(); // Example call
        player.sendSystemMessage(Component.literal("Your balance: " + balance));
        return Command.SINGLE_SUCCESS;
    }

    private static int addMoneyToUser(CommandContext<CommandSourceStack> context) {
        String targetName = StringArgumentType.getString(context, "target");
        int amount = IntegerArgumentType.getInteger(context, "amount");
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();

        // Attempt to find the player online
        ServerPlayer targetPlayer = server.getPlayerList().getPlayerByName(targetName);
        if (targetPlayer != null) {
            // Player is online
            UUID targetUUID = targetPlayer.getUUID();
            ServerBank.getBank(targetUUID).deposit(amount);
            source.sendSuccess(() -> Component.literal("Added " + amount + " money to " + targetName + "'s account!"), true);
        } else {
            // Player is offline, attempt to resolve GameProfile and UUID
            GameProfile targetProfile = server.getProfileCache().get(targetName).orElse(null);
            if (targetProfile != null) {
                UUID targetUUID = targetProfile.getId();
                ServerBank.getBank(targetUUID).deposit(amount);
                source.sendSuccess(() -> Component.literal("Added " + amount + " money to " + targetName + "'s account!"), true);
            } else {
                source.sendFailure(Component.literal("Player " + targetName + " not found."));
            }
        }

        return 1; // Command executed successfully
    }

    private static int addMoneyToSelf(CommandContext<CommandSourceStack> context) {
        int amount = IntegerArgumentType.getInteger(context, "amount");
        CommandSourceStack source = context.getSource();

        try {
            ServerPlayer player = source.getPlayerOrException();
            ServerBank.getBank(player.getUUID()).deposit(amount);
            source.sendSuccess(() -> Component.literal("Added " + amount + " money to your account!"), true);
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("You must be a player to execute this command."));
        }

        return 1; // Command executed successfully
    }

    private static int getMoney(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            ServerPlayer player = source.getPlayerOrException();
            long balance = ServerBank.getBank(player.getUUID()).getBalance();
            source.sendSuccess(() -> Component.literal("Your current balance is: " + balance), false);
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("You must be a player to execute this command."));
        }

        return 1; // Command executed successfully
    }
    // Main command logic
    /*private static int runMainCommand(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess((Supplier<Component>) Component.literal("Main command executed!"), false);
        return 1; // Return a result code
    }

    // Subcommand logic
    private static int runSubCommand(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess((Supplier<Component>) Component.literal("Subcommand executed!"), false);
        return 1; // Return a result code
    }*/

    /*
    private static int command_money(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if(player == null) {
            return 0;
        }

        MoneyBank bank = ServerBank.getBank(player.getStringUUID());
        if(bank == null) {
            return 0;
        }

        context.getSource().sendSuccess((Supplier<Component>) Component.literal("You have $"+bank.getBalance()), false);
        return 1; // Return a result code
    }

    private static int command_money_add(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if(player == null) {
            return 0;
        }

        MoneyBank bank = ServerBank.getBank(player.getStringUUID());
        if(bank == null) {
            return 0;
        }

        int amount = context.getArgument("amount", Integer.class);
        bank.deposit(amount);


        context.getSource().sendSuccess((Supplier<Component>) Component.literal("You have $"+bank.getBalance()), false);
        return 1; // Return a result code
    }*/
}
