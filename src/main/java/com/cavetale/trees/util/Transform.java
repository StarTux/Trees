package com.cavetale.trees.util;

import com.cavetale.core.struct.Vec3i;
import java.util.EnumSet;
import java.util.Set;
import org.bukkit.Axis;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Wall;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import static com.cavetale.trees.TreesPlugin.vector;

/**
 * Vector transformation utilities.
 */
public final class Transform {
    private Transform() { }

    /**
     * Rotate a vector around the origin (0, 0, 0) along the Y axis.
     * @param vec the input vector
     * @param rotation the rotation
     * @param mirror the mirror
     * @return the rotated vector
     */
    public static Vec3i rotate(Vec3i vec, StructureRotation rotation, Mirror mirror) {
        if (rotation == StructureRotation.NONE && mirror == Mirror.NONE) return vec;
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
        return vector(x, y, z);
    }

    public static BlockFace rotate(BlockFace blockFace, StructureRotation rotation, Mirror mirror) {
        if (rotation == StructureRotation.NONE && mirror == Mirror.NONE) return blockFace;
        Vec3i vec = Vec3i.of(blockFace);
        vec = rotate(vec, rotation, mirror);
        if (vec.x > 0) return BlockFace.EAST;
        if (vec.x < 0) return BlockFace.WEST;
        if (vec.z > 0) return BlockFace.SOUTH;
        if (vec.z < 0) return BlockFace.NORTH;
        if (vec.y > 0) return BlockFace.UP;
        if (vec.y < 0) return BlockFace.DOWN;
        return blockFace;
    }

    /**
     * Rotate an axis.  Mirror does not make a difference.
     */
    public static Axis rotate(Axis axis, StructureRotation rotation) {
        if (axis == Axis.Y) return axis;
        if (rotation == StructureRotation.NONE) return axis;
        switch (rotation) {
        case CLOCKWISE_90:
        case COUNTERCLOCKWISE_90:
            axis = (axis == Axis.X) ? Axis.Z : Axis.X;
            break;
        case CLOCKWISE_180:
        case NONE:
        default:
            break;
        }
        return axis;
    }

    /**
     * Modify block data in accordance with the given rotations.
     * @param blockData the block data to be modified
     * @param rotation the rotation
     * @param mirror the mirror
     */
    public static void rotate(BlockData blockData, StructureRotation rotation, Mirror mirror) {
        if (rotation == StructureRotation.NONE && mirror == Mirror.NONE) return;
        if (blockData instanceof Directional directional) {
            BlockFace face = rotate(directional.getFacing(), rotation, mirror);
            if (directional.getFaces().contains(face)) {
                directional.setFacing(face);
            }
        }
        if (blockData instanceof MultipleFacing facing) {
            Set<BlockFace> oldFaces = facing.getFaces();
            Set<BlockFace> newFaces = EnumSet.noneOf(BlockFace.class);
            for (BlockFace face : oldFaces) {
                newFaces.add(rotate(face, rotation, mirror));
            }
            for (BlockFace it : oldFaces) facing.setFace(it, false);
            Set<BlockFace> allowed = facing.getAllowedFaces();
            for (BlockFace it : newFaces) {
                if (allowed.contains(it)) {
                    facing.setFace(it, true);
                }
            }
        }
        if (blockData instanceof Orientable ori) {
            Axis axis = rotate(ori.getAxis(), rotation);
            if (ori.getAxes().contains(axis)) {
                ori.setAxis(axis);
            }
        }
        if (blockData instanceof Wall wall) {
            BlockFace[] faces = {
                BlockFace.NORTH,
                BlockFace.EAST,
                BlockFace.SOUTH,
                BlockFace.WEST,
            };
            String old = wall.getAsString(false);
            Wall.Height[] heights = new Wall.Height[faces.length];
            for (int i = 0; i < faces.length; i += 1) {
                BlockFace face = faces[i];
                heights[i] = wall.getHeight(face);
            }
            for (int i = 0; i < heights.length; i += 1) {
                BlockFace face = rotate(faces[i], rotation, mirror);
                wall.setHeight(face, heights[i]);
            }
        }
    }
}
