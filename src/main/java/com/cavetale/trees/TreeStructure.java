package com.cavetale.trees;

import com.cavetale.area.struct.Vec3i;
import com.cavetale.mytems.item.tree.CustomTreeType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.structure.Structure;
import static org.bukkit.persistence.PersistentDataType.*;

@RequiredArgsConstructor @Data
public final class TreeStructure {
    public static final Set<Material> GROUND_MATERIALS = EnumSet.of(Material.DIRT, new Material[] {
            Material.COARSE_DIRT,
            Material.GRASS_BLOCK,
            Material.MOSS_BLOCK,
            Material.ROOTED_DIRT,
        });
    public static final Set<Material> VINE_MATERIALS = EnumSet.of(Material.VINE, new Material[] {
            Material.CAVE_VINES,
            Material.CAVE_VINES_PLANT,
            Material.GLOW_LICHEN,
            Material.TWISTING_VINES,
            Material.TWISTING_VINES,
            Material.TWISTING_VINES_PLANT,
            Material.WEEPING_VINES,
            Material.WEEPING_VINES_PLANT,
        });
    public static final String ORIGIN_WORLD = "origin_world";
    public static final String ORIGIN = "origin";
    public static final String SAPLING = "sapling";
    public static final int[] EMPTY = new int[0];
    protected final CustomTreeType type;
    protected final String name;
    protected final Structure structure;
    /** Origin world name, to identify duplicates. */
    protected String originWorld;
    /**  Origin to identify duplicates. */
    protected Vec3i origin;
    /** Marks the relative spot where the sapling goes. */
    protected Vec3i sapling;

    /**
     * Call after loading from file in order to pull the metadata out
     * of the structure NBT.
     */
    public void load() {
        PersistentDataContainer pdc = structure.getPersistentDataContainer();
        originWorld = pdc.getOrDefault(TreesPlugin.namespacedKey(ORIGIN_WORLD),
                                       STRING, "");
        int[] intArray;
        intArray = pdc.getOrDefault(TreesPlugin.namespacedKey(ORIGIN),
                                    INTEGER_ARRAY, EMPTY);
        origin = intArray.length == 3
            ? new Vec3i(intArray[0], intArray[1], intArray[2])
            : Vec3i.ZERO;
        intArray = pdc.getOrDefault(TreesPlugin.namespacedKey(SAPLING),
                                    INTEGER_ARRAY, EMPTY);
        sapling = intArray.length == 3
            ? new Vec3i(intArray[0], intArray[1], intArray[2])
            : Vec3i.ZERO;
    }

    /**
     * Call after creation of a new structure in order to find the sapling spots.
     * @param theOriginWorld the origin world name.
     * @param offset the origin world offset of the structure.
     * @return true if sapling and origin were successfully determined
     *         and saved, false otherwise.
     */
    public boolean preprocess(@NonNull String theOriginWorld, @NonNull Vec3i offset) {
        PersistentDataContainer pdc = structure.getPersistentDataContainer();
        this.originWorld = theOriginWorld;
        this.origin = offset;
        pdc.set(TreesPlugin.namespacedKey(ORIGIN_WORLD), STRING, originWorld);
        pdc.set(TreesPlugin.namespacedKey(ORIGIN), INTEGER_ARRAY, new int[] {
                origin.x,
                origin.y,
                origin.z,
            });
        // Now the tricky part: Find the sapling!
        int tallestGroundBlock = -1;
        for (var palette : structure.getPalettes()) {
            for (var blockState : palette.getBlocks()) {
                if (GROUND_MATERIALS.contains(blockState.getType())) {
                    if (tallestGroundBlock < blockState.getY()) {
                        tallestGroundBlock = blockState.getY();
                    }
                }
            }
        }
        if (tallestGroundBlock < 0) return false;
        List<Vec3i> saplingBlockList = new ArrayList<>();
        for (var palette : structure.getPalettes()) {
            for (var blockState : palette.getBlocks()) {
                if (blockState.getY() == tallestGroundBlock + 1) {
                    if (!blockState.getType().isEmpty() && !GROUND_MATERIALS.contains(blockState.getType())) {
                        saplingBlockList.add(new Vec3i(blockState.getX(), blockState.getY(), blockState.getZ()));
                    }
                }
            }
        }
        if (saplingBlockList.isEmpty()) return false;
        int totalX = 0;
        int totalZ = 0;
        for (Vec3i it : saplingBlockList) {
            totalX += it.x;
            totalZ += it.z;
        }
        this.sapling = new Vec3i(totalX / saplingBlockList.size(),
                                 tallestGroundBlock + 1,
                                 totalZ / saplingBlockList.size());
        pdc.set(TreesPlugin.namespacedKey(SAPLING), INTEGER_ARRAY, new int[] {
                sapling.x,
                sapling.y,
                sapling.z,
            });
        return true;
    }

    public void show(Player player, Vec3i offset) {
        Map<Location, BlockData> blockChanges = new HashMap<>();
        for (var palette : structure.getPalettes()) {
            for (var blockState : palette.getBlocks()) {
                Vec3i at = offset.add(blockState.getX() - sapling.x,
                                      blockState.getY() - sapling.y,
                                      blockState.getZ() - sapling.z);
                blockChanges.put(at.toLocation(player.getWorld()), blockState.getBlockData());
            }
        }
        player.sendMultiBlockChange(blockChanges);
    }

    public Map<Vec3i, BlockData> createBlockDataMap() {
        Map<Vec3i, BlockData> blockDataMap = new HashMap<>();
        for (var blockState : structure.getPalettes().get(0).getBlocks()) {
            blockDataMap.put(new Vec3i(blockState.getX(), blockState.getY(), blockState.getZ()),
                             blockState.getBlockData());
        }
        return blockDataMap;
    }

    private static List<Vec3i> getAllFaces() {
        List<Vec3i> result = new ArrayList<>(8);
        for (int y = -1; y <= 1; y += 1) {
            for (int z = -1; z <= 1; z += 1) {
                for (int x = -1; x <= 1; x += 1) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    result.add(new Vec3i(x, y, z));
                }
            }
        }
        Collections.shuffle(result);
        return result;
    }

    public List<Vec3i> createPlaceBlockList(Map<Vec3i, BlockData> blockDataMap) {
        List<Vec3i> blockList = new ArrayList<>();
        Set<Vec3i> doneBlockSet = new HashSet<>();
        blockList.add(sapling);
        doneBlockSet.add(sapling);
        int blockIndex = 0;
        while (blockIndex < blockList.size()) {
            int i = blockIndex++;
            Vec3i center = blockList.get(i);
            List<Vec3i> faces = getAllFaces();
            for (Vec3i nborFace : faces) {
                Vec3i nborVec = center.add(nborFace);
                if (doneBlockSet.contains(nborVec)) continue;
                doneBlockSet.add(nborVec);
                BlockData nborBlock = blockDataMap.get(nborVec);
                if (nborBlock == null) continue;
                Material nborMat = nborBlock.getMaterial();
                if (nborMat.isEmpty()) continue;
                if (GROUND_MATERIALS.contains(nborMat)) continue;
                blockList.add(nborVec);
            }
        }
        // Sort them: Logs go first
        List<Vec3i> logs = new ArrayList<>();
        List<Vec3i> leaves = new ArrayList<>();
        for (Vec3i vec : blockList) {
            BlockData blockData = blockDataMap.get(vec);
            Material material = blockData.getMaterial();
            if (Tag.LEAVES.isTagged(material)) {
                leaves.add(vec);
            } else if (Tag.LOGS.isTagged(material)) {
                logs.add(vec);
            } else if (VINE_MATERIALS.contains(material)) {
                leaves.add(vec);
            } else {
                logs.add(vec);
            }
        }
        List<Vec3i> result = new ArrayList<>();
        result.addAll(logs);
        result.addAll(leaves);
        return result;
    }

    public List<Vec3i> createPlaceBlockList() {
        return createPlaceBlockList(createBlockDataMap());
    }
}
