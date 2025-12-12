package com.example.christmaspresents;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.ChatColor;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ChristmasPresents extends JavaPlugin implements Listener, TabCompleter {
    private static ChristmasPresents instance;
    private File configFile;
    private FileConfiguration customConfig;
    private final NamespacedKey presentTypeKey = new NamespacedKey(this, "present_type");
    private final NamespacedKey harmlessKey = new NamespacedKey(this, "harmless_fx");
    private final NamespacedKey presentTitleKey = new NamespacedKey(this, "present_title");
    private final NamespacedKey presentTitleCreatedKey = new NamespacedKey(this, "present_title_created");
    private final Random random = new Random();
    private final Map<String, Long> specialCooldowns = new HashMap<>();
    private final Map<String, List<PresentEntry>> drops = new HashMap<>();
    private DropRateGUI dropRateGUI;
    private File messagesFile;
    private FileConfiguration messagesConfig;
    private File titlesFile;
    private FileConfiguration titlesConfig;
    private String displayNameCommon;
    private String displayNameSpecial;
    private java.util.List<String> loreCommon;
    private java.util.List<String> loreSpecial;
    private final java.util.List<DeadZone> deadZones = new java.util.ArrayList<>();
    private final Set<org.bukkit.Location> presentLocations = new HashSet<>();
    private final Map<org.bukkit.Location, java.util.UUID> presentNameEntities = new HashMap<>();
    private boolean spawnEnabled = true;
    private int spawnTaskId = -1;

    public static ChristmasPresents getInstance() {
        return instance;
    }

    @EventHandler
    public void onEntityDamageByEntity(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        org.bukkit.entity.Entity damager = event.getDamager();
        if (damager == null) return;
        if (damager.getPersistentDataContainer().has(harmlessKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onEnable() {
        instance = this;
        loadCustomConfig();
        ensureDefaultSettings();
        loadDropsIntoMemory();
        loadMessages();
        saveDefaultConfig();
        loadDisplayFromConfig();
        loadDeadZones();
        loadTitleUuidMap();
        loadCooldowns();
        dropRateGUI = new DropRateGUI(this);
        registerCommands();
        getServer().getPluginManager().registerEvents(this, this);
        scheduleNextSpawn();
        loadPresentLocations();
        restorePresentTitles();
        getLogger().info("ChristmasPresents has been enabled!");
    }

    @Override
    public void onDisable() {
        saveDropsFromMemory();
        saveCooldowns();
        saveTitleUuidMap();
        saveCustomConfig();
        getLogger().info("ChristmasPresents has been disabled!");
    }

    private void registerCommands() {
        Objects.requireNonNull(getCommand("presents")).setExecutor(this);
        Objects.requireNonNull(getCommand("presents")).setTabCompleter(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Player player = sender instanceof Player ? (Player) sender : null;

        if (args.length == 0) {
            if (player != null) {
                player.sendMessage("§6§lChristmasPresents Commands:");
                player.sendMessage("§e/presents additem <common|special> §7- Add item to present drops");
                player.sendMessage("§e/presents give <common|special> [player] [amount] §7- Give presents");
                player.sendMessage("§e/presents droprates §7- Configure drop rates");
            } else {
                sender.sendMessage("/presents additem <common|special>");
                sender.sendMessage("/presents give <common|special> [player] [amount]");
                sender.sendMessage("/presents droprates");
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "additem":
                if (!sender.hasPermission("presents.admin")) return true;
                if (!(sender instanceof Player)) return true;
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /presents additem <common|special>");
                    return true;
                }
                handleAddItem(player, args[1]);
                break;
            case "addall":
                if (!sender.hasPermission("presents.admin")) return true;
                if (!(sender instanceof Player)) return true;
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /presents addall <common|special>");
                    return true;
                }
                handleAddAll(player, args[1]);
                break;
            case "give":
                if (!sender.hasPermission("presents.admin")) return true;
                if (args.length < 2) {
                    if (player != null) player.sendMessage("§cUsage: /presents give <common|special> [player] [amount]");
                    else sender.sendMessage("Usage: /presents give <common|special> [player] [amount]");
                    return true;
                }
                String typeArg = args[1].toLowerCase();
                boolean special = typeArg.equals("special");
                boolean common = typeArg.equals("common");
                if (!special && !common) {
                    if (player != null) player.sendMessage("§cType must be common or special");
                    else sender.sendMessage("Type must be common or special");
                    return true;
                }
                Player target;
                int amount = 1;
                if (args.length >= 3) {
                    target = getServer().getPlayerExact(args[2]);
                    if (target == null) {
                        if (player != null) target = player; else {
                            sender.sendMessage("Player not found");
                            return true;
                        }
                    }
                    if (args.length >= 4) {
                        try { amount = Math.max(1, Integer.parseInt(args[3])); } catch (Exception ignored) {}
                    }
                } else {
                    if (player == null) {
                        sender.sendMessage("Specify a player when using from console");
                        return true;
                    }
                    target = player;
                }
                ItemStack present = createPresent(special);
                for (int i = 0; i < amount; i++) target.getInventory().addItem(present.clone());
                break;
            case "droprates":
                if (!sender.hasPermission("presents.admin")) return true;
                if (!(sender instanceof Player)) return true;
                dropRateGUI.openMainMenu(player);
                break;
            case "spawn":
                if (!sender.hasPermission("presents.admin")) return true;
                if (args.length < 2) {
                    sender.sendMessage("Usage: /presents spawn <start|stop>");
                    return true;
                }
                if (args[1].equalsIgnoreCase("start")) {
                    spawnEnabled = true;
                    customConfig.set("settings.spawn.enabled", true);
                    saveCustomConfig();
                    sender.sendMessage("§aSpawning enabled");
                    spawnRandomPresents(10, null);
                } else if (args[1].equalsIgnoreCase("stop")) {
                    spawnEnabled = false;
                    customConfig.set("settings.spawn.enabled", false);
                    saveCustomConfig();
                    sender.sendMessage("§cSpawning disabled");
                } else {
                    sender.sendMessage("Usage: /presents spawn <start|stop>");
                }
                break;
            case "clear":
                if (!sender.hasPermission("presents.admin")) return true;
                int batch = 50;
                if (args.length >= 2) {
                    try { batch = Math.max(1, Integer.parseInt(args[1])); } catch (Exception ignored) {}
                }
                int removed = removeAllPresentsBatch(batch);
                sender.sendMessage("§eRemoved " + removed + " presents from the world");
                break;
            case "debugspawn":
                if (!sender.hasPermission("presents.admin")) return true;
                int cnt = 10;
                if (args.length >= 2) {
                    try { cnt = Math.max(1, Integer.parseInt(args[1])); } catch (Exception ignored) {}
                }
                spawnRandomPresents(cnt, sender);
                break;
            case "createdeadzone":
                if (!sender.hasPermission("presents.admin")) return true;
                if (args.length < 3) { sender.sendMessage("Usage: /presents createdeadzone <x,y,z> <x,y,z>"); return true; }
                org.bukkit.World dw;
                if (sender instanceof Player) dw = ((Player)sender).getWorld(); else {
                    dw = null;
                    for (org.bukkit.World w : getServer().getWorlds()) { if (w.getEnvironment() == org.bukkit.World.Environment.NORMAL) { dw = w; break; } }
                    if (dw == null) { sender.sendMessage("§cNo world found"); return true; }
                }
                int[] p1 = parseXYZ(args[1]);
                int[] p2 = parseXYZ(args[2]);
                if (p1 == null || p2 == null) { sender.sendMessage("§cInvalid coordinates. Use x,y,z"); return true; }
                DeadZone dz = new DeadZone(dw.getName(), p1[0], p1[1], p1[2], p2[0], p2[1], p2[2]);
                deadZones.add(dz);
                saveDeadZones();
                sender.sendMessage("§aDeadzone created for world " + dw.getName());
                break;
            case "resetdrops":
                if (!sender.hasPermission("presents.admin")) return true;
                drops.clear();
                customConfig.set("items.common", null);
                customConfig.set("items.special", null);
                saveCustomConfig();
                sender.sendMessage("§cAll present drops have been reset");
                break;
            default:
                if (player != null) player.sendMessage("§cUnknown command. Use /presents for help.");
                else sender.sendMessage("Unknown subcommand");
        }

        return true;
    }

    private void handleAddAll(Player player, String type) {
        if (!type.equalsIgnoreCase("common") && !type.equalsIgnoreCase("special")) {
            player.sendMessage("§cType must be either 'common' or 'special'");
            return;
        }
        String presentType = type.toLowerCase();
        ItemStack[] storage = player.getInventory().getStorageContents();
        int added = 0;
        for (ItemStack it : storage) {
            if (it == null || it.getType() == Material.AIR) continue;
            String itemKey = UUID.randomUUID().toString();
            drops.computeIfAbsent(presentType, k -> new ArrayList<>()).add(PresentEntry.item(itemKey, it.clone(), 50.0, true));
            added++;
        }
        saveDropsFromMemory();
        player.sendMessage("§aAdded " + added + " items to " + presentType + " presents at 50% each");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("presents")) return Collections.emptyList();
        boolean admin = sender.hasPermission("presents.admin");
        if (args.length == 1) {
            List<String> base = new ArrayList<>();
            if (admin) {
                base.add("additem");
                base.add("addall");
                base.add("give");
                base.add("droprates");
                base.add("spawn");
                base.add("clear");
                base.add("resetdrops");
                base.add("createdeadzone");
            }
            return filter(base, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (admin && (sub.equals("additem") || sub.equals("addall") || sub.equals("give"))) {
                return filter(Arrays.asList("common", "special"), args[1]);
            }
            if (admin && sub.equals("spawn")) {
                return filter(Arrays.asList("start", "stop"), args[1]);
            }
            if (admin && sub.equals("clear")) {
                return filter(Arrays.asList("10","25","50","100","250","500"), args[1]);
            }
        }
        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (admin && sub.equals("give")) {
                List<String> names = new ArrayList<>();
                for (Player p : getServer().getOnlinePlayers()) names.add(p.getName());
                return filter(names, args[2]);
            }
        }
        if (args.length == 4) {
            String sub = args[0].toLowerCase();
            if (admin && sub.equals("give")) {
                return filter(Arrays.asList("1","2","3","4","5","8","16","32","64"), args[3]);
            }
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String prefix) {
        if (prefix == null || prefix.isEmpty()) return options;
        String lc = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase().startsWith(lc)) out.add(o);
        return out;
    }

    private void handleAddItem(Player player, String type) {
        if (!type.equalsIgnoreCase("common") && !type.equalsIgnoreCase("special")) {
            player.sendMessage("§cType must be either 'common' or 'special'");
            return;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand.getType() == Material.AIR) {
            player.sendMessage("§cYou must be holding an item in your hand!");
            return;
        }

        String itemKey = UUID.randomUUID().toString();
        String presentType = type.toLowerCase();
        PresentEntry entry = PresentEntry.item(itemKey, itemInHand.clone(), 50.0, true);
        drops.computeIfAbsent(presentType, k -> new ArrayList<>()).add(entry);
        saveDropsFromMemory();
        player.sendMessage("§aAdded item to " + presentType + " presents with a 50% drop rate");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        org.bukkit.block.Block block = event.getClickedBlock();
        if (block.getType() != org.bukkit.Material.CHEST) return;
        if (!(block.getState() instanceof org.bukkit.block.TileState)) return;
        org.bukkit.block.TileState state = (org.bukkit.block.TileState) block.getState();
        if (!state.getPersistentDataContainer().has(presentTypeKey, PersistentDataType.STRING)) return;
        event.setCancelled(true);
        String presentType = state.getPersistentDataContainer().get(presentTypeKey, PersistentDataType.STRING);
        org.bukkit.Location loc = block.getLocation();
        block.setType(org.bukkit.Material.AIR);
        cleanupTitlesAt(loc);
        presentLocations.remove(loc);
        savePresentLocations();
        openPresent(event.getPlayer(), presentType, loc);
    }

    private void openPresent(Player player, String presentType) {
        openPresent(player, presentType, player.getLocation());
    }

    private void openPresent(Player player, String presentType, org.bukkit.Location chestLoc) {
        List<PresentEntry> list = drops.getOrDefault(presentType, Collections.emptyList());
        List<PresentEntry> enabled = new ArrayList<>();
        for (PresentEntry e : list) if (e.enabled) enabled.add(e);
        if (enabled.isEmpty()) {
            player.sendMessage("§cNo items configured for " + presentType + " presents");
            return;
        }
        double total = 0.0;
        for (PresentEntry e : enabled) total += Math.max(0.0, e.chance);
        if (total <= 0.0) {
            player.sendMessage("§cNo items have a positive drop chance");
            return;
        }
        double roll = random.nextDouble() * total;
        double sum = 0.0;
        PresentEntry chosen = null;
        for (PresentEntry e : enabled) {
            sum += Math.max(0.0, e.chance);
            if (roll <= sum) { chosen = e; break; }
        }
        if (chosen == null) chosen = enabled.get(enabled.size() - 1);
        if (chosen.kind == EntryKind.ITEM) {
            ItemStack reward = chosen.item.clone();
            playOpenAnimation(chestLoc, true, reward, player);
            String name = reward.hasItemMeta() && reward.getItemMeta().hasDisplayName() ? reward.getItemMeta().getDisplayName() : reward.getType().name();
            player.sendMessage(ChatColor.GREEN + "You received: " + ChatColor.GOLD + ChatColor.BOLD + name);
        } else if (chosen.kind == EntryKind.EFFECT) {
            playOpenAnimation(chestLoc, false, null, player);
            executeEffect(player, chosen.effectKey, presentType);
        }
    }

    public ItemStack createPresent(boolean isSpecial) {
        ItemStack present = new ItemStack(Material.CHEST);
        ItemMeta meta = present.getItemMeta();
        meta.setDisplayName(isSpecial ? displayNameSpecial : displayNameCommon);
        meta.getPersistentDataContainer().set(presentTypeKey, PersistentDataType.STRING, isSpecial ? "special" : "common");
        present.setItemMeta(meta);
        return present;
    }

    private void loadDisplayFromConfig() {
        String nameCommon = getConfig().getString("present_names.common", "&aFruitmas Present");
        String nameSpecial = getConfig().getString("present_names.special", "&6Special Fruitmas Present");
        displayNameCommon = translateHexColorCodes(nameCommon);
        displayNameSpecial = translateHexColorCodes(nameSpecial);
        loreCommon = new java.util.ArrayList<>();
        loreSpecial = new java.util.ArrayList<>();
    }
    
    private String translateHexColorCodes(String message) {
        java.util.regex.Pattern hexPattern = java.util.regex.Pattern.compile("&#([A-Fa-f0-9]{6})");
        java.util.regex.Matcher matcher = hexPattern.matcher(message);
        StringBuffer buffer = new StringBuffer(message.length() + 4 * 8);
        while (matcher.find()) {
            String group = matcher.group(1);
            matcher.appendReplacement(buffer, net.md_5.bungee.api.ChatColor.of("#" + group).toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    private void loadCustomConfig() {
        configFile = new File(getDataFolder(), "data.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            try { configFile.createNewFile(); } catch (IOException ignored) {}
        }

        customConfig = new YamlConfiguration();
        try {
            customConfig.load(configFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveCustomConfig() {
        try {
            customConfig.save(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadDropsIntoMemory() {
        drops.clear();
        for (String t : Arrays.asList("common", "special")) {
            if (!customConfig.isConfigurationSection("items." + t)) continue;
            List<PresentEntry> list = new ArrayList<>();
            for (String key : customConfig.getConfigurationSection("items." + t).getKeys(false)) {
                String kind = customConfig.getString("items." + t + "." + key + ".kind", "item");
                double chance = customConfig.getDouble("items." + t + "." + key + ".chance", 50.0);
                boolean enabled = customConfig.getBoolean("items." + t + "." + key + ".enabled", true);
                if ("effect".equalsIgnoreCase(kind)) {
                    String effectKey = customConfig.getString("items." + t + "." + key + ".effectKey", "");
                    if (effectKey != null && !effectKey.isEmpty()) list.add(PresentEntry.effect(key, effectKey, chance, enabled));
                } else {
                    ItemStack it = customConfig.getItemStack("items." + t + "." + key + ".item");
                    if (it != null) list.add(PresentEntry.item(key, it, chance, enabled));
                }
            }
            drops.put(t, list);
        }
    }

    void saveDropsFromMemory() {
        for (String t : Arrays.asList("common", "special")) customConfig.set("items." + t, null);
        for (Map.Entry<String, List<PresentEntry>> e : drops.entrySet()) {
            String t = e.getKey();
            for (PresentEntry pe : e.getValue()) {
                String base = "items." + t + "." + pe.id;
                customConfig.set(base + ".kind", pe.kind == EntryKind.EFFECT ? "effect" : "item");
                if (pe.kind == EntryKind.ITEM) customConfig.set(base + ".item", pe.item);
                if (pe.kind == EntryKind.EFFECT) customConfig.set(base + ".effectKey", pe.effectKey);
                customConfig.set(base + ".chance", pe.chance);
                customConfig.set(base + ".enabled", pe.enabled);
            }
        }
        saveCustomConfig();
    }

    private void loadCooldowns() {
        specialCooldowns.clear();
        if (!customConfig.isConfigurationSection("cooldowns")) return;
        for (String uuid : customConfig.getConfigurationSection("cooldowns").getKeys(false)) {
            long until = customConfig.getLong("cooldowns." + uuid, 0L);
            specialCooldowns.put(uuid, until);
        }
    }

    private void saveCooldowns() {
        customConfig.set("cooldowns", null);
        for (Map.Entry<String, Long> e : specialCooldowns.entrySet()) {
            customConfig.set("cooldowns." + e.getKey(), e.getValue());
        }
        saveCustomConfig();
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        return;
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        return;
    }

    private void maybeDropPresent(Player p, boolean fromFishing) {
        return;
    }

    boolean isOnSpecialCooldown(UUID uuid) {
        long now = System.currentTimeMillis();
        Long until = specialCooldowns.get(uuid.toString());
        return until != null && until > now;
    }

    void setSpecialCooldown(UUID uuid, long until) {
        specialCooldowns.put(uuid.toString(), until);
    }

    Map<String, List<PresentEntry>> getDrops() { return drops; }

    private void ensureDefaultSettings() {
        if (!customConfig.isConfigurationSection("settings.presentDrop")) {
            customConfig.set("settings.presentDrop.common", 0.10D);
            customConfig.set("settings.presentDrop.special", 0.01D);
            saveCustomConfig();
        } else {
            if (!customConfig.contains("settings.presentDrop.common")) customConfig.set("settings.presentDrop.common", 0.10D);
            if (!customConfig.contains("settings.presentDrop.special")) customConfig.set("settings.presentDrop.special", 0.01D);
            saveCustomConfig();
        }
        if (!customConfig.contains("settings.spawn.specialChance")) {
            customConfig.set("settings.spawn.specialChance", 0.01D);
        }
        if (!customConfig.isConfigurationSection("settings.drops.enabled.mobs")) {
            customConfig.set("settings.drops.enabled.mobs.common", true);
            customConfig.set("settings.drops.enabled.mobs.special", true);
        }
        if (!customConfig.isConfigurationSection("settings.drops.enabled.fishing")) {
            customConfig.set("settings.drops.enabled.fishing.common", true);
            customConfig.set("settings.drops.enabled.fishing.special", true);
        }
        if (!customConfig.isConfigurationSection("items.common")) customConfig.createSection("items.common");
        boolean hasMsg = false;
        if (customConfig.isConfigurationSection("items.common")) {
            java.util.List<String> toDelete = new java.util.ArrayList<>();
            for (String key : customConfig.getConfigurationSection("items.common").getKeys(false)) {
                String kind = customConfig.getString("items.common." + key + ".kind", "item");
                if ("effect".equalsIgnoreCase(kind)) {
                    String ek = customConfig.getString("items.common." + key + ".effectKey", "");
                    if ("snow_overlay".equalsIgnoreCase(ek)) toDelete.add(key);
                    if ("random_message".equalsIgnoreCase(ek)) hasMsg = true;
                }
            }
            for (String key : toDelete) customConfig.set("items.common." + key, null);
        }
        if (!hasMsg) {
            String id = UUID.randomUUID().toString();
            customConfig.set("items.common." + id + ".kind", "effect");
            customConfig.set("items.common." + id + ".effectKey", "random_message");
            customConfig.set("items.common." + id + ".chance", 10.0D);
            customConfig.set("items.common." + id + ".enabled", true);
        }
        boolean hasMoneyCommon = false;
        if (customConfig.isConfigurationSection("items.common")) {
            for (String key : customConfig.getConfigurationSection("items.common").getKeys(false)) {
                String kind = customConfig.getString("items.common." + key + ".kind", "item");
                if ("effect".equalsIgnoreCase(kind)) {
                    String ek = customConfig.getString("items.common." + key + ".effectKey", "");
                    if ("money_reward".equalsIgnoreCase(ek)) hasMoneyCommon = true;
                }
            }
        }
        if (!hasMoneyCommon) {
            String id = UUID.randomUUID().toString();
            customConfig.set("items.common." + id + ".kind", "effect");
            customConfig.set("items.common." + id + ".effectKey", "money_reward");
            customConfig.set("items.common." + id + ".chance", 15.0D);
            customConfig.set("items.common." + id + ".enabled", true);
        }
        if (!customConfig.isConfigurationSection("items.special")) customConfig.createSection("items.special");
        boolean hasMoneySpecial = false;
        if (customConfig.isConfigurationSection("items.special")) {
            for (String key : customConfig.getConfigurationSection("items.special").getKeys(false)) {
                String kind = customConfig.getString("items.special." + key + ".kind", "item");
                if ("effect".equalsIgnoreCase(kind)) {
                    String ek = customConfig.getString("items.special." + key + ".effectKey", "");
                    if ("money_reward".equalsIgnoreCase(ek)) hasMoneySpecial = true;
                }
            }
        }
        if (!hasMoneySpecial) {
            String id = UUID.randomUUID().toString();
            customConfig.set("items.special." + id + ".kind", "effect");
            customConfig.set("items.special." + id + ".effectKey", "money_reward");
            customConfig.set("items.special." + id + ".chance", 20.0D);
            customConfig.set("items.special." + id + ".enabled", true);
        }
        saveCustomConfig();
    }

    private double getCommonPresentDropRate() {
        return Math.max(0D, Math.min(1D, customConfig.getDouble("settings.presentDrop.common", 0.10D)));
    }

    private double getSpecialPresentDropRate() {
        return Math.max(0D, Math.min(1D, customConfig.getDouble("settings.presentDrop.special", 0.01D)));
    }

    private boolean isEnabled(String path, boolean def) {
        return customConfig.getBoolean(path, def);
    }

    private void executeEffect(Player player, String key) {
        executeEffect(player, key, "common");
    }

    private void executeEffect(Player player, String key, String presentType) {
        if ("snow_overlay".equalsIgnoreCase(key)) {
            org.bukkit.World w = player.getWorld();
            org.bukkit.Location base = player.getLocation();
            new org.bukkit.scheduler.BukkitRunnable() {
                int ticks = 0;
                public void run() {
                    ticks += 2;
                    w.spawnParticle(org.bukkit.Particle.SNOWFLAKE, base, 200, 20.0, 10.0, 20.0, 0.0);
                    if (ticks >= 200) cancel();
                }
            }.runTaskTimer(this, 0L, 2L);
            return;
        }
        if ("random_message".equalsIgnoreCase(key)) {
            List<String> msgs = messagesConfig.getStringList("messages");
            if (msgs == null || msgs.isEmpty()) return;
            String m = msgs.get(random.nextInt(msgs.size()));
            player.sendMessage(ChatColor.AQUA + m);
            return;
        }
        if ("money_reward".equalsIgnoreCase(key)) {
            int min = getConfig().getInt("money_rewards." + presentType + ".min", 50);
            int max = getConfig().getInt("money_rewards." + presentType + ".max", 200);
            int amount = min + random.nextInt(Math.max(1, max - min + 1));
            if (getServer().getPluginManager().getPlugin("Vault") != null) {
                org.bukkit.plugin.RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> rsp = getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
                if (rsp != null) {
                    net.milkbowl.vault.economy.Economy econ = rsp.getProvider();
                    econ.depositPlayer(player, amount);
                    player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "You received $" + amount + "!");
                    return;
                }
            }
            player.sendMessage(ChatColor.RED + "Money reward failed - Vault not found");
        }
    }

    private void loadMessages() {
        messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            try { messagesFile.getParentFile().mkdirs(); messagesFile.createNewFile(); } catch (IOException e) { getLogger().warning("Failed to create messages.yml: " + e.getMessage()); }
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        java.util.List<String> defaults = java.util.Collections.singletonList("Santa wishes you a merry Fruitmas!");
        messagesConfig.set("messages", defaults);
        try { messagesConfig.save(messagesFile); } catch (IOException ignored) {}
    }

    enum EntryKind { ITEM, EFFECT }

    static class PresentEntry {
        final String id;
        final EntryKind kind;
        final ItemStack item;
        final String effectKey;
        double chance;
        boolean enabled;
        PresentEntry(String id, EntryKind kind, ItemStack item, String effectKey, double chance, boolean enabled) { this.id = id; this.kind = kind; this.item = item; this.effectKey = effectKey; this.chance = chance; this.enabled = enabled; }
        static PresentEntry item(String id, ItemStack item, double chance, boolean enabled) { return new PresentEntry(id, EntryKind.ITEM, item, null, chance, enabled); }
        static PresentEntry effect(String id, String effectKey, double chance, boolean enabled) { return new PresentEntry(id, EntryKind.EFFECT, null, effectKey, chance, enabled); }
    }

    @EventHandler
    public void onPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (!meta.getPersistentDataContainer().has(presentTypeKey, PersistentDataType.STRING)) return;
        org.bukkit.block.Block block = event.getBlockPlaced();
        if (!(block.getState() instanceof org.bukkit.block.TileState)) return;
        String presentType = meta.getPersistentDataContainer().get(presentTypeKey, PersistentDataType.STRING);
        org.bukkit.block.TileState state = (org.bukkit.block.TileState) block.getState();
        state.getPersistentDataContainer().set(presentTypeKey, PersistentDataType.STRING, presentType);
        state.update(true, false);
        org.bukkit.Location loc = block.getLocation();
        spawnPresentName(loc, presentType);
        getServer().getScheduler().runTaskLater(this, () -> ensureTitleFor(loc, presentType), 1L);
        presentLocations.add(loc);
        savePresentLocations();
    }

    private void spawnPresentName(org.bukkit.Location loc, String presentType) {
        org.bukkit.World world = loc.getWorld();
        if (world == null) return;
        org.bukkit.entity.TextDisplay td = (org.bukkit.entity.TextDisplay) world.spawnEntity(loc.clone().add(0.5, 1.3, 0.5), org.bukkit.entity.EntityType.TEXT_DISPLAY);
        td.setText("special".equalsIgnoreCase(presentType) ? displayNameSpecial : displayNameCommon);
        td.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
        td.setShadowed(false);
        try { td.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0)); } catch (Throwable ignored) {}
        try { td.getPersistentDataContainer().set(presentTitleKey, PersistentDataType.BYTE, (byte)1); } catch (Throwable ignored) {}
        try { td.getPersistentDataContainer().set(presentTitleCreatedKey, PersistentDataType.LONG, System.currentTimeMillis()); } catch (Throwable ignored) {}
        presentNameEntities.put(loc, td.getUniqueId());
        saveTitleUuidMap();
    }

    private void ensureTitleFor(org.bukkit.Location loc, String presentType) {
        org.bukkit.World w = loc.getWorld();
        if (w == null) return;
        java.util.UUID id = presentNameEntities.get(loc);
        if (id != null) {
            org.bukkit.entity.Entity existing = org.bukkit.Bukkit.getEntity(id);
            if (existing != null && existing.isValid()) return;
        }
        org.bukkit.entity.TextDisplay found = null;
        for (org.bukkit.entity.Entity e : w.getNearbyEntities(loc.clone().add(0.5, 1.2, 0.5), 1.5, 1.5, 1.5)) {
            if (e.getType() != org.bukkit.entity.EntityType.TEXT_DISPLAY) continue;
            org.bukkit.entity.TextDisplay td = (org.bukkit.entity.TextDisplay) e;
            boolean ours = td.getPersistentDataContainer().has(presentTitleKey, PersistentDataType.BYTE);
            if (!ours) {
                org.bukkit.entity.Display.Billboard bb = td.getBillboard();
                if (bb == org.bukkit.entity.Display.Billboard.CENTER) {
                    String expected = "special".equalsIgnoreCase(presentType) ? displayNameSpecial : displayNameCommon;
                    if (expected != null && expected.equals(td.getText())) ours = true;
                }
            }
            if (ours) { found = td; break; }
        }
        if (found != null) {
            try { found.getPersistentDataContainer().set(presentTitleKey, PersistentDataType.BYTE, (byte)1); } catch (Throwable ignored) {}
            try { found.getPersistentDataContainer().set(presentTitleCreatedKey, PersistentDataType.LONG, System.currentTimeMillis()); } catch (Throwable ignored) {}
            presentNameEntities.put(loc, found.getUniqueId());
            return;
        }
        spawnPresentName(loc, presentType);
    }

    private void removePresentName(org.bukkit.Location loc) {
        java.util.UUID id = presentNameEntities.remove(loc);
        if (id == null) return;
        org.bukkit.entity.Entity e = org.bukkit.Bukkit.getEntity(id);
        if (e != null) e.remove();
    }

    private void cleanupTitlesAt(org.bukkit.Location loc) {
        removePresentName(loc);
        org.bukkit.World w = loc.getWorld();
        if (w == null) return;
        org.bukkit.Location center = loc.clone().add(0.5, 1.2, 0.5);
        for (org.bukkit.entity.Entity e : w.getNearbyEntities(center, 1.2, 1.2, 1.2)) {
            org.bukkit.entity.EntityType t = e.getType();
            if (t == org.bukkit.entity.EntityType.TEXT_DISPLAY || t == org.bukkit.entity.EntityType.ARMOR_STAND) e.remove();
        }
        saveTitleUuidMap();
    }

    private void loadTitleUuidMap() {
        titlesFile = new File(getDataFolder(), "titles.yml");
        if (!titlesFile.exists()) {
            try { getDataFolder().mkdirs(); titlesFile.createNewFile(); } catch (IOException ignored) {}
        }
        titlesConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(titlesFile);
        presentNameEntities.clear();
        java.util.List<String> list = titlesConfig.getStringList("titles");
        if (list == null) return;
        for (String s : list) {
            String[] parts = s.split(";");
            if (parts.length != 6) continue;
            org.bukkit.World w = getServer().getWorld(parts[0]);
            if (w == null) continue;
            int x, y, z;
            try { x = Integer.parseInt(parts[1]); y = Integer.parseInt(parts[2]); z = Integer.parseInt(parts[3]); } catch (Exception e) { continue; }
            java.util.UUID uuid;
            try { uuid = java.util.UUID.fromString(parts[5]); } catch (Exception e) { continue; }
            org.bukkit.Location loc = new org.bukkit.Location(w, x, y, z);
            presentNameEntities.put(loc, uuid);
        }
    }

    private void saveTitleUuidMap() {
        if (titlesConfig == null) titlesConfig = new org.bukkit.configuration.file.YamlConfiguration();
        java.util.List<String> list = new java.util.ArrayList<>();
        for (java.util.Map.Entry<org.bukkit.Location, java.util.UUID> e : presentNameEntities.entrySet()) {
            org.bukkit.Location l = e.getKey();
            if (l.getWorld() == null) continue;
            String s = l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ() + ";" + 0 + ";" + e.getValue().toString();
            list.add(s);
        }
        titlesConfig.set("titles", list);
        try { titlesConfig.save(new File(getDataFolder(), "titles.yml")); } catch (IOException ignored) {}
    }


    private void scheduleNextSpawn() {
        if (spawnTaskId != -1) getServer().getScheduler().cancelTask(spawnTaskId);
        int minMinutes = getConfig().getInt("spawn_settings.spawn-interval-min", 30);
        int maxMinutes = getConfig().getInt("spawn_settings.spawn-interval-max", 90);
        int delayMinutes = minMinutes + random.nextInt(Math.max(1, maxMinutes - minMinutes + 1));
        long delayTicks = delayMinutes * 60L * 20L;
        spawnTaskId = getServer().getScheduler().runTaskLater(this, () -> {
            int count = getConfig().getInt("spawn_settings.spawn-count", 10);
            spawnRandomPresents(count, null);
            if (getConfig().getBoolean("spawn_settings.spawn-message.enabled", true)) {
                String msg = getConfig().getString("spawn_settings.spawn-message.text", "&c&lSanta has delivered some presents!");
                getServer().broadcastMessage(translateHexColorCodes(msg));
            }
            scheduleNextSpawn();
        }, delayTicks).getTaskId();
        getLogger().info("Next present spawn scheduled in " + delayMinutes + " minutes");
    }

    private void spawnRandomPresents(int count, CommandSender feedback) {
        if (!spawnEnabled) { if (feedback != null) feedback.sendMessage("§cSpawning disabled"); return; }
        java.util.List<String> activeWorlds = getConfig().getStringList("spawn_settings.active-worlds");
        if (activeWorlds.isEmpty()) activeWorlds = java.util.Arrays.asList("world");
        java.util.List<Player> eligiblePlayers = new java.util.ArrayList<>();
        for (Player p : getServer().getOnlinePlayers()) {
            if (activeWorlds.contains(p.getWorld().getName())) eligiblePlayers.add(p);
        }
        if (eligiblePlayers.isEmpty()) { if (feedback != null) feedback.sendMessage("§eNo players online in active worlds"); return; }
        final java.util.List<Player> players = eligiblePlayers;
        final int[] spawned = {0};
        final int[] attempts = {0};
        final int maxAttempts = count * 50;
        final int attemptsPerTick = 50;
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                int tickAttempts = 0;
                while (spawned[0] < count && attempts[0] < maxAttempts && tickAttempts < attemptsPerTick) {
                    attempts[0]++;
                    tickAttempts++;
                    Player basePlayer = players.get(random.nextInt(players.size()));
                    if (!basePlayer.isOnline()) continue;
                    org.bukkit.World w = basePlayer.getWorld();
                    int bx = basePlayer.getLocation().getBlockX();
                    int bz = basePlayer.getLocation().getBlockZ();
                    int rx = bx + random.nextInt(301) - 150;
                    int rz = bz + random.nextInt(301) - 150;
                    org.bukkit.block.Block top = w.getHighestBlockAt(rx, rz);
                    org.bukkit.block.Block ground = top;
                    for (int dy = 0; dy < 6 && ground.getY() > w.getMinHeight(); dy++) {
                        org.bukkit.Material m = ground.getType();
                        if (m == org.bukkit.Material.AIR || m == org.bukkit.Material.SNOW || m == org.bukkit.Material.SNOW_BLOCK || m == org.bukkit.Material.TALL_GRASS || m == org.bukkit.Material.SEAGRASS || m == org.bukkit.Material.KELP || m == org.bukkit.Material.WATER || m == org.bukkit.Material.LAVA || m == org.bukkit.Material.AZALEA_LEAVES || m == org.bukkit.Material.OAK_LEAVES || m == org.bukkit.Material.SPRUCE_LEAVES || m == org.bukkit.Material.BIRCH_LEAVES || m == org.bukkit.Material.JUNGLE_LEAVES || m == org.bukkit.Material.ACACIA_LEAVES || m == org.bukkit.Material.DARK_OAK_LEAVES || m == org.bukkit.Material.MANGROVE_LEAVES || m == org.bukkit.Material.CHERRY_LEAVES) {
                            ground = ground.getRelative(0, -1, 0);
                        } else {
                            break;
                        }
                    }
                    org.bukkit.block.Block place = ground.getRelative(0, 1, 0);
                    if (place.getType() != org.bukkit.Material.AIR) continue;
                    if (!isAllowedSurface(ground.getType())) continue;
                    org.bukkit.Location base = place.getLocation();
                    if (isBlockedByDeadzone(base)) continue;
                    if (!isFarFromPlayers(base, 30.0)) continue;
                    if (!isFarFromPresents(base, 30.0)) continue;
                    place.setType(org.bukkit.Material.CHEST, false);
                    if (place.getState() instanceof org.bukkit.block.TileState) {
                        org.bukkit.block.TileState st = (org.bukkit.block.TileState) place.getState();
                        double specialChance = Math.max(0D, Math.min(1D, customConfig.getDouble("settings.spawn.specialChance", 0.01D)));
                        String type = random.nextDouble() < specialChance ? "special" : "common";
                        st.getPersistentDataContainer().set(presentTypeKey, PersistentDataType.STRING, type);
                        st.update(true, false);
                        org.bukkit.Location pl = place.getLocation();
                        spawnPresentName(pl, type);
                        presentLocations.add(place.getLocation());
                        getLogger().info("Spawned present at " + base.getBlockX() + "," + base.getBlockY() + "," + base.getBlockZ() + " near " + basePlayer.getName());
                        spawned[0]++;
                    } else {
                        place.setType(org.bukkit.Material.AIR, false);
                    }
                }
                if (spawned[0] >= count || attempts[0] >= maxAttempts) {
                    cancel();
                    savePresentLocations();
                    saveTitleUuidMap();
                    getLogger().info("Spawn attempt complete. Spawned: " + spawned[0] + "/" + count + ", attempts: " + attempts[0]);
                    if (feedback != null) feedback.sendMessage("§eSpawned " + spawned[0] + "/" + count + " presents (attempts: " + attempts[0] + ")");
                }
            }
        }.runTaskTimer(ChristmasPresents.this, 0L, 1L);
    }

    private boolean isAllowedSurface(org.bukkit.Material mat) {
        if (mat == org.bukkit.Material.WATER || mat == org.bukkit.Material.LAVA) return false;
        return mat.isSolid();
    }

    private boolean isBlockedByDeadzone(org.bukkit.Location loc) {
        if (loc.getWorld() == null) return true;
        if (loc.getWorld().getEnvironment() == org.bukkit.World.Environment.NORMAL) {
            int x = loc.getBlockX();
            int z = loc.getBlockZ();
            long d2 = 1L * x * x + 1L * z * z;
            if (d2 < 300L * 300L) return true;
        }
        for (DeadZone dz : deadZones) {
            if (dz.world.equals(loc.getWorld().getName())) {
                int x = loc.getBlockX();
                int y = loc.getBlockY();
                int z = loc.getBlockZ();
                if (x >= dz.minX && x <= dz.maxX && y >= dz.minY && y <= dz.maxY && z >= dz.minZ && z <= dz.maxZ) return true;
            }
        }
        return false;
    }

    private boolean isFarFromPlayers(org.bukkit.Location loc, double minDist) {
        double minSq = minDist * minDist;
        for (Player p : getServer().getOnlinePlayers()) {
            if (p.getWorld() != loc.getWorld()) continue;
            if (p.getLocation().distanceSquared(loc) < minSq) return false;
        }
        return true;
    }

    private boolean isFarFromPresents(org.bukkit.Location loc, double minDist) {
        double minSq = minDist * minDist;
        for (org.bukkit.Location l : presentLocations) {
            if (l.getWorld() != loc.getWorld()) continue;
            if (l.distanceSquared(loc) < minSq) return false;
        }
        return true;
    }

    private void loadDeadZones() {
        deadZones.clear();
        java.util.List<String> list = getConfig().getStringList("present_deadzones");
        if (list == null) return;
        for (String s : list) {
            String[] parts = s.split(";");
            if (parts.length != 7) continue;
            String w = parts[0];
            try {
                int x1 = Integer.parseInt(parts[1]);
                int y1 = Integer.parseInt(parts[2]);
                int z1 = Integer.parseInt(parts[3]);
                int x2 = Integer.parseInt(parts[4]);
                int y2 = Integer.parseInt(parts[5]);
                int z2 = Integer.parseInt(parts[6]);
                deadZones.add(new DeadZone(w, x1, y1, z1, x2, y2, z2));
            } catch (Exception ignored) {}
        }
    }

    private void saveDeadZones() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (DeadZone d : deadZones) {
            out.add(d.world + ";" + d.minX + ";" + d.minY + ";" + d.minZ + ";" + d.maxX + ";" + d.maxY + ";" + d.maxZ);
        }
        getConfig().set("present_deadzones", out);
        saveConfig();
    }

    private int[] parseXYZ(String arg) {
        String[] p = arg.split(",");
        if (p.length != 3) return null;
        try {
            return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])};
        } catch (Exception e) {
            return null;
        }
    }

    static class DeadZone {
        final String world;
        final int minX, minY, minZ;
        final int maxX, maxY, maxZ;
        DeadZone(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
            this.world = world;
            this.minX = Math.min(x1, x2);
            this.minY = Math.min(y1, y2);
            this.minZ = Math.min(z1, z2);
            this.maxX = Math.max(x1, x2);
            this.maxY = Math.max(y1, y2);
            this.maxZ = Math.max(z1, z2);
        }
    }

    private void playOpenAnimation(org.bukkit.Location loc, boolean hasItem, ItemStack reward, Player player) {
        org.bukkit.World w = loc.getWorld();
        if (w == null) return;
        org.bukkit.entity.Firework fw = (org.bukkit.entity.Firework) w.spawnEntity(loc.clone().add(0.5, 0.2, 0.5), org.bukkit.entity.EntityType.FIREWORK);
        org.bukkit.inventory.meta.FireworkMeta meta = fw.getFireworkMeta();
        meta.addEffect(org.bukkit.FireworkEffect.builder().withColor(org.bukkit.Color.RED, org.bukkit.Color.GREEN).with(org.bukkit.FireworkEffect.Type.BALL_LARGE).trail(true).flicker(true).build());
        meta.setPower(0);
        fw.setFireworkMeta(meta);
        try { fw.getPersistentDataContainer().set(harmlessKey, PersistentDataType.BYTE, (byte)1); } catch (Throwable ignored) {}
        getServer().getScheduler().runTaskLater(this, fw::detonate, 10L);
        org.bukkit.Location origin = loc.clone().add(0.5, 1.0, 0.5);
        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            public void run() {
                ticks += 4;
                for (int i = 0; i < 4; i++) {
                    org.bukkit.entity.Snowball sb = (org.bukkit.entity.Snowball) w.spawnEntity(origin, org.bukkit.entity.EntityType.SNOWBALL);
                    sb.setShooter(player);
                    try { sb.getPersistentDataContainer().set(harmlessKey, PersistentDataType.BYTE, (byte)1); } catch (Throwable ignored) {}
                    double angle = (random.nextDouble() * Math.PI * 2);
                    double radius = random.nextDouble() * 0.6;
                    double vx = Math.cos(angle) * radius;
                    double vz = Math.sin(angle) * radius;
                    double vy = 0.6 + random.nextDouble() * 0.4;
                    sb.setVelocity(new org.bukkit.util.Vector(vx, vy, vz));
                }
                if (ticks >= 60) {
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, 4L);
        executeEffect(player, "snow_overlay");
        getServer().getScheduler().runTaskLater(this, () -> player.playSound(loc, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f), 15L);
        if (hasItem && reward != null) {
            org.bukkit.entity.Item item = w.dropItem(loc.clone().add(0.5, 1.1, 0.5), reward);
            item.setGravity(false);
            item.setPickupDelay(20 * 5);
            item.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            getServer().getScheduler().runTaskLater(this, () -> {
                if (!item.isValid()) return;
                item.setGravity(true);
                item.setPickupDelay(0);
            }, 40L);
        }
    }

    @EventHandler
    public void onChunkUnload(org.bukkit.event.world.ChunkUnloadEvent event) {
        org.bukkit.Chunk chunk = event.getChunk();
        java.util.List<org.bukkit.Location> toRemove = new java.util.ArrayList<>();
        for (java.util.Map.Entry<org.bukkit.Location, java.util.UUID> e : new java.util.HashMap<>(presentNameEntities).entrySet()) {
            org.bukkit.Location l = e.getKey();
            if (l.getWorld() == chunk.getWorld() && l.getChunk().getX() == chunk.getX() && l.getChunk().getZ() == chunk.getZ()) {
                removePresentName(l);
                toRemove.add(l);
            }
        }
        for (org.bukkit.Location l : toRemove) presentNameEntities.remove(l);
    }

    @EventHandler
    public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
        org.bukkit.Chunk chunk = event.getChunk();
        java.util.List<org.bukkit.Location> toRestore = new java.util.ArrayList<>();
        for (org.bukkit.Location l : presentLocations) {
            if (l.getWorld() != chunk.getWorld()) continue;
            if (l.getChunk().getX() != chunk.getX() || l.getChunk().getZ() != chunk.getZ()) continue;
            toRestore.add(l);
        }
        for (org.bukkit.Location l : toRestore) {
            org.bukkit.block.Block b = l.getWorld().getBlockAt(l);
            if (!(b.getState() instanceof org.bukkit.block.TileState)) continue;
            org.bukkit.block.TileState st = (org.bukkit.block.TileState) b.getState();
            String type = st.getPersistentDataContainer().get(presentTypeKey, PersistentDataType.STRING);
            if (type == null) continue;
            ensureTitleFor(l, type);
        }
    }

    private void savePresentLocations() {
        java.util.List<String> list = new java.util.ArrayList<>();
        for (org.bukkit.Location l : presentLocations) {
            if (l.getWorld() == null) continue;
            String s = l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ();
            list.add(s);
        }
        customConfig.set("worldPresents", list);
        saveCustomConfig();
    }

    private void loadPresentLocations() {
        presentLocations.clear();
        java.util.List<String> list = customConfig.getStringList("worldPresents");
        if (list == null) return;
        for (String s : list) {
            String[] parts = s.split(";");
            if (parts.length != 4) continue;
            org.bukkit.World w = getServer().getWorld(parts[0]);
            if (w == null) continue;
            int x, y, z;
            try { x = Integer.parseInt(parts[1]); y = Integer.parseInt(parts[2]); z = Integer.parseInt(parts[3]); } catch (Exception e) { continue; }
            org.bukkit.Location loc = new org.bukkit.Location(w, x, y, z);
            if (w.getBlockAt(x, y, z).getType() == org.bukkit.Material.CHEST) presentLocations.add(loc);
        }
    }

    private void restorePresentTitles() {
        java.util.Set<org.bukkit.Location> toRemove = new java.util.HashSet<>();
        for (org.bukkit.Location loc : new java.util.HashSet<>(presentLocations)) {
            org.bukkit.block.Block b = loc.getWorld().getBlockAt(loc);
            if (!(b.getState() instanceof org.bukkit.block.TileState)) { toRemove.add(loc); continue; }
            org.bukkit.block.TileState st = (org.bukkit.block.TileState) b.getState();
            String type = st.getPersistentDataContainer().get(presentTypeKey, PersistentDataType.STRING);
            if (type == null) { toRemove.add(loc); continue; }
            ensureTitleFor(loc, type);
        }
        if (!toRemove.isEmpty()) {
            presentLocations.removeAll(toRemove);
            savePresentLocations();
        }
    }

    private int removeAllPresentsBatch(int amount) {
        int removed = 0;
        java.util.List<org.bukkit.Location> list = new java.util.ArrayList<>(presentLocations);
        for (org.bukkit.Location loc : list) {
            if (removed >= amount) break;
            org.bukkit.block.Block b = loc.getBlock();
            if (b.getType() == org.bukkit.Material.CHEST) b.setType(org.bukkit.Material.AIR);
            removePresentName(loc);
            presentLocations.remove(loc);
            removed++;
        }
        if (removed > 0) savePresentLocations();
        return removed;
    }
}
