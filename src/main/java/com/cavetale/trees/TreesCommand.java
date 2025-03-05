package com.cavetale.trees;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.struct.Cuboid;
import com.cavetale.core.struct.Vec3i;
import com.cavetale.mytems.item.axis.CuboidOutline;
import com.cavetale.mytems.item.tree.CustomTreeType;
import com.cavetale.trees.util.Transform;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.structure.Structure;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.event.ClickEvent.runCommand;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;

public final class TreesCommand extends AbstractCommand<TreesPlugin> {
    protected TreesCommand(final TreesPlugin plugin) {
        super(plugin, "trees");
    }

    @Override
    protected void onEnable() {
        rootNode.addChild("reload").denyTabCompletion()
            .description("Reload trees")
            .senderCaller(this::reload);
        rootNode.addChild("info").denyTabCompletion()
            .description("Print tree info")
            .senderCaller(this::info);
        rootNode.addChild("test").denyTabCompletion()
            .description("Test all trees")
            .senderCaller(this::test);
        rootNode.addChild("create").arguments("<type> <name>")
            .completers(CommandArgCompleter.enumLowerList(CustomTreeType.class),
                        CommandArgCompleter.supplyStream(() -> plugin.treeStructureList.stream()
                                                         .map(TreeStructure::getName)))
            .description("Create tree structure")
            .playerCaller(this::create);
        rootNode.addChild("auto").arguments("<type> <name>")
            .completers(CommandArgCompleter.enumLowerList(CustomTreeType.class),
                        CommandArgCompleter.supplyStream(() -> plugin.treeStructureList.stream()
                                                         .map(TreeStructure::getName)))
            .description("Detect a tree and await confirmation")
            .playerCaller(this::auto);
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
        CommandNode dangerNode = rootNode.addChild("danger")
            .description("Dangerous commands! Do not touch unless in a void world!");
        dangerNode.addChild("pasteall").arguments("<type> ITreeWhatImDoing")
            .description("Paste all trees")
            .completers(CommandArgCompleter.enumLowerList(CustomTreeType.class))
            .playerCaller(this::dangerPasteAll);
    }

    protected boolean reload(CommandSender sender, String[] args) {
        if (args.length != 0) return false;
        plugin.loadTreeStructures();
        sender.sendMessage(text("Reloading tree structures...", YELLOW));
        return true;
    }

    protected boolean info(CommandSender sender, String[] args) {
        if (args.length != 0) return false;
        for (CustomTreeType it : CustomTreeType.values()) {
            sender.sendMessage(text(plugin.findTreeStructures(it).size()
                                    + " " + it.name().toLowerCase(),
                                    YELLOW));
        }
        return true;
    }

    protected boolean test(CommandSender sender, String[] args) {
        if (args.length != 0) return false;
        int errorCount = 0;
        int okCount = 0;
        for (TreeStructure it : plugin.treeStructureList) {
            if (!it.testPlaceBlockList()) {
                sender.sendMessage(text("Too few place blocks: " + it.type + " " + it.name, YELLOW));
                errorCount += 1;
                continue;
            }
            okCount += 1;
        }
        sender.sendMessage(text("Errors: " + errorCount, RED));
        sender.sendMessage(text("OK: " + okCount, GREEN));
        return true;
    }

    protected boolean create(Player player, String[] args) {
        if (args.length != 2) return false;
        String typeArg = args[0];
        String nameArg = args[1];
        Cuboid cuboid = Cuboid.requireSelectionOf(player);
        Vec3i size = cuboid.getSize();
        if (size.x <= 1 || size.y <= 1 || size.z <= 1) {
            throw new CommandWarn("Invalid selection size: " + size);
        }
        final CustomTreeType type = CommandArgCompleter.requireEnum(CustomTreeType.class, typeArg);
        Structure structure = Bukkit.getStructureManager().createStructure();
        World w = player.getWorld();
        structure.fill(cuboid.getMin().toLocation(w),
                       cuboid.getMax().add(1, 1, 1).toLocation(w), true);
        TreeStructure treeStructure = new TreeStructure(type, nameArg, structure);
        TreeStructure.PreprocessResult pr = treeStructure.preprocess(structure, w.getName(), cuboid.getMin());
        if (pr != TreeStructure.PreprocessResult.SUCCESS) {
            throw new CommandWarn("Preprocessing failed: " + cuboid + ", " + pr);
        }
        if (!treeStructure.testPlaceBlockList()) {
            throw new CommandWarn("Too few placeable blocks: " + cuboid);
        }
        plugin.treeStructureList.add(treeStructure);
        if (!plugin.saveTreeStructure(treeStructure, structure)) {
            throw new CommandWarn("Saving failed. See console.");
        }
        player.sendMessage(text("Structure saved."
                                + " sapling=" + treeStructure.getSapling()
                                + " entities=" + structure.getEntityCount(),
                                YELLOW));
        return true;
    }

    protected boolean auto(Player player, String[] args) {
        if (args.length == 1 && args[0].equals("confirm")) {
            final AutoTreeCache cache = AutoTreeCache.remove(player);
            if (cache == null) {
                throw new CommandWarn("Nothing to confirm");
            }
            if (!cache.isValid()) {
                throw new CommandWarn("Tree not valid!");
            }
            final TreeStructure treeStructure = cache.getTreeStructure();
            final Structure structure = cache.getStructure();
            plugin.treeStructureList.add(treeStructure);
            if (!plugin.saveTreeStructure(treeStructure, structure)) {
                throw new CommandWarn("Saving failed. See console.");
            }
            player.sendMessage(text("Structure saved."
                                    + " sapling=" + treeStructure.getSapling()
                                    + " entities=" + structure.getEntityCount(),
                                    YELLOW));
            return true;
        } else if (args.length == 1 && args[0].equals("cancel")) {
            final AutoTreeCache cache = AutoTreeCache.remove(player);
            if (cache == null) {
                throw new CommandWarn("Nothing to cancel");
            }
            player.sendMessage(text("TreeStructure cancelled: " + cache.getTreeStructure().getType()
                                    + ", " + cache.getTreeStructure().getName(), YELLOW));
            return true;
        }
        if (args.length != 2) return false;
        AutoTreeCache.remove(player);
        // Parse arguments
        final CustomTreeType type = CommandArgCompleter.requireEnum(CustomTreeType.class, args[0]);
        final String nameArg = args[1];
        // Find tree blocks with Flood Fill
        final Block lookAtBlock = player.getTargetBlockExact(8);
        if (lookAtBlock == null) {
            throw new CommandWarn("Must look at tree block!");
        }
        final List<Block> blocks = new ArrayList<>();
        int blocksIndex = 0;
        final int maxBlocksSize = 8192;
        blocks.add(lookAtBlock);
        int ax = lookAtBlock.getX();
        int ay = lookAtBlock.getY();
        int az = lookAtBlock.getZ();
        int bx = ax;
        int by = ay;
        int bz = az;
        while (blocksIndex < blocks.size()) {
            if (blocksIndex == maxBlocksSize) {
                throw new CommandWarn("Tree size exceeds " + maxBlocksSize);
            }
            final Block block = blocks.get(blocksIndex++);
            for (int dy = -1; dy <= 1; dy += 1) {
                for (int dz = -1; dz <= 1; dz += 1) {
                    for (int dx = -1; dx <= 1; dx += 1) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        final Block nbor = block.getRelative(dx, dy, dz);
                        if (blocks.contains(nbor)) continue;
                        if (nbor.isEmpty()) continue;
                        blocks.add(nbor);
                        final int x = nbor.getX();
                        final int y = nbor.getY();
                        final int z = nbor.getZ();
                        if (x < ax) ax = x;
                        if (y < ay) ay = y;
                        if (z < az) az = z;
                        if (x > bx) bx = x;
                        if (y > by) by = y;
                        if (z > bz) bz = z;
                    }
                }
            }
        }
        final Cuboid cuboid = new Cuboid(ax, ay, az, bx, by, bz);
        // Create structure
        Structure structure = Bukkit.getStructureManager().createStructure();
        World w = player.getWorld();
        structure.fill(cuboid.getMin().toLocation(w),
                       cuboid.getMax().add(1, 1, 1).toLocation(w), true);
        TreeStructure treeStructure = new TreeStructure(type, nameArg, structure);
        // Make AutoTreeCache
        final AutoTreeCache autoTreeCache = AutoTreeCache.create(player, treeStructure, structure);
        final CuboidOutline boundingBox = new CuboidOutline(player.getWorld(), cuboid);
        boundingBox.showOnlyTo(player);
        boundingBox.spawn();
        boundingBox.glow(Color.WHITE);
        autoTreeCache.setBoundingBox(boundingBox);
        // Find Bad Blocks
        for (Vec3i it : cuboid.enumerate()) {
            Block block = it.toBlock(player.getWorld());
            if (blocks.contains(block) || block.isEmpty()) continue;
            final CuboidOutline badBlockOutline = new CuboidOutline(player.getWorld(), new Cuboid(it.x, it.y, it.z, it.x, it.y, it.z));
            badBlockOutline.showOnlyTo(player);
            badBlockOutline.spawn();
            badBlockOutline.glow(Color.RED);
            autoTreeCache.getBadBlocks().add(badBlockOutline);
        }
        // Preprocess
        TreeStructure.PreprocessResult pr = treeStructure.preprocess(structure, w.getName(), cuboid.getMin());
        if (pr != TreeStructure.PreprocessResult.SUCCESS) {
            throw new CommandWarn("Preprocessing failed: " + cuboid + ", " + pr);
        }
        if (!treeStructure.testPlaceBlockList()) {
            throw new CommandWarn("Too few placeable blocks: " + cuboid);
        }
        // Sapling highlight
        final Vec3i sapling = treeStructure.getSapling().add(treeStructure.getOrigin());
        final CuboidOutline saplingOutline = new CuboidOutline(player.getWorld(), new Cuboid(sapling.x, sapling.y, sapling.z, sapling.x, sapling.y, sapling.z));
        saplingOutline.showOnlyTo(player);
        saplingOutline.spawn();
        saplingOutline.glow(Color.GREEN);
        autoTreeCache.setSapling(saplingOutline);
        // Check Duplicates
        for (TreeStructure other : plugin.getTreeStructureList()) {
            if (treeStructure.getOriginWorld().equals(other.getOriginWorld()) && treeStructure.getOrigin().equals(other.getOrigin())) {
                throw new CommandWarn("Duplicate of " + other.getType() + ", " + other.getName());
            }
            if (treeStructure.getName().equals(other.getName())) {
                throw new CommandWarn("Duplicate name: " + other.getType() + ", " + other.getName());
            }
        }
        autoTreeCache.setValid(true);
        // Feedback
        player.sendMessage(textOfChildren(text("Tree found!", GREEN),
                                          space(),
                                          text(nameArg, BLUE),
                                          space(),
                                          text(type.name(), AQUA, ITALIC),
                                          space(),
                                          text("origin:", GRAY), text("" + treeStructure.getOrigin(), WHITE),
                                          space(),
                                          text("blocks:", GRAY), text(blocks.size(), WHITE),
                                          space(),
                                          text("sapling:", GRAY), text("" + sapling, WHITE),
                                          space(),
                                          text("entities:", GRAY), text(structure.getEntityCount(), WHITE),
                                          space(),
                                          text("bad:", GRAY), text(autoTreeCache.getBadBlocks().size(), RED)));
        player.sendMessage(textOfChildren(text("[Confirm]", GREEN)
                                          .clickEvent(runCommand("/trees auto confirm"))
                                          .hoverEvent(showText(text("/trees auto confirm", GREEN))),
                                          text("  "),
                                          text("[Cancel]", RED)
                                          .clickEvent(runCommand("/trees auto cancel"))
                                          .hoverEvent(showText(text("/trees auto cancel", RED)))));
        return true;
    }

    protected boolean grid(Player player, String[] args) {
        if (args.length != 5) return false;
        String typeArg = args[0];
        String prefixArg = args[1];
        Cuboid selection = Cuboid.requireSelectionOf(player);
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
                structure.fill(cuboid.getMin().toLocation(w),
                               cuboid.getMax().add(1, 1, 1).toLocation(w), true);
                String name = prefixArg + "_" + x + "_" + z;
                TreeStructure treeStructure = new TreeStructure(type, name, structure);
                TreeStructure.PreprocessResult pr = treeStructure.preprocess(structure, w.getName(), cuboid.getMin());
                if (pr != TreeStructure.PreprocessResult.SUCCESS) {
                    throw new CommandWarn("Preprocessing failed: " + name + ", " + cuboid + ", " + pr);
                }
                if (!treeStructure.testPlaceBlockList()) {
                    throw new CommandWarn("Too few placeable blocks: " + name + ", " + cuboid);
                }
                plugin.treeStructureList.add(treeStructure);
                if (!plugin.saveTreeStructure(treeStructure, structure)) {
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
        Cuboid selection = Cuboid.requireSelectionOf(player);
        treeStructure.show(player, selection.getMin());
        player.sendMessage(text("Showing " + type + ", " + name + " at " + selection.getMin(), YELLOW));
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
        Cuboid selection = Cuboid.selectionOf(player);
        Vec3i sapling = selection.getMin().add(0, 1, 0);
        if (selection == null) throw new CommandWarn("No selection");
        player.sendMessage(text("Fake growing " + type + ", " + name + " at " + sapling, YELLOW));
        Map<Vec3i, BlockData> blockDataMap = treeStructure.getBlockDataMap();
        List<Vec3i> placeBlockList = treeStructure.getPlaceBlockList();
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
                        Transform.rotate(blockData, rotation, mirror);
                        Vec3i vec2 = Transform.rotate(vec1.subtract(treeStructure.sapling), rotation, mirror);
                        try {
                            player.sendBlockChange(vec2.add(selection.getMin()).toLocation(w), blockData);
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

    protected boolean dangerPasteAll(Player player, String[] args) {
        if (args.length != 2) return false;
        if (!args[1].equals("ITreeWhatImDoing")) return false;
        final Location location = player.getLocation();
        final int y = location.getBlockY();
        final int z = location.getBlockZ();
        final CustomTreeType type;
        try {
            type = CustomTreeType.valueOf(args[0].toUpperCase());
        } catch (IllegalArgumentException iae) {
            throw new CommandWarn("Invalid type: " + args[0]);
        }
        List<TreeStructure> treeStructureList = plugin.findTreeStructures(type);
        int x = location.getBlockX();
        int maxWidth = 0;
        for (TreeStructure treeStructure : treeStructureList) {
            Block block = location.getWorld().getBlockAt(x, y, z);
            treeStructure.place(block);
            x += treeStructure.getSize().getX() + 1;
            maxWidth = Math.max(maxWidth, (int) treeStructure.getSize().getZ());
            while (!block.isEmpty() && block.getY() < 255) block = block.getRelative(0, 1, 0);
            block.setBlockData(Material.OAK_SIGN.createBlockData());
            if (block.getState() instanceof Sign sign) {
                sign.getSide(Side.FRONT).line(0, text("" + type.name().toLowerCase()));
                sign.getSide(Side.FRONT).line(1, text(treeStructure.getName()));
                sign.getSide(Side.FRONT).line(2, text("" + ((int) treeStructure.getSize().getX())
                                                      + "x" + ((int) treeStructure.getSize().getY())
                                                      + "x" + ((int) treeStructure.getSize().getZ())));
                sign.update();
            }
        }
        player.sendMessage(text("Pasted " + treeStructureList.size()
                                + " " + type.name().toLowerCase() + " trees",
                                YELLOW));
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
