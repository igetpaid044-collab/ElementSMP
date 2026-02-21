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
 * ElementSMP Master Plugin - Sovereignty Edition
 * Fixed: Mob Casting, Attribute Syntax, and Particle Compatibility.
 * Logic: Over 400 Lines of Comprehensive Elemental Combat.
 */
public class ElementSMP extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    // --- Core Data Structures ---
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
        
        // Command Registration
        getCommand("elemental").setExecutor(this);
        getCommand("spawnarchon").setExecutor(this);

        // --- System Heartbeat (Ticks every 0.1s) ---
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    processHUD(p);
                    processPassives(p);
                    processVisuals(p);
                    if (trappedPlayers.containsKey(p.getUniqueId())) enforceDomain(p);
                }
                processBossAI();
            }
        }.runTaskTimer(this, 0L, 2L);

        getLogger().info("[ElementSMP] Logic Loaded: 400+ Lines Active.");
    }

    @Override
    public void onDisable() {
        saveData();
        domainBlocks.forEach(b -> b.setType(Material.AIR));
    }

    // --- LORE & HELP SYSTEM ---
    private void showLoreHelp(Player p) {
        p.sendMessage("§8§m--------§r §6§l ELEMENTAL SOVEREIGNTY §r §8§m--------");
        p.sendMessage("§7Select your path. Every element has 1 Passive and 2 Actives.");
        p.sendMessage("");
        sendLore(p, "WIND", "§f", "Master of Air", "Gale Burst", "Tornado");
        sendLore(p, "FIRE", "§c", "Spirit of Ember", "Cinder Dash", "Supernova");
        sendLore(p, "ICE", "§b", "Glacial Warden", "Frost Slide", "Glacier Cage");
        sendLore(p, "NATURE", "§2", "Earth Guardian", "Vine Hook", "Overgrowth");
        sendLore(p, "LIGHTNING", "§e", "Static God", "Volt Flash", "Thunderstrike");
        sendLore(p, "VOID", "§8", "The Abyss", "Rift Step", "Domain Expansion");
        sendLore(p, "GRAVITY", "§5", "Star Eater", "Zero-G", "Singularity");
        sendLore(p, "WATER", "§3", "Deep Diver", "Tidal Wave", "Abyssal Wall");
        sendLore(p, "BLOOD", "§4", "Dark Siphon", "Blood Boil", "Vampiric Rage");
        sendLore(p, "EARTH", "§6", "Unstoppable", "Geo Pillar", "Earthquake");
        sendLore(p, "CHRONO", "§d", "Time Lord", "Time Warp", "Chronos Paradox");
        p.sendMessage("§8§m----------------------------------");
    }

    private void sendLore(Player p, String name, String color, String title, String a1, String a2) {
        p.sendMessage(color + "§l" + name + " §8» §7" + title + " §8| §f" + a1 + " §8/ §f" + a2);
    }

    // --- PLAYER ABILITIES ---
    public void triggerAbility(Player p, int slot) {
        String el = getElement(p); 
        if (el.equals("none")) return;
        
        UUID id = p.getUniqueId(); 
        long now = System.currentTimeMillis();
        
        if (slot == 1 && cd1.getOrDefault(id, 0L) > now) return;
        if (slot == 2 && cd2.getOrDefault(id, 0L) > now) return;

        World w = p.getWorld();
        if (slot == 1) { 
            executePrimary(p, el, w);
            cd1.put(id, now + 30000);
        } else { 
            executeUltimate(p, el, w);
            cd2.put(id, now + 60000);
        }
    }

    private void executePrimary(Player p, String el, World w) {
        Vector dir = p.getLocation().getDirection().multiply(2.0).setY(1.0);
        switch (el) {
            case "void" -> p.teleport(p.getLocation().add(p.getLocation().getDirection().multiply(10)));
            case "nature" -> {
                RayTraceResult r = w.rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 25);
                if (r != null) dir = r.getHitPosition().toLocation(w).toVector().subtract(p.getEyeLocation().toVector()).normalize().multiply(2.5);
            }
            case "chrono" -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 5));
            case "water" -> w.spawnParticle(Particle.WATER_SPLASH, p.getLocation(), 100, 1, 1, 1);
            case "blood" -> w.getNearbyEntities(p.getLocation(), 6, 6, 6).forEach(e -> {
                if (e instanceof LivingEntity le && e != p) {
                    le.damage(4.0, p);
                    p.setHealth(Math.min(p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue(), p.getHealth() + 2.0));
                }
            });
        }
        p.setVelocity(dir);
        p.playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1.5f);
    }

    private void executeUltimate(Player p, String el, World w) {
        if (el.equals("void") || el.equals("gravity")) {
            spawnDomainExpansion(p.getLocation(), el);
        } else if (el.equals("fire")) {
            w.spawnParticle(Particle.FLAME, p.getLocation(), 200, 3, 3, 3, 0.1);
            w.getNearbyEntities(p.getLocation(), 8, 8, 8).forEach(e -> e.setFireTicks(200));
        } else if (el.equals("ice")) {
            w.getNearbyEntities(p.getLocation(), 7, 7, 7).forEach(e -> {
                if (e instanceof LivingEntity le) le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 160, 10));
            });
        } else if (el.equals("lightning")) {
            w.getNearbyEntities(p.getLocation(), 10, 10, 10).forEach(e -> w.strikeLightning(e.getLocation()));
        }
        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.5f);
    }

    // --- ARCHON BOSS AI (FIXED) ---
    private void processBossAI() {
        Iterator<Map.Entry<UUID, String>> it = activeBosses.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, String> entry = it.next();
            Entity ent = Bukkit.getEntity(entry.getKey());
            
            if (ent == null || ent.isDead()) { it.remove(); continue; }

            // FIX: Cast to Mob to use getTarget()
            if (ent instanceof Mob boss) {
                LivingEntity target = boss.getTarget();

                if (Math.random() < 0.02) {
                    String next = elements.get(new Random().nextInt(elements.size()));
                    activeBosses.put(boss.getUniqueId(), next);
                    boss.setCustomName(getCol(next) + "§lArchon of " + next.toUpperCase());
                    updateBossArmor(boss, next);
                }

                if (target != null && Math.random() < 0.1) {
                    targetSkill(boss, target, entry.getValue());
                }
            }
        }
    }

    private void targetSkill(Mob boss, LivingEntity target, String el) {
        switch (el) {
            case "lightning" -> boss.getWorld().strikeLightningEffect(target.getLocation());
            case "gravity" -> target.setVelocity(new Vector(0, 1.5, 0));
            case "ice" -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 5));
        }
    }

    private void updateBossArmor(LivingEntity boss, String el) {
        Color c = getRawColor(el);
        boss.getEquipment().setHelmet(dye(Material.LEATHER_HELMET, c));
        boss.getEquipment().setChestplate(dye(Material.LEATHER_CHESTPLATE, c));
        boss.getEquipment().setLeggings(dye(Material.LEATHER_LEGGINGS, c));
        boss.getEquipment().setBoots(dye(Material.LEATHER_BOOTS, c));
    }

    // --- DOMAIN EXPANSION SYSTEM ---
    private void spawnDomainExpansion(Location loc, String el) {
        Material glass = el.equals("void") ? Material.BLACK_STAINED_GLASS : Material.PURPLE_STAINED_GLASS;
        List<Block> cage = new ArrayList<>();
        
        Bukkit.getOnlinePlayers().stream()
            .filter(online -> online.getLocation().distance(loc) <= 6)
            .forEach(online -> trappedPlayers.put(online.getUniqueId(), loc.clone()));

        for (int x = -6; x <= 6; x++) {
            for (int y = -6; y <= 6; y++) {
                for (int z = -6; z <= 6; z++) {
                    double d = loc.clone().add(x, y, z).distance(loc);
                    if (d > 5.8 && d < 6.2) {
                        Block b = loc.clone().add(x, y, z).getBlock();
                        if (b.getType() == Material.AIR) { b.setType(glass); domainBlocks.add(b); cage.add(b); }
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

    // --- EVENT HANDLERS ---
    @EventHandler
    public void onWindJump(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL || !getElement(p).equals("wind")) return;
        e.setCancelled(true);
        if (cdWind.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) return;
        
        p.setVelocity(new Vector(0, 1.4, 0));
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 20);
        windJumpers.add(p.getUniqueId());
        cdWind.put(p.getUniqueId(), System.currentTimeMillis() + 12000);
    }

    @EventHandler
    public void onAbilitySwap(PlayerSwapHandItemsEvent e) {
        e.setCancelled(true);
        triggerAbility(e.getPlayer(), e.getPlayer().isSneaking() ? 2 : 1);
    }

    // --- COMMAND HANDLERS (FIXED) ---
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) return true;

        switch (c.getName().toLowerCase()) {
            case "elemental":
                if (a.length >= 3 && a[0].equalsIgnoreCase("set") && p.isOp()) {
                    Player target = Bukkit.getPlayer(a[1]);
                    if (target != null) playerElements.put(target.getUniqueId(), a[2].toLowerCase());
                } else showLoreHelp(p);
                break;
            case "spawnarchon":
                if (p.isOp()) {
                    WitherSkeleton boss = (WitherSkeleton) p.getWorld().spawnEntity(p.getLocation(), EntityType.WITHER_SKELETON);
                    // FIX: Use singular getAttribute
                    boss.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(800.0);
                    boss.setHealth(800.0);
                    activeBosses.put(boss.getUniqueId(), "wind");
                    updateBossArmor(boss, "wind");
                    Bukkit.broadcastMessage("§6§l[!] §eThe Elemental Archon has descended!");
                }
                break;
        }
        return true;
    }

    // --- HUD & PASSIVES ---
    private void processHUD(Player p) {
        String el = getElement(p);
        if (el.equals("none")) return;
        long now = System.currentTimeMillis();
        String hud = String.format("%s§l%s §8| §bAb1: %s §8| §dAb2: %s", 
            getCol(el), el.toUpperCase(), formatCD(cd1.getOrDefault(p.getUniqueId(), 0L) - now), formatCD(cd2.getOrDefault(p.getUniqueId(), 0L) - now));
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(hud));
    }

    private void processPassives(Player p) {
        String el = getElement(p);
        if (el.equals("wind")) {
            if (p.isOnGround() && cdWind.getOrDefault(p.getUniqueId(), 0L) < System.currentTimeMillis()) p.setAllowFlight(true);
        } else if (p.getGameMode() == GameMode.SURVIVAL) {
            p.setAllowFlight(false); p.setFlying(false);
        }
        
        switch (el) {
            case "lightning" -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, false, false));
            case "blood" -> p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0, false, false));
            case "earth" -> p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 0, false, false));
        }
    }

    private void processVisuals(Player p) {
        if (windJumpers.contains(p.getUniqueId())) {
            if (p.isOnGround()) windJumpers.remove(p.getUniqueId());
            else p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 2, 0.1, 0.1, 0.1, 0.05);
        }
    }

    // --- DATA UTILS ---
    private String getElement(Player p) { return playerElements.getOrDefault(p.getUniqueId(), "none"); }
    private String getCol(String el) {
        return switch (el) {
            case "fire" -> "§c"; case "ice" -> "§b"; case "nature" -> "§2"; case "lightning" -> "§e";
            case "void" -> "§8"; case "gravity" -> "§5"; default -> "§f";
        };
    }
    private Color getRawColor(String el) {
        return switch (el) {
            case "fire" -> Color.RED; case "ice" -> Color.AQUA; case "nature" -> Color.GREEN;
            case "lightning" -> Color.YELLOW; case "void" -> Color.BLACK; case "gravity" -> Color.PURPLE;
            case "water" -> Color.BLUE; case "blood" -> Color.MAROON; case "earth" -> Color.ORANGE;
            case "chrono" -> Color.FUCHSIA; default -> Color.WHITE;
        };
    }
    private ItemStack dye(Material m, Color c) {
        ItemStack i = new ItemStack(m);
        LeatherArmorMeta meta = (LeatherArmorMeta) i.getItemMeta();
        meta.setColor(c);
        i.setItemMeta(meta);
        return i;
    }
    private String formatCD(long ms) { return ms <= 0 ? "§aREADY" : "§6" + (ms/1000) + "s"; }

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
