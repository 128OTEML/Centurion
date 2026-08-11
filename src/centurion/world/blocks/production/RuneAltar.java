package centurion.world.blocks.production;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.scene.ui.Image;
import arc.scene.ui.layout.Table;
import arc.util.io.Reads;
import arc.util.io.Writes;
import centurion.content.CItems;
import centurion.utils.stats.CenturyItems;
import centurion.utils.stats.RuneItem;
import mindustry.gen.Building;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.blocks.heat.HeatConsumer;

import java.util.Arrays;

public class RuneAltar extends Block {

    public float craftTime = 120f;
    public float maxSuccessRate = 0.95f; // 95% max probability
    public float heatRequirement = 10f; // Minimum heat needed to operate

    public RuneAltar(String name) {
        super(name);
        update = true;
        solid = true;
        hasItems = true;
        itemCapacity = 20;
        configurable = true;
        saveConfig = true;

        config(Integer.class, (RuneAltarBuild tile, Integer value) -> {
            tile.selectedTier = value;
        });
    }

    @Override
    public void setBars() {
        super.setBars();

        addBar("heat", (RuneAltarBuild entity) -> new Bar(
            () -> "Calor: " + (int)entity.heat + " / " + (int)heatRequirement,
            () -> Pal.lightOrange,
            () -> Math.min(1f, entity.heat / heatRequirement)
        ));

        addBar("charge", (RuneAltarBuild entity) -> new Bar(
            () -> "Carga: " + (int)entity.currentCharge + " / " + (int)entity.getTargetThreshold(),
            () -> entity.getTierColor(),
            () -> entity.getTargetThreshold() > 0 ? Math.min(1f, entity.currentCharge / entity.getTargetThreshold()) : 0f
        ));

        addBar("chance", (RuneAltarBuild entity) -> new Bar(
            () -> "Éxito: " + (int)(entity.getSuccessChance() * 100f) + "%",
            () -> Pal.accent,
            () -> entity.getSuccessChance()
        ));
    }

    public class RuneAltarBuild extends Building implements HeatConsumer {
        public float currentCharge = 0f;
        public boolean hasEmptyRune = false;
        public float progress = 0f;
        public int selectedTier = 1;

        public float[] sideHeat = new float[4];
        public float heat = 0f;

        public float[] sideHeat() {
            return sideHeat;
        }

        public float heat() {
            return heat;
        }

        public float heatRequirement() {
            return heatRequirement;
        }

        public float getTargetThreshold() {
            if (selectedTier == 3) return 10000f;
            if (selectedTier == 2) return 5000f;
            return 1000f;
        }

        public Color getTierColor() {
            return RuneItem.tierColor(selectedTier);
        }

        public float getSuccessChance() {
            if (!hasEmptyRune) return 0f;
            float threshold = getTargetThreshold();
            float rawRatio = (currentCharge / threshold) * maxSuccessRate;
            return Math.min(maxSuccessRate, rawRatio);
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            if (item == CItems.emptyRune) {
                return !hasEmptyRune;
            }
            if (item instanceof CenturyItems) {
                CenturyItems cItem = (CenturyItems) item;
                return cItem.magicCharge > 0 && currentCharge < getTargetThreshold();
            }
            return false;
        }

        @Override
        public void handleItem(Building source, Item item) {
            if (item == CItems.emptyRune && !hasEmptyRune) {
                hasEmptyRune = true;
                return;
            }
            if (item instanceof CenturyItems) {
                float charge = ((CenturyItems) item).magicCharge;
                currentCharge += charge;
            }
        }

        @Override
        public void buildConfiguration(Table table) {
            table.defaults().size(50f, 50f).pad(6f);

            for (int t = 1; t <= 3; t++) {
                final int tier = t;
                table.button(b -> {
                    Image baseImg = new Image(CItems.emptyRune != null && CItems.emptyRune.uiIcon != null ? CItems.emptyRune.uiIcon : Core.atlas.find("clear"));
                    Image lineImg = new Image(CItems.emptyRune != null && CItems.emptyRune.lineRegion != null ? CItems.emptyRune.lineRegion : Core.atlas.find("clear"));
                    lineImg.color.set(RuneItem.tierColor(tier));

                    b.stack(baseImg, lineImg).size(32f);
                }, Styles.clearTogglei, () -> {
                    configure(tier);
                    deselect();
                }).checked(selectedTier == tier);
            }
        }

        @Override
        public Integer config() {
            return selectedTier;
        }

        @Override
        public void updateTile() {
            // Calculate total heat from heat producers
            heat = calculateHeat(sideHeat);
            // Clear sideHeat for next tick so heaters can write heat
            Arrays.fill(sideHeat, 0f);

            // Continuously dump stored items to adjacent conveyors/containers
            if (items.total() > 0) {
                dump();
            }

            if (!hasEmptyRune) {
                progress = 0f;
                return;
            }

            // Requires sufficient heat AND accumulated charge to progress infusion
            if (heat >= heatRequirement && currentCharge > 0) {
                progress += edelta() / craftTime;

                if (progress >= 1f) {
                    craft();
                    progress = 0f;
                }
            }
        }

        public void craft() {
            float chance = getSuccessChance();
            boolean success = Mathf.chance(chance);

            if (success) {
                items.add(CItems.emptyRune, 1);
                dump(CItems.emptyRune);
            }

            // Reset altar state after sacrifice cycle
            hasEmptyRune = false;
            currentCharge = 0f;
        }

        @Override
        public void draw() {
            super.draw();

            if (hasEmptyRune) {
                float floatY = y + Mathf.sin(totalProgress() * 0.05f, 2f, 1.5f);
                Color activeColor = getTierColor();

                if (CItems.emptyRune != null && CItems.emptyRune.uiIcon != null) {
                    Draw.rect(CItems.emptyRune.uiIcon, x, floatY, 12f, 12f);
                }

                if (CItems.emptyRune != null && CItems.emptyRune.lineRegion != null && CItems.emptyRune.lineRegion.found()) {
                    Draw.color(activeColor);
                    Draw.rect(CItems.emptyRune.lineRegion, x, floatY, 12f, 12f);
                    Draw.color();
                }

                if (heat >= heatRequirement) {
                    Draw.z(Layer.effect);
                    Draw.color(activeColor, 0.4f);
                    Lines.stroke(1.5f);
                    Lines.circle(x, y, 8f + Mathf.absin(totalProgress() * 0.1f, 4f, 2f));
                    Draw.color();
                }
            }
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.f(currentCharge);
            write.bool(hasEmptyRune);
            write.f(progress);
            write.i(selectedTier);
            write.f(heat);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            currentCharge = read.f();
            hasEmptyRune = read.bool();
            progress = read.f();
            selectedTier = read.i();
            heat = read.f();
        }
    }
}
