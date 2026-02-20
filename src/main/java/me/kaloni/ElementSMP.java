package me.kaloni;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
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
        ItemStack reroller = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = reroller.getItemMeta();
        meta.setDisplayName("§b§lElement Reroller");
        reroller.setItemMeta(meta);

        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(this, "reroller"), reroller);
        recipe.shape("NSN", "SWS", "NSN");
        recipe.setIngredient('N', Material.NETHERITE_INGOT);
        recipe.setIngredient('W', Material.NETHER_STAR);
        recipe.setIngredient('S', Material.SKELETON_SKULL);
        Bukkit.addRecipe(recipe);
    }

    @EventHandler
    public void onReroll(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (event.getItem() != null && event.getItem().getType() == Material.NETHER_STAR && event.getItem().getItemMeta().getDisplayName().equals("§b§lElement Reroller")) {
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                event.getItem().setAmount(event.getItem().getAmount() - 1);
                runCoolReroll(p);
            }
        }
    }

    private void runCoolReroll(Player p) {
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks > 30) {
                    String finalEl = elements[new Random().nextInt(elements.length)];
                    playerElements.put(p.getUniqueId(), finalEl);
                    p.sendTitle("§a§l" + finalEl.toUpperCase(), "§7Power Unlocked", 10, 40, 10);
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
                    this.cancel();
                    return;
                }
                // Particle Swirl
                double angle = ticks * 0.5;
                double x = Math.cos(angle);
                double z = Math.sin(angle);
                p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, p.getLocation().add(x, ticks*0.1, z), 5, 0, 0, 0, 0);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1, 2);
                ticks++;
            }
        }.runTaskTimer(this, 0, 1);
    }
}
