package me.kaloni;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;

public class AbilityCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;
        String e = ElementSMP.playerElements.getOrDefault(p.getUniqueId(), "None");

        if (args[0].equals("1")) {
            p.sendMessage("§e§l[!] §7Using Ability 1...");
            if (e.equals("Fire")) {
                p.launchProjectile(SmallFireball.class);
                p.getWorld().spawnParticle(Particle.FLAME, p.getLocation(), 50, 0.5, 0.5, 0.5, 0.1);
            }
            if (e.equals("Air")) {
                p.setVelocity(new Vector(0, 1.2, 0));
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 30, 0.2, 0.2, 0.2, 0.1);
            }
            if (e.equals("Shadow")) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 200, 0));
                p.getWorld().spawnParticle(Particle.LARGE_SMOKE, p.getLocation(), 40, 0.3, 1, 0.3, 0.05);
            }
        } else if (args[0].equals("2")) {
            p.sendMessage("§e§l[!] §7Using Ability 2...");
            if (e.equals("Lightning")) {
                p.getWorld().strikeLightning(p.getTargetBlock(null, 20).getLocation());
                p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation(), 50, 1, 1, 1, 0.5);
            }
            if (e.equals("Ice")) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 10));
                p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation(), 100, 1, 1, 1, 0.1);
            }
            if (e.equals("Void")) {
                p.teleport(p.getLocation().add(0, 5, 0));
                p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation(), 100, 0.5, 1, 0.5, 0.2);
            }
        }
        return true;
    }
}
