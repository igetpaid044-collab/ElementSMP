package me.kaloni;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class AbilityCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;
        String e = ElementSMP.playerElements.getOrDefault(p.getUniqueId(), "None");

        if (args.length == 0) return true;

        if (args[0].equals("1")) {
            // VOID - MAXIMUM DAMAGE (6 HEARTS)
            if (e.equals("Void")) {
                Entity target = getNearestEntity(p, 10);
                if (target instanceof LivingEntity) {
                    ((LivingEntity) target).damage(12.0); // 12.0 = 6 Hearts
                    p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, target.getLocation(), 100, 0.5, 1, 0.5, 0.1);
                    p.getWorld().playSound(target.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1, 1);
                    p.sendMessage("§5§lVoid §7> Singularity hit for §c6 Hearts!");
                }
            }
            // FIRE - (4 HEARTS)
            else if (e.equals("Fire")) {
                Entity target = getNearestEntity(p, 10);
                if (target instanceof LivingEntity) {
                    ((LivingEntity) target).damage(8.0); // 8.0 = 4 Hearts
                    target.setFireTicks(60);
                    p.getWorld().spawnParticle(Particle.FLAME, target.getLocation(), 50, 0.5, 0.5, 0.5, 0.1);
                }
            }
            // LIGHTNING - (4.5 HEARTS)
            else if (e.equals("Lightning")) {
                Entity target = getNearestEntity(p, 10);
                if (target instanceof LivingEntity) {
                    ((LivingEntity) target).damage(9.0); // 9.0 = 4.5 Hearts
                    p.getWorld().strikeLightningEffect(target.getLocation());
                }
            }
        }
        return true;
    }

    private Entity getNearestEntity(Player player, int range) {
        for (Entity e : player.getNearbyEntities(range, range, range)) {
            if (e instanceof LivingEntity && !e.equals(player)) return e;
        }
        return null;
    }
}
