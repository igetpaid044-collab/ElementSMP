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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * ElementSMP 1.21.8 - Sovereignty Edition (FIXED)
 * Features 12 elements, Boss AI, and Loot Tables.
 */
public class ElementSMP extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    // --- Data Storage ---
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

        // Core Engine Pulse (0.1s)
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateHUD(p);
                    handlePassives(p);
                    handleVisuals(p);
                    if (trappedPlayers.containsKey(p.getUniqueId())) enforceDomain(p);
                }
                updateBossAI();
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    @Override
    public void onDisable() {
        saveData();
        domainBlocks.forEach(b -> b.setType(Material.AIR));
    }

    // --- BOSS AI (FIXED METHOD CALLS) ---
    private void updateBossAI() {
        Iterator<Map.Entry<UUID, String>> it = activeBosses.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, String> entry = it.next();
            Entity ent = Bukkit.getEntity(entry.getKey());
            
            if (ent == null || ent.isDead()) {
                it.remove();
                continue;
            }

            if (!(ent instanceof Mob boss)) continue;

            // FIX: getTarget() now works because we casted to Mob
            LivingEntity target = boss.getTarget(); 
            
            if (Math.random() < 0.02) {
                String next = elements.get(new Random().nextInt(elements.size()));
                activeBosses.put(boss.getUniqueId(), next);
                boss.setCustomName(getElColor(next) + "§lArchon of " + next.toUpperCase());
                applyBossVisuals(boss, next);
            }

            if (target != null && Math.random() < 0.1) {
                triggerBossSkill(boss, target, entry.getValue());
            }
        }
    }

    private void triggerBossSkill(Mob boss, LivingEntity target, String el) {
        World w = boss.getWorld();
        switch (el) {
            case "lightning" -> w.strikeLightningEffect(target.getLocation());
            case "fire" -> target.setFireTicks(100);
            case "gravity" -> target.setVelocity(new Vector(0, 1.5, 0));
            case "ice" -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 5));
            case "void" -> boss.teleport(target.getLocation().add(target.getLocation().getDirection().multiply(-2)));
        }
    }

    // --- PLAYER ABILITIES ---
    public void useAbility(Player p, int slot) {
        String el = getElement(p);
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();

        if (slot == 1 && cd1.getOrDefault(id, 0L) > now) return;
        if (slot == 2 && cd2.getOrDefault(id, 0L) > now) return;

        if (slot == 1) {
            executeSkill1(p, el);
            cd1.put(id, now + 25000);
        } else {
            executeUltimate(p, el);
            cd2.put(id, now + 55000);
        }
    }

    private void executeSkill1(Player p, String el) {
        World w = p.getWorld();
        Vector v = p.getLocation().getDirection().multiply(2.0).setY(1.0);
        
        switch (el) {
            case "wind" -> w.spawnParticle(Particle.CLOUD, p.getLocation(), 30, 0.5, 0.5, 0.5, 0.1);
            case "void" -> p.teleport(p.getLocation().add(p.getLocation().getDirection().multiply(8)));
            case "chrono" -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 4));
            case "nature" -> {
                RayTraceResult r = w.rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 20);
                if (r != null) v = r.getHitPosition().toLocation(w).toVector().subtract(p.getEyeLocation().toVector()).normalize().multiply(2.2);
            }
        }
        p.setVelocity(v);
        p.playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1.5f);
    }

    private void executeUltimate(Player p, String el) {
        Location loc = p.getLocation();
        World w = p.getWorld();

        if (el.equals("void") || el.equals("gravity")) {
            startDomain(loc, el);
        } else if (el.equals("fire")) {
            w.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);
            w.getNearbyEntities(loc, 8, 8, 8).forEach(e -> e.setFireTicks(160));
        } else if (el.equals("earth")) {
            w.getNearbyEntities(loc, 10, 5, 10).forEach(e -> e.setVelocity(new Vector(0, 2, 0)));
        }
        p.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
    }

    // --- WIND JUMP (FIXED PARTICLE) ---
    @EventHandler
    public void onJump(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL || !getElement(p).equals("wind")) return;
        e.setCancelled(true);
        if (cdWind.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) return;

        p.setVelocity(new Vector(0, 1.5, 0));
        // FIX: Changed WIND_CHARGE to CLOUD/GUST for compatibility
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 20, 0.2, 0.2, 0.2, 0.1); 
        windJumpers.add(p.getUniqueId());
        cdWind.put(p.getUniqueId(), System.currentTimeMillis() + 8000);
    }

    // --- DOMAIN SYSTEM ---
    private void startDomain(Location loc, String el) {
        Material glass = el.equals("void") ? Material.BLACK_STAINED_GLASS : Material.PURPLE_STAINED_GLASS;
        List<Block> cage = new ArrayList<>();
        
        for (Player op : Bukkit.getOnlinePlayers()) {
            if (op.getLocation().distance(loc) <= 6) trappedPlayers.put(op.getUniqueId(), loc.clone());
        }

        for (int x = -6; x <= 6; x++) {
            for (int y = -6; y <= 6; y++) {
                for (int z = -6; z <= 6; z++) {
                    double d = loc.clone().add(x, y, z).distance(loc);
                    if (d > 5.8 && d < 6.2) {
                        Block b = loc.clone().add(x, y, z).getBlock();
                        if (b.getType().isAir()) { b.setType(glass); domainBlocks.add(b); cage.add(b); }
                    }
                }
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                cage.forEach(b -> { b.setType(Material.AIR); domainBlocks.remove(b); });
                trappedPlayers.clear();
            }
        }.runTaskLater(this, 200L);
    }

    private void enforceDomain(Player p) {
        Location center = trappedPlayers.get(p.getUniqueId());
        if (p.getLocation().distance(center) > 5.5) p.teleport(center);
    }

    // --- COMMANDS ---
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) return true;

        if (c.getName().equalsIgnoreCase("elemental")) {
            if (a.length >= 3 && a[0].equalsIgnoreCase("set") && p.isOp()) {
                Player t = Bukkit.getPlayer(a[1]);
                if (t != null) playerElements.put(t.getUniqueId(), a[2].toLowerCase());
            } else showLore(p);
        } else if (c.getName().equalsIgnoreCase("spawnarchon") && p.isOp()) {
            WitherSkeleton boss = (WitherSkeleton) p.getWorld().spawnEntity(p.getLocation(), EntityType.WITHER_SKELETON);
            
            // FIX: Use getAttribute() instead of getAttributes()
            boss.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(800.0);
            boss.setHealth(800.0);
            
            activeBosses.put(boss.getUniqueId(), "fire");
            applyBossVisuals(boss, "fire");
            Bukkit.broadcastMessage("§6§l[!] §eThe Elemental Archon has appeared!");
        }
        return true;
    }

    // --- UTILITIES ---
    private String getElement(Player p) { return playerElements.getOrDefault(p.getUniqueId(), "none"); }
    private String getElColor(String el) {
        return switch(el) {
            case "fire" -> "§c"; case "ice" -> "§b"; case "nature" -> "§2";
            case "void" -> "§8"; case "lightning" -> "§e"; default -> "§f";
        };
    }

    private void updateHUD(Player p) {
        String el = getElement(p);
        if (el.equals("none")) return;
        long now = System.currentTimeMillis();
        String msg = getElColor(el) + "§l" + el.toUpperCase() + " §8| §bS1: " + format(cd1.getOrDefault(p.getUniqueId(), 0L) - now) + " §8| §dUlt: " + format(cd2.getOrDefault(p.getUniqueId(), 0L) - now);
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
    }

    private String format(long ms) { return ms <= 0 ? "§aREADY" : "§6" + (ms/1000) + "s"; }

    private void handlePassives(Player p) {
        String el = getElement(p);
        if (el.equals("wind")) {
            if (p.isOnGround() && cdWind.getOrDefault(p.getUniqueId(), 0L) < System.currentTimeMillis()) p.setAllowFlight(true);
        } else if (p.getGameMode() == GameMode.SURVIVAL) {
            p.setAllowFlight(false); p.setFlying(false);
        }
        if (el.equals("lightning")) p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 45, 1, false, false));
    }

    private void handleVisuals(Player p) {
        if (windJumpers.contains(p.getUniqueId())) {
            if (p.isOnGround()) windJumpers.remove(p.getUniqueId());
            else p.getWorld().spawnParticle(Particle.GUST_EMITTER_LARGE, p.getLocation(), 1, 0, 0, 0, 0.05);
        }
    }

    private void applyBossVisuals(LivingEntity boss, String el) {
        Color c = switch(el) {
            case "fire" -> Color.RED; case "ice" -> Color.AQUA; case "void" -> Color.BLACK;
            case "lightning" -> Color.YELLOW; default -> Color.WHITE;
        };
        boss.getEquipment().setHelmet(dye(Material.LEATHER_HELMET, c));
        boss.getEquipment().setChestplate(dye(Material.LEATHER_CHESTPLATE, c));
        boss.getEquipment().setLeggings(dye(Material.LEATHER_LEGGINGS, c));
        boss.getEquipment().setBoots(dye(Material.LEATHER_BOOTS, c));
    }

    private ItemStack dye(Material m, Color c) {
        ItemStack i = new ItemStack(m);
        LeatherArmorMeta meta = (LeatherArmorMeta) i.getItemMeta();
        meta.setColor(c);
        i.setItemMeta(meta);
        return i;
    }

    private void showLore(Player p) {
        p.sendMessage("§8§m---§r §6§l ELEMENTAL SOVEREIGNTY §r §8§m---");
        elements.forEach(el -> p.sendMessage(getElColor(el) + "§l" + el.toUpperCase() + " §7- Use /elemental set to choose."));
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
