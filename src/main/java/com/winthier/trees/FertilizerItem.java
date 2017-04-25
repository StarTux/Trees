package com.winthier.trees;

import com.winthier.custom.item.CustomItem;
import com.winthier.custom.item.ItemContext;
import com.winthier.custom.item.ItemDescription;
import com.winthier.custom.item.UncraftableItem;
import com.winthier.generic_events.GenericEventsPlugin;
import lombok.Getter;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

@Getter
public final class FertilizerItem implements CustomItem, UncraftableItem {
    public static final String CUSTOM_ID = "trees:fertilizer";
    private final TreesPlugin plugin;
    private final String customId = CUSTOM_ID;
    private final ItemDescription itemDescription;
    private final ItemStack itemStack;

    FertilizerItem(TreesPlugin plugin) {
        this.plugin = plugin;
        ItemStack item = new ItemStack(Material.INK_SACK, 1, (short)3); // Cocoa beans
        ItemDescription desc = ItemDescription.of(plugin.getConfig().getConfigurationSection("fertilizer.description"));
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        desc.apply(item);
        this.itemStack = item;
        this.itemDescription = desc;
    }

    @Override
    public ItemStack spawnItemStack(int amount) {
        ItemStack item = itemStack.clone();
        item.setAmount(amount);
        return item;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event, ItemContext context) {
        if (!event.hasBlock()) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        TreeType treeType = TreeType.of(event.getClickedBlock());
        if (treeType == null) return;
        event.setCancelled(true);
        Player player = context.getPlayer();
        Block block = event.getClickedBlock();
        if (!GenericEventsPlugin.getInstance().playerCanBuild(player, block)) return;
        Tree tree = plugin.getRandomTree(treeType);
        if (tree == null) return;
        if (tree.isBlocked(block)) return;
        tree.growSlowly(plugin, player, block);
        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack item = context.getItemStack();
            item.setAmount(item.getAmount() - 1);
        }
    }
}
