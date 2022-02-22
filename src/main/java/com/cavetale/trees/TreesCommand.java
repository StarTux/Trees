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
        rootNode.addChild("info").denyTabCompletion()
            .description("Info Command")
            .senderCaller(this::info);
        rootNode.addChild("create").arguments("<type> <name>")
            .completers(CommandArgCompleter.enumLowerList(CustomTreeType.class),
                        CommandArgCompleter.EMPTY)
            .description("Create tree structure")
            .playerCaller(this::create);
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

    protected boolean info(CommandSender sender, String[] args) {
        return false;
    }

    protected boolean create(Player player, String[] args) {
        if (args.length != 2) return false;
        String typeArg = args[0];
        String nameArg = args[1];
        Cuboid selection = WorldEdit.getSelection(player);
        if (selection == null) throw new CommandWarn("No selection");
        CustomTreeType type;
        try {
            type = CustomTreeType.valueOf(args[0].toUpperCase());
        } catch (IllegalArgumentException iae) {
            throw new CommandWarn("Unknown tree type: " + typeArg);
        }
        Structure structure = Bukkit.getStructureManager().createStructure();
        World w = player.getWorld();
        structure.fill(selection.min.toLocation(w), selection.max.toLocation(w), true);
        TreeStructure treeStructure = new TreeStructure(type, nameArg, structure);
        if (!treeStructure.preprocess(w.getName(), selection.min)) {
            throw new CommandWarn("Preprocessing failed!");
        }
        plugin.treeStructureList.add(treeStructure);
        if (!plugin.saveTreeStructure(treeStructure)) {
            throw new CommandWarn("Saving failed. See console.");
        }
        player.sendMessage(text("Structure saved."
                                + " sapling=" + treeStructure.getSapling()
                                + " entities=" + structure.getEntities(),
                                YELLOW));
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
                    Vec3i vec1 = placeBlockList.get(index++);
                    BlockData blockData = blockDataMap.get(vec1);
                    Vec3i vec2 = Transform.rotate(vec1.subtract(treeStructure.sapling), rotation, mirror);
                    player.sendMessage(vec1 + " => " + vec2);
                    try {
                        player.sendBlockChange(vec2.add(selection.min).toLocation(w), blockData);
                    } catch (Exception e) {
                        cancel();
                        plugin.getLogger().log(Level.SEVERE, "trees grow", e);
                        return;
                    }
                }
            };
        task.runTaskTimer(plugin, 1L, 1L);
        return true;
    }
}
