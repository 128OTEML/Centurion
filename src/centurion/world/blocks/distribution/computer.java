package centurion.world.blocks.distribution;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.graphics.Layer;
import mindustry.type.Item;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.Tile;

public class computer extends Block {

    public TextureRegion topRegion, lightsRegion;
    public int rangeTiles = 6;

    public computer(String name) {
        super(name);
        size         = 1;
        update       = true;
        solid        = true;
        configurable = true;
        saveConfig   = true;

        config(int[].class, (ComputerArmBuild b, int[] v) -> {
            if (v != null && v.length >= 2) {
                b.targetArmX = v[0];
                b.targetArmY = v[1];
                b.allowedItems.clear();
                for (int i = 2; i < v.length; i++) {
                    Item it = Vars.content.item(v[i]);
                    if (it != null && !b.allowedItems.contains(it)) {
                        b.allowedItems.add(it);
                    }
                }
            }
        });
    }

    @Override
    public void load() {
        super.load();
        topRegion    = Core.atlas.find(name + "-top",    region);
        lightsRegion = Core.atlas.find(name + "-lights", region);
    }

    public class ComputerArmBuild extends Building {

        public int targetArmX = -1, targetArmY = -1;
        public Seq<Item> allowedItems = new Seq<>();
        public boolean selectingArm = false;
        public boolean mouseHover   = false;

        public boolean isLinked() {
            return targetArmX >= 0 && targetArmY >= 0;
        }

        public SafetyArm.SafetyArmBuild getLinkedArm() {
            if (!isLinked()) return null;
            Building b = Vars.world.build(targetArmX, targetArmY);
            return (b instanceof SafetyArm.SafetyArmBuild) ? (SafetyArm.SafetyArmBuild) b : null;
        }

        public boolean isItemAllowed(Item item) {
            if (allowedItems.isEmpty()) return true;
            return allowedItems.contains(item);
        }

        public void toggleItem(Item item) {
            if (allowedItems.contains(item)) {
                allowedItems.remove(item);
            } else {
                allowedItems.add(item);
            }
            sendConfig();
        }

        public void sendConfig() {
            int[] configData = new int[2 + allowedItems.size];
            configData[0] = targetArmX;
            configData[1] = targetArmY;
            for (int i = 0; i < allowedItems.size; i++) {
                configData[i + 2] = allowedItems.get(i).id;
            }
            configure(configData);
        }

        @Override
        public void updateTile() {
            float mx = Core.input.mouseWorldX();
            float my = Core.input.mouseWorldY();
            float hs = size * Vars.tilesize / 2f;
            mouseHover = mx >= x - hs && mx <= x + hs && my >= y - hs && my <= y + hs;

            if (selectingArm && Core.input.justTouched()) {
                Tile ct = Vars.world.tileWorld(mx, my);
                if (ct != null) {
                    Building clicked = ct.build;
                    if (clicked instanceof SafetyArm.SafetyArmBuild) {
                        if (Mathf.dst(tile.x, tile.y, ct.x, ct.y) <= rangeTiles) {
                            targetArmX = ct.x;
                            targetArmY = ct.y;
                            selectingArm = false;
                            sendConfig();
                            deselect();
                            Vars.ui.showInfoToast("¡Brazo robótico vinculado a la computadora!", 2.5f);
                        } else {
                            Vars.ui.showInfoToast("[scarlet]¡Brazo fuera de rango!", 2f);
                        }
                    } else if (clicked == this) {
                        selectingArm = false;
                        Vars.ui.showInfoToast("[scarlet]Vinculación cancelada", 1.5f);
                    }
                }
            }

            if (isLinked() && getLinkedArm() == null) {
                targetArmX = -1;
                targetArmY = -1;
                sendConfig();
            }
        }

        @Override
        public void tapped() {
            selectingArm = !selectingArm;
            if (selectingArm) {
                deselect();
                Vars.ui.showInfoToast("Haz clic en el Brazo Robótico en el mapa", 2.8f);
            }
        }

        @Override
        public void buildConfiguration(Table table) {
            table.clear();
            table.defaults().pad(3f);

            // 1. Estado de enlace
            SafetyArm.SafetyArmBuild arm = getLinkedArm();
            table.add(arm != null
                    ? "Brazo Vinculado: (" + targetArmX + ", " + targetArmY + ")"
                    : "[gray]Sin Brazo Vinculado").colspan(2).center().row();

            table.button(arm != null ? "Desvincular Brazo" : "Vincular a Brazo", () -> {
                if (arm != null) {
                    targetArmX = -1; targetArmY = -1;
                    sendConfig();
                    deselect();
                    Vars.ui.showInfoToast("[scarlet]Brazo desvinculado", 1.5f);
                } else {
                    selectingArm = true;
                    deselect();
                    Vars.ui.showInfoToast("Haz clic en el Brazo Robótico en el mapa", 2.5f);
                }
            }).colspan(2).size(220f, 36f).row();

            // 3. Título del Filtro
            table.add("[lightgray]Filtro Múltiple de Ítems:").colspan(2).padTop(8f).padBottom(4f).center().row();

            // 4. Herramientas rápidas
            Table tools = new Table();
            tools.defaults().size(80f, 28f).pad(2f);

            tools.button("Todos", Styles.flatTogglet, () -> {
                allowedItems.clear();
                for (Item it : Vars.content.items()) {
                    if (!it.hidden) allowedItems.add(it);
                }
                sendConfig();
            });

            tools.button("Limpiar", Styles.flatTogglet, () -> {
                allowedItems.clear();
                sendConfig();
            });

            table.add(tools).colspan(2).padBottom(6f).row();

            // 5. Cuadrícula oscura
            Table itemsTable = new Table();
            itemsTable.background(Styles.black5);
            itemsTable.margin(6f);
            itemsTable.defaults().size(34f).pad(2f);

            int cols = 0;
            for (Item item : Vars.content.items()) {
                if (item.hidden) continue;
                Item it = item;
                itemsTable.button(b -> b.image(it.uiIcon).size(22f), Styles.clearNoneTogglei, () -> {
                    toggleItem(it);
                }).checked(allowedItems.contains(item)).tooltip(item.localizedName);

                cols++;
                if (cols % 5 == 0) itemsTable.row();
            }

            table.add(itemsTable).colspan(2).padTop(2f).row();
        }

        @Override
        public void draw() {
            super.draw();

            if (topRegion != null && topRegion.found()) {
                Draw.rect(topRegion, x, y);
            }
            if (lightsRegion != null && lightsRegion.found()) {
                Draw.rect(lightsRegion, x, y);
            }

            // Dibuja la línea de enlace con el brazo enlazado
            SafetyArm.SafetyArmBuild arm = getLinkedArm();
            if (arm != null && (mouseHover || selectingArm)) {
                Draw.z(Layer.power + 1f);
                Lines.stroke(1.5f);
                Lines.line(x, y, arm.x, arm.y);
                Fill.circle(arm.x, arm.y, 3f);
            }
        }

        @Override
        public int[] config() {
            int[] configData = new int[2 + allowedItems.size];
            configData[0] = targetArmX;
            configData[1] = targetArmY;
            for (int i = 0; i < allowedItems.size; i++) {
                configData[i + 2] = allowedItems.get(i).id;
            }
            return configData;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(targetArmX);
            write.i(targetArmY);
            write.s(allowedItems.size);
            for (Item item : allowedItems) {
                write.s(item.id);
            }
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            targetArmX = read.i();
            targetArmY = read.i();
            int count = read.s();
            allowedItems.clear();
            for (int i = 0; i < count; i++) {
                int id = read.s();
                Item it = Vars.content.item(id);
                if (it != null) allowedItems.add(it);
            }
        }
    }
}