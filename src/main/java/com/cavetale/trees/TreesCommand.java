package com.cavetale.trees;

import com.cavetale.area.struct.Cuboid;
import com.cavetale.area.worldedit.WorldEdit;
import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.mytems.item.tree.CustomTreeType;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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
            .description("Show the tree")
            .completers(CommandArgCompleter.enumLowerList(CustomTreeType.class),
                        CommandArgCompleter.supplyStream(() -> plugin.treeStructureList.stream()
                                                         .map(TreeStructure::getName)))
            .playerCaller(this::show);
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
}
