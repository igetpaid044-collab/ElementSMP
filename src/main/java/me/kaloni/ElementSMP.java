package me.kaloni;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

public class ElementSMP extends JavaPlugin implements Listener {

    public static HashMap<UUID, String> playerElements = new HashMap<>();
    private final String[] elements = {"Fire", "Water", "Earth", "Air", "Ice", "Nature", "Lightning", "Shadow", "Light", "Magma", "Void", "Wind"};

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("elements") != null) getCommand("elements").setExecutor(new PowerCommand());
        if (getCommand("ability") != null) getCommand("ability").setExecutor(new AbilityCommand());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!playerElements.containsKey(player.getUniqueId())) {
            String randomElement = elements[new Random().nextInt(elements.length)];
            playerElements.put(player.getUniqueId(), randomElement);
            player.sendMessage("§6§lELEMENT §7> You have been assigned: §e" + randomElement);
        }
    }
}
