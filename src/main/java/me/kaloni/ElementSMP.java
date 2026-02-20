package me.kaloni;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
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

public class ElementSMP extends JavaPlugin implements Listener {

    public static HashMap<UUID, String> playerElements = new HashMap<>();
    private final String[] elements = {"Fire", "Water", "Earth", "Air", "Ice", "Nature", "Lightning", "Shadow", "Light", "Magma", "Void", "Wind"};

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("elements").setExecutor(new PowerCommand());
        getCommand("ability").setExecutor(new AbilityCommand());
        
        registerRerollRecipe();
    }

    private void registerRerollRecipe() {
        ItemStack reroller = getRerollItem();
        NamespacedKey key = new NamespacedKey(this, "element_reroller");
        ShapedRecipe recipe = new ShapedRecipe(key, reroller);

        // N = Netherite, W = Wither Star, S = Skeleton Skull
        recipe.shape("NSN", "SWS", "NSN");
        recipe.setIngredient('N', Material.NETHERITE_INGOT);
        recipe.setIngredient('W', Material.NETHER_STAR);
        recipe.setIngredient('S', Material.SKELETON_SKULL);

        Bukkit.addRecipe(recipe);
    }

    public ItemStack getRerollItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b§lElement Reroller");
        meta.setLore(java.util.Arrays.asList("§7Right-click to gamble your powers!", "§eRequires 1 usage."));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onReroll(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item != null && item.hasItemMeta() && item.getItemMeta().getDisplayName().equals("§b§lElement Reroller")) {
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
                item.setAmount(item.getAmount() - 1); // Consume the item
                startRerollAnimation(player);
            }
        }
    }

    private void startRerollAnimation(Player player) {
        new BukkitRunnable() {
            int count = 0;
            final Random random = new Random();

            @Override
            public void run() {
                if (count >= 20) { // After 20 "swaps", pick the final one
                    String finalElement = elements[random.nextInt(elements.length)];
                    playerElements.put(player.getUniqueId(), finalElement);
                    player.sendMessage("§a§lSUCCESS! §7Your new element is: §e§l" + finalElement);
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f
