package com.cavetale.trees;

import com.cavetale.area.struct.Vec3i;
import com.cavetale.mytems.item.tree.CustomTreeType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;
import static org.bukkit.persistence.PersistentDataType.*;

@Data
public final class TreeStructure {
    public static final NamespacedKey ORIGIN_WORLD = NamespacedKey.fromString("trees:origin_world");
    public static final NamespacedKey ORIGIN = NamespacedKey.fromString("trees:origin");
    public static final NamespacedKey SAPLING = NamespacedKey.fromString("trees:sapling");
    public static final int[] EMPTY = new int[0];
    protected final CustomTreeType type;
    protected final String name;
    private Map<Vec3i, BlockData> blockDataMap;
    private List<Vec3i> placeBlockList;
    private final Vec3i size;
    /** Origin world name, to identify duplicates. */
    protected String originWorld;
    /**  Origin to identify duplicates. */
    protected Vec3i origin;
    /** Marks the relative spot where the sapling goes. */
    protected Vec3i sapling;

    public TreeStructure(final CustomTreeType type, final String name, final Structure structure) {
        this.type = type;
        this.name = name;
        BlockVector blockVector = structure.getSize();
        this.size = new Vec3i(blockVector.getBlockX(), blockVector.getBlockY(), blockVector.getBlockZ());
        PersistentDataContainer pdc = structure.getPersistentDataContainer();
        this.originWorld = pdc.getOrDefault(ORIGIN_WORLD, STRING, "");
        int[] intArray;
        intArray = pdc.getOrDefault(ORIGIN, INTEGER_ARRAY, EMPTY);
        this.origin = intArray.length == 3
            ? new Vec3i(intArray[0], intArray[1], intArray[2])
            : Vec3i.ZERO;
        intArray = pdc.getOrDefault(SAPLING, INTEGER_ARRAY, EMPTY);
        this.sapling = intArray.length == 3
            ? new Vec3i(intArray[0], intArray[1], intArray[2])
            : Vec3i.ZERO;
        this.blockDataMap = createBlockDataMap(structure);
        this.placeBlockList = createPlaceBlockList(blockDataMap, sapling);
    }

    public enum PreprocessResult {
        SUCCESS,
        NO_FLOOR,
        NO_SAPLING;
    }

    /**
     * Call after creation of a new structure in order to find the sapling spots.
     * @param theOriginWorld the origin world name.
     * @param offset the origin world offset of the structure.
     * @return true if sapling and origin were successfully determined
     *         and saved, false otherwise.
     */
    public PreprocessResult preprocess(Structure structure, String theOriginWorld, Vec3i offset) {
        PersistentDataContainer pdc = structure.getPersistentDataContainer();
        this.originWorld = theOriginWorld;
        this.origin = offset;
        pdc.set(ORIGIN_WORLD, STRING, originWorld);
        pdc.set(ORIGIN, INTEGER_ARRAY, new int[] {
                origin.x,
                origin.y,
                origin.z,
            });
        // Now the tricky part: Find the sapling!
        int tallestGroundBlock = -1;
        for (var palette : structure.getPalettes()) {
            for (var blockState : palette.getBlocks()) {
                if (Materials.GROUND.contains(blockState.getType())) {
                    if (tallestGroundBlock < blockState.getY()) {
                        tallestGroundBlock = blockState.getY();
                    }
                }
            }
        }
        if (tallestGroundBlock < 0) return PreprocessResult.NO_FLOOR;
        List<Vec3i> saplingBlockList = new ArrayList<>();
        for (var palette : structure.getPalettes()) {
            for (var blockState : palette.getBlocks()) {
                if (blockState.getY() == tallestGroundBlock + 1) {
                    if (!blockState.getType().isEmpty() && !Materials.GROUND.contains(blockState.getType())) {
                        saplingBlockList.add(new Vec3i(blockState.getX(), blockState.getY(), blockState.getZ()));
                    }
                }
            }
        }
        if (saplingBlockList.isEmpty()) return PreprocessResult.NO_SAPLING;
        int totalX = 0;
        int totalZ = 0;
        for (Vec3i it : saplingBlockList) {
            totalX += it.x;
            totalZ += it.z;
        }
        this.sapling = new Vec3i(totalX / saplingBlockList.size(),
                                 tallestGroundBlock + 1,
                                 totalZ / saplingBlockList.size());
        pdc.set(SAPLING, INTEGER_ARRAY, new int[] {
                sapling.x,
                sapling.y,
                sapling.z,
            });
        this.blockDataMap = createBlockDataMap(structure);
        this.placeBlockList = createPlaceBlockList(blockDataMap, sapling);
        return PreprocessResult.SUCCESS;
    }

    public void show(Player player, Vec3i offset) {
        Map<Location, BlockData> blockChanges = new HashMap<>();
        for (Vec3i vec : placeBlockList) {
            BlockData blockData = blockDataMap.get(vec);
            Vec3i at = offset.add(vec.getX() - sapling.x,
                                  vec.getY() - sapling.y,
                                  vec.getZ() - sapling.z);
            blockChanges.put(at.toLocation(player.getWorld()), blockData);
        }
        player.sendMultiBlockChange(blockChanges);
    }

    private static Map<Vec3i, BlockData> createBlockDataMap(Structure structure) {
        Map<Vec3i, BlockData> blockDataMap = new HashMap<>();
        for (var blockState : structure.getPalettes().get(0).getBlocks()) {
            BlockData blockData = blockState.getBlockData();
            if (blockData == null || blockData.getMaterial().isAir()) continue;
            Vec3i vec = new Vec3i(blockState.getX(), blockState.getY(), blockState.getZ());
            blockDataMap.put(vec, blockData);
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

    private static List<Vec3i> createPlaceBlockList(Map<Vec3i, BlockData> blockDataMap, Vec3i start) {
        List<Vec3i> blockList = new ArrayList<>();
        Set<Vec3i> doneBlockSet = new HashSet<>();
        blockList.add(start);
        doneBlockSet.add(start);
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
                if (Materials.GROUND.contains(nborMat)) continue;
                if (Materials.IGNORED.contains(nborMat)) continue;
                blockList.add(nborVec);
            }
        }
        blockList.removeIf(v -> !blockDataMap.containsKey(v));
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
            } else if (Materials.VINE.contains(material)) {
                leaves.add(vec);
            } else {
                logs.add(vec);
            }
        }
        List<Vec3i> result = new ArrayList<>();
        result.addAll(logs);
        result.addAll(leaves);
        blockDataMap.keySet().retainAll(result);
        return result;
    }

    public boolean testPlaceBlockList() {
        return placeBlockList.size() >= 8;
    }

    public void place(Block blockOrigin) {
        for (Vec3i vec : placeBlockList) {
            BlockData blockData = blockDataMap.get(vec);
            blockOrigin.getRelative(vec.x, vec.y, vec.z).setBlockData(blockData, false);
        }
    }
}
