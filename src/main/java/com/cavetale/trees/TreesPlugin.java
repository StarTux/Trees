package com.cavetale.trees;

import com.cavetale.mytems.item.tree.CustomTreeType;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.zip.ZipFile;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.structure.Structure;

public final class TreesPlugin extends JavaPlugin {
    @Getter protected static TreesPlugin instance;
    private static final String STRUCTURE_SUFFIX = ".dat";
    private final TreesCommand treesCommand = new TreesCommand(this);
    protected final List<TreeStructure> treeStructureList = new ArrayList<>();

    @Override
    public void onEnable() {
        instance = this;
        treesCommand.enable();
        loadTreeStructures();
    }

    protected void loadTreeStructures() {
        treeStructureList.clear();
        loadZipTreeStructures();
        loadLocalTreeStructures();
        for (TreeStructure it : treeStructureList) {
            it.load();
        }
    }

    protected void loadZipTreeStructures() {
        final String fn = "loadZipTreeStructures";
        try (ZipFile zipFile = new ZipFile(getFile())) {
            zipFile.stream().forEach(zipEntry -> {
                    if (zipEntry.isDirectory()) return;
                    String name = zipEntry.getName();
                    String[] names = name.split("/");
                    if (names.length != 3) return;
                    if (!"trees".equals(names[0])) return;
                    String filename = names[2];
                    if (!filename.endsWith(STRUCTURE_SUFFIX)) return;
                    filename = filename.substring(0, filename.length() - STRUCTURE_SUFFIX.length());
                    CustomTreeType customTreeType;
                    try {
                        customTreeType = CustomTreeType.valueOf(names[1].toUpperCase());
                    } catch (IllegalArgumentException iae) {
                        return;
                    }
                    Structure structure;
                    try {
                        structure = Bukkit.getStructureManager().loadStructure(zipFile.getInputStream(zipEntry));
                    } catch (IOException ioe) {
                        getLogger().log(Level.SEVERE, fn + " " + name, ioe);
                        return;
                    }
                    treeStructureList.add(new TreeStructure(customTreeType, filename, structure));
                });
        } catch (IOException ioe) {
            getLogger().log(Level.SEVERE, fn, ioe);
        }
    }

    protected void loadLocalTreeStructures() {
        final String fn = "loadLocalTreeStructures";
        File folder = new File(getDataFolder(), "trees");
        if (!folder.isDirectory()) return;
        for (CustomTreeType customTreeType : CustomTreeType.values()) {
            File subfolder = new File(folder, customTreeType.name().toLowerCase());
            if (!subfolder.isDirectory()) continue;
            for (File file : subfolder.listFiles()) {
                String filename = file.getName();
                if (!filename.endsWith(STRUCTURE_SUFFIX)) continue;
                filename = filename.substring(0, filename.length() - STRUCTURE_SUFFIX.length());
                Structure structure;
                try {
                    structure = Bukkit.getStructureManager().loadStructure(new FileInputStream(file));
                } catch (IOException ioe) {
                    getLogger().log(Level.SEVERE, fn + " " + file, ioe);
                    continue;
                }
                treeStructureList.add(new TreeStructure(customTreeType, filename, structure));
            }
        }
    }

    protected boolean saveTreeStructure(TreeStructure treeStructure) {
        File folder = new File(new File(getDataFolder(), "trees"), treeStructure.getType().name().toLowerCase());
        folder.mkdirs();
        File file = new File(folder, treeStructure.getName() + STRUCTURE_SUFFIX);
        try {
            Bukkit.getStructureManager().saveStructure(file, treeStructure.getStructure());
        } catch (IOException ioe) {
            getLogger().log(Level.SEVERE, "writing " + file, ioe);
            return false;
        }
        return true;
    }

    protected static NamespacedKey namespacedKey(String name) {
        return new NamespacedKey(instance, name);
    }

    public TreeStructure findTreeStructure(CustomTreeType type, String name) {
        for (TreeStructure it : treeStructureList) {
            if (it.type == type && name.equals(it.name)) return it;
        }
        return null;
    }
}
