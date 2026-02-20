class AbilityCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player)) return true;
        // This checks if you typed /ability 1 or /ability 2
        int num = (a.length > 0 && a[0].equals("2")) ? 2 : 1;
        // This triggers the damage, particles, and cooldowns
        ElementSMP.executeAbility((Player) s, num);
        return true;
    }
}
