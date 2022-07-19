package com.cavetale.trees;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.Tag;

public final class Materials {
    /**
     * These materials determine where which blocks count as the
     * floor.  They are also not considered part of any tree.
     */
    public static final Set<Material> GROUND = Set.of(Material.DIRT,
                                                      Material.COARSE_DIRT,
                                                      Material.GRASS_BLOCK,
                                                      Material.MOSS_BLOCK,
                                                      Material.ROOTED_DIRT,
                                                      Material.STONE);

    /**
     * In addition to ground materials, ignored materials are not
     * considered part of any tree within createPlaceBlockList().
     */
    public static final Set<Material> IGNORED;

    /**
     * Vine materials, in addition to leaves, are placed after any
     * other block within createPlaceBlockList().
     */
    public static final Set<Material> VINE = Set.of(Material.VINE,
                                                    Material.CAVE_VINES,
                                                    Material.CAVE_VINES_PLANT,
                                                    Material.GLOW_LICHEN,
                                                    Material.TWISTING_VINES,
                                                    Material.TWISTING_VINES_PLANT,
                                                    Material.WEEPING_VINES,
                                                    Material.WEEPING_VINES_PLANT,
                                                    Material.SNOW);

    static {
        Set<Material> ignored = new HashSet<>();
        ignored.addAll(Set.of(Material.DIRT, Material.SNOW));
        ignored.addAll(Tag.SIGNS.getValues());
        IGNORED = Set.copyOf(ignored);
    }

    private Materials() { }
}
