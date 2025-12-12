package com.example.christmaspresents;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.ChatColor;

public class DropRateGUI implements Listener {
    private final ChristmasPresents plugin;
    private final String MAIN_TITLE = ChatColor.GOLD + "" + ChatColor.BOLD + "Present Drop Rates";
    private final NamespacedKey entryKey;
    private final Map<UUID, String> openType = new HashMap<>();

    public DropRateGUI(ChristmasPresents plugin) {
        this.plugin = plugin;
        this.entryKey = new NamespacedKey(plugin, "entry_id");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, MAIN_TITLE);
        
        ItemStack commonPresents = createMenuItem(
            Material.CHEST,
            ChatColor.GREEN + "" + ChatColor.BOLD + "Common Presents",
            ChatColor.GRAY + "" + ChatColor.BOLD + "Click to configure drop rates",
            ChatColor.GRAY + "for common presents."
        );
        
        ItemStack specialPresents = createMenuItem(
            Material.ENDER_CHEST,
            ChatColor.GOLD + "" + ChatColor.BOLD + "Special Presents",
            ChatColor.GRAY + "" + ChatColor.BOLD + "Click to configure drop rates",
            ChatColor.GRAY + "for special presents."
        );
            
        inv.setItem(3, commonPresents);
        inv.setItem(5, specialPresents);
        ItemStack filler = createMenuItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, filler);
        
        player.openInventory(inv);
        openType.remove(player.getUniqueId());
    }
    
    public void openItemList(Player player, String presentType) {
        List<ChristmasPresents.PresentEntry> list = plugin.getDrops().getOrDefault(presentType, java.util.Collections.emptyList());
        List<ChristmasPresents.PresentEntry> itemEntries = list.stream().collect(Collectors.toList());
        
        int size = (int) Math.ceil((itemEntries.size() + 8) / 9.0) * 9;
        size = Math.max(9, Math.min(54, size));
        
        Inventory inv = Bukkit.createInventory(null, size, ChatColor.GOLD + "" + ChatColor.BOLD + "Select Items: " + ChatColor.WHITE + ChatColor.BOLD + presentType);
        
        for (int i = 0; i < itemEntries.size(); i++) {
            ChristmasPresents.PresentEntry entry = itemEntries.get(i);
            ItemStack item;
            if (entry.kind == ChristmasPresents.EntryKind.EFFECT) {
                Material mat = Material.PAPER;
                String name = "Effect";
                if ("snow_overlay".equalsIgnoreCase(entry.effectKey)) { mat = Material.SNOWBALL; name = "Snow Overlay"; }
                else if ("random_message".equalsIgnoreCase(entry.effectKey)) { mat = Material.WRITABLE_BOOK; name = "Random Message"; }
                else if ("money_reward".equalsIgnoreCase(entry.effectKey)) { mat = Material.GOLD_INGOT; name = "Money Reward"; }
                item = new ItemStack(mat);
                ItemMeta m = item.getItemMeta();
                m.setDisplayName(ChatColor.AQUA + name);
                item.setItemMeta(m);
            } else {
                item = entry.item.clone();
            }
            double chance = entry.chance;
            
            ItemMeta meta = item.getItemMeta();
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            lore.add(" ");
            lore.add(ChatColor.GRAY + "" + ChatColor.BOLD + "Current Drop Chance: " + ChatColor.YELLOW + ChatColor.BOLD + String.format("%.1f%%", chance));
            if (entry.kind == ChristmasPresents.EntryKind.EFFECT) lore.add(ChatColor.GRAY + "Type: " + ChatColor.AQUA + entry.effectKey);
            lore.add(ChatColor.GRAY + "" + ChatColor.BOLD + "Status: " + (entry.enabled ? ChatColor.GREEN : ChatColor.RED) + ChatColor.BOLD + (entry.enabled ? "Enabled" : "Disabled"));
            lore.add(" ");
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Left-Click " + ChatColor.GRAY + "to increase by 5%");
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Right-Click " + ChatColor.GRAY + "to decrease by 5%");
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Middle-Click " + ChatColor.GRAY + "to toggle enabled");
            
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(entryKey, PersistentDataType.STRING, entry.id);
            item.setItemMeta(meta);
            
            inv.setItem(i, item);
        }
        
        ItemStack backButton = createMenuItem(Material.BARRIER, ChatColor.RED + "" + ChatColor.BOLD + "Back to Main Menu");
        ItemStack saveButton = createMenuItem(Material.EMERALD_BLOCK, ChatColor.GREEN + "" + ChatColor.BOLD + "Save");
        inv.setItem(inv.getSize() - 1, backButton);
        inv.setItem(inv.getSize() - 2, saveButton);
        ItemStack filler = createMenuItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, filler);
        
        player.openInventory(inv);
        openType.put(player.getUniqueId(), presentType);
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        String titleText = event.getView().getTitle();
        
        if (titleText.equals(MAIN_TITLE)) {
            event.setCancelled(true);
            
            if (event.getCurrentItem() == null) return;
            
            switch (event.getRawSlot()) {
                case 3:
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                    openItemList(player, "common");
                    break;
                case 5:
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                    openItemList(player, "special");
                    break;
                default:
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.6f);
                    break;
            }
        } 
        else if (openType.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            
            if (event.getRawSlot() == event.getView().getTopInventory().getSize() - 2 &&
                event.getCurrentItem() != null &&
                event.getCurrentItem().getType() == Material.EMERALD_BLOCK) {
                plugin.saveDropsFromMemory();
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
                player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "Saved drop rates");
                openMainMenu(player);
                openType.remove(player.getUniqueId());
                return;
            }
            
            if (event.getRawSlot() == event.getView().getTopInventory().getSize() - 1 && 
                event.getCurrentItem() != null && 
                event.getCurrentItem().getType() == Material.BARRIER) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.9f);
                openMainMenu(player);
                openType.remove(player.getUniqueId());
                return;
            }
            
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.8f);
                return;
            }
            
            if (event.getCurrentItem() != null && event.getCurrentItem().hasItemMeta()) {
                String presentType = openType.get(player.getUniqueId());
                String id = event.getCurrentItem().getItemMeta().getPersistentDataContainer().get(entryKey, PersistentDataType.STRING);
                ChristmasPresents.PresentEntry entry = findEntryById(presentType, id);
                if (entry != null) {
                    double currentChance = entry.chance;
                    if (event.getClick() == ClickType.LEFT) { currentChance = Math.min(100.0, currentChance + 5.0); player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.1f);} 
                    else if (event.getClick() == ClickType.RIGHT) { currentChance = Math.max(0.0, currentChance - 5.0); player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.9f);} 
                    else if (event.getClick() == ClickType.MIDDLE) { entry.enabled = !entry.enabled; player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, entry.enabled ? 1.4f : 0.6f); player.sendMessage((entry.enabled ? ChatColor.GREEN : ChatColor.RED) + "" + ChatColor.BOLD + (entry.enabled ? "Enabled" : "Disabled")); openItemList(player, presentType); return; }
                    entry.chance = currentChance;
                    openItemList(player, presentType);
                }
            }
        }
    }
    
    private ChristmasPresents.PresentEntry findEntryById(String presentType, String id) {
        if (id == null) return null;
        List<ChristmasPresents.PresentEntry> list = plugin.getDrops().getOrDefault(presentType, java.util.Collections.emptyList());
        for (ChristmasPresents.PresentEntry e : list) if (e.id.equals(id)) return e;
        return null;
    }
    
    private ItemStack createMenuItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null && lore.length > 0) meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }
}
