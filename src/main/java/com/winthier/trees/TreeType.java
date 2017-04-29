package com.winthier.trees;

import org.bukkit.TreeSpecies;
import org.bukkit.block.Block;
import org.bukkit.material.MaterialData;
import org.bukkit.material.Sapling;

public enum TreeType {
    OAK,
    BIRCH,
    SPRUCE,
    JUNGLE,
    DARK_OAK,
    ACACIA,
    BROWN_MUSHROOM,
    RED_MUSHROOM,
    DEAD_BUSH,
    TALL_GRASS,
    FERN,
    POPPY,
    BLUE_ORCHID,
    ALLIUM,
    AZURE_BLUET,
    RED_TULIP,
    ORANGE_TULIP,
    WHITE_TULIP,
    PINK_TULIP,
    OXEYE_DAISY;

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
        switch (block.getType()) {
        case SAPLING:
            MaterialData materialData = block.getState().getData();
            if (materialData instanceof Sapling) {
                return of(((Sapling)materialData).getSpecies());
            } else {
                return null;
            }
        case BROWN_MUSHROOM:
            return BROWN_MUSHROOM;
        case RED_MUSHROOM:
            return RED_MUSHROOM;
        case DEAD_BUSH:
            switch (block.getData()) {
            case 0: return DEAD_BUSH;
            default: return null;
            }
        case LONG_GRASS:
            switch (block.getData()) {
            case 0: return DEAD_BUSH;
            case 1: return TALL_GRASS;
            case 2: return FERN;
            default: return null;
            }
        case RED_ROSE:
            switch (block.getData()) {
            case 0: return POPPY;
            case 1: return BLUE_ORCHID;
            case 2: return ALLIUM;
            case 3: return AZURE_BLUET;
            case 4: return RED_TULIP;
            case 5: return ORANGE_TULIP;
            case 6: return WHITE_TULIP;
            case 7: return PINK_TULIP;
            case 8: return OXEYE_DAISY;
            default: return null;
            }
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
