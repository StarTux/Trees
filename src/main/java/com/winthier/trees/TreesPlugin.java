package com.winthier.trees;

import com.winthier.custom.event.CustomRegisterEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BlockIterator;

@Getter
public final class TreesPlugin extends JavaPlugin implements Listener {
    private Tree cachedTree;
    private Block cachedRootBlock;
    private List<Tree> trees;
    private final Random random = new Random(System.currentTimeMillis());

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onCustomRegister(CustomRegisterEvent event) {
        trees = null;
        reloadConfig();
        event.addItem(new FertilizerItem(this));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        final Player player = sender instanceof Player ? (Player)sender : null;
        String cmd = args.length == 0 ? null : args[0].toLowerCase();
        if (cmd == null) {
            return false;
        } else if (cmd.equals("select")) {
            if (player == null) return false;
            Block rootBlock = getTargetBlock(player);
            if (rootBlock == null) return false;
            final Tree tree = new Tree();
            tree.select(rootBlock);
            EnumSet<Material> mats = EnumSet.noneOf(Material.class);
            for (Tree.Voxel voxel: tree.getVoxels()) mats.add(Material.getMaterial(voxel.getType()));
            StringBuilder sb = new StringBuilder("Total " + tree.getVoxels().size() + ":");
            for (Material mat: mats) sb.append(" ").append(mat.name().toLowerCase());
            player.sendMessage(sb.toString());
            this.cachedTree = tree;
            this.cachedRootBlock = rootBlock;
            tree.highlight(player, rootBlock);
            new BukkitRunnable() {
                @Override public void run() {
                    if (!player.isValid()) return;
                    tree.show(player, rootBlock);
                }
            }.runTaskLater(this, 40);
        } else if (cmd.equals("show")) {
            if (player == null) return false;
            Tree tree = this.cachedTree;
            if (tree == null) return false;
            Block block = this.cachedRootBlock;
            tree.highlight(player, block);
            new BukkitRunnable() {
                @Override public void run() {
                    if (!player.isValid()) return;
                    tree.show(player, block);
                }
            }.runTaskLater(this, 40);
            player.sendMessage("Showing tree");
        } else if (cmd.equals("save") && args.length == 3) {
            if (player == null) return false;
            Tree tree = this.cachedTree;
            if (tree == null) return false;
            String filename = args[2] + ".yml";
            tree.setType(TreeType.valueOf(args[1].toUpperCase()));
            YamlConfiguration config = new YamlConfiguration();
            tree.serialize(config);
            getTreesFolder().mkdirs();
            File file = new File(getTreesFolder(), filename);
            try {
                config.save(file);
                player.sendMessage("Tree saved at " + file);
                trees = null;
            } catch (IOException ioe) {
                ioe.printStackTrace();
                player.sendMessage("Cannot save. See console.");
            }
        } else if (cmd.equals("grow")) {
            if (player == null) return false;
            Tree tree = this.cachedTree;
            if (tree == null) return false;
            tree.grow(player.getLocation().getBlock());
            player.sendMessage("Tree grown.");
        } else {
            return false;
        }
        return true;
    }

    private static Block getTargetBlock(Player player) {
        BlockIterator iter = new BlockIterator(player, 16);
        int count = 0;
        while (iter.hasNext() && count < 16) {
            count += 1;
            Block block = iter.next();
            if (block.getType() != Material.AIR) {
                return block;
            }
        }
        return null;
    }

    File getTreesFolder() {
        return new File(getDataFolder(), "trees");
    }

    List<Tree> getTrees() {
        if (trees == null) {
            trees = new ArrayList<>();
            File dir = getTreesFolder();
            if (dir.isDirectory()) {
                for (File file: dir.listFiles()) {
                    YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                    Tree tree = new Tree();
                    tree.deserialize(config);
                    trees.add(tree);
                }
            }
        }
        return trees;
    }

    public Tree getRandomTree(TreeType treeType) {
        List<Tree> myTrees = new ArrayList<>();
        for (Tree tree: getTrees()) {
            if (tree.getType() == treeType) myTrees.add(tree);
        }
        if (myTrees.isEmpty()) return null;
        return myTrees.get(random.nextInt(myTrees.size()));
    }
}
