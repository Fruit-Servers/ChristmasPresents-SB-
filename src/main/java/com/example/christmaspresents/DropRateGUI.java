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
import org.bukkit.ChatColor;

public class DropRateGUI implements Listener {
    private final ChristmasPresents plugin;
    private final String MAIN_TITLE = ChatColor.GOLD + "" + ChatColor.BOLD + "Present Drop Rates";
    private final String LIST_TITLE_PREFIX = ChatColor.GOLD + "" + ChatColor.BOLD + "Items: ";
    private final NamespacedKey entryKey;
    private final Map<UUID, String> openType = new HashMap<>();
    private final Map<UUID, Integer> openPage = new HashMap<>();
    private static final int ITEMS_PER_PAGE = 45;

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
        openPage.remove(player.getUniqueId());
    }
    
    public void openItemList(Player player, String presentType) {
        openItemList(player, presentType, 0);
    }
    
    public void openItemList(Player player, String presentType, int page) {
        List<ChristmasPresents.PresentEntry> list = plugin.getDrops().getOrDefault(presentType, java.util.Collections.emptyList());
        int totalPages = Math.max(1, (int) Math.ceil(list.size() / (double) ITEMS_PER_PAGE));
        page = Math.max(0, Math.min(page, totalPages - 1));
        
        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, list.size());
        
        Inventory inv = Bukkit.createInventory(null, 54, LIST_TITLE_PREFIX + presentType + " (" + (page + 1) + "/" + totalPages + ")");
        
        for (int i = startIndex; i < endIndex; i++) {
            ChristmasPresents.PresentEntry entry = list.get(i);
            ItemStack item;
            if (entry.kind == ChristmasPresents.EntryKind.EFFECT) {
                Material mat = Material.PAPER;
                String name = "Effect";
                if ("snow_overlay".equalsIgnoreCase(entry.effectKey)) { mat = Material.SNOWBALL; name = "Snow Overlay"; }
                else if ("random_message".equalsIgnoreCase(entry.effectKey)) { mat = Material.WRITABLE_BOOK; name = "Random Message"; }
                else if ("money_reward".equalsIgnoreCase(entry.effectKey)) { mat = Material.GOLD_INGOT; name = "Money Reward"; }
                item = new ItemStack(mat);
                ItemMeta m = item.getItemMeta();
                if (m != null) {
                    m.setDisplayName(ChatColor.AQUA + name);
                    item.setItemMeta(m);
                }
            } else {
                item = entry.item.clone();
            }
            double chance = entry.chance;
            
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add(" ");
                lore.add(ChatColor.GRAY + "" + ChatColor.BOLD + "Drop Chance: " + ChatColor.YELLOW + ChatColor.BOLD + String.format("%.1f%%", chance));
                if (entry.kind == ChristmasPresents.EntryKind.EFFECT) lore.add(ChatColor.GRAY + "Type: " + ChatColor.AQUA + entry.effectKey);
                lore.add(ChatColor.GRAY + "" + ChatColor.BOLD + "Status: " + (entry.enabled ? ChatColor.GREEN : ChatColor.RED) + ChatColor.BOLD + (entry.enabled ? "Enabled" : "Disabled"));
                lore.add(" ");
                lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Left-Click " + ChatColor.GRAY + "+5%");
                lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Right-Click " + ChatColor.GRAY + "-5%");
                lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Middle-Click " + ChatColor.GRAY + "toggle");
                
                meta.setLore(lore);
                meta.getPersistentDataContainer().set(entryKey, PersistentDataType.STRING, entry.id);
                item.setItemMeta(meta);
            }
            
            inv.setItem(i - startIndex, item);
        }
        
        ItemStack filler = createMenuItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);
        
        if (page > 0) {
            inv.setItem(45, createMenuItem(Material.ARROW, ChatColor.YELLOW + "" + ChatColor.BOLD + "Previous Page"));
        }
        if (page < totalPages - 1) {
            inv.setItem(53, createMenuItem(Material.ARROW, ChatColor.YELLOW + "" + ChatColor.BOLD + "Next Page"));
        }
        inv.setItem(49, createMenuItem(Material.BARRIER, ChatColor.RED + "" + ChatColor.BOLD + "Back"));
        inv.setItem(50, createMenuItem(Material.EMERALD_BLOCK, ChatColor.GREEN + "" + ChatColor.BOLD + "Save"));
        
        player.openInventory(inv);
        openType.put(player.getUniqueId(), presentType);
        openPage.put(player.getUniqueId(), page);
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String titleText = event.getView().getTitle();
        
        if (titleText.equals(MAIN_TITLE)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
            
            switch (event.getRawSlot()) {
                case 3:
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                    openItemList(player, "common");
                    break;
                case 5:
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                    openItemList(player, "special");
                    break;
            }
            return;
        }
        
        if (titleText.startsWith(LIST_TITLE_PREFIX)) {
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            event.setCancelled(true);
            
            String presentType = openType.get(player.getUniqueId());
            int currentPage = openPage.getOrDefault(player.getUniqueId(), 0);
            if (presentType == null) return;
            
            int slot = event.getRawSlot();
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;
            
            if (slot == 45 && clicked.getType() == Material.ARROW) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                openItemList(player, presentType, currentPage - 1);
                return;
            }
            if (slot == 53 && clicked.getType() == Material.ARROW) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                openItemList(player, presentType, currentPage + 1);
                return;
            }
            if (slot == 49 && clicked.getType() == Material.BARRIER) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.9f);
                openMainMenu(player);
                return;
            }
            if (slot == 50 && clicked.getType() == Material.EMERALD_BLOCK) {
                plugin.saveDropsFromMemory();
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
                player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "Saved drop rates");
                return;
            }
            
            if (clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;
            
            if (slot >= 0 && slot < 45) {
                ItemMeta meta = clicked.getItemMeta();
                if (meta == null) return;
                String id = meta.getPersistentDataContainer().get(entryKey, PersistentDataType.STRING);
                if (id == null) return;
                
                ChristmasPresents.PresentEntry entry = findEntryById(presentType, id);
                if (entry == null) return;
                
                ClickType click = event.getClick();
                if (click == ClickType.LEFT) {
                    entry.chance = Math.min(100.0, entry.chance + 5.0);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.1f);
                    openItemList(player, presentType, currentPage);
                } else if (click == ClickType.RIGHT) {
                    entry.chance = Math.max(0.0, entry.chance - 5.0);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.9f);
                    openItemList(player, presentType, currentPage);
                } else if (click == ClickType.MIDDLE || click == ClickType.CREATIVE) {
                    entry.enabled = !entry.enabled;
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, entry.enabled ? 1.4f : 0.6f);
                    player.sendMessage((entry.enabled ? ChatColor.GREEN : ChatColor.RED) + "" + ChatColor.BOLD + (entry.enabled ? "Enabled" : "Disabled"));
                    openItemList(player, presentType, currentPage);
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
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && lore.length > 0) meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}
