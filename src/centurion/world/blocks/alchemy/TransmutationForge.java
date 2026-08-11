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
import centurion.world.blocks.alchemy.energy.CovalentConsumer;
import centurion.world.blocks.alchemy.energy.CovalentNetwork;
import mindustry.ctype.ContentType;
import mindustry.gen.Building;
import mindustry.type.Item;
import mindustry.ui.Bar;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.content;

/**
 * Forja de transmutación: convierte energía covalente (E.C.) en ítems.
 * El ítem a fabricar se selecciona al pulsar el bloque; su coste es el
 * {@code covalentEnergyCost()} del ítem. Consume E.C. mientras fabrica.
 */
public class TransmutationForge extends CovalentConsumer {

    public TextureRegion topRegion;
    /** Segundos para fabricar un ítem a plena satisfacción. */
    public float craftTime = 60f;
    public float powerCapacity = 2000f;
    public Color liquidColor = CPal.covalent;

    public TransmutationForge(String name) {
        super(name);
        hasItems = true;
        itemCapacity = 10;
        update = true;
        solid = true;
        configurable = true;
        saveConfig = true;
        config(Item.class, (TransmutationForgeBuild tile, Item item) -> tile.outputItem = item);
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(CenturyStats.covalentConsumption, covalentConsumption * 60f, StatUnit.none);
        stats.add(CenturyStats.covalentCapacity, powerCapacity, StatUnit.none);
        stats.add(CenturyStats.transmuteTime, craftTime, StatUnit.seconds);
    }

    @Override
    public void setBars() {
        super.setBars();
        addBar("covalent-status", (TransmutationForgeBuild entity) -> new Bar(
            () -> Core.bundle.format("bar.covalventstatus", (int)(entity.covalent.status * 100f)),
            () -> CPal.covalent,
            () -> entity.covalent.status
        ));
        addBar("covalent-progress", (TransmutationForgeBuild entity) -> new Bar(
            () -> Core.bundle.format("bar.covalentprogress", Strings.fixed(entity.getProgress() * 100f, 1)),
            () -> CPal.covalentLight,
            () -> entity.getProgress()
        ));
    }

    @Override
    public void load() {
        super.load();
        topRegion = Core.atlas.find(name + "-top");
    }

    /** Coste en E.C. del ítem que se está fabricando. */
    public float itemCost(Item item) {
        return item instanceof CenturyItems ci ? ci.covalentEnergyCost() : 0f;
    }

    public class TransmutationForgeBuild extends ConsumerBuild {

        /** ítem que se está fabricando en este edificio. */
        public Item outputItem = CItems.cecilion;
        /**
         * E.C. acumulada hacia cada ítem (en unidades de E.C.). Se guarda por ítem
         * para que cambiar el objetivo no pierda la E.C. ya invertida en un ítem incompleto.
         */
        public ObjectFloatMap<Item> charges = new ObjectFloatMap<>();

        /** E.C. acumulada hacia el ítem indicado. */
        public float getCharge(Item item) {
            if (item == null) return 0f;
            return charges.get(item, 0f);
        }

        /** Progreso (0..1) de fabricación del ítem actual. */
        public float getProgress() {
            if (outputItem == null) return 0f;
            float cost = itemCost(outputItem);
            return cost <= 0f ? 0f : Mathf.clamp(getCharge(outputItem) / cost);
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
        public void updateTile() {
            super.updateTile();

            //siempre intentar sacar el producto almacenado, incluso con el inventario lleno o si se cambió el
            //objetivo mientras la salida estaba tapada; sin esto el bloque quedaba atascado sin poder vaciarse.
            //solo se recorren los ítems presentes (no todo el catálogo) y se usa dump(item), que no escanea
            //todos los ítems por vecino
            if (items.total() > 0) {
                items.each((item, amount) -> dump(item));
            }

            if (outputItem != null && items.total() < itemCapacity && covalent.status >= 0.999f) {
                float cost = itemCost(outputItem);
                if (cost > 0f) {
                    float charge = getCharge(outputItem) + getCovalentConsumption() * delta();
                    if (charge >= cost) {
                        float excess = charge - cost;
                        charges.put(outputItem, 0f);
                        items.add(outputItem, 1);
                        //el exceso sobre el coste vuelve a la red; lo que no quepa se conserva como crédito
                        if (excess > 0.001f) {
                            float returned = returnExcessToGraph(excess);
                            if (excess - returned > 0.001f) charges.put(outputItem, excess - returned);
                        }
                    } else {
                        charges.put(outputItem, charge);
                    }
                }
            }
        }

        @Override
        public void buildConfiguration(Table table) {
            TransmuteItemSelection.buildTable(block, table, content.items(), () -> outputItem, this::configure, true);
        }

        @Override
        public float getCovalentConsumption() {
            return enabled && items.total() < itemCapacity ? itemCost(outputItem) / craftTime : 0f;
        }

        @Override
        public boolean shouldConsumeCovalent() {
            return enabled && items.total() < itemCapacity;
        }

        @Override
        public Item config() {
            return outputItem;
        }

        @Override
        public void draw() {
            super.draw();
            if (topRegion.found() && outputItem != null) {
                Draw.z(mindustry.graphics.Layer.blockOver);
                if (outputItem instanceof CenturyItems ci) {
                    Draw.color(ci.color);
                } else {
                    Draw.color(liquidColor);
                }
                Draw.alpha(0.3f + 0.4f * getProgress());
                Draw.rect(topRegion, x, y);
                Draw.color();
                Draw.alpha(1f);
            }
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(outputItem == null ? -1 : outputItem.id);
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
            outputItem = id == -1 ? null : (Item)content.getByID(ContentType.item, id);
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
