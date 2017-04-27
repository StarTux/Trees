package com.winthier.trees;

import com.winthier.custom.item.CustomItem;
import com.winthier.custom.item.ItemContext;
import com.winthier.custom.item.ItemDescription;
import com.winthier.custom.item.UncraftableItem;
import com.winthier.generic_events.GenericEventsPlugin;
import com.winthier.generic_events.ItemNameEvent;
import lombok.Getter;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
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
        Block block = event.getClickedBlock();
        TreeType treeType = TreeType.of(block);
        Location loc = block.getLocation();
        if (treeType == null) {
            block.getWorld().spawnParticle(Particle.TOTEM, block.getRelative(event.getBlockFace()).getLocation(loc).add(0.5, 0.5, 0.5), 4, 0.25, 0.25, 0.25, 0);
            block.getWorld().playSound(block.getLocation(loc), Sound.ENTITY_SLIME_ATTACK, SoundCategory.BLOCKS, 2.0f, 0.5f);
            return;
        }
        event.setCancelled(true);
        Player player = context.getPlayer();
        if (!GenericEventsPlugin.getInstance().playerCanBuild(player, block)) return;
        Tree tree = plugin.getRandomTree(treeType);
        if (tree == null) return;
        if (tree.isBlocked(block)) {
            block.getWorld().spawnParticle(Particle.TOTEM, block.getLocation(loc).add(0.5, 0.5, 0.5), 4, 0.25, 0.25, 0.25, 0);
            block.getWorld().playSound(block.getLocation(loc), Sound.ENTITY_SLIME_DEATH, SoundCategory.BLOCKS, 2.0f, 0.5f);
            return;
        }
        plugin.getLogger().info(String.format("Growing %s for %s", tree.getName(), player.getName()));
        tree.growSlowly(plugin, player, block);
        block.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, block.getLocation(loc).add(0.5, 0.5, 0.5), 8, 0.25, 0.25, 0.25, 0);
        block.getWorld().playSound(block.getLocation(loc), Sound.ENTITY_SLIME_JUMP, SoundCategory.BLOCKS, 2.0f, 1.5f);
        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack item = context.getItemStack();
            item.setAmount(item.getAmount() - 1);
        }
    }

    @EventHandler
    public void onItemName(ItemNameEvent event, ItemContext context) {
        event.setItemName(itemDescription.getDisplayName());
    }
}
