package me.kaloni;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashMap;
import java.util.Random;
import java.util.UUID;
import java.util.Arrays;

public class ElementSMP extends JavaPlugin implements Listener {

    public static HashMap<UUID, String> playerElements = new HashMap<>();
    public static HashMap<UUID, Long> cooldowns = new HashMap<>();
    private final String[] elements = {"Fire", "Water", "Earth", "Air", "Ice", "Nature", "Lightning", "Shadow", "Light", "Magma", "Void", "Wind"};

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("elements").setExecutor(new PowerCommand());
        getCommand("ability").setExecutor(new AbilityCommand());
        registerRerollRecipe();
    }

    private void registerRerollRecipe() {
        ItemStack reroller = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = reroller.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b§lElement Reroller");
            meta.setLore(Arrays.asList("§7Right-click to gamble your powers!"));
            reroller.setItemMeta(meta);
        }

        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(this, "reroller"), reroller);
        recipe.shape("NSN", "SWS", "NSN");
        recipe.setIngredient('N', Material.NETHERITE_INGOT);
        recipe.setIngredient('S', Material.SKELETON_SKULL);
        recipe.setIngredient('W', Material.NETHER_STAR);
        Bukkit.addRecipe(recipe);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (!playerElements.containsKey(p.getUniqueId())) {
            String randomElement = elements[new Random().nextInt(elements.length)];
            playerElements.put(p.getUniqueId(), randomElement);
            p.sendTitle("§6§lELEMENT CHOSEN", "§fMaster of §e§l" + randomElement, 10, 80, 20);
        }
    }

    public static String getIcon(String element) {
        switch (element) {
            case "Fire": return "🔥"; case "Water": return "💧"; case "Earth": return "🌿";
            case "Air": return "💨"; case "Ice": return "❄️"; case "Nature": return "🌲";
            case "Lightning": return "⚡"; case "Shadow": return "🌑"; case "Light": return "☀️";
            case "Magma": return "🌋"; case "Void": return "🌌"; case "Wind": return "🌀";
            default: return "✨";
        }
    }
} // <--- Make sure this bracket is here!
