package com.cavetale.trees;

import com.cavetale.area.struct.Vec3i;
import com.cavetale.mytems.item.tree.CustomTreeType;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.structure.Structure;
import static org.bukkit.persistence.PersistentDataType.*;

@RequiredArgsConstructor @Data
public final class TreeStructure {
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
        Set<Material> groundBlockTypes = EnumSet.of(Material.DIRT, Material.GRASS);
        for (var palette : structure.getPalettes()) {
            for (var blockState : palette.getBlocks()) {
                if (groundBlockTypes.contains(blockState.getType())) {
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
                    if (!blockState.getType().isEmpty() && !groundBlockTypes.contains(blockState.getType())) {
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
}
