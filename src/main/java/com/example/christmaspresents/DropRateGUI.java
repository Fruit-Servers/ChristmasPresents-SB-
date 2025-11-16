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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class DropRateGUI implements Listener {
    private final ChristmasPresents plugin;
    private final Component MAIN_TITLE = Component.text("Present Drop Rates").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD);
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
            Component.text("Common Presents").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
            Component.text("Click to configure drop rates").color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
            Component.text("for common presents.").color(NamedTextColor.GRAY)
        );
        
        ItemStack specialPresents = createMenuItem(
            Material.ENDER_CHEST,
            Component.text("Special Presents").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
            Component.text("Click to configure drop rates").color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
            Component.text("for special presents.").color(NamedTextColor.GRAY)
        );
            
        inv.setItem(3, commonPresents);
        inv.setItem(5, specialPresents);
        ItemStack filler = createMenuItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, filler);
        
        player.openInventory(inv);
        openType.remove(player.getUniqueId());
    }
    
    public void openItemList(Player player, String presentType) {
        List<ChristmasPresents.PresentEntry> list = plugin.getDrops().getOrDefault(presentType, java.util.Collections.emptyList());
        List<ChristmasPresents.PresentEntry> itemEntries = list.stream().collect(Collectors.toList());
        
        int size = (int) Math.ceil((itemEntries.size() + 8) / 9.0) * 9;
        size = Math.max(9, Math.min(54, size));
        
        Inventory inv = Bukkit.createInventory(null, size, Component.text("Select Items: ").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD).append(Component.text(presentType).color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD)));
        
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
                m.displayName(Component.text(name).color(NamedTextColor.AQUA));
                item.setItemMeta(m);
            } else {
                item = entry.item.clone();
            }
            double chance = entry.chance;
            
            ItemMeta meta = item.getItemMeta();
            java.util.List<Component> lore = meta.lore() != null ? meta.lore() : new java.util.ArrayList<>();
            lore.add(Component.text(" "));
            lore.add(Component.text("Current Drop Chance: ").color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD).append(Component.text(String.format("%.1f%%", chance)).color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)));
            if (entry.kind == ChristmasPresents.EntryKind.EFFECT) lore.add(Component.text("Type: ").color(NamedTextColor.GRAY).append(Component.text(entry.effectKey).color(NamedTextColor.AQUA)));
            lore.add(Component.text("Status: ").color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD).append(Component.text(entry.enabled ? "Enabled" : "Disabled").color(entry.enabled ? NamedTextColor.GREEN : NamedTextColor.RED).decorate(TextDecoration.BOLD)));
            lore.add(Component.text(" "));
            lore.add(Component.text("Left-Click ").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD).append(Component.text("to increase by 5%" ).color(NamedTextColor.GRAY)));
            lore.add(Component.text("Right-Click ").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD).append(Component.text("to decrease by 5%" ).color(NamedTextColor.GRAY)));
            lore.add(Component.text("Middle-Click ").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD).append(Component.text("to toggle enabled" ).color(NamedTextColor.GRAY)));
            
            meta.lore(lore);
            meta.getPersistentDataContainer().set(entryKey, PersistentDataType.STRING, entry.id);
            item.setItemMeta(meta);
            
            inv.setItem(i, item);
        }
        
        ItemStack backButton = createMenuItem(Material.BARRIER, Component.text("Back to Main Menu").color(NamedTextColor.RED).decorate(TextDecoration.BOLD));
        ItemStack saveButton = createMenuItem(Material.EMERALD_BLOCK, Component.text("Save").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
        inv.setItem(inv.getSize() - 1, backButton);
        inv.setItem(inv.getSize() - 2, saveButton);
        ItemStack filler = createMenuItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, filler);
        
        player.openInventory(inv);
        openType.put(player.getUniqueId(), presentType);
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        Component title = event.getView().title();
        
        if (title.equals(MAIN_TITLE)) {
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
                player.sendMessage(Component.text("Saved drop rates").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
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
                    else if (event.getClick() == ClickType.MIDDLE) { entry.enabled = !entry.enabled; player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, entry.enabled ? 1.4f : 0.6f); player.sendMessage(Component.text(entry.enabled ? "Enabled" : "Disabled").color(entry.enabled ? NamedTextColor.GREEN : NamedTextColor.RED).decorate(TextDecoration.BOLD)); openItemList(player, presentType); return; }
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
    
    private ItemStack createMenuItem(Material material, Component name, Component... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        if (lore != null && lore.length > 0) meta.lore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }
}
