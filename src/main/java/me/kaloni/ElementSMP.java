package me.kaloni;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import java.util.*;

public class ElementSMP extends JavaPlugin implements Listener {

    public static HashMap<UUID, String> playerElements = new HashMap<>();
    public static HashMap<UUID, String> secondaryElements = new HashMap<>();
    public static HashMap<UUID, Boolean> useHotkeys = new HashMap<>();
    public static HashMap<UUID, Long> cdAbility1 = new HashMap<>();
    public static HashMap<UUID, Long> cdAbility2 = new HashMap<>();
    public static HashMap<UUID, Long> cdDoubleJump = new HashMap<>();
    
    private final String[] elementList = {
        "Wind", "Fire", "Water", "Earth", "Lightning", "Void", 
        "Ice", "Nature", "Blood", "Ocean", "Psychic", "Gravity"
    };

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("ability").setExecutor(new AbilityHandler());
        getCommand("controls").setExecutor(new ControlToggle());
        getCommand("give-reroll").setExecutor(new RerollItemCommand());
        getCommand("give-chaos").setExecutor(new ChaosItemCommand());
        getCommand("elementsmp").setExecutor(new AdminElementHandler(this));

        registerRerollRecipe();

        new BukkitRunnable() {
            double angle = 0;
            @Override
            public void run() {
                angle += 0.15;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateActionBarHUD(p);
                    spawnElementalHalo(p, angle);
                    applyPassives(p, playerElements.getOrDefault(p.getUniqueId(), "Wind"));
                    if (secondaryElements.containsKey(p.getUniqueId())) {
                        applyPassives(p, secondaryElements.get(p.getUniqueId()));
                    }
                }
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    private void registerRerollRecipe() {
        ItemStack reroll = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = reroll.getItemMeta();
        meta.setDisplayName("§b§lElemental Reroll");
        reroll.setItemMeta(meta);
        NamespacedKey key = new NamespacedKey(this, "elemental_reroll");
        ShapedRecipe recipe = new ShapedRecipe(key, reroll);
        recipe.shape("NBN", "NSN", "NNN");
        recipe.setIngredient('N', Material.NETHERITE_INGOT);
        recipe.setIngredient('S', Material.WITHER_SKELETON_SKULL);
        recipe.setIngredient('B', Material.BOOK);
        Bukkit.addRecipe(recipe);
    }

    private void applyPassives(Player p, String element) {
        String el = element.toLowerCase();
        if (el.equalsIgnoreCase("psychic")) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, 45, 0, false, false));
            ItemStack item = p.getInventory().getItemInMainHand();
            if (item != null && item.getType() != Material.AIR) {
                if (item.getEnchantmentLevel(Enchantment.LOOTING) < 5) item.addUnsafeEnchantment(Enchantment.LOOTING, 5);
            }
        }
        switch (el) {
            case "void" -> p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 45, 0, false, false));
            case "fire" -> p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 45, 0, false, false));
            case "water" -> p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 45, 0, false, false));
            case "earth" -> p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 45, 1, false, false));
            case "lightning" -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 45, 1, false, false));
            case "ice" -> p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 45, 0, false, false));
            case "nature" -> p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 45, 0, false, false));
            case "blood" -> p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 45, 0, false, false));
            case "ocean" -> p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 45, 0, false, false));
            case "gravity" -> p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 45, 0, false, false));
        }
    }

    public void startRerollAnimation(Player p, boolean forceChaos) {
        new BukkitRunnable() {
            int ticks = 0;
            final Random rand = new Random();
            @Override
            public void run() {
                if (ticks >= 50) {
                    secondaryElements.remove(p.getUniqueId());
                    String finalEl = elementList[rand.nextInt(elementList.length)];
                    playerElements.put(p.getUniqueId(), finalEl);
                    
                    if (forceChaos || rand.nextDouble() <= 0.001) { 
                        String secondEl = elementList[rand.nextInt(elementList.length)];
                        secondaryElements.put(p.getUniqueId(), secondEl);
                        p.sendTitle("§5§lCHAOS AWAKENED", "§dDual Elements: " + finalEl + " & " + secondEl, 10, 100, 20);
                        p.getWorld().strikeLightningEffect(p.getLocation());
                        Bukkit.broadcastMessage("§8[§bElements§8] §5§lCHAOS!! §d" + p.getName() + " has awakened Dual Elements: §f" + finalEl + " §d& §f" + secondEl);
                        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 0.5f);
                    } else {
                        p.sendTitle(getElementColor(finalEl) + "§l" + finalEl.toUpperCase(), "§7Power Awakened!", 10, 70, 20);
                    }
                    this.cancel();
                    return;
                }
                String randomEl = elementList[rand.nextInt(elementList.length)];
                p.sendTitle("§k||| " + getElementColor(randomEl) + randomEl.toUpperCase() + " §r§k|||", "§fSelecting...", 0, 6, 0);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 2f);
                ticks++;
            }
        }.runTaskTimer(this, 0L, 4L); 
    }

    private void updateActionBarHUD(Player p) {
        long now = System.currentTimeMillis();
        String el = playerElements.getOrDefault(p.getUniqueId(), "Wind");
        String display = getElementColor(el) + el.toUpperCase();
        if (secondaryElements.containsKey(p.getUniqueId())) {
            String el2 = secondaryElements.get(p.getUniqueId());
            display = "§5§k!§r " + display + " §7& " + getElementColor(el2) + el2.toUpperCase() + " §5§k!";
        }
        long cd1 = cdAbility1.getOrDefault(p.getUniqueId(), 0L) - now;
        long cd2 = cdAbility2.getOrDefault(p.getUniqueId(), 0L) - now;
        String status = (cd1 > 0 ? "§6A1: " + (cd1/1000) + "s " : "§aA1 ") + (cd2 > 0 ? "§eA2: " + (cd2/1000) + "s" : "§aA2");
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§b§lElementSMP §8| " + display + " §8| " + status));
    }

    public String getElementColor(String el) {
        return switch (el.toLowerCase()) {
            case "fire" -> "§c"; case "water" -> "§3"; case "earth" -> "§2";
            case "lightning" -> "§e"; case "void" -> "§5"; case "ice" -> "§b";
            case "nature" -> "§a"; case "blood" -> "§4"; case "ocean" -> "§9";
            case "psychic" -> "§d"; case "gravity" -> "§8"; default -> "§f";
        };
    }

    private void spawnElementalHalo(Player p, double angle) {
        String el = (secondaryElements.containsKey(p.getUniqueId()) && System.currentTimeMillis() % 1000 < 500) 
                    ? secondaryElements.get(p.getUniqueId()) : playerElements.getOrDefault(p.getUniqueId(), "Wind");
        Location loc = p.getLocation().add(0, 2.2, 0);
        double x = Math.cos(angle) * 0.6;
        double z = Math.sin(angle) * 0.6;
        loc.add(x, 0, z);
        p.getWorld().spawnParticle(getParticleFor(el), loc, 1, 0, 0, 0, 0.01);
    }

    private Particle getParticleFor(String el) {
        return switch (el.toLowerCase()) {
            case "fire" -> Particle.FLAME; case "water" -> Particle.BUBBLE;
            case "blood" -> Particle.DAMAGE_INDICATOR; case "nature" -> Particle.HAPPY_VILLAGER;
            case "ice" -> Particle.SNOWFLAKE; case "gravity" -> Particle.PORTAL;
            case "lightning" -> Particle.ELECTRIC_SPARK; default -> Particle.CLOUD;
        };
    }

    @EventHandler public void onFKey(PlayerSwapHandItemsEvent event) { if (useHotkeys.getOrDefault(event.getPlayer().getUniqueId(), false)) { event.setCancelled(true); triggerAbility(event.getPlayer(), event.getPlayer().isSneaking() ? 2 : 1); } }
    @EventHandler public void onJoin(PlayerJoinEvent event) { playerElements.putIfAbsent(event.getPlayer().getUniqueId(), "Wind"); }
    
    @EventHandler public void onRerollUse(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (item != null && item.getType() == Material.NETHER_STAR && item.hasItemMeta()) {
            String name = item.getItemMeta().getDisplayName();
            if (name.contains("Reroll")) {
                e.setCancelled(true); item.setAmount(item.getAmount() - 1); startRerollAnimation(e.getPlayer(), false);
            } else if (name.contains("Chaos")) {
                e.setCancelled(true); item.setAmount(item.getAmount() - 1); startRerollAnimation(e.getPlayer(), true);
            }
        }
    }

    public static void triggerAbility(Player p, int num) {
        UUID id = p.getUniqueId();
        String e = playerElements.getOrDefault(id, "Wind");
        HashMap<UUID, Long> cdMap = (num == 1) ? cdAbility1 : cdAbility2;
        if (cdMap.getOrDefault(id, 0L) > System.currentTimeMillis()) return;
        if (num == 1) { handlePrimary(p, e); cdMap.put(id, System.currentTimeMillis() + 45000); }
        else { handleSecondary(p, e); cdMap.put(id, System.currentTimeMillis() + 60000); }
    }

    private static void handlePrimary(Player p, String e) {
        for (Entity target : p.getNearbyEntities(6, 6, 6)) {
            if (target instanceof LivingEntity le && !target.equals(p)) {
                switch (e.toLowerCase()) {
                    case "blood" -> { le.damage(6.0, p); p.setHealth(Math.min(p.getHealth() + 2.0, p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue())); }
                    case "gravity" -> { le.setVelocity(new Vector(0, 1.8, 0)); }
                    default -> le.damage(5.0);
                }
            }
        }
    }

    private static void handleSecondary(Player p, String e) {
        p.setVelocity(p.getLocation().getDirection().multiply(2.2).setY(0.4));
    }
}

// --- COMMAND CLASSES ---

class AdminElementHandler implements CommandExecutor {
    private final ElementSMP plugin;
    public AdminElementHandler(ElementSMP plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] args) {
        if (!s.isOp()) return true;
        if (args.length >= 2 && args[0].equalsIgnoreCase("reroll")) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target != null) plugin.startRerollAnimation(target, false);
            return true;
        }
        return true;
    }
}

class ChaosItemCommand implements CommandExecutor {
    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] args) {
        if (!s.isOp() || !(s instanceof Player p)) return true;
        ItemStack chaos = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = chaos.getItemMeta();
        meta.setDisplayName("§5§lChaos Reroll");
        meta.setLore(List.of("§7Guarantees Dual Elements!"));
        chaos.setItemMeta(meta);
        p.getInventory().addItem(chaos);
        return true;
    }
}

class RerollItemCommand implements CommandExecutor {
    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] args) {
        if (!s.isOp() || !(s instanceof Player p)) return true;
        ItemStack reroll = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = reroll.getItemMeta(); meta.setDisplayName("§b§lElemental Reroll"); reroll.setItemMeta(meta);
        p.getInventory().addItem(reroll); return true;
    }
}

class AbilityHandler implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p) ElementSMP.triggerAbility(p, (a.length > 0 && a[0].equals("2")) ? 2 : 1); return true; } }
class ControlToggle implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p) { ElementSMP.useHotkeys.put(p.getUniqueId(), !ElementSMP.useHotkeys.getOrDefault(p.getUniqueId(), false)); p.sendMessage("§bHotkeys Toggled!"); } return true; } }
