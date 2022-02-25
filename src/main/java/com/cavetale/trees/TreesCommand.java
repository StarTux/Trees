package com.cavetale.trees;

import com.cavetale.area.struct.Cuboid;
import com.cavetale.area.struct.Vec3i;
import com.cavetale.area.worldedit.WorldEdit;
import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.mytems.item.tree.CustomTreeType;
import com.cavetale.trees.util.Transform;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.structure.Structure;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class TreesCommand extends AbstractCommand<TreesPlugin> {
    protected TreesCommand(final TreesPlugin plugin) {
        super(plugin, "trees");
    }

    @Override
    protected void onEnable() {
        rootNode.addChild("reload").denyTabCompletion()
            .description("Reload trees")
            .senderCaller(this::reload);
        rootNode.addChild("create").arguments("<type> <name>")
            .completers(CommandArgCompleter.enumLowerList(CustomTreeType.class),
                        CommandArgCompleter.EMPTY)
            .description("Create tree structure")
            .playerCaller(this::create);
        rootNode.addChild("grid").arguments("<type> <prefix> <width-x> <height-z> <gap>")
            .description("Create trees from a grid")
            .completers(CommandArgCompleter.enumLowerList(CustomTreeType.class),
                        CommandArgCompleter.supplyStream(() -> plugin.treeStructureList.stream()
                                                         .map(TreeStructure::getName)),
                        CommandArgCompleter.integer(x -> x > 0),
                        CommandArgCompleter.integer(z -> z > 0))
            .playerCaller(this::grid);
        rootNode.addChild("show").arguments("<type> <name>")
            .description("Show tree")
            .completers(CommandArgCompleter.enumLowerList(CustomTreeType.class),
                        CommandArgCompleter.supplyStream(() -> plugin.treeStructureList.stream()
                                                         .map(TreeStructure::getName)))
            .playerCaller(this::show);
        rootNode.addChild("grow").arguments("<type> <name> <rotation> <mirror>")
            .description("Fake grow tree")
            .completers(CommandArgCompleter.enumLowerList(CustomTreeType.class),
                        CommandArgCompleter.supplyStream(() -> plugin.treeStructureList.stream()
                                                         .map(TreeStructure::getName)),
                        CommandArgCompleter.enumLowerList(StructureRotation.class),
                        CommandArgCompleter.enumLowerList(Mirror.class))
            .playerCaller(this::grow);
    }

    protected boolean reload(CommandSender sender, String[] args) {
        if (args.length != 0) return false;
        plugin.loadTreeStructures();
        sender.sendMessage(text("Reloading tree structures...", YELLOW));
        return true;
    }

    protected boolean create(Player player, String[] args) {
        if (args.length != 2) return false;
        String typeArg = args[0];
        String nameArg = args[1];
        Cuboid cuboid = WorldEdit.getSelection(player);
        if (cuboid == null) throw new CommandWarn("No selection");
        Vec3i size = cuboid.getSize();
        if (size.x <= 1 || size.y <= 1 || size.z <= 1) {
            throw new CommandWarn("Invalid selection size: " + size);
        }
        CustomTreeType type;
        try {
            type = CustomTreeType.valueOf(args[0].toUpperCase());
        } catch (IllegalArgumentException iae) {
            throw new CommandWarn("Unknown tree type: " + typeArg);
        }
        Structure structure = Bukkit.getStructureManager().createStructure();
        World w = player.getWorld();
        structure.fill(cuboid.min.toLocation(w),
                       cuboid.max.add(1, 1, 1).toBaseLocation(w), true);
        TreeStructure treeStructure = new TreeStructure(type, nameArg, structure);
        TreeStructure.PreprocessResult pr = treeStructure.preprocess(w.getName(), cuboid.min);
        if (pr != TreeStructure.PreprocessResult.SUCCESS) {
            throw new CommandWarn("Preprocessing failed: " + cuboid + ", " + pr);
        }
        plugin.treeStructureList.add(treeStructure);
        if (!plugin.saveTreeStructure(treeStructure)) {
            throw new CommandWarn("Saving failed. See console.");
        }
        player.sendMessage(text("Structure saved."
                                + " sapling=" + treeStructure.getSapling()
                                + " entities=" + structure.getEntityCount(),
                                YELLOW));
        return true;
    }

    protected boolean grid(Player player, String[] args) {
        if (args.length != 5) return false;
        String typeArg = args[0];
        String prefixArg = args[1];
        Cuboid selection = WorldEdit.getSelection(player);
        if (selection == null) throw new CommandWarn("No selection");
        Vec3i size = selection.getSize();
        if (size.x <= 1 || size.y <= 1 || size.z <= 1) {
            throw new CommandWarn("Invalid selection size: " + size);
        }
        CustomTreeType type;
        try {
            type = CustomTreeType.valueOf(args[0].toUpperCase());
        } catch (IllegalArgumentException iae) {
            throw new CommandWarn("Unknown tree type: " + typeArg);
        }
        final int sizeX = requireInt(args[2], x -> x > 0);
        final int sizeZ = requireInt(args[3], z -> z > 0);
        final int gap = requireInt(args[4], g -> g >= 0);
        final Vec3i selectionSize = selection.getSize();
        World w = player.getWorld();
        for (int z = 0; z < sizeZ; z += 1) {
            for (int x = 0; x < sizeX; x += 1) {
                Cuboid cuboid = selection.shift(x * (gap + selectionSize.x),
                                                0,
                                                z * (gap + selectionSize.z));
                Structure structure = Bukkit.getStructureManager().createStructure();
                structure.fill(cuboid.min.toLocation(w),
                               cuboid.max.add(1, 1, 1).toBaseLocation(w), true);
                String name = prefixArg + "_" + x + "_" + z;
                TreeStructure treeStructure = new TreeStructure(type, name, structure);
                TreeStructure.PreprocessResult pr = treeStructure.preprocess(w.getName(), cuboid.min);
                if (pr != TreeStructure.PreprocessResult.SUCCESS) {
                    throw new CommandWarn("Preprocessing failed: " + cuboid + ", " + pr);
                }
                plugin.treeStructureList.add(treeStructure);
                if (!plugin.saveTreeStructure(treeStructure)) {
                    throw new CommandWarn("Saving failed. See console.");
                }
                player.sendMessage(text("Saved: " + name + ", " + cuboid, YELLOW));
            }
        }
        return true;
    }

    protected boolean show(Player player, String[] args) {
        if (args.length != 2) return false;
        String typeArg = args[0];
        String name = args[1];
        CustomTreeType type;
        try {
            type = CustomTreeType.valueOf(args[0].toUpperCase());
        } catch (IllegalArgumentException iae) {
            throw new CommandWarn("Unknown tree type: " + typeArg);
        }
        TreeStructure treeStructure = plugin.findTreeStructure(type, name);
        if (treeStructure == null) {
            throw new CommandWarn("Tree Structure not found: " + type + ", " + name);
        }
        Cuboid selection = WorldEdit.getSelection(player);
        if (selection == null) throw new CommandWarn("No selection");
        treeStructure.show(player, selection.min);
        player.sendMessage(text("Showing " + type + ", " + name + " at " + selection.min, YELLOW));
        return true;
    }

    protected boolean grow(Player player, String[] args) {
        if (args.length < 2 || args.length > 4) return false;
        String typeArg = args[0];
        String name = args[1];
        String rotationArg = args.length >= 3 ? args[2] : null;
        String mirrorArg = args.length >= 4 ? args[3] : null;
        CustomTreeType type;
        try {
            type = CustomTreeType.valueOf(args[0].toUpperCase());
        } catch (IllegalArgumentException iae) {
            throw new CommandWarn("Unknown tree type: " + typeArg);
        }
        TreeStructure treeStructure = plugin.findTreeStructure(type, name);
        if (treeStructure == null) {
            throw new CommandWarn("Tree Structure not found: " + type + ", " + name);
        }
        StructureRotation rotation;
        if (rotationArg != null) {
            try {
                rotation = StructureRotation.valueOf(rotationArg.toUpperCase());
            } catch (IllegalArgumentException iae) {
                throw new CommandWarn("Invalid rotation: " + rotationArg);
            }
        } else {
            rotation = StructureRotation.NONE;
        }
        Mirror mirror;
        if (mirrorArg != null) {
            try {
                mirror = Mirror.valueOf(mirrorArg.toUpperCase());
            } catch (IllegalArgumentException iae) {
                throw new CommandWarn("Invalid mirror: " + mirrorArg);
            }
        } else {
            mirror = Mirror.NONE;
        }
        Cuboid selection = WorldEdit.getSelection(player);
        Vec3i sapling = selection.min.add(0, 1, 0);
        if (selection == null) throw new CommandWarn("No selection");
        player.sendMessage(text("Fake growing " + type + ", " + name + " at " + sapling, YELLOW));
        Map<Vec3i, BlockData> blockDataMap = treeStructure.createBlockDataMap();
        List<Vec3i> placeBlockList = treeStructure.createPlaceBlockList(blockDataMap);
        World w = player.getWorld();
        BukkitRunnable task = new BukkitRunnable() {
                int index = 0;
                @Override
                public void run() {
                    if (!player.isOnline() || !player.getWorld().equals(w) || index >= placeBlockList.size()) {
                        cancel();
                        player.sendMessage(text("Done growing " + type + ", " + name + ": " + index, YELLOW));
                        return;
                    }
                    for (int i = 0; i < 10; i += 1) {
                        if (index >= placeBlockList.size()) break;
                        Vec3i vec1 = placeBlockList.get(index++);
                        BlockData blockData = blockDataMap.get(vec1);
                        Vec3i vec2 = Transform.rotate(vec1.subtract(treeStructure.sapling), rotation, mirror);
                        try {
                            player.sendBlockChange(vec2.add(selection.min).toLocation(w), blockData);
                        } catch (Exception e) {
                            cancel();
                            plugin.getLogger().log(Level.SEVERE, "trees grow", e);
                            return;
                        }
                    }
                }
            };
        task.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private int requireInt(String in, IntPredicate predicate) {
        int result;
        try {
            result = Integer.parseInt(in);
        } catch (NumberFormatException nfe) {
            throw new CommandWarn("Number expected: " + in);
        }
        if (!predicate.test(result)) throw new CommandWarn("Unexpected value: " + result);
        return result;
    }
}
