package me.kaloni;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

public class ElementSMP extends JavaPlugin implements Listener {

    // This stores which player has which element
    public static HashMap<UUID, String> playerElements = new HashMap<>();

    private final String[] elements = {
        "Fire", "Water", "Earth", "Air", 
        "Ice", "Nature", "Lightning", "Shadow", 
        "Light", "Magma", "Void", "Wind"
    };

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("elements").setExecutor(new PowerCommand());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Only assign if they don't have one yet
        if (!playerElements.containsKey(player.getUniqueId())) {
            String randomElement = elements[new Random().nextInt(elements.length)];
            playerElements.put(player.getUniqueId(), randomElement);
            
            player.sendMessage("§6§lELEMENT §7> You are now a master of: §e" + randomElement);
        }
    }

    // --- ELEMENT ABILITIES ---

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            String element = playerElements.get(player.getUniqueId());

            if (element == null) return;

            // FIRE/MAGMA: Immune to Fire and Lava damage
            if ((element.equals("Fire") || element.equals("Magma")) && 
                (event.getCause() == EntityDamageEvent.DamageCause.FIRE || 
                 event.getCause() == EntityDamageEvent.DamageCause.LAVA)) {
                event.setCancelled(true);
            }
            
            // VOID: No Fall Damage
            if (element.equals("Void") && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        String element = playerElements.get(player.getUniqueId());

        if (element == null) return;

        // WIND: Permanent Speed 2
        if (element.equals("Wind")) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, false, false));
        }

        // WATER: Permanent Water Breathing
        if (element.equals("Water")) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 40, 0, false, false));
        }
        
        // ICE: Walk on Water (Frost Walker effect)
        if (element.equals("Ice") && player.getLocation().getBlock().getType() == Material.WATER) {
             player.addPotionEffect(new PotionEffect(Potion
