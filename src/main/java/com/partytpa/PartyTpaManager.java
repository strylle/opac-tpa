package com.partytpa;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.server.api.OpenPACServerAPI;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Party-gating uses Open Parties and Claims' API
 */
public class PartyTpaManager {

    private static final long DEFAULT_COOLDOWN_MILLIS = 60L * 60L * 1000L; // 1 real-world hour
    private static final int DEFAULT_FREE_TELEPORTS = 3;
    private static final long DEFAULT_COMBAT_TAG_MILLIS = 15L * 1000L; // 15 seconds
    public static final long REQUEST_TIMEOUT_MILLIS = 60L * 1000L; // 60 seconds to respond

    private static final Path DATA_FILE = FabricLoader.getInstance().getConfigDir().resolve("party-tpa.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private StoredData data = new StoredData();
    private final Map<UUID, PendingRequest> pendingRequests = new HashMap<>(); // (target must accept/deny)
    private final Map<UUID, Long> lastCombatAtMillis = new HashMap<>(); // not persisted, resets on restart

    public static class PlayerEntry {
        public int freeTeleportsUsed = 0;
        public long lastTeleportAtMillis = 0L;
    }

    public static class StoredData {
        public Map<String, PlayerEntry> players = new HashMap<>();
        public long cooldownMillis = DEFAULT_COOLDOWN_MILLIS;
        public int freeTeleports = DEFAULT_FREE_TELEPORTS;
        public long combatTagMillis = DEFAULT_COMBAT_TAG_MILLIS;
    }

    public static class PendingRequest {
        public final UUID requesterUuid;
        public final long expiresAtMillis;

        public PendingRequest(UUID requesterUuid, long expiresAtMillis) {
            this.requesterUuid = requesterUuid;
            this.expiresAtMillis = expiresAtMillis;
        }
    }

    public synchronized void load() {
        if (!Files.exists(DATA_FILE)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(DATA_FILE, StandardCharsets.UTF_8)) {
            StoredData loaded = GSON.fromJson(reader, StoredData.class);
            if (loaded != null) {
                data = loaded;
                if (data.players == null) {
                    data.players = new HashMap<>();
                }
            }
        } catch (IOException e) {
            PartyTpaMod.LOGGER.error("[PartyTPA] Failed to load party-tpa.json", e);
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(DATA_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(DATA_FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            PartyTpaMod.LOGGER.error("[PartyTPA] Failed to save party-tpa.json", e);
        }
    }

    private PlayerEntry getOrCreate(UUID uuid) {
        return data.players.computeIfAbsent(uuid.toString(), id -> new PlayerEntry());
    }

    public synchronized int getFreeTeleportsUsed(UUID uuid) {
        return getOrCreate(uuid).freeTeleportsUsed;
    }

    public synchronized boolean canTeleportNow(UUID uuid) {
        PlayerEntry entry = getOrCreate(uuid);
        if (entry.freeTeleportsUsed < data.freeTeleports) {
            return true;
        }
        return System.currentTimeMillis() - entry.lastTeleportAtMillis >= data.cooldownMillis;
    }

    public synchronized long getRemainingCooldownMillis(UUID uuid) {
        PlayerEntry entry = getOrCreate(uuid);
        if (entry.freeTeleportsUsed < data.freeTeleports) {
            return 0;
        }
        long remaining = data.cooldownMillis - (System.currentTimeMillis() - entry.lastTeleportAtMillis);
        return Math.max(0, remaining);
    }

    public synchronized void recordCompletedTeleport(UUID uuid) {
        PlayerEntry entry = getOrCreate(uuid);
        if (entry.freeTeleportsUsed < data.freeTeleports) {
            entry.freeTeleportsUsed++;
        }
        entry.lastTeleportAtMillis = System.currentTimeMillis();
        save();
    }

    // --- Config (via /partytpa) ---

    public synchronized long getCooldownMinutes() {
        return data.cooldownMillis / 60_000L;
    }

    public synchronized void setCooldownMinutes(int minutes) {
        data.cooldownMillis = minutes * 60_000L;
        save();
    }

    public synchronized int getFreeTeleports() {
        return data.freeTeleports;
    }

    public synchronized void setFreeTeleports(int count) {
        data.freeTeleports = count;
        save();
    }

    /** resets the cooldown timer for one player & does NOT touch their free tps */
    public synchronized void resetCooldown(UUID uuid) {
        PlayerEntry entry = getOrCreate(uuid);
        entry.lastTeleportAtMillis = 0L;
        save();
    }

    /** resets the cooldown timer for EVERYONE */
    public synchronized void resetAllCooldowns() {
        for (PlayerEntry entry : data.players.values()) {
            entry.lastTeleportAtMillis = 0L;
        }
        save();
    }

    /** resets the free-teleport count for one player & does NOT touch their cooldown timer */
    public synchronized void resetFreeTeleports(UUID uuid) {
        PlayerEntry entry = getOrCreate(uuid);
        entry.freeTeleportsUsed = 0;
        save();
    }

    /** resets the free-teleport count for EVERYONE */
    public synchronized void resetAllFreeTeleports() {
        for (PlayerEntry entry : data.players.values()) {
            entry.freeTeleportsUsed = 0;
        }
        save();
    }

    // --- Combat tag (in-memory; prevents tpa-ing out of a fight) ---

    public synchronized long getCombatTagSeconds() {
        return data.combatTagMillis / 1000L;
    }

    public synchronized void setCombatTagSeconds(int seconds) {
        data.combatTagMillis = seconds * 1000L;
        save();
    }

    public synchronized void markInCombat(UUID uuid) {
        lastCombatAtMillis.put(uuid, System.currentTimeMillis());
    }

    public synchronized long getRemainingCombatMillis(UUID uuid) {
        Long taggedAt = lastCombatAtMillis.get(uuid);
        if (taggedAt == null) {
            return 0;
        }
        long remaining = data.combatTagMillis - (System.currentTimeMillis() - taggedAt);
        return Math.max(0, remaining);
    }

    // --- Open Parties and Claims funcs ---

    public boolean isSameParty(MinecraftServer server, UUID a, UUID b) {
        if (a.equals(b)) {
            return false;
        }
        var api = OpenPACServerAPI.get(server);
        if (api == null) {
            return false;
        }
        var partyManager = api.getPartyManager();
        var partyOfA = partyManager.getPartyByMember(a);
        if (partyOfA == null) {
            return false;
        }
        var partyOfB = partyManager.getPartyByMember(b);
        return partyOfA == partyOfB;
    }

    public void teleportToTarget(ServerPlayer requester, ServerPlayer target) {
        ServerLevel level = target.serverLevel();
        requester.teleportTo(level, target.getX(), target.getY(), target.getZ(),
                Set.of(), target.getYRot(), target.getXRot());
        recordCompletedTeleport(requester.getUUID());
    }

    public synchronized void addRequest(UUID targetUuid, UUID requesterUuid) {
        pendingRequests.put(targetUuid, new PendingRequest(requesterUuid, System.currentTimeMillis() + REQUEST_TIMEOUT_MILLIS));
    }

    public synchronized PendingRequest takeRequest(UUID targetUuid) {
        return pendingRequests.remove(targetUuid);
    }

    public synchronized void removeExpired(BiConsumer<UUID, PendingRequest> onExpire) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, PendingRequest>> it = pendingRequests.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PendingRequest> entry = it.next();
            if (entry.getValue().expiresAtMillis <= now) {
                onExpire.accept(entry.getKey(), entry.getValue());
                it.remove();
            }
        }
    }
}
