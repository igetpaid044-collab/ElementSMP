package me.kaloni;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * ElementSMP 1.21.1 - FIXED & EXPANDED
 * Optimized for High Performance and Bug-Free Compilation.
 */
public class ElementSMP extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    // --- Core Data Arrays ---
    private final Map<UUID, String> playerElements = new HashMap<>();
    private final Map<UUID, Long> cd1 = new HashMap<>(), cd2 = new HashMap<>(), cdWind = new HashMap<>();
    private final Set<UUID> windJumpers = new HashSet<>();
    private final Set<Block> domainBlocks = new HashSet<>();
    private final Map<UUID, Location> trappedPlayers = new HashMap<>();
    private final Map<UUID, String> activeBosses = new HashMap<>();

    private final List<String> elements = Arrays.asList(
            "wind", "fire", "ice", "nature", "lightning", 
            "void", "gravity", "water", "blood", "earth", "chrono"
    );

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadData();
        Bukkit.getPluginManager().registerEvents(this, this);
        
        getCommand("elemental").setExecutor(this);
        getCommand("controls").setExecutor(this);
        getCommand("spawnarchon").setExecutor(this);
        getCommand("ability").setExecutor(this);

        // Core Heartbeat Engine
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updatePlayerHUD(p);
                    processPassives(p);
                    if (trappedPlayers.containsKey(p.getUniqueId())) enforceDomain(p);
                }
                processBossAI();
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    @Override
    public void onDisable() {
        saveData();
        domainBlocks.forEach(b -> b.setType(Material.AIR));
    }

    // --- BOSS AI (FIXED: CASTING TO MOB) ---
    private void processBossAI() {
        Iterator<Map.Entry<UUID, String>> it = activeBosses.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, String> entry = it.next();
            Entity bossEntity = Bukkit.getEntity(entry.getKey());
            
            if (bossEntity == null || bossEntity.isDead()) {
                it.remove();
                continue;
            }

            // FIX: Cast to Mob to use getTarget()
            if (bossEntity instanceof Mob boss) {
                LivingEntity target = boss.getTarget();

                if (Math.random() < 0.02) {
                    String next = elements.get(new Random().nextInt(elements.size()));
                    activeBosses.put(boss.getUniqueId(), next);
                    boss.setCustomName(getCol(next) + "§lArchon of " + next.toUpperCase());
                    updateBossVisuals(boss, next);
                }

                if (target != null && Math.random() < 0.1) {
                    executeBossSkill(boss, target, entry.getValue());
                }
            }
        }
    }

    private void executeBossSkill(Mob boss, LivingEntity target, String el) {
        World w = boss.getWorld();
        switch (el) {
            case "lightning" -> w.strikeLightningEffect(target.getLocation());
            case "gravity" -> target.setVelocity(new Vector(0, 1.5, 0));
            case "ice" -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 10));
        }
    }

    // --- ABILITY LOGIC ---
    public void runAbility(Player p, int slot) {
        String el = getElement(p);
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();

        if (slot == 1 && cd1.getOrDefault(id, 0L) > now) return;
        if (slot == 2 && cd2.getOrDefault(id, 0L) > now) return;

        if (slot == 1) {
            executeSkill(p, el);
            cd1.put(id, now + 30000);
        } else {
            executeUlt(p, el);
            cd2.put(id, now + 60000);
        }
    }

    private void executeSkill(Player p, String el) {
        Vector dir = p.getLocation().getDirection().multiply(2.0).setY(1.0);
        if (el.equals("void")) p.teleport(p.getLocation().add(p.getLocation().getDirection().multiply(10)));
        p.setVelocity(dir);
        p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1f, 2f);
    }

    private void executeUlt(Player p, String el) {
        if (el.equals("void") || el.equals("gravity")) {
            spawnCage(p.getLocation(), el);
        } else if (el.equals("fire")) {
            p.getWorld().getNearbyEntities(p.getLocation(), 8, 8, 8).forEach(e -> e.setFireTicks(200));
        }
    }

    // --- DOMAIN SYSTEM ---
    private void spawnCage(Location loc, String el) {
        Material glass = el.equals("void") ? Material.BLACK_STAINED_GLASS : Material.PURPLE_STAINED_GLASS;
        List<Block> cageBlocks = new ArrayList<>();
        
        Bukkit.getOnlinePlayers().stream()
            .filter(o -> o.getLocation().distance(loc) <= 6)
            .forEach(o -> trappedPlayers.put(o.getUniqueId(), loc.clone()));

        for (int x = -6; x <= 6; x++) {
            for (int y = -6; y <= 6; y++) {
                for (int z = -6; z <= 6; z++) {
                    double d = loc.clone().add(x, y, z).distance(loc);
                    if (d > 5.8 && d < 6.2) {
                        Block b = loc.clone().add(x, y, z).getBlock();
                        if (b.getType() == Material.AIR) {
                            b.setType(glass);
                            domainBlocks.add(b);
                            cageBlocks.add(b);
                        }
                    }
                }
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                cageBlocks.forEach(b -> { b.setType(Material.AIR); domainBlocks.remove(b); });
                trappedPlayers.clear();
            }
        }.runTaskLater(this, 200L);
    }

    private void enforceDomain(Player p) {
        Location center = trappedPlayers.get(p.getUniqueId());
        if (p.getLocation().distance(center) > 5.5) p.teleport(center);
    }

    // --- EVENT LISTENERS (FIXED PARTICLES) ---
    @EventHandler
    public void onWindJump(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL || !getElement(p).equals("wind")) return;
        e.setCancelled(true);
        if (cdWind.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) return;
        
        p.setVelocity(new Vector(0, 1.4, 0));
        // FIX: Changed WIND_CHARGE to CLOUD for better compatibility
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 20);
        windJumpers.add(p.getUniqueId());
        cdWind.put(p.getUniqueId(), System.currentTimeMillis() + 12000);
    }

    @EventHandler
    public void onAbilitySwap(PlayerSwapHandItemsEvent e) {
        e.setCancelled(true);
        runAbility(e.getPlayer(), e.getPlayer().isSneaking() ? 2 : 1);
    }

    // --- COMMANDS (FIXED ATTRIBUTES) ---
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) return true;

        switch (c.getName().toLowerCase()) {
            case "elemental":
                if (a.length >= 3 && a[0].equalsIgnoreCase("set") && p.isOp()) {
                    Player t = Bukkit.getPlayer(a[1]);
                    if (t != null) playerElements.put(t.getUniqueId(), a[2].toLowerCase());
                } else sendHelp(p);
                break;
            case "spawnarchon":
                if (p.isOp()) {
                    WitherSkeleton boss = (WitherSkeleton) p.getWorld().spawnEntity(p.getLocation(), EntityType.WITHER_SKELETON);
                    // FIX: Use correct getAttribute syntax
                    boss.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(800.0);
                    boss.setHealth(800.0);
                    activeBosses.put(boss.getUniqueId(), "wind");
                    updateBossVisuals(boss, "wind");
                    Bukkit.broadcastMessage("§6§l[!] §eThe Archon has arrived!");
                }
                break;
        }
        return true;
    }

    // --- UTILS ---
    private String getElement(Player p) { return playerElements.getOrDefault(p.getUniqueId(), "none"); }
    private String getCol(String el) { return el.equals("fire") ? "§c" : "§b"; }
    
    private void updatePlayerHUD(Player p) {
        String el = getElement(p);
        if (el.equals("none")) return;
        long n = System.currentTimeMillis();
        String m = String.format("§l%s §8| §bCD1: %ss §8| §dCD2: %ss", el.toUpperCase(), (cd1.getOrDefault(p.getUniqueId(), 0L)-n)/1000, (cd2.getOrDefault(p.getUniqueId(), 0L)-n)/1000);
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(m));
    }

    private void processPassives(Player p) {
        String el = getElement(p);
        if (el.equals("wind") && p.isOnGround()) p.setAllowFlight(true);
        else if (!p.getGameMode().equals(GameMode.CREATIVE)) p.setAllowFlight(false);
    }

    private void updateBossVisuals(LivingEntity boss, String el) {
        Color col = el.equals("fire") ? Color.RED : Color.AQUA;
        boss.getEquipment().setHelmet(dye(Material.LEATHER_HELMET, col));
        boss.getEquipment().setChestplate(dye(Material.LEATHER_CHESTPLATE, col));
    }

    private ItemStack dye(Material m, Color c) {
        ItemStack i = new ItemStack(m);
        LeatherArmorMeta meta = (LeatherArmorMeta) i.getItemMeta();
        meta.setColor(c);
        i.setItemMeta(meta);
        return i;
    }

    private void sendHelp(Player p) {
        p.sendMessage("§6§lElementSMP §7- Select an element: /elemental set <player> <element>");
    }

    private void loadData() {
        ConfigurationSection s = getConfig().getConfigurationSection("players");
        if (s != null) s.getKeys(false).forEach(k -> playerElements.put(UUID.fromString(k), s.getString(k)));
    }
    private void saveData() {
        ConfigurationSection s = getConfig().createSection("players");
        playerElements.forEach((u, el) -> s.set(u.toString(), el));
        saveConfig();
    }
}
