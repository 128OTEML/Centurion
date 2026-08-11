package centurion.world.blocks.alchemy;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectFloatMap;
import arc.util.Strings;
import arc.util.io.Reads;
import arc.util.io.Writes;
import centurion.content.CItems;
import centurion.utils.CPal;
import centurion.utils.stats.CenturyItems;
import centurion.utils.stats.CenturyStats;
import centurion.world.blocks.alchemy.energy.CovalentBlock;
import centurion.world.blocks.alchemy.energy.CovalentGenerator;
import centurion.world.blocks.alchemy.energy.CovalentNetwork;
import mindustry.core.UI;
import mindustry.ctype.ContentType;
import mindustry.gen.Building;
import mindustry.graphics.Layer;
import mindustry.type.Item;
import mindustry.ui.Bar;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.content;

/**
 * Cámara de transmutación: convierte ítems en energía covalente (E.C.).
 * Los ítems entran y se "queman" para llenar un buffer interno de E.C. que
 * luego se suelta a la red como productor.
 */
public class TransmutationChamber extends CovalentGenerator {

    public TextureRegion topRegion;
    public Item transmuteItem = CItems.cecilion;
    /** Segundos necesarios para transmutar un ítem. */
    public float transmuteTime = 60f;
    /** Capacidad del buffer interno de E.C. */
    public float powerBuffer = 2000f;
    /** Color del glow del buffer. */
    public Color liquidColor = CPal.covalent;

    public TransmutationChamber(String name) {
        super(name);
        hasItems = true;
        itemCapacity = 10;
        update = true;
        solid = true;
        //si no es configurable, transmuta automáticamente el ítem de mayor valor E.C.
        configurable = false;
        saveConfig = true;

        config(Item.class, (TransmutationChamberBuild tile, Item item) -> tile.transmuteItem = item);
        configClear((TransmutationChamberBuild tile) -> tile.transmuteItem = this.transmuteItem);
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(CenturyStats.covalentProduction, covalentProduction * 60f, StatUnit.none);
        stats.add(CenturyStats.covalentCapacity, powerBuffer, StatUnit.none);
        stats.add(CenturyStats.transmuteTime, transmuteTime, StatUnit.seconds);
    }

    @Override
    public void setBars() {
        super.setBars();
        addBar("covalent-buffer", (TransmutationChamberBuild entity) -> new Bar(
            () -> Core.bundle.format("bar.covalentbuffer", UI.formatAmount((long)entity.energy), UI.formatAmount((long)((TransmutationChamber)entity.block).powerBuffer)),
            () -> CPal.covalent,
            () -> Mathf.clamp(entity.energy / ((TransmutationChamber)entity.block).powerBuffer)
        ));
        addBar("covalent-progress", (TransmutationChamberBuild entity) -> new Bar(
            () -> Core.bundle.format("bar.covalentprogress", Strings.fixed(entity.getCharge(entity.getTargetItem()) * 100f, 1)),
            () -> CPal.covalentLight,
            () -> entity.getCharge(entity.getTargetItem())
        ));
    }

    @Override
    public void load() {
        super.load();
        topRegion = Core.atlas.find(name + "-top");
    }

    /** Energía covalente entregada al transmutar el ítem configurado. */
    public float itemEnergy(Item item) {
        return item instanceof CenturyItems ci ? ci.covalentEnergy : 0f;
    }

    public class TransmutationChamberBuild extends GeneratorBuild {

        /** E.C. acumulada en el buffer interno, lista para volcarse a la red. */
        public float energy = 0f;
        /** ítem que este edificio transmuta (configurable por bloque). */
        public Item transmuteItem = TransmutationChamber.this.transmuteItem;
        /**
         * E.C. invertida en cada ítem incompleto (progreso 0..1 por ítem). Se guarda
         * por ítem para que cambiar el objetivo no pierda la E.C. ya usada.
         */
        public ObjectFloatMap<Item> charges = new ObjectFloatMap<>();

        /** Progreso (0..1) invertido en el ítem indicado. */
        public float getCharge(Item item) {
            if (item == null) return 0f;
            return charges.get(item, 0f);
        }

        /** Devuelve el exceso de E.C. a las celdas de la red; retorna lo que no pudo almacenarse. */
        public float returnExcessToGraph(float amount) {
            if (amount <= 0.001f) return 0f;
            float remaining = amount;
            for (Building b : covalent.graph.all) {
                if (remaining <= 0.001f) break;
                float cap = CovalentBlock.covalentCapacity(b);
                if (cap <= 0f || !b.enabled) continue;
                CovalentNetwork net = CovalentBlock.covalent(b);
                if (net == null) continue;
                float room = cap * (1f - net.status);
                if (room <= 0.001f) continue;
                float add = Math.min(room, remaining);
                net.status += add / cap;
                remaining -= add;
            }
            return amount - remaining;
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            if (items.total() >= itemCapacity) return false;
            if (block.configurable) return transmuteItem != null && item == transmuteItem;
            //modo automático: acepta cualquier ítem transmutable
            return item instanceof CenturyItems ci && ci.isTransmutable;
        }

        /** ítem a transmutar: el configurado si el bloque es configurable; si no,
         *  el de mayor ganancia E.C. presente en el buffer. */
        public Item getTargetItem() {
            if (block.configurable) return transmuteItem;
            Item[] best = {null};
            float[] bestValue = {-1f};
            //solo se recorren los ítems realmente almacenados, no todos los del juego
            items.each((item, amount) -> {
                if (item instanceof CenturyItems ci && ci.isTransmutable && ci.covalentEnergy > bestValue[0]) {
                    bestValue[0] = ci.covalentEnergy;
                    best[0] = item;
                }
            });
            return best[0];
        }

        @Override
        public void buildConfiguration(Table table) {
            TransmuteItemSelection.buildTable(block, table, content.items(), () -> transmuteItem, this::configure, false);
        }

        @Override
        public Item config() {
            return transmuteItem;
        }

        @Override
        public void updateTile() {
            super.updateTile();

            Item target = getTargetItem();

            //transmutar ítems en E.C. almacenada en el buffer
            if (target != null && items.has(target) && energy < powerBuffer) {
                float charge = getCharge(target) + timeScale() * delta() / transmuteTime;
                if (charge >= 1f) {
                    float gained = itemEnergy(target);
                    items.remove(target, 1);
                    energy += gained;
                    charge = 0f;
                    //el exceso sobre la capacidad vuelve a la red (no se pierde ni se atasca)
                    if (energy > powerBuffer) {
                        energy -= returnExcessToGraph(energy - powerBuffer);
                    }
                }
                charges.put(target, charge);
            }

            //volcar el buffer a la red como producción
            productionEfficiency = Mathf.lerpDelta(productionEfficiency, energy > 0.001f ? 1f : 0f, 0.05f);
            float produced = getCovalentProduction() * delta();
            energy = Math.max(0f, energy - Math.min(energy, produced));
        }

        @Override
        public void draw() {
            super.draw();
            if (topRegion.found() && energy > 0.001f) {
                Draw.z(Layer.blockOver);
                Draw.color(liquidColor, Mathf.clamp(energy / powerBuffer));
                Draw.alpha(0.6f);
                Draw.rect(topRegion, x, y);
                Draw.color();
                Draw.alpha(1f);
            }
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(transmuteItem == null ? -1 : transmuteItem.id);
            write.f(energy);
            write.s((short)charges.size);
            for (ObjectFloatMap.Entry<Item> entry : charges) {
                write.i(entry.key == null ? -1 : entry.key.id);
                write.f(entry.value);
            }
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            int id = read.i();
            transmuteItem = id == -1 ? null : (Item)content.getByID(ContentType.item, id);
            energy = read.f();
            charges.clear();
            short amount = read.s();
            for (int i = 0; i < amount; i++) {
                int iid = read.i();
                float charge = read.f();
                Item item = iid == -1 ? null : (Item)content.getByID(ContentType.item, iid);
                if (item != null) charges.put(item, charge);
            }
        }
    }
}
