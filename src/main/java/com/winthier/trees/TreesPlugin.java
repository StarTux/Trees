package com.winthier.trees;

import com.winthier.custom.event.CustomRegisterEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;
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
    private List<Tree> trees;
    private final Random random = new Random(System.currentTimeMillis());
    private final HashMap<UUID, Session> sessions = new HashMap<>();

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
            session(player).cachedTree = tree;
            session(player).cachedRootBlock = rootBlock;
            tree.highlight(player, rootBlock);
            new BukkitRunnable() {
                @Override public void run() {
                    if (!player.isValid()) return;
                    tree.show(player, rootBlock);
                }
            }.runTaskLater(this, 40);
        } else if (cmd.equals("highlight") || cmd.equals("hl")) {
            if (player == null) return false;
            Tree tree = session(player).cachedTree;
            if (tree == null) return false;
            Block block = session(player).cachedRootBlock;
            tree.highlight(player, block);
            new BukkitRunnable() {
                @Override public void run() {
                    if (!player.isValid()) return;
                    tree.show(player, block);
                }
            }.runTaskLater(this, 40);
            player.sendMessage("Highlighting tree");
        } else if (cmd.equals("save") && args.length == 3) {
            if (player == null) return false;
            TreeType treeType = TreeType.valueOf(args[1].toUpperCase());
            Tree tree = session(player).cachedTree;
            if (tree == null) return false;
            String name = session(player).name + "." + args[2];
            String filename = name + ".yml";
            tree.setType(treeType);
            tree.setName(name);
            tree.setAuthor(session(player).uuid);
            YamlConfiguration config = new YamlConfiguration();
            tree.serialize(config);
            getTreesFolder().mkdirs();
            File file = new File(getTreesFolder(), filename);
            try {
                config.save(file);
                player.sendMessage("Tree saved as " + name);
                trees = null;
            } catch (IOException ioe) {
                ioe.printStackTrace();
                player.sendMessage("Cannot save. See console.");
            }
        } else if (cmd.equals("load") && args.length == 2) {
            if (player == null) return false;
            String name = args[1];
            Tree found = null;
            for (Tree tree: getTrees()) {
                if (name.equalsIgnoreCase(tree.getName())) {
                    found = tree;
                    break;
                }
            }
            if (found != null) {
                session(player).cachedTree = found;
                player.sendMessage("Tree loaded: " + found.getName());
            } else {
                player.sendMessage("Tree not found: " + name);
            }
        } else if (cmd.equals("grow")) {
            if (player == null) return false;
            Tree tree = session(player).cachedTree;
            if (tree == null) return false;
            tree.grow(getTargetBlock(player).getRelative(0, 1, 0));
            player.sendMessage("Tree grown.");
        } else if (cmd.equals("show")) {
            if (player == null) return false;
            Tree tree = session(player).cachedTree;
            if (tree == null) return false;
            Block block = getTargetBlock(player);
            if (block == null) return false;
            Block rootBlock = block.getRelative(0, 1, 0);
            tree.show(player, rootBlock);
            new BukkitRunnable() {
                @Override public void run() {
                    tree.hide(player, rootBlock);
                }
            }.runTaskLater(this, 60);
            player.sendMessage("Tree shown.");
        } else if (cmd.equals("clear")) {
            if (player == null) return false;
            sessions.remove(player.getUniqueId());
            player.sendMessage("Session cleared");
        } else if (cmd.equals("mask") && (args.length == 3 || args.length == 2)) {
            String name = args[1];
            UUID uuid;
            if (args.length == 3) {
                uuid = UUID.fromString(args[2]);
            } else if (getServer().getPlayerExact(name) != null) {
                uuid = getServer().getPlayerExact(name).getUniqueId();
            } else {
                uuid = getServer().getOfflinePlayer(name).getUniqueId();
            }
            session(player).name = name;
            session(player).uuid = uuid;
            player.sendMessage("Masked as " + name + ": " + uuid);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return null;
        String cmd = args[0].toLowerCase();
        if (args.length == 2 && "load".equals(cmd)) {
            List<String> result = new ArrayList<>();
            String pat = args[1].toLowerCase();
            for (Tree tree: getTrees()) {
                if (tree.getName() != null && tree.getName().toLowerCase().startsWith(pat)) {
                    result.add(tree.getName());
                }
            }
            return result;
        } else if (args.length == 2 && "save".equals(cmd)) {
            List<String> result = new ArrayList<>();
            String pat = args[1].toUpperCase();
            for (TreeType type: TreeType.values()) {
                if (type.name().startsWith(pat)) {
                    result.add(type.name());
                }
            }
            return result;
        } else {
            return null;
        }
    }

    private static Block getTargetBlock(Player player) {
        final int limit = 32;
        BlockIterator iter = new BlockIterator(player, limit);
        int count = 0;
        while (iter.hasNext() && count < limit) {
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

    Session session(Player player) {
        Session result = sessions.get(player.getUniqueId());
        if (result == null) {
            result = new Session();
            result.name = player.getName();
            result.uuid = player.getUniqueId();
            sessions.put(player.getUniqueId(), result);
        }
        return result;
    }

    class Session {
        private UUID uuid;
        private String name;
        private Tree cachedTree;
        private Block cachedRootBlock;
    }
}
