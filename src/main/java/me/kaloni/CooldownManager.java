package me.kaloni;

import java.util.HashMap;
import java.util.UUID;

public class CooldownManager {
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();

    public void setCooldown(UUID playerUUID, long seconds) {
        cooldowns.put(playerUUID, System.currentTimeMillis() + (seconds * 1000));
    }

    public boolean isFinished(UUID playerUUID) {
        return !cooldowns.containsKey(playerUUID) || System.currentTimeMillis() >= cooldowns.get(playerUUID);
    }
}
