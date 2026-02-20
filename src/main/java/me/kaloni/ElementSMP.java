package me.kaloni;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;

public class ElementSMP extends JavaPlugin implements Listener {

    public static HashMap<UUID, String> playerElements = new HashMap<>();
    public static HashMap<UUID, Long> cooldowns = new HashMap<>();
    public static HashMap<UUID, Boolean> useHotkeys = new HashMap<>();
    private final String[] elements = {"Fire", "Water", "Earth", "Air", "Ice", "Nature", "Lightning", "Shadow", "Light", "Magma", "Void", "Wind"};

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("ability").setExecutor(new AbilityHandler());
        getCommand("controls").setExecutor(new ControlToggle());
        AdminCommands admin = new AdminCommands(this);
        getCommand("elements").setExecutor(admin);
        getCommand("elements").setTabCompleter(admin);

        addRerollRecipe();

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) applyPassives(p);
        }, 0L, 100L);
    }

    private void addRerollRecipe() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§d§lElement Reroll");
        meta.setLore(Arrays.asList("§7Right-click to gamble your fate!", "§8Consumable"));
        
        // GLOW EFFECT: Add a useless enchant and hide it
        meta.addEnchant(Enchantment.INFINITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        
        item.setItemMeta(meta);
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(this, "reroll"), item);
        recipe.shape("GDG", "DND", "GDG");
        recipe.setIngredient('G', Material.GOLD_INGOT);
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('N', Material.NETHERITE_INGOT);
        Bukkit.addRecipe(recipe);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.NETHER_STAR && item.hasItemMeta() && item.getItemMeta().getDisplayName().contains("Reroll")) {
            event.setCancelled(true);
            item.setAmount(item.getAmount() - 1);
            startRerollAnimation(event.getPlayer());
        }
    }

    public void startRerollAnimation(Player p) {
        new BukkitRunnable() {
            int ticks = 0;
            final Random rand = new Random();

            @Override
            public void run() {
                if (ticks < 20) {
                    // Cyle through random elements rapidly
                    String rolling = elements[rand.nextInt(elements.length)];
                    p.sendTitle("§k?? §f" + rolling.toUpperCase() + " §k??", "§7Choosing...", 0, 5, 0);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.5f);
                    
                    // Particle Circle
                    double angle = ticks * 0.5;
                    double x = Math.cos(angle) * 1.5;
                    double z = Math.sin(angle) * 1.5;
                    p.getWorld().spawnParticle(Particle.WITCH, p.getLocation().add(x, 1, z), 1, 0, 0, 0, 0);
                } else {
                    // Final Choice
                    String finalEl = elements[rand.nextInt(elements.length)];
                    playerElements.put(p.getUniqueId(), finalEl);
                    p.sendTitle("§6§l" + finalEl.toUpperCase(), "§aElement Assigned!", 5, 40, 10);
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
                    p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation(), 100, 0.5, 1, 0.5, 0.2);
                    this.cancel();
                }
                ticks++;
            }
        }.runTaskTimer(this, 0L, 2L); // Runs every 2 ticks (10 times per second)
    }

    public static void triggerAbility(Player p, int num) {
        String e = playerElements.getOrDefault(p.getUniqueId(), "Void");
        long timeLeft = cooldowns.getOrDefault(p.getUniqueId(), 0L) - System.currentTimeMillis();

        if (timeLeft > 0) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§c§lCOOLDOWN: " + (timeLeft / 1000) + "s"));
            return;
        }

        p.getWorld().spawnParticle(Particle.FLASH, p.getLocation().add(0,1,0), 1);
        p.playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1f);
        cooldowns.put(p.getUniqueId(), System.currentTimeMillis() + 10000);
    }

    private void applyPassives(Player p) {
        String e = playerElements.get(p.getUniqueId());
        if (e != null && e.equals("Void")) p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 120, 0, true, false));
    }
}

// Admin and Handler classes (Simplified for brevity, keep your previous logic if preferred)
class AdminCommands implements CommandExecutor, TabCompleter {
    private final ElementSMP plugin;
    public AdminCommands(ElementSMP plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (s.isOp() && a.length >= 2 && a[0].equalsIgnoreCase("reroll")) {
            Player t = Bukkit.getPlayer(a[1]);
            if (t != null) plugin.startRerollAnimation(t);
        }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) { return Arrays.asList("reroll"); }
}

class AbilityHandler implements CommandExecutor {
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (s instanceof Player) ElementSMP.triggerAbility((Player) s, 1);
        return true;
    }
}

class ControlToggle implements CommandExecutor {
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (s instanceof Player) {
            UUID id = ((Player) s).getUniqueId();
            ElementSMP.useHotkeys.put(id, !ElementSMP.useHotkeys.getOrDefault(id, false));
            s.sendMessage("§eHotkeys Toggled!");
        }
        return true;
    }
}
