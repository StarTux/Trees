package com.cavetale.trees;

import com.cavetale.core.event.block.PlayerBlockAbilityQuery;
import com.cavetale.core.struct.Vec3i;
import com.cavetale.mytems.item.tree.CustomTreeType;
import com.cavetale.mytems.item.tree.TreeSeed;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.structure.Structure;
import static java.util.function.Function.identity;

public final class TreesPlugin extends JavaPlugin implements Listener {
    @Getter protected static TreesPlugin instance;
    private static final String STRUCTURE_SUFFIX = ".dat";
    private final TreesCommand treesCommand = new TreesCommand(this);
    protected List<TreeStructure> treeStructureList = List.of();
    private static final Map<Vec3i, Vec3i> VECTOR_CACHE = new HashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        treesCommand.enable();
        loadTreeStructures();
        for (CustomTreeType it : CustomTreeType.values()) {
            if (!(it.seedMytems.getMytem() instanceof TreeSeed treeSeed)) continue;
            treeSeed.setRightClickHandler(event -> onRightClick(event, it));
        }
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        for (CustomTreeType it : CustomTreeType.values()) {
            if (!(it.seedMytems.getMytem() instanceof TreeSeed treeSeed)) continue;
            treeSeed.setRightClickHandler(null);
        }
    }

    protected void loadTreeStructures() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                long time = System.currentTimeMillis();
                List<TreeStructure> loadList = new ArrayList<>();
                loadTreeStructures(new File(getDataFolder(), "trees"), loadList);
                loadTreeStructures(new File("/home/mc/public/config/Trees/trees"), loadList);
                time = System.currentTimeMillis() - time;
                double seconds = (double) time / 1000.0;
                Bukkit.getScheduler().runTask(this, () -> {
                        treeStructureList = loadList;
                        getLogger().info(treeStructureList.size() + " tree structures loaded in "
                                         + String.format("%.3f", seconds) + "s");
                    });
            });
    }

    private void loadTreeStructures(File folder, List<TreeStructure> list) {
        if (!folder.isDirectory()) return;
        final String fn = "loadTreeStructures";
        for (CustomTreeType customTreeType : CustomTreeType.values()) {
            File subfolder = new File(folder, customTreeType.name().toLowerCase());
            if (!subfolder.isDirectory()) continue;
            for (File file : subfolder.listFiles()) {
                String filename = file.getName();
                if (!filename.endsWith(STRUCTURE_SUFFIX)) continue;
                filename = filename.substring(0, filename.length() - STRUCTURE_SUFFIX.length());
                Structure structure;
                try {
                    structure = Bukkit.getStructureManager().loadStructure(new FileInputStream(file));
                } catch (IOException ioe) {
                    getLogger().log(Level.SEVERE, fn + " " + file, ioe);
                    continue;
                }
                list.add(new TreeStructure(customTreeType, filename, structure));
            }
        }
    }

    protected boolean saveTreeStructure(TreeStructure treeStructure, Structure structure) {
        File folder = new File(new File(getDataFolder(), "trees"), treeStructure.getType().name().toLowerCase());
        folder.mkdirs();
        File file = new File(folder, treeStructure.getName() + STRUCTURE_SUFFIX);
        try {
            Bukkit.getStructureManager().saveStructure(file, structure);
        } catch (IOException ioe) {
            getLogger().log(Level.SEVERE, "writing " + file, ioe);
            return false;
        }
        return true;
    }

    public TreeStructure findTreeStructure(CustomTreeType type, String name) {
        for (TreeStructure it : treeStructureList) {
            if (it.type == type && name.equals(it.name)) return it;
        }
        return null;
    }

    public List<TreeStructure> findTreeStructures(CustomTreeType type) {
        List<TreeStructure> list = new ArrayList<>();
        for (TreeStructure it : treeStructureList) {
            if (it.type == type) list.add(it);
        }
        return list;
    }

    protected void onRightClick(PlayerInteractEvent event, CustomTreeType type) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.hasBlock()) return;
        Block block = event.getClickedBlock();
        if (block.getType() != Material.FARMLAND) return;
        Block above = block.getRelative(0, 1, 0);
        if (!above.isEmpty()) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        if (!PlayerBlockAbilityQuery.Action.BUILD.query(player, block)) return;
        SeedPlantTask task = new SeedPlantTask(this, player, type, player.getWorld(), Vec3i.of(above));
        task.start();
        switch (player.getGameMode()) {
        case CREATIVE:
            break;
        case SURVIVAL:
        case ADVENTURE:
        default:
            event.getItem().subtract(1);
            break;
        }
    }

    @EventHandler(ignoreCancelled = true)
    private void onStructureGrow(StructureGrowEvent event) {
        Block block = event.getLocation().getBlock();
        if (SeedPlantTask.SAPLING_BLOCKS.contains(block)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    private void onBlockBreak(BlockBreakEvent event) {
        if (SeedPlantTask.SAPLING_BLOCKS.contains(event.getBlock())) event.setCancelled(true);
    }

    public static Vec3i vector(int x, int y, int z) {
        return VECTOR_CACHE.computeIfAbsent(new Vec3i(x, y, z), identity());
    }
}
