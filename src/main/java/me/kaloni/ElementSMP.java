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
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!playerElements.containsKey(player.getUniqueId())) {
            String randomElement = elements[new Random().nextInt(elements.length)];
            playerElements.put(player.getUniqueId(), randomElement);
            
            player.sendTitle("§d§lELEMENT AWAKENED", "§fMaster of §5§l" + randomElement, 10, 80, 20);
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f);
            
            sendAbilityInfo(player, randomElement);
        }
    }

    private void sendAbilityInfo(Player p, String element) {
        p.sendMessage("§8§m-----------------------------------------");
        p.sendMessage("§d§l" + element.toUpperCase() + " ABILITIES:");
        
        switch (element) {
            case "Void":
                p.sendMessage("§5[1] Singularity: §fA devastating blast. §c(6 Hearts)");
                p.sendMessage("§5[2] Abyssal Grip: §fTrap and wither enemies. §c(3 Hearts)");
                break;
            case "Fire":
                p.sendMessage("§e[1] Fireball: §fExplosive strike. §c(4 Hearts)");
                p.sendMessage("§e[2] Inferno: §fBurn area of effect. §c(2 Hearts)");
                break;
            case "Lightning":
                p.sendMessage("§e[1] Bolt: §fQuick high-voltage strike. §c(4.5 Hearts)");
                p.sendMessage("§e[2] Discharge: §fStun nearby enemies. §c(1.5 Hearts)");
                break;
            case "Magma":
                p.sendMessage("§e[1] Lava Bomb: §fHeavy impact. §c(4 Hearts)");
                p.sendMessage("§e[2] Melt: §fSlow and burn enemies. §c(2 Hearts)");
                break;
            default:
                p.sendMessage("§e[1] Primary: §fStandard Power. §c(3 Hearts)");
                p.sendMessage("§e[2] Secondary: §fUtility Power. §c(1.5 Hearts)");
        }
        p.sendMessage("§8§m-----------------------------------------");
    }
}
