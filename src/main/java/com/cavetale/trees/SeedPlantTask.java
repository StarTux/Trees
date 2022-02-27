package com.cavetale.trees;

import com.cavetale.area.struct.Vec3i;
import com.cavetale.core.event.block.PlayerBlockAbilityQuery;
import com.cavetale.core.event.block.PlayerChangeBlockEvent;
import com.cavetale.mytems.item.tree.CustomTreeType;
import com.cavetale.trees.util.Transform;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.SoundGroup;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
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
            Material.FARMLAND,
            Material.DIRT_PATH,
            Material.SAND,
            Material.GRASS,
            Material.TALL_GRASS,
            Material.PODZOL,
            Material.MOSS_CARPET,
            Material.MOSS_BLOCK,
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
    private int saplingTicks = 200;
    private int totalTicks;
    private int blockIndex;
    private boolean valid;

    static {
        REPLACEABLES.addAll(Tag.DIRT.getValues());
        REPLACEABLES.addAll(Tag.SAPLINGS.getValues());
        REPLACEABLES.addAll(Tag.FLOWERS.getValues());
        REPLACEABLES.addAll(Tag.TALL_FLOWERS.getValues());
        REPLACEABLES.addAll(Tag.LOGS.getValues());
        REPLACEABLES.addAll(Tag.LEAVES.getValues());
        REPLACEABLES.addAll(Tag.REPLACEABLE_PLANTS.getValues());
    }

    private void protectBlock(Block block) { }

    private void releaseBlock(Block block) { }

    private boolean canReplaceBlock(Block block) {
        if (!block.isEmpty() && !REPLACEABLES.contains(block.getType())) {
            return false;
        }
        return PlayerBlockAbilityQuery.Action.BUILD.query(player, block);
    }

    public Vec3i toWorldVector(Vec3i vec) {
        vec = vec.subtract(treeStructure.sapling);
        vec = Transform.rotate(vec, rotation, mirror);
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
        StructureRotation[] rotations = StructureRotation.values();
        Mirror[] mirrors = Mirror.values();
        this.rotation = rotations[random.nextInt(rotations.length)];
        this.mirror = mirrors[random.nextInt(mirrors.length)];
        this.blockDataMap = treeStructure.createBlockDataMap();
        this.placeBlockList = treeStructure.createPlaceBlockList(blockDataMap);
        if (placeBlockList.size() < 2) return false;
        for (Vec3i vec : placeBlockList) {
            if (!canReplaceBlock(toWorldVector(vec).toBlock(world))) return false;
        }
        saplingTicks = 200 + random.nextInt(200) - random.nextInt(50);
        return true;
    }

    public void stop() {
        if (task == null) return;
        task.cancel();
    }

    private void drop() {
        if (!world.getGameRuleValue(GameRule.DO_TILE_DROPS)) return;
        world.dropItem(sapling.toLocation(world).add(0.0, 0.5, 0.0), type.seedMytems.createItemStack());
    }

    private void tick() {
        int ticks = totalTicks++;
        if (!player.isOnline() || !world.isChunkLoaded(sapling.x >> 4, sapling.z >> 4)) {
            stop();
            return;
        }
        if (ticks == 0) {
            Block saplingBlock = sapling.toBlock(world);
            Block floorBlock = saplingBlock.getRelative(0, -1, 0);
            if (!canReplaceBlock(saplingBlock) || !canReplaceBlock(floorBlock)) {
                stop();
                drop();
                return;
            }
            new PlayerChangeBlockEvent(player, floorBlock, Material.DIRT.createBlockData()).callEvent();
            floorBlock.setType(Material.DIRT);
            new PlayerChangeBlockEvent(player, saplingBlock, type.saplingMaterial.createBlockData()).callEvent();
            saplingBlock.setType(type.saplingMaterial);
            protectBlock(saplingBlock);
            protectBlock(floorBlock);
        } else if (ticks < saplingTicks) {
            if (sapling.toBlock(world).getType() != type.saplingMaterial) {
                stop();
                return;
            }
            if (ticks % 12 == 0) {
                Location location = sapling.toLocation(world).add(0.0, 0.5, 0.0);
                world.spawnParticle(Particle.BLOCK_DUST, location, 8, 0.0, 0.0, 0.0, 0.0,
                                    type.saplingMaterial.createBlockData());
                world.playSound(location, Sound.BLOCK_GRASS_BREAK, SoundCategory.BLOCKS, 0.5f, 1.75f);
            }
            return;
        } else if (ticks == saplingTicks) {
            if (!valid) {
                stop();
                sapling.toBlock(world).setType(Material.AIR);
                drop();
                Location location = sapling.toLocation(world).add(0.5, 0.5, 0.5);
                world.playSound(location, Sound.BLOCK_GRASS_BREAK, SoundCategory.BLOCKS, 1.0f, 0.5f);
                return;
            }
            plugin.getLogger().info("Growing " + type + " " + treeStructure.name
                                    + " at " + world.getName() + " " + sapling
                                    + " for " + player.getName());
        } else {
            for (int i = 0; i < 8;) {
                if (blockIndex >= placeBlockList.size()) {
                    stop();
                    return;
                }
                Vec3i originVector = placeBlockList.get(blockIndex++);
                BlockData blockData = blockDataMap.get(originVector);
                i += Tag.LEAVES.isTagged(blockData.getMaterial()) ? 1 : 4;
                Vec3i blockVector = toWorldVector(originVector);
                Block block = blockVector.toBlock(world);
                if (!canReplaceBlock(block)) {
                    stop();
                    return;
                }
                if (blockData instanceof Leaves leaves) {
                    leaves.setDistance(0);
                    leaves.setPersistent(true);
                }
                Transform.rotate(blockData, rotation, mirror);
                new PlayerChangeBlockEvent(player, block, blockData).callEvent();
                block.setBlockData(blockData, false);
                SoundGroup soundGroup = blockData.getSoundGroup();
                world.playSound(block.getLocation().add(0.5, 0.5, 0.5), soundGroup.getPlaceSound(), SoundCategory.BLOCKS, 0.5f, 1.65f);
            }
        }
    }
}
