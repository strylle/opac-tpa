package com.partytpa;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class PartyTpaCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("tpa")
                    .then(literal("status").executes(PartyTpaCommands::tpaStatus))
                    .then(argument("player", EntityArgument.player())
                            .executes(PartyTpaCommands::tpa)));
            dispatcher.register(literal("tpaccept").executes(PartyTpaCommands::tpaccept));
            dispatcher.register(literal("tpadeny").executes(PartyTpaCommands::tpadeny));

            // operator commands
            dispatcher.register(literal("partytpa")
                    .then(literal("cooldown")
                            .requires(source -> source.hasPermission(2))
                            .then(argument("minutes", IntegerArgumentType.integer(0))
                                    .executes(PartyTpaCommands::setCooldown)))
                    .then(literal("freeteleports")
                            .requires(source -> source.hasPermission(2))
                            .then(argument("count", IntegerArgumentType.integer(0))
                                    .executes(PartyTpaCommands::setFreeTeleports)))
                    .then(literal("combattag")
                            .requires(source -> source.hasPermission(2))
                            .then(argument("seconds", IntegerArgumentType.integer(0))
                                    .executes(PartyTpaCommands::setCombatTag))
                            .then(literal("partyexempt")
                                    .then(argument("enabled", BoolArgumentType.bool())
                                            .executes(PartyTpaCommands::setCombatTagPartyExempt)))
                            .then(literal("notify")
                                    .then(argument("enabled", BoolArgumentType.bool())
                                            .executes(PartyTpaCommands::setCombatTagNotify))))
                    .then(literal("reset")
                            .requires(source -> source.hasPermission(2))
                            .then(literal("cooldown")
                                    .then(literal("all").executes(PartyTpaCommands::resetAllCooldowns))
                                    .then(argument("player", EntityArgument.player())
                                            .executes(PartyTpaCommands::resetCooldown)))
                            .then(literal("teleports")
                                    .then(literal("all").executes(PartyTpaCommands::resetAllTeleports))
                                    .then(argument("player", EntityArgument.player())
                                            .executes(PartyTpaCommands::resetTeleports))))
                    .then(literal("status")
                            .requires(source -> source.hasPermission(2))
                            .then(argument("player", EntityArgument.player())
                                    .executes(PartyTpaCommands::showPlayerStatus))));
        });
    }

    private static int setCooldown(CommandContext<CommandSourceStack> ctx) {
        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
        PartyTpaMod.MANAGER.setCooldownMinutes(minutes);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "TPA cooldown (after free teleports run out) is now " + minutes + " minute(s).")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setFreeTeleports(CommandContext<CommandSourceStack> ctx) {
        int count = IntegerArgumentType.getInteger(ctx, "count");
        PartyTpaMod.MANAGER.setFreeTeleports(count);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Free lifetime TPA teleports is now " + count + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setCombatTag(CommandContext<CommandSourceStack> ctx) {
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        PartyTpaMod.MANAGER.setCombatTagSeconds(seconds);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Combat tag is now " + seconds + " second(s). Players can't tpa-out while tagged.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setCombatTagPartyExempt(CommandContext<CommandSourceStack> ctx) {
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
        PartyTpaMod.MANAGER.setCombatTagPartyExempt(enabled);
        ctx.getSource().sendSuccess(() -> Component.literal(enabled
                ? "Hits from your own party no longer count as combat tag."
                : "Hits from your own party now count as combat tag, same as anyone else.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setCombatTagNotify(CommandContext<CommandSourceStack> ctx) {
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
        PartyTpaMod.MANAGER.setCombatTagNotifyEnabled(enabled);
        ctx.getSource().sendSuccess(() -> Component.literal(enabled
                ? "Players will now be told when they enter/leave combat tag."
                : "Players will no longer be told when they enter/leave combat tag.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int resetCooldown(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        PartyTpaMod.MANAGER.resetCooldown(target.getUUID());
        String targetName = target.getGameProfile().getName();
        ctx.getSource().sendSuccess(() -> Component.literal(
                targetName + "'s TPA cooldown has been reset. (Free-teleport count untouched.)")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int resetAllCooldowns(CommandContext<CommandSourceStack> ctx) {
        PartyTpaMod.MANAGER.resetAllCooldowns();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Everyone's TPA cooldown has been reset. (Free-teleport counts untouched.)")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int resetTeleports(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        PartyTpaMod.MANAGER.resetFreeTeleports(target.getUUID());
        String targetName = target.getGameProfile().getName();
        ctx.getSource().sendSuccess(() -> Component.literal(
                targetName + "'s free-teleport count has been reset. (Cooldown timer untouched.)")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int resetAllTeleports(CommandContext<CommandSourceStack> ctx) {
        PartyTpaMod.MANAGER.resetAllFreeTeleports();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Everyone's free-teleport count has been reset. (Cooldown timers untouched.)")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static String buildPlayerStatusText(UUID uuid) {
        int used = PartyTpaMod.MANAGER.getFreeTeleportsUsed(uuid);
        int total = PartyTpaMod.MANAGER.getFreeTeleports();

        StringBuilder text = new StringBuilder();
        text.append("Free teleports used: ").append(used).append("/").append(total).append(". ");

        long cooldownRemaining = PartyTpaMod.MANAGER.getRemainingCooldownMillis(uuid);
        if (cooldownRemaining > 0) {
            long minutes = (cooldownRemaining / 60_000L) + 1;
            text.append("Cooldown: ").append(minutes).append(" minute(s) remaining. ");
        } else {
            text.append("Cooldown: ready. ");
        }

        long combatRemaining = PartyTpaMod.MANAGER.getRemainingCombatMillis(uuid);
        if (combatRemaining > 0) {
            long seconds = (combatRemaining / 1000L) + 1;
            text.append("Combat tag: ").append(seconds).append(" second(s) remaining.");
        } else {
            text.append("Combat tag: clear.");
        }

        return text.toString();
    }

    private static int tpaStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String status = buildPlayerStatusText(player.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal(status).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int showPlayerStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String targetName = target.getGameProfile().getName();
        String status = buildPlayerStatusText(target.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal(targetName + " - " + status)
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int tpa(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer requester = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        MinecraftServer server = ctx.getSource().getServer();

        if (requester == target) {
            ctx.getSource().sendFailure(Component.literal("You can't TPA to yourself."));
            return 0;
        }

        if (!PartyTpaMod.MANAGER.isSameParty(server, requester.getUUID(), target.getUUID())) {
            ctx.getSource().sendFailure(Component.literal(
                    "You can only TPA to players in your own party."));
            return 0;
        }

        long remaining = PartyTpaMod.MANAGER.getRemainingCooldownMillis(requester.getUUID());
        if (remaining > 0) {
            long minutes = (remaining / 60_000L) + 1;
            ctx.getSource().sendFailure(Component.literal(
                    "You're out of free teleports for now. Try again in about " + minutes + " more minute(s)."));
            return 0;
        }

        long combatRemaining = PartyTpaMod.MANAGER.getRemainingCombatMillis(requester.getUUID());
        if (combatRemaining > 0) {
            long seconds = (combatRemaining / 1000L) + 1;
            ctx.getSource().sendFailure(Component.literal(
                    "You're in combat and can't send a teleport request for " + seconds + " more second(s)."));
            return 0;
        }

        PartyTpaMod.MANAGER.addRequest(target.getUUID(), requester.getUUID());

        String requesterName = requester.getGameProfile().getName();
        String targetName = target.getGameProfile().getName();

        ctx.getSource().sendSuccess(() -> Component.literal(
                "Teleport request sent to " + targetName + ". It expires in 60 seconds.")
                .withStyle(ChatFormatting.GREEN), false);

        target.sendSystemMessage(Component.literal(requesterName + " wants to teleport to you. ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal("[Accept]").withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept"))))
                .append(Component.literal("  ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("[Deny]").withStyle(style -> style
                        .withColor(ChatFormatting.RED)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpadeny")))));

        return 1;
    }

    private static int tpaccept(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer accepter = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();

        PartyTpaManager.PendingRequest pending = PartyTpaMod.MANAGER.takeRequest(accepter.getUUID());
        if (pending == null || pending.expiresAtMillis <= System.currentTimeMillis()) {
            ctx.getSource().sendFailure(Component.literal("You don't have a pending teleport request."));
            return 0;
        }

        ServerPlayer requester = server.getPlayerList().getPlayer(pending.requesterUuid);
        if (requester == null) {
            ctx.getSource().sendFailure(Component.literal("That player is no longer online."));
            return 0;
        }

        if (!PartyTpaMod.MANAGER.isSameParty(server, requester.getUUID(), accepter.getUUID())) {
            ctx.getSource().sendFailure(Component.literal(
                    "You're no longer in the same party as that player."));
            return 0;
        }

        long combatRemaining = PartyTpaMod.MANAGER.getRemainingCombatMillis(requester.getUUID());
        if (combatRemaining > 0) {
            long seconds = (combatRemaining / 1000L) + 1;
            String requesterName = requester.getGameProfile().getName();
            ctx.getSource().sendFailure(Component.literal(
                    requesterName + " is in combat and can't be teleported for " + seconds + " more second(s)."));
            requester.sendSystemMessage(Component.literal(
                    "You're in combat, so your teleport request can't be accepted right now.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        PartyTpaMod.MANAGER.teleportToTarget(requester, accepter);

        String accepterName = accepter.getGameProfile().getName();
        String requesterName = requester.getGameProfile().getName();

        requester.sendSystemMessage(Component.literal(
                accepterName + " accepted your teleport request.").withStyle(ChatFormatting.GREEN));
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Teleported " + requesterName + " to you.").withStyle(ChatFormatting.GREEN), false);

        return 1;
    }

    private static int tpadeny(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();

        PartyTpaManager.PendingRequest pending = PartyTpaMod.MANAGER.takeRequest(player.getUUID());
        if (pending == null) {
            ctx.getSource().sendFailure(Component.literal("You don't have a pending teleport request."));
            return 0;
        }

        ServerPlayer requester = server.getPlayerList().getPlayer(pending.requesterUuid);
        if (requester != null) {
            String playerName = player.getGameProfile().getName();
            requester.sendSystemMessage(Component.literal(
                    playerName + " denied your teleport request.").withStyle(ChatFormatting.RED));
        }

        ctx.getSource().sendSuccess(() -> Component.literal("Denied the teleport request.")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }
}
