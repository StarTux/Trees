package com.winthier.trees;

import org.bukkit.TreeSpecies;
import org.bukkit.block.Block;
import org.bukkit.material.MaterialData;
import org.bukkit.material.Wood;

public enum TreeType {
    OAK,
    BIRCH,
    SPRUCE,
    JUNGLE,
    DARK_OAK,
    ACACIA,
    BROWN_MUSHROOM,
    RED_MUSHROOM,
    DEAD_BUSH;

    static TreeType of(TreeSpecies species) {
        if (species == null) throw new NullPointerException();
        switch (species) {
        case ACACIA: return ACACIA;
        case BIRCH: return BIRCH;
        case DARK_OAK: return DARK_OAK;
        case GENERIC: return OAK;
        case JUNGLE: return JUNGLE;
        case REDWOOD: return SPRUCE;
        default: return null;
        }
    }

    static TreeType of(Block block) {
        MaterialData materialData = block.getState().getData();
        if (materialData instanceof Wood) {
            return of(((Wood)materialData).getSpecies());
        }
        switch (block.getType()) {
        case BROWN_MUSHROOM:
        case HUGE_MUSHROOM_1:
            return BROWN_MUSHROOM;
        case RED_MUSHROOM:
        case HUGE_MUSHROOM_2:
            return RED_MUSHROOM;
        case DEAD_BUSH:
            if (block.getData() == 0) return DEAD_BUSH;
        default:
            return null;
        }
    }

    org.bukkit.TreeType getBukkitTreeType() {
        switch (this) {
        case OAK: return org.bukkit.TreeType.TREE;
        case BIRCH: return org.bukkit.TreeType.BIRCH;
        case SPRUCE: return org.bukkit.TreeType.REDWOOD;
        case JUNGLE: return org.bukkit.TreeType.JUNGLE;
        case DARK_OAK: return org.bukkit.TreeType.DARK_OAK;
        case ACACIA: return org.bukkit.TreeType.ACACIA;
        case BROWN_MUSHROOM: return org.bukkit.TreeType.BROWN_MUSHROOM;
        case RED_MUSHROOM: return org.bukkit.TreeType.RED_MUSHROOM;
        default: return null;
        }
    }
}
