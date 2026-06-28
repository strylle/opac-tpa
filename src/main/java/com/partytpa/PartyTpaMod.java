package com.partytpa;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PartyTpaMod implements ModInitializer {

    public static final String MOD_ID = "party-tpa";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final PartyTpaManager MANAGER = new PartyTpaManager();

    private static final int EXPIRY_CHECK_INTERVAL_TICKS = 20;
    private int tickCounter = 0;

    @Override
    public void onInitialize() {
        MANAGER.load();

        PartyTpaCommands.register();

        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, damageSource, baseDamageTaken, damageTaken, blocked) -> {
            if (!(entity instanceof ServerPlayer victim)) {
                return;
            }
            if (!(damageSource.getEntity() instanceof ServerPlayer attacker) || attacker == victim) {
                return;
            }
            MANAGER.markInCombat(victim.getUUID());
            MANAGER.markInCombat(attacker.getUUID());
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter < EXPIRY_CHECK_INTERVAL_TICKS) {
                return;
            }
            tickCounter = 0;

            MANAGER.removeExpired((targetUuid, pending) -> {
                ServerPlayer target = server.getPlayerList().getPlayer(targetUuid);
                if (target != null) {
                    target.sendSystemMessage(Component.literal("A teleport request to you has expired.")
                            .withStyle(ChatFormatting.GRAY));
                }
                ServerPlayer requester = server.getPlayerList().getPlayer(pending.requesterUuid);
                if (requester != null) {
                    requester.sendSystemMessage(Component.literal("Your teleport request has expired.")
                            .withStyle(ChatFormatting.GRAY));
                }
            });
        });

        LOGGER.info("[PartyTPA] loaded.");
    }
}
