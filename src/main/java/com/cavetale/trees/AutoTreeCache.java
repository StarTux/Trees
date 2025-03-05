package com.cavetale.trees;

import com.cavetale.mytems.item.axis.CuboidOutline;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import org.bukkit.entity.Player;
import org.bukkit.structure.Structure;

@Data
final class AutoTreeCache {
    private static final Map<UUID, AutoTreeCache> PLAYER_MAP = new HashMap<>();
    private final TreeStructure treeStructure;
    private final Structure structure;
    private CuboidOutline boundingBox;
    private CuboidOutline sapling;
    private final List<CuboidOutline> badBlocks = new ArrayList<>();
    private boolean valid;

    public void clear() {
        if (boundingBox != null) {
            boundingBox.remove();
            boundingBox = null;
        }
        if (sapling != null) {
            sapling.remove();
            sapling = null;
        }
        for (CuboidOutline it : badBlocks) {
            it.remove();
        }
        badBlocks.clear();
    }

    public static AutoTreeCache get(Player player) {
        return PLAYER_MAP.get(player.getUniqueId());
    }

    public static AutoTreeCache remove(Player player) {
        final AutoTreeCache result = PLAYER_MAP.remove(player.getUniqueId());
        if (result != null) result.clear();
        return result;
    }

    public static AutoTreeCache create(Player player, TreeStructure treeStructure, Structure structure) {
        remove(player);
        final AutoTreeCache result = new AutoTreeCache(treeStructure, structure);
        PLAYER_MAP.put(player.getUniqueId(), result);
        return result;
    }

    public static void clearAll() {
        for (AutoTreeCache cache : PLAYER_MAP.values()) {
            cache.clear();
        }
        PLAYER_MAP.clear();
    }
}
