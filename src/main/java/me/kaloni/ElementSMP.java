package me.kaloni;

import org.bukkit.*;
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
        getCommand("elements").setExecutor(new PowerCommand());
        getCommand("ability").setExecutor(new AbilityCommand());
        // ... (reroller recipe code goes here)
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // If they don't have an element yet, give them one
        if (!playerElements.containsKey(player.getUniqueId())) {
            String randomElement = elements[new Random().nextInt(elements.length)];
            playerElements.put(player.getUniqueId(), randomElement);
            
            // --- NEW: DISPLAY ON JOIN ---
            
            // 1. Send big text in the middle of the screen
            // player.sendTitle(Title, Subtitle, FadeIn, Stay, FadeOut) - Times are in Ticks (20 = 1 sec)
            player.sendTitle("§6§lELEMENT ASSIGNED", "§fYou are the master of: §e§l" + randomElement, 10, 70, 20);
            
            // 2. Play a cool level-up sound
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            
            // 3. Send the message in chat as a backup
            player.sendMessage("§8§m-------------------------------");
            player.sendMessage("§6§lElementSMP §7> Your element is: §e§l" + randomElement);
            player.sendMessage("§7Use §b/ability 1 §7to test your powers!");
            player.sendMessage("§8§m-------------------------------");
        } else {
            // If they already have one, just remind them in the subtitle
            String existing = playerElements.get(player.getUniqueId());
            player.sendTitle("§6§lWELCOME BACK", "§7Current Element: §e" + existing, 10, 40, 10);
        }
    }
}
