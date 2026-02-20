package me.kaloni;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Random;

public class ElementSMP extends JavaPlugin implements Listener {

    // The 12 Elements
    private final String[] elements = {
        "§cFire", "§bWater", "§aEarth", "§fAir", 
        "§3Ice", "§2Nature", "§eLightning", "§8Shadow", 
        "§eLight", "§6Magma", "§5Void", "§7Wind"
    };

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("elements") != null) {
            getCommand("elements").setExecutor(new PowerCommand());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Pick a random element from the list
        String randomElement = elements[new Random().nextInt(elements.length)];
        
        // Send the message to the player
        event.getPlayer().sendMessage("§8§m-------------------------------");
        event.getPlayer().sendMessage("§6§lElementSMP §7> Welcome!");
        event.getPlayer().sendMessage("§7You have been assigned the element: " + randomElement);
        event.getPlayer().sendMessage("§8§m-------------------------------");
        
        // Note: In a full plugin, you would save this to a database or file 
        // so they don't get a new one every single time they join.
    }
}
