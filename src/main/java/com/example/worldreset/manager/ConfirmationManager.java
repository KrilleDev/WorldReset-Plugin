package com.example.worldreset.manager;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ConfirmationManager {

    private static final long CONFIRM_TIMEOUT_MILLIS = 30_000L;

    private static final UUID CONSOLE_KEY = new UUID(0L, 0L);

    private final Map<UUID, PendingReset> pendingResets = new ConcurrentHashMap<>();

    public void addPending(CommandSender sender, String worldName) {
        pendingResets.put(keyOf(sender), new PendingReset(worldName, System.currentTimeMillis() + CONFIRM_TIMEOUT_MILLIS));
    }

    public Optional<String> consumePending(CommandSender sender) {
        PendingReset pending = pendingResets.remove(keyOf(sender));
        if (pending == null || pending.isExpired()) {
            return Optional.empty();
        }
        return Optional.of(pending.worldName());
    }

    public void clearPending(CommandSender sender) {
        pendingResets.remove(keyOf(sender));
    }

    private UUID keyOf(CommandSender sender) {
        if (sender instanceof Player player) {
            return player.getUniqueId();
        }
        return CONSOLE_KEY;
    }

    private record PendingReset(String worldName, long expireAtMillis) {
        boolean isExpired() {
            return System.currentTimeMillis() > expireAtMillis;
        }
    }
}
