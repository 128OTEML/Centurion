package centurion.utils.stats;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.scene.ui.Image;
import mindustry.gen.Tex;

public class RuneItem extends CenturyItems {

    public TextureRegion lineRegion;

    public static final Color tier1Color = Color.valueOf("3b82f6"); // Blue
    public static final Color tier2Color = Color.valueOf("ef4444"); // Red
    public static final Color tier3Color = Color.valueOf("a855f7"); // Purple
    public static final Color emptyColor = Color.valueOf("808080"); // Gray

    public RuneItem(String name, Color color) {
        super(name, color);
    }

    public RuneItem(String name) {
        super(name);
    }

    public static Color tierColor(int tier) {
        switch (tier) {
            case 1: return tier1Color;
            case 2: return tier2Color;
            case 3: return tier3Color;
            default: return emptyColor;
        }
    }

    @Override
    public void setStats() {
        super.setStats();

        stats.add(CenturyStats.runeTiers, table -> {
            table.row();
            for (int t = 1; t <= 3; t++) {
                final int tier = t;
                table.table(tTable -> {
                    tTable.left();
                    tTable.table(imgBox -> {
                        imgBox.background(Tex.pane);
                        imgBox.stack(
                            new Image(uiIcon != null ? uiIcon : Core.atlas.find("clear")),
                            new Image(lineRegion != null ? lineRegion : Core.atlas.find("clear")) {{
                                setColor(tierColor(tier));
                            }}
                        ).size(32f).pad(2f);
                    }).size(36f).padRight(8f);

                    // Tier requirement description
                    String chargeStr = tier == 1 ? "" : (tier == 2 ? "" : "");
                    tTable.add("Tier " + tier + " - Carga: " + chargeStr).left();
                }).left().pad(4f).row();
            }
        });
    }

    @Override
    public void loadIcon() {
        super.loadIcon();
        if (Core.atlas.has(name + "-line")) {
            lineRegion = Core.atlas.find(name + "-line");
        } else if (Core.atlas.has("C-" + name + "-line")) {
            lineRegion = Core.atlas.find("C-" + name + "-line");
        } else if (Core.atlas.has("C-empty-rune-line")) {
            lineRegion = Core.atlas.find("C-empty-rune-line");
        } else if (Core.atlas.has("empty-rune-line")) {
            lineRegion = Core.atlas.find("empty-rune-line");
        }
    }

    public void drawRuneLayer(float x, float y, float width, float height, int tier) {
        Draw.color();
        Draw.rect(uiIcon, x, y, width, height);
        if (lineRegion != null && lineRegion.found()) {
            Draw.color(tierColor(tier));
            Draw.rect(lineRegion, x, y, width, height);
            Draw.color();
        }
    }
}
