package me.kaloni;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
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
        NamespacedKey key = new NamespacedKey(this, "element_reroller");
        ShapedRecipe recipe = new ShapedRecipe(key, reroller);
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
            p.sendTitle("§d§lELEMENT AWAKENED", "§fMaster of " + getIcon(randomElement) + " §l" + randomElement, 10, 80, 20);
        }
    }

    @EventHandler
    public void onReroll(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.NETHER_STAR && item.hasItemMeta() && item.getItemMeta().getDisplayName().equals("§b§lElement Reroller")) {
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
                item.setAmount(item.getAmount() - 1);
                new BukkitRunnable() {
                    int ticks = 0;
                    public void run() {
                        if (ticks >= 20) {
                            String finalEl = elements[new Random().nextInt(elements.length)];
                            playerElements.put(p.getUniqueId(), finalEl);
                            p.sendTitle("§a§l" + finalEl.toUpperCase(), "§7New Power Unlocked!", 10, 40, 10);
                            this.cancel();
                            return;
                        }
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1, 2);
                        ticks++;
                    }
                }.runTaskTimer(this, 0, 2);
            }
        }
    }

    public static String getIcon(String element) {
        switch (element) {
            case "Fire": return "§c[!]"; case "Water": return "§b[≈]"; case "Earth": return "§2[#]";
            case "Air": return "§f[≡]"; case "Ice": return "§3[*]"; case "Nature": return "§a[v]";
            case "Lightning": return "§e[⚡]"; case "Shadow": return "§8[o]"; case "Light": return "§e[+]";
            case "Magma": return "§6[x]"; case "Void": return "§5[?] "; case "Wind": return "§7[~]";
            default: return "§f[*]";
        }
    }
}

class AbilityCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;
        String e = ElementSMP.playerElements.getOrDefault(p.getUniqueId(), "None");
        String icon = ElementSMP.getIcon(e);
        
        long timeLeft = ElementSMP.cooldowns.getOrDefault(p.getUniqueId(), 0L) - System.currentTimeMillis();
        if (timeLeft > 0) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§c§l" + icon + " COOLDOWN: " + (timeLeft / 1000) + "s"));
            return true;
        }

        if (args.length > 0 && args[0].equals("1")) {
            // This is the part that now correctly identifies the ability used
            String abilityName = e.equals("Void") ? "Singularity" : "Elemental Strike";
            p.sendMessage("§a§l" + icon + " Ability Used: §f" + abilityName);
            
            for (Entity entity : p.getNearbyEntities(10, 10, 10)) {
                if (entity instanceof LivingEntity && !entity.equals(p)) {
                    double dmg = e.equals("Void") ? 12.0 : 6.0;
                    ((LivingEntity) entity).damage(dmg, p);
                    if (e.equals("Void")) p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, entity.getLocation(), 50);
                    break;
                }
            }
            ElementSMP.cooldowns.put(p.getUniqueId(), System.currentTimeMillis() + 15000);
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§a§l" + icon + " " + abilityName.toUpperCase() + " ACTIVATED!"));
        }
        return true;
    }
}

class PowerCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        Inventory inv = Bukkit.createInventory(null, 18, "§8Element Info");
        String current = ElementSMP.playerElements.getOrDefault(player.getUniqueId(), "None");
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName("§eCurrent: §l" + current);
        meta.setLore(Arrays.asList("§7Icon: " + ElementSMP.getIcon(current), "§7Use /ability 1 to attack."));
        info.setItemMeta(meta);
        inv.setItem(4, info);
        player.openInventory(inv);
        return true;
    }
}
