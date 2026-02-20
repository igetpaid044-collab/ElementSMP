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

import java.util.*;

public class ElementSMP extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    public static final HashMap<UUID, String> playerElements = new HashMap<>();
    public static final HashMap<UUID, Boolean> hotkeysEnabled = new HashMap<>();
    public static final HashMap<UUID, Long> cd1 = new HashMap<>(), cd2 = new HashMap<>();
    private final Set<UUID> windJumpers = new HashSet<>();
    private final Set<Block> domainBlocks = new HashSet<>();
    private final HashMap<UUID, Location> trappedPlayers = new HashMap<>();
    private final List<String> elements = Arrays.asList("wind", "fire", "ice", "nature", "lightning", "void", "gravity", "water", "blood", "earth");

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadData();
        Bukkit.getPluginManager().registerEvents(this, this);
        
        String[] cmds = {"elemental", "ability", "controls"};
        for (String cmd : cmds) {
            getCommand(cmd).setExecutor(this);
            getCommand(cmd).setTabCompleter(this);
        }

        // Main Processor: HUD, Passives, Trails, and Traps
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateHUD(p);
                    handlePassives(p);
                    if (trappedPlayers.containsKey(p.getUniqueId())) checkTraps(p);
                }
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    @Override
    public void onDisable() { saveData(); }

    // --- DATA MANAGEMENT ---
    private void saveData() {
        ConfigurationSection s = getConfig().createSection("players");
        playerElements.forEach((uuid, el) -> s.set(uuid.toString(), el));
        saveConfig();
    }

    private void loadData() {
        ConfigurationSection s = getConfig().getConfigurationSection("players");
        if (s != null) s.getKeys(false).forEach(k -> playerElements.put(UUID.fromString(k), s.getString(k)));
    }

    // --- HUD SYSTEM ---
    private void updateHUD(Player p) {
        String el = getElement(p);
        String color = getElementColor(el);
        long now = System.currentTimeMillis();
        String t1 = formatCD(cd1.getOrDefault(p.getUniqueId(), 0L) - now);
        String t2 = formatCD(cd2.getOrDefault(p.getUniqueId(), 0L) - now);

        String hud = String.format("%s§l(%s) §b(%s %s)  §d(%s %s)", 
            color, el.toUpperCase(), getAbilityName(el, 1), t1, getAbilityName(el, 2), t2);
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(hud));
    }

    private String getElementColor(String el) {
        return switch (el) {
            case "fire" -> "§c"; case "ice" -> "§b"; case "nature" -> "§2";
            case "lightning" -> "§e"; case "void" -> "§8"; case "gravity" -> "§5";
            case "water" -> "§3"; case "blood" -> "§4"; case "earth" -> "§6"; default -> "§f";
        };
    }

    private String getAbilityName(String el, int slot) {
        if (slot == 1) return switch(el) {
            case "nature" -> "Vine"; case "fire" -> "Burst"; case "ice" -> "Freeze"; 
            case "blood" -> "Siphon"; case "earth" -> "Shield"; default -> "Dash";
        };
        return (el.equals("void") || el.equals("gravity")) ? "Domain" : "Ultimate";
    }

    private String formatCD(long ms) {
        return ms <= 0 ? "§aREADY" : String.format("§6%02d:%02d", (ms/1000)/60, (ms/1000)%60);
    }

    // --- PASSIVES & MECHANICS ---
    private void handlePassives(Player p) {
        String el = getElement(p);
        if (el.equals("wind") && p.isOnGround()) p.setAllowFlight(true);
        
        // Apply Continuous Effects
        applyEffect(p, PotionEffectType.SPEED, el.equals("lightning") ? 1 : -1);
        applyEffect(p, PotionEffectType.INVISIBILITY, el.equals("void") ? 0 : -1);
        applyEffect(p, PotionEffectType.SLOW_FALLING, el.equals("gravity") ? 0 : -1);
        applyEffect(p, PotionEffectType.WATER_BREATHING, el.equals("water") ? 0 : -1);
        applyEffect(p, PotionEffectType.RESISTANCE, el.equals("earth") ? 0 : -1);
        applyEffect(p, PotionEffectType.STRENGTH, el.equals("blood") ? 0 : -1);

        // Wind Trail
        if (windJumpers.contains(p.getUniqueId())) {
            if (p.isOnGround()) windJumpers.remove(p.getUniqueId());
            else p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 3, 0.1, 0.1, 0.1, 0.02);
        }
    }

    private void applyEffect(Player p, PotionEffectType type, int amp) {
        if (amp >= 0) p.addPotionEffect(new PotionEffect(type, 40, amp, false, false));
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        String el = getElement(p);
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && el.equals("wind")) e.setCancelled(true);
        if (e.getCause() == EntityDamageEvent.DamageCause.FIRE && el.equals("fire")) e.setCancelled(true);
    }

    // --- DOMAIN TRAP SYSTEM ---
    private void checkTraps(Player p) {
        Location center = trappedPlayers.get(p.getUniqueId());
        if (p.getLocation().distance(center) > 5.5) {
            p.teleport(center);
            p.sendMessage("§c§lYOU ARE TETHERED TO THE DOMAIN!");
        }
    }

    private void spawnDomain(Location center, String el) {
        Material mat = el.equals("void") ? Material.BLACK_STAINED_GLASS : Material.PURPLE_STAINED_GLASS;
        List<Block> cageBlocks = new ArrayList<>();
        List<UUID> localTrapped = new ArrayList<>();

        for (Player t : Bukkit.getOnlinePlayers()) {
            if (t.getLocation().distance(center) <= 6) {
                trappedPlayers.put(t.getUniqueId(), center.clone());
                localTrapped.add(t.getUniqueId());
            }
        }

        for (int x = -6; x <= 6; x++) {
            for (int y = -6; y <= 6; y++) {
                for (int z = -6; z <= 6; z++) {
                    if (center.clone().add(x,y,z).distance(center) > 5.5 && center.clone().add(x,y,z).distance(center) < 6.5) {
                        Block b = center.clone().add(x,y,z).getBlock();
                        if (b.getType() == Material.AIR) { b.setType(mat); domainBlocks.add(b); cageBlocks.add(b); }
                    }
                }
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Block b : cageBlocks) { b.setType(Material.AIR); domainBlocks.remove(b); }
                for (UUID id : localTrapped) trappedPlayers.remove(id);
            }
        }.runTaskLater(this, 200L);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) { if (domainBlocks.contains(e.getBlock())) e.setCancelled(true); }

    // --- ABILITIES ---
    @EventHandler
    public void onFly(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.SURVIVAL && getElement(p).equals("wind")) {
            e.setCancelled(true); p.setAllowFlight(false);
            p.setVelocity(p.getLocation().getDirection().multiply(1.4).setY(0.8));
            p.playSound(p.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1f, 1.2f);
            windJumpers.add(p.getUniqueId());
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (!hotkeysEnabled.getOrDefault(e.getPlayer().getUniqueId(), true)) return;
        e.setCancelled(true);
        triggerAbility(e.getPlayer(), e.getPlayer().isSneaking() ? 2 : 1);
    }

    public void triggerAbility(Player p, int slot) {
        String el = getElement(p); long now = System.currentTimeMillis();
        if ((slot == 1 && cd1.getOrDefault(p.getUniqueId(), 0L) > now) || (slot == 2 && cd2.getOrDefault(p.getUniqueId(), 0L) > now)) return;

        if (slot == 1) {
            if (el.equals("nature")) {
                RayTraceResult r = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 25);
                if (r != null) {
                    p.setVelocity(r.getHitPosition().toLocation(p.getWorld()).toVector().subtract(p.getEyeLocation().toVector()).normalize().multiply(1.8).setY(0.6));
                    p.getWorld().spawnParticle(Particle.BLOCK, r.getHitPosition().toLocation(p.getWorld()), 30, Material.EMERALD_BLOCK.createBlockData());
                }
            } else p.setVelocity(p.getLocation().getDirection().multiply(1.5).setY(0.2));
            cd1.put(p.getUniqueId(), now + 5000);
        } else {
            if (el.equals("void") || el.equals("gravity")) {
                p.sendTitle("§d§lDOMAIN EXPANSION", "§7Infinite Information", 10, 60, 10);
                spawnDomain(p.getLocation(), el);
            }
            cd2.put(p.getUniqueId(), now + 60000);
        }
    }

    // --- COMMANDS ---
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) return true;
        if (c.getName().equalsIgnoreCase("elemental") && s.isOp() && a.length >= 3) {
            Player t = Bukkit.getPlayer(a[1]);
            if (t != null) { playerElements.put(t.getUniqueId(), a[2].toLowerCase()); p.sendMessage("§aSuccess!"); }
        } else if (c.getName().equalsIgnoreCase("ability")) {
            triggerAbility(p, (a.length > 0 && a[0].equals("2")) ? 2 : 1);
        } else if (c.getName().equalsIgnoreCase("controls")) {
            boolean b = !hotkeysEnabled.getOrDefault(p.getUniqueId(), true);
            hotkeysEnabled.put(p.getUniqueId(), b);
            p.sendMessage("§bHotkeys: " + (b ? "§aON" : "§cOFF"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1 && c.getName().equals("elemental")) return Collections.singletonList("set");
        if (a.length == 3) return elements;
        return Arrays.asList("1", "2");
    }

    public String getElement(Player p) { return playerElements.getOrDefault(p.getUniqueId(), "wind"); }
}
