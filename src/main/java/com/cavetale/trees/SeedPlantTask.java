package com.cavetale.trees;

import com.cavetale.area.struct.Vec3i;
import com.cavetale.core.event.block.PlayerBlockAbilityQuery;
import com.cavetale.mytems.item.tree.CustomTreeType;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

@RequiredArgsConstructor @Data
public final class SeedPlantTask {
    public static final Set<Material> REPLACEABLES = EnumSet.of(Material.DIRT, new Material[] {
            Material.COARSE_DIRT,
            Material.GRASS_BLOCK,
            Material.MOSS_BLOCK,
            Material.ROOTED_DIRT,
            Material.GRAVEL,
            Material.SAND,
            Material.GRASS,
            Material.TALL_GRASS,
        });
    private final TreesPlugin plugin;
    private final Player player;
    private final CustomTreeType type;
    private final World world;
    private final Vec3i sapling;
    private TreeStructure treeStructure;
    private StructureRotation rotation = StructureRotation.NONE;
    private Mirror mirror = Mirror.NONE;
    private Map<Vec3i, BlockData> blockDataMap;
    private List<Vec3i> placeBlockList;
    private BukkitTask task;
    private int ticks;
    private boolean valid;

    static {
        REPLACEABLES.addAll(Tag.DIRT.getValues());
        REPLACEABLES.addAll(Tag.FLOWERS.getValues());
        REPLACEABLES.addAll(Tag.TALL_FLOWERS.getValues());
        REPLACEABLES.addAll(Tag.LOGS.getValues());
        REPLACEABLES.addAll(Tag.LEAVES.getValues());
    }

    private void protectBlock(Block block) { }

    private void releaseBlock(Block block) { }

    private boolean canReplaceBlock(Block block) {
        if (!block.isEmpty() && !REPLACEABLES.contains(block.getType())) {
            return false;
        }
        return PlayerBlockAbilityQuery.Action.BUILD.query(player, block);
    }

    public static Vec3i transform(Vec3i vec, StructureRotation rotation, Mirror mirror) {
        int x;
        int y = vec.y;
        int z;
        switch (rotation) {
        case CLOCKWISE_90:
            x = vec.z;
            z = -vec.x;
            break;
        case COUNTERCLOCKWISE_90:
            x = -vec.z;
            z = vec.x;
            break;
        case CLOCKWISE_180:
            x = -vec.x;
            z = -vec.z;
            break;
        case NONE: default:
            x = vec.x;
            z = vec.z;
        }
        switch (mirror) {
        case FRONT_BACK:
            z = -z;
            break;
        case LEFT_RIGHT:
            x = -x;
            break;
        case NONE: default: break;
        }
        return new Vec3i(x, y, z);
    }

    public Vec3i toWorldVector(Vec3i vec) {
        vec = vec.subtract(treeStructure.sapling);
        vec = transform(vec, rotation, mirror);
        return vec.add(sapling);
    }

    public void start() {
        this.valid = initialize();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private boolean initialize() {
        List<TreeStructure> treeStructureList = plugin.findTreeStructures(type);
        if (treeStructureList.isEmpty()) return false;
        Random random = ThreadLocalRandom.current();
        this.treeStructure = treeStructureList.get(random.nextInt(treeStructureList.size()));
        // TODO: rotation, mirror
        this.blockDataMap = treeStructure.createBlockDataMap();
        this.placeBlockList = treeStructure.createPlaceBlockList(blockDataMap);
        if (placeBlockList.size() < 2) return false;
        for (Vec3i vec : placeBlockList) {
            if (!canReplaceBlock(toWorldVector(vec).toBlock(world))) return false;
        }
        return true;
    }

    public void stop() {
        if (task == null) return;
        task.cancel();
    }

    private void tick() {
        if (ticks == 0) {
            Block saplingBlock = toWorldVector(sapling).toBlock(world);
            Block floorBlock = saplingBlock.getRelative(0, -1, 0);
            floorBlock.setType(Material.DIRT);
            saplingBlock.setType(type.saplingMaterial);
            protectBlock(saplingBlock);
            protectBlock(floorBlock);
        }
        ticks += 1;
        if (ticks < 60) {
            return;
        }
    }
}
