package me.kaloni;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

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
        
        // Register the Crafting Recipe
        registerRerollRecipe();
    }

    private void registerRerollRecipe() {
        ItemStack reroller = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = reroller.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b§lElement Reroller");
            meta.setLore(Arrays.asList("§7Right-click to gamble your powers!", "§8Cost: 4 Netherite, 4 Skulls, 1 Star"));
            reroller.setItemMeta(meta);
        }

        // Create the recipe key
        NamespacedKey key = new NamespacedKey(this, "element_reroller");
        ShapedRecipe recipe = new ShapedRecipe(key, reroller);

        // N = Netherite, S = Skeleton Skull, W = Wither Star
        recipe.shape("NSN", "SWS", "NSN");
        recipe.setIngredient('N', Material.NETHERITE_INGOT);
        recipe.setIngredient('S', Material.SKELETON_SKULL);
        recipe.setIngredient('W', Material.NETHER_STAR);

        Bukkit.addRecipe(recipe);
    }

    @EventHandler
    public void onRerollUse(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();

        // Check if they are right-clicking with the Reroller
        if (item != null && item.getType() == Material.NETHER_STAR && item.hasItemMeta()) {
            if (item.getItemMeta().getDisplayName().equals("§b§lElement Reroller")) {
                if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    event.setCancelled(true);
                    item.setAmount(item.getAmount() - 1); // Consume the item
                    startCoolRerollAnimation(p);
                }
            }
        }
    }

    private void startCoolRerollAnimation(Player p) {
        new BukkitRunnable() {
            int ticks = 0;
            final Random random = new Random();

            @Override
            public void run() {
                if (ticks >= 30) {
                    String finalEl = elements[random.nextInt(elements.length)];
                    playerElements.put(p.getUniqueId(), finalEl);
                    p
