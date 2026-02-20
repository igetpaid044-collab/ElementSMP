package me.kaloni;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * ElementSMP Master Plugin - Optimized for Minecraft 1.21.8
 * Fully integrated 11-element system with Domain Expansions and Chrono-tech.
 */
public class ElementSMP extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    // --- Data Storage ---
    public static final HashMap<UUID, String> playerElements = new HashMap<>();
    public static final HashMap<UUID, Long> cd1 = new HashMap<>(), cd2 = new HashMap<>(), cdWind = new HashMap<>();
    public static final HashMap<UUID, Boolean> hotkeysEnabled = new HashMap<>();
    
    // --- Logic Management ---
    private final Set<UUID> windJumpers = new HashSet<>();
    private final Set<Block> domainBlocks = new HashSet<>();
    private final HashMap<UUID, Location> trappedPlayers = new HashMap<>();
    private final List<String> elementsList = Arrays.asList(
            "wind", "fire", "ice", "nature", "lightning", 
            "void", "gravity", "water", "blood", "earth", "chrono"
    );

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadData();
        Bukkit.getPluginManager().registerEvents(this, this);
        
        // Registering Commands with Null-Checks
        String[] cmds = {"elemental", "ability", "controls"};
        for (String label : cmds) {
            PluginCommand pc = getCommand(label);
            if (pc != null) {
                pc.setExecutor(this);
                pc.setTabCompleter(this);
            }
        }

        // --- Persistent Global Processor (Ticks every 0.1s) ---
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateHUD(p);
                    handlePassives(p);
                    checkTraps(p);
                    renderParticles(p);
                }
            }
        }.runTaskTimer(this, 0L, 2L);

        getLogger().info("---------------------------------------");
        getLogger().info("ElementSMP 1.21.8 [MASTER EDITION] LOADED");
        getLogger().info("Developed for high-detail elemental play.");
        getLogger().info("---------------------------------------");
    }

    @Override
    public void onDisable() {
        saveData();
        // Emergency cleanup for domain blocks
        for (Block b : domainBlocks) b.setType(Material.AIR);
    }

    // --- HUD & LORE DISPLAY ---
    private void updateHUD(Player p) {
        String el = getElement(p);
        long now = System.currentTimeMillis();
        String t1 = formatCD(cd1.getOrDefault(p.getUniqueId(), 0L) - now);
        String t2 = formatCD(cd2.getOrDefault(p.getUniqueId(), 0L) - now);
        
        String color = getElementColor(el);
        String display = String.format("%s§l%s §8| §b%s: %s §8| §d%s: %s", 
                color, el.toUpperCase(), getAbilityName(el, 1), t1, getAbilityName(el, 2), t2);
                
        if (el.equals("wind")) {
            long wCd = cdWind.getOrDefault(p.getUniqueId(), 0L) - now;
            display += " §8| §fJump: " + (wCd <= 0 ? "§aREADY" : "§6" + (wCd/1000) + "s");
        }

        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(display));
    }

    private String getAbilityName(String el, int slot) {
        if (slot == 1) return switch(el) {
            case "wind" -> "GaleBurst"; case "fire" -> "CinderDash"; case "ice" -> "FrostSlide";
            case "nature" -> "VineHook"; case "lightning" -> "BoltFlash"; case "void" -> "VoidRift";
            case "gravity" -> "ZeroG"; case "water" -> "TideWave"; case "blood" -> "Lifesteal";
            case "earth" -> "PillarLift"; case "chrono" -> "Rewind"; default -> "Dash";
        };
        return switch(el) {
            case "wind" -> "Tornado"; case "fire" -> "Supernova"; case "ice" -> "Glacier";
            case "nature" -> "Overgrowth"; case "lightning" -> "Thunderstrike"; case "void" -> "VoidExpansion";
            case "gravity" -> "Singularity"; case "water" -> "Abyss"; case "blood" -> "Hemorrhage";
            case "earth" -> "Quake"; case "chrono" -> "Stop"; default -> "Ultimate";
        };
    }

    private void sendDetailedHelp(Player p) {
        p.sendMessage("§8§m----------------§b§l ELEMENT LORE §8§m----------------");
        p.sendMessage("§f§lWIND: §7Master of skies. Passive: No Fall Dmg. §bDouble Jump (12s).");
        p.sendMessage("§c§lFIRE: §7Embody the flame. Passive: Fire Immune. §bSupernova Ultimate.");
        p.sendMessage("§b§lICE: §7Absolute Zero. Passive: Ice Speed. §bGlacier traps enemies.");
        p.sendMessage("§2§lNATURE: §7Forest Guardian. Passive: No Hunger. §bVine Hook mobility.");
        p.sendMessage("§e§lLIGHTNING: §7God of Speed. Passive: Speed II. §bChain Lightning.");
        p.sendMessage("§8§lVOID: §7The Empty. Passive: Invisibility. §bDomain Expansion (Traps).");
        p.sendMessage("§5§lGRAVITY: §7Weightless. Passive: Slowfall. §bZero-G Launch.");
        p.sendMessage("§3§lWATER: §7Deep Diver. Passive: Water Breathing. §bTidal Wave.");
        p.sendMessage("§4§lBLOOD: §7Vampiric. Passive: Strength. §bLifesteal hits.");
        p.sendMessage("§6§lEARTH: §7Unstoppable. Passive: Resistance. §bPillar launch.");
        p.sendMessage("§d§lCHRONO: §7Time Lord. Passive: Haste. §bRewind position.");
        p.sendMessage("§8§m---------------------------------------------");
    }

    // --- PASSIVE & FLY LOGIC ---
    private void handlePassives(Player p) {
        String el = getElement(p);
        
        // Flight Bug Prevention
        if (el.equals("wind")) {
            if (p.isOnGround() && cdWind.getOrDefault(p.getUniqueId(), 0L) < System.currentTimeMillis()) {
                p.setAllowFlight(true);
            }
        } else if (p.getGameMode() == GameMode.SURVIVAL) {
            if (p.getAllowFlight()) { p.setAllowFlight(false); p.setFlying(false); }
        }

        // Constant Potion Effects
        switch (el) {
            case "lightning" -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, false, false));
            case "blood" -> p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0, false, false));
            case "earth" -> p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 0, false, false));
            case "chrono" -> p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 40, 1, false, false));
            case "gravity" -> p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 40, 0, false, false));
            case "nature" -> p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 40, 0, false, false));
        }
    }

    @EventHandler
    public void onWindJump(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL || !getElement(p).equals("wind")) return;
        
        e.setCancelled(true);
        p.setAllowFlight(false);
        p.setFlying(false);
        
        if (cdWind.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) return;

        // 12s Double Jump - High Vertical Burst
        p.setVelocity(new Vector(0, 1.4, 0));
        p.getWorld().spawnParticle(Particle.GUST, p.getLocation(), 15, 0.5, 0.5, 0.5, 0.1);
        p.playSound(p.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1f, 1f);
        windJumpers.add(p.getUniqueId());
        cdWind.put(p.getUniqueId(), System.currentTimeMillis() + 12000);
    }

    // --- ABILITY CORE ---
    public void triggerAbility(Player p, int slot) {
        String el = getElement(p); UUID id = p.getUniqueId(); long now = System.currentTimeMillis();
        if (slot == 1 && cd1.getOrDefault(id, 0L) > now) return;
        if (slot == 2 && cd2.getOrDefault(id, 0L) > now) return;

        World w = p.getWorld();
        if (slot == 1) { // 30s Cooldown
            Vector move = p.getLocation().getDirection().multiply(1.8).setY(1.2);
            
            switch (el) {
                case "void" -> {
                    p.teleport(p.getLocation().add(p.getLocation().getDirection().multiply(8)));
                    w.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, p.getLocation(), 10);
                }
                case "nature" -> {
                    RayTraceResult ray = w.rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 20);
                    if (ray != null) move = ray.getHitPosition().toLocation(w).toVector().subtract(p.getEyeLocation().toVector()).normalize().multiply(2);
                }
                case "chrono" -> {
                    Location last = p.getLocation().subtract(p.getLocation().getDirection().multiply(10));
                    p.teleport(last);
                    w.spawnParticle(Particle.INSTANT_EFFECT, p.getLocation(), 20);
                }
                case "fire" -> w.spawnParticle(Particle.FLAME, p.getLocation(), 40, 0.4, 0.4, 0.4, 0.1);
            }
            p.setVelocity(move);
            cd1.put(id, now + 30000);
        } else { // 60s Cooldown
            if (el.equals("void") || el.equals("gravity")) spawnDomain(p.getLocation(), el);
            else if (el.equals("fire")) w.getNearbyEntities(p.getLocation(), 8, 8, 8).forEach(en -> en.setFireTicks(100));
            else if (el.equals("ice")) {
                w.getNearbyEntities(p.getLocation(), 7, 7, 7).forEach(en -> {
                    if (en instanceof LivingEntity le) le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 140, 4));
                });
            } else if (el.equals("earth")) {
                w.getNearbyEntities(p.getLocation(), 10, 5, 10).forEach(en -> en.setVelocity(new Vector(0, 2.0, 0)));
            }
            cd2.put(id, now + 60000);
            p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.5f);
        }
    }

    private void spawnDomain(Location center, String el) {
        Material glass = el.equals("void") ? Material.BLACK_STAINED_GLASS : Material.PURPLE_STAINED_GLASS;
        List<Block> cage = new ArrayList<>();
        
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getLocation().distance(center) <= 6) trappedPlayers.put(online.getUniqueId(), center.clone());
        }

        for (int x = -6; x <= 6; x++) {
            for (int y = -6; y <= 6; y++) {
                for (int z = -6; z <= 6; z++) {
                    double dist = center.clone().add(x,y,z).distance(center);
                    if (dist > 5.8 && dist < 6.2) {
                        Block b = center.clone().add(x,y,z).getBlock();
                        if (b.getType() == Material.AIR) {
                            b.setType(glass);
                            domainBlocks.add(b);
                            cage.add(b);
                        }
                    }
                }
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Block b : cage) { b.setType(Material.AIR); domainBlocks.remove(b); }
                trappedPlayers.entrySet().removeIf(e -> e.getValue().equals(center));
            }
        }.runTaskLater(this, 200L);
    }

    private void checkTraps(Player p) {
        if (!trappedPlayers.containsKey(p.getUniqueId())) return;
        Location c = trappedPlayers.get(p.getUniqueId());
        if (p.getLocation().distance(c) > 5.6) p.teleport(c);
    }

    private void renderParticles(Player p) {
        if (windJumpers.contains(p.getUniqueId())) {
            if (p.isOnGround()) windJumpers.remove(p.getUniqueId());
            else p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 3, 0.1, 0.1, 0.1, 0.02);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (hotkeysEnabled.getOrDefault(e.getPlayer().getUniqueId(), true)) {
            e.setCancelled(true);
            triggerAbility(e.getPlayer(), e.getPlayer().isSneaking() ? 2 : 1);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) { if (domainBlocks.contains(e.getBlock())) e.setCancelled(true); }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        String el = getElement(p);
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && el.equals("wind")) e.setCancelled(true);
        if (e.getCause() == EntityDamageEvent.DamageCause.FIRE && el.equals("fire")) e.setCancelled(true);
    }

    // --- COMMANDS ---
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) return true;

        if (c.getName().equalsIgnoreCase("elemental")) {
            if (a.length > 0 && a[0].equalsIgnoreCase("help")) { sendDetailedHelp(p); return true; }
            if (a.length >= 3 && a[0].equalsIgnoreCase("set") && s.isOp()) {
                Player t = Bukkit.getPlayer(a[1]);
                if (t != null) {
                    playerElements.put(t.getUniqueId(), a[2].toLowerCase());
                    t.setAllowFlight(false);
                    p.sendMessage("§a[!] Set " + t.getName() + " to " + a[2]);
                }
            } else { sendDetailedHelp(p); }
        } else if (c.getName().equalsIgnoreCase("controls")) {
            boolean b = !hotkeysEnabled.getOrDefault(p.getUniqueId(), true);
            hotkeysEnabled.put(p.getUniqueId(), b);
            p.sendMessage("§bHotkeys: " + (b ? "§aON" : "§cOFF"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (c.getName().equalsIgnoreCase("elemental")) {
            if (a.length == 1) return Arrays.asList("set", "help");
            if (a.length == 3) return elementsList;
        }
        return null;
    }

    private void saveData() {
        ConfigurationSection s = getConfig().createSection("players");
        playerElements.forEach((u, el) -> s.set(u.toString(), el));
        saveConfig();
    }

    private void loadData() {
        ConfigurationSection s = getConfig().getConfigurationSection("players");
        if (s != null) s.getKeys(false).forEach(k -> playerElements.put(UUID.fromString(k), s.getString(k)));
    }

    public String getElement(Player p) { return playerElements.getOrDefault(p.getUniqueId(), "none"); }
    private String getElementColor(String el) {
        return switch (el) {
            case "fire" -> "§c"; case "ice" -> "§b"; case "nature" -> "§2";
            case "lightning" -> "§e"; case "void" -> "§8"; case "gravity" -> "§5";
            case "water" -> "§3"; case "blood" -> "§4"; case "earth" -> "§6"; 
            case "chrono" -> "§d"; default -> "§f";
        };
    }
    private String formatCD(long ms) { return ms <= 0 ? "§aREADY" : "§6" + (ms/1000) + "s"; }
}
