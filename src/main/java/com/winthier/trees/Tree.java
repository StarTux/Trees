package com.winthier.trees;

import com.winthier.custom.CustomPlugin;
import com.winthier.generic_events.GenericEventsPlugin;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

@Getter
public final class Tree {
    @Value
    static class Voxel {
        private final int x, y, z, type, data;
        void setBlock(Block block) {
            block.setTypeIdAndData(type, (byte)data, true);
        }
    }

    @Setter private TreeType type;
    private final List<Voxel> voxels = new ArrayList<>();

    void serialize(ConfigurationSection section) {
        List<Integer> list = new ArrayList<>(voxels.size() * 5);
        for (Voxel voxel: voxels) {
            list.add(voxel.x);
            list.add(voxel.y);
            list.add(voxel.z);
            list.add(voxel.type);
            list.add(voxel.data);
        }
        if (type != null) section.set("Type", type.name());
        section.set("Voxels", list);
    }

    void deserialize(ConfigurationSection section) {
        String typeName = section.getString("Type");
        if (typeName != null) {
            try {
                type = TreeType.valueOf(typeName);
            } catch (IllegalArgumentException iae) {
                iae.printStackTrace();
            }
        }
        for (Iterator<Integer> iter = section.getIntegerList("Voxels").iterator(); iter.hasNext();) {
            voxels.add(new Voxel(iter.next(), iter.next(), iter.next(), iter.next(), iter.next()));
        }
    }

    void select(Block rootBlock) {
        List<Block> found = new ArrayList<>(999);
        LinkedList<Block> todo = new LinkedList<>();
        Set<Block> done = new HashSet<>();
        todo.add(rootBlock);
        while (!todo.isEmpty() && found.size() <= 999) {
            Block block = todo.removeFirst();
            if (block.getType() == Material.AIR || block.getType() == Material.BEDROCK) continue;
            found.add(block);
            for (int y = -1; y <= 1; y += 1) {
                for (int z = -1; z <= 1; z += 1) {
                    for (int x = -1; x <= 1; x += 1) {
                        if (x == 0 && y == 0 && z == 0) continue;
                        Block nbor = block.getRelative(x, y, z);
                        if (done.contains(nbor)) continue;
                        todo.add(nbor);
                        done.add(nbor);
                    }
                }
            }
        }
        for (Block block: found) {
            voxels.add(new Voxel(block.getX() - rootBlock.getX(),
                                 block.getY() - rootBlock.getY(),
                                 block.getZ() - rootBlock.getZ(),
                                 block.getTypeId(), (int)block.getData()));
        }
    }

    void grow(Block rootBlock) {
        for (Voxel voxel: voxels) {
            Block block = rootBlock.getRelative(voxel.x, voxel.y, voxel.z);
            voxel.setBlock(block);
        }
    }

    void growSlowly(TreesPlugin plugin, Player player, Block rootBlock) {
        new BukkitRunnable() {
            private int i = 0;
            @Override public void run() {
                if (i >= voxels.size()) {
                    cancel();
                    return;
                }
                Voxel voxel = voxels.get(i);
                i += 1;
                Block block = rootBlock.getRelative(voxel.x, voxel.y, voxel.z);
                if (voxel.x != 0 && voxel.y != 0 && voxel.z != 0) {
                    if (!canPlaceBlock(block)) return;
                }
                if (!GenericEventsPlugin.getInstance().playerCanBuild(player, block)) return;
                Material mat = Material.getMaterial(voxel.type);
                switch (mat) {
                case LEAVES:
                case LEAVES_2:
                    block.getWorld().playSound(block.getLocation(), Sound.BLOCK_GRASS_PLACE, SoundCategory.BLOCKS, 1f, 1f);
                    break;
                default:
                    block.getWorld().playSound(block.getLocation(), Sound.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1f, 1f);
                    break;
                }
                voxel.setBlock(block);
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    void highlight(Player player, Block rootBlock) {
        for (Voxel voxel: voxels) {
            if (voxel.x == 0 && voxel.y == 0 && voxel.z == 0) {
                player.sendBlockChange(rootBlock.getLocation(), Material.GOLD_BLOCK.getId(), (byte)0);
            } else {
                player.sendBlockChange(rootBlock.getRelative(voxel.x, voxel.y, voxel.z).getLocation(),
                                       Material.STAINED_GLASS.getId(), (byte)0);
            }
        }
    }

    void show(Player player, Block rootBlock) {
        for (Voxel voxel: voxels) {
            player.sendBlockChange(rootBlock.getRelative(voxel.x, voxel.y, voxel.z).getLocation(),
                                       voxel.type, (byte)voxel.data);
        }
    }

    private static boolean canPlaceBlock(Block block) {
        if (CustomPlugin.getInstance().getBlockManager().getBlockWatcher(block) != null) return false;
        switch (block.getType()) {
        case AIR:
        case DIRT:
        case GRASS:
        case GRASS_PATH:
        case GRAVEL:
        case LEAVES:
        case LEAVES_2:
        case LONG_GRASS:
        case SAPLING:
            return true;
        default:
            return false;
        }
    }

    boolean isBlocked(Block rootBlock) {
        for (Voxel voxel: voxels) {
            if (!canPlaceBlock(rootBlock.getRelative(voxel.x, voxel.y, voxel.z))) {
                return true;
            }
        }
        return false;
    }
}