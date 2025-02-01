package org.ironsight.wpplugin.expandLayerTool.operations.ValueProviders;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;

import java.awt.*;
import java.util.Arrays;
import java.util.HashSet;

public class StonePaletteApplicator implements IPositionValueSetter {
    private final Terrain[] materials;
    private final transient HashSet<Terrain> mats;
    Color[] colors = new Color[]{
            // Terrain.GRASS (Grass Block)
            new Color(85, 107, 47),      // Dark Olive Green
            // Terrain.GRAVEL (Gravel)
            new Color(112, 112, 112),    // Gray
            // Terrain.STONE (Stone)
            new Color(169, 169, 169),    // Dark Gray
            // Terrain.COBBLESTONE (Cobblestone)
            new Color(128, 128, 128),    // Gray
            // Terrain.MOSSY_COBBLESTONE (Mossy Cobblestone)
            new Color(85, 107, 47),      // Dark Olive Green
            // Terrain.GRANITE (Granite)
            new Color(179, 83, 57),      // Reddish Granite
            // Terrain.DIORITE (Diorite)
            new Color(212, 212, 212),    // Light Gray
            // Terrain.ANDESITE (Andesite)
            new Color(128, 128, 128),    // Gray
            // Terrain.DEEPSLATE (Deepslate)
            new Color(44, 44, 48),       // Dark Slate
            // Terrain.STONE_MIX (Stone Mix)
            new Color(128, 128, 128),    // Mixed Gray
            // Terrain.ROCK (Rock)
            new Color(169, 169, 169)     // Dark Gray (similar to Stone)
    };

    public StonePaletteApplicator() {
        materials = new Terrain[]{Terrain.GRASS, Terrain.GRAVEL, Terrain.STONE, Terrain.COBBLESTONE,
                Terrain.MOSSY_COBBLESTONE, Terrain.GRANITE, Terrain.DIORITE, Terrain.ANDESITE, Terrain.DEEPSLATE,
                Terrain.STONE_MIX, Terrain.ROCK};
        mats = new HashSet<>(Arrays.asList(materials));
    }

    @Override
    public String getName() {
        return "Stone Palette";
    }

    @Override
    public String getDescription() {
        return "a palette of most common stones";
    }

    @Override
    public void setValueAt(Dimension dim, int x, int y, int value) {
        dim.setTerrainAt(x, y, materials[value]);
    }

    @Override
    public int getMinValue() {
        return 0;
    }

    @Override
    public int getMaxValue() {
        return materials.length - 1;
    }

    @Override
    public String valueToString(int value) {
        if (value < 0 || value > materials.length) return "INVALID";
        return materials[value].getName() + "(" + value + ")";
    }

    @Override
    public boolean isDiscrete() {
        return true;
    }

    @Override
    public void paint(Graphics g, int value, java.awt.Dimension dim) {
        g.setColor(colors[value]);
        g.fillRect(0, 0, dim.width, dim.height);
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && this.getClass().equals(obj.getClass()) && Arrays.equals(materials,
                ((StonePaletteApplicator) obj).materials);
    }
}
