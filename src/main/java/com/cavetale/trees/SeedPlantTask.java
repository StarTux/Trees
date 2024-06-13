package com.cavetale.trees;

import com.cavetale.core.event.block.PlayerBlockAbilityQuery;
import com.cavetale.core.event.block.PlayerChangeBlockEvent;
import com.cavetale.core.struct.Vec3i;
import com.cavetale.mytems.item.tree.CustomTreeType;
import com.cavetale.trees.util.Transform;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
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
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

@RequiredArgsConstructor @Data
public final class SeedPlantTask {
    private static final float BLOCK_DISPLAY_SCALE = 1f / 16f;
    protected static final Map<Block, SeedPlantTask> SEED_PLANT_TASK_MAP = new HashMap<>();
    public static final Set<Material> REPLACEABLES = EnumSet.of(Material.DIRT, new Material[] {
            Material.COARSE_DIRT,
            Material.GRASS_BLOCK,
            Material.MOSS_BLOCK,
            Material.ROOTED_DIRT,
            Material.GRAVEL,
            Material.FARMLAND,
            Material.DIRT_PATH,
            Material.SAND,
            Material.SHORT_GRASS,
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
    private BukkitTask task;
    private int saplingTicks = 200;
    private int totalTicks;
    private int blockIndex;
    private boolean valid;
    private final List<BlockDisplay> blockDisplayList = new ArrayList<>();
    private State state = State.INIT;

    public enum State {
        INIT,
        INITIALIZED,
        SPROUT_PREVIEW,
        START_GROWING,
        GROW,
        DONE,
        INVALID,
        CANCELLED,
        ;
    }

    static {
        REPLACEABLES.addAll(Tag.DIRT.getValues());
        REPLACEABLES.addAll(Tag.SAPLINGS.getValues());
        REPLACEABLES.addAll(Tag.FLOWERS.getValues());
        REPLACEABLES.addAll(Tag.TALL_FLOWERS.getValues());
        REPLACEABLES.addAll(Tag.LOGS.getValues());
        REPLACEABLES.addAll(Tag.LEAVES.getValues());
    }

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
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 1L);
        SEED_PLANT_TASK_MAP.put(sapling.toBlock(world), this);
        state = State.INITIALIZED;
    }

    private boolean initialize() {
        for (SeedPlantTask it : SEED_PLANT_TASK_MAP.values()) {
            if (player.equals(it.player)) return false;
        }
        List<TreeStructure> treeStructureList = plugin.findTreeStructures(type);
        if (treeStructureList.isEmpty()) return false;
        Random random = ThreadLocalRandom.current();
        this.treeStructure = treeStructureList.get(random.nextInt(treeStructureList.size()));
        StructureRotation[] rotations = StructureRotation.values();
        Mirror[] mirrors = Mirror.values();
        this.rotation = rotations[random.nextInt(rotations.length)];
        this.mirror = mirrors[random.nextInt(mirrors.length)];
        for (Vec3i vec : treeStructure.getPlaceBlockList()) {
            if (!canReplaceBlock(toWorldVector(vec).toBlock(world))) return false;
        }
        saplingTicks = 200 + random.nextInt(200) - random.nextInt(50);
        final Location location = sapling.toCenterFloorLocation(world).add(0, 1.0, 0);
        for (Vec3i vec : treeStructure.getPlaceBlockList()) {
            final BlockData blockData = treeStructure.getBlockDataMap().get(vec);
            final Vec3i vector = vec.subtract(treeStructure.getSapling());
            final Vector3f translation = new Vector3f((float) vector.x, (float) vector.y, (float) vector.z)
                .sub(0.5f, 0.5f, 0.5f)
                .mul(BLOCK_DISPLAY_SCALE);
            final AxisAngle4f leftRotation = new AxisAngle4f(0f, 0f, 1f, 0f);
            final Vector3f scale = new Vector3f(BLOCK_DISPLAY_SCALE, BLOCK_DISPLAY_SCALE, BLOCK_DISPLAY_SCALE);
            final AxisAngle4f rightRotation = new AxisAngle4f(0f, 0f, 0f, 0f);
            final BlockDisplay blockDisplay = world.spawn(location, BlockDisplay.class, e -> {
                    e.setPersistent(false);
                    e.setBlock(blockData);
                    e.setTransformation(new Transformation(translation, leftRotation, scale, rightRotation));
                });
            blockDisplayList.add(blockDisplay);
        }
        return true;
    }

    public void stop() {
        SEED_PLANT_TASK_MAP.remove(sapling.toBlock(world));
        clearBlockDisplays();
        if (task != null) {
            task.cancel();
        }
    }

    private void clearBlockDisplays() {
        for (BlockDisplay it : blockDisplayList) {
            it.remove();
        }
        blockDisplayList.clear();
    }

    private void drop() {
        if (!world.getGameRuleValue(GameRule.DO_TILE_DROPS)) return;
        world.dropItem(sapling.toCenterLocation(world), type.seedMytems.createItemStack());
    }

    private void tick() {
        int ticks = totalTicks++;
        if (!player.isOnline() || !world.isChunkLoaded(sapling.x >> 4, sapling.z >> 4)) {
            stop();
            return;
        }
        switch (state) {
        case CANCELLED:
            stop();
            drop();
            break;
        case INITIALIZED: {
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
            state = State.SPROUT_PREVIEW;
            break;
        }
        case SPROUT_PREVIEW:
            if (sapling.toBlock(world).getType() != type.saplingMaterial) {
                stop();
                return;
            }
            if (ticks % 12 == 0) {
                Location location = sapling.toCenterLocation(world);
                world.spawnParticle(Particle.BLOCK, location, 8, 0.25, 0.25, 0.25, 0.0,
                                    type.saplingMaterial.createBlockData());
                world.playSound(location, Sound.BLOCK_GRASS_BREAK, SoundCategory.BLOCKS, 0.5f, 1.75f);
            }
            for (BlockDisplay blockDisplay : blockDisplayList) {
                blockDisplay.setRotation((float) ticks * 4.0f, 0f);
            }
            if (ticks >= saplingTicks) {
                state = State.START_GROWING;
            }
            break;
        case START_GROWING:
            clearBlockDisplays();
            sapling.toBlock(world).setType(Material.AIR);
            if (!valid) {
                state = State.INVALID;
                stop();
                drop();
                Location location = sapling.toLocation(world).add(0.5, 0.5, 0.5);
                world.playSound(location, Sound.BLOCK_GRASS_BREAK, SoundCategory.BLOCKS, 1.0f, 0.5f);
            } else {
                state = State.GROW;
                plugin.getLogger().info("Growing " + type + " " + treeStructure.name
                                        + " at " + world.getName() + " " + sapling
                                        + " for " + player.getName());
            }
            break;
        case GROW:
            for (int i = 0; i < 8;) {
                if (blockIndex >= treeStructure.getPlaceBlockList().size()) {
                    state = State.DONE;
                    stop();
                    return;
                }
                Vec3i originVector = treeStructure.getPlaceBlockList().get(blockIndex++);
                BlockData blockData = treeStructure.getBlockDataMap().get(originVector);
                i += Tag.LEAVES.isTagged(blockData.getMaterial()) ? 1 : 4;
                Vec3i blockVector = toWorldVector(originVector);
                Block block = blockVector.toBlock(world);
                if (!canReplaceBlock(block)) {
                    stop();
                    return;
                }
                if (blockData instanceof Leaves leaves) {
                    leaves.setPersistent(true);
                }
                Transform.rotate(blockData, rotation, mirror);
                new PlayerChangeBlockEvent(player, block, blockData).callEvent();
                block.setBlockData(blockData, false);
                SoundGroup soundGroup = blockData.getSoundGroup();
                world.playSound(block.getLocation().add(0.5, 0.5, 0.5), soundGroup.getPlaceSound(), SoundCategory.BLOCKS, 0.5f, 1.65f);
            }
            break;
        default: throw new IllegalStateException("state=" + state);
        }
    }

    /**
     * Player breaks sapling.  This function checks if the sapling can
     * currently be broken and updates the state of this task
     * accordingly.  The calling event handler must stop the sapling
     * from dropping.
     *
     * @return true if the sapling is now considered broken, false
     * otherwise.
     */
    protected boolean onBreakSapling(Player thePlayer) {
        if (state != State.SPROUT_PREVIEW) {
            return false;
        }
        final Block saplingBlock = sapling.toBlock(world);
        if (saplingBlock.getType() != type.getSaplingMaterial()) {
            return false;
        }
        state = State.CANCELLED;
        return true;
    }
}
