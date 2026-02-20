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
            meta.setLore(Arrays.asList("§7Right-click to gamble your powers!"));
            reroller.setItemMeta(meta);
        }
        NamespacedKey key = new NamespacedKey(this, "reroller");
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
            p.sendTitle("§6§lELEMENT CHOSEN", "§fMaster of §e§l" + randomElement, 10, 80, 20);
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
            // FIX: This now identifies exactly which ability is used
            String abilityName = getAbilityName(e);
            p.sendMessage("§a§l" + icon + " Ability Used: §f" + abilityName);
            
            for (Entity entity : p.getNearbyEntities(10, 10, 10)) {
                if (entity instanceof LivingEntity && !entity.equals(p)) {
                    // VOID BALANCING: 6 hearts (12.0) for Void, 3 hearts (6.0) for others
                    double dmg = e.equals("Void") ? 12.0 : 6.0;
                    ((LivingEntity) entity).damage(dmg, p);
                    
                    if (e.equals("Void")) {
                        p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, entity.getLocation(), 50);
                    }
                    break;
                }
            }
            ElementSMP.cooldowns.put(p.getUniqueId(), System.currentTimeMillis() + 15000);
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§a§l" + icon + " " + abilityName.toUpperCase() + " USED!"));
        }
        return true;
    }

    private String getAbilityName(String element) {
        switch (element) {
            case "Void": return "Singularity";
            case "Fire": return "Inferno Strike";
            case "Lightning": return "Voltage Bolt";
            case "Water": return "Tidal Blast";
            default: return "Elemental Pulse";
        }
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
