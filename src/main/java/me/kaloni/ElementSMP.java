package me.kaloni;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;

public class ElementSMP extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("ElementSMP has been enabled!");
        getCommand("elements").setExecutor(new PowerCommand());
    }

    @Override
    public void onDisable() {
        getLogger().info("ElementSMP has been disabled!");
    }
}
