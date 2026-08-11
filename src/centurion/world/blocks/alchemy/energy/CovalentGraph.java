package centurion.world.blocks.alchemy.energy;

import arc.math.Mathf;
import arc.struct.IntSet;
import arc.struct.Queue;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.gen.Building;
import mindustry.world.blocks.power.PowerGraph;

import static centurion.world.blocks.alchemy.energy.CovalentBlock.covalent;
import static centurion.world.blocks.alchemy.energy.CovalentBlock.covalentCapacity;
import static centurion.world.blocks.alchemy.energy.CovalentBlock.covalentConnections;
import static centurion.world.blocks.alchemy.energy.CovalentBlock.covalentConsumes;
import static centurion.world.blocks.alchemy.energy.CovalentBlock.covalentConsumption;
import static centurion.world.blocks.alchemy.energy.CovalentBlock.covalentOutputs;
import static centurion.world.blocks.alchemy.energy.CovalentBlock.covalentProduction;
import static centurion.world.blocks.alchemy.energy.CovalentBlock.shouldConsumeCovalent;

/**
 * Red de energía covalente (E.C.) que conecta a todos los edificios covalentes
 * enlazados (por proximidad o por nodos). Es el análogo directo de {@link PowerGraph},
 * pero usando un "almacén" compartido: la energía producida se guarda en las celdas
 * (bloques con {@code covalentCapacity > 0}) y los consumidores la drenan proporcionalmente.
 */
public class CovalentGraph {

    private static final Queue<Building> queue = new Queue<>();
    private static final Seq<Building> outArray1 = new Seq<>();
    private static final Seq<Building> outArray2 = new Seq<>();
    private static final IntSet closedSet = new IntSet();

    /** Todos los edificios de la red. */
    public final Seq<Building> all = new Seq<>(false, 16, Building.class);

    public float lastProduced, lastNeeded, lastStored, lastCapacity;
    /** Cobertura (0..1) de la energía demandada; usada para la UI. */
    public float satisfaction;

    private float lastUpdateTime = -1f;

    public void update() {
        //una sola vez por frame: todos los edificios de la red llaman a este método en updateTile()
        if (lastUpdateTime == Time.time) return;
        lastUpdateTime = Time.time;

        float produced = 0f, needed = 0f;

        for (Building b : all) {
            if (covalentOutputs(b)) {
                produced += covalentProduction(b) * b.delta();
            }
            if (covalentConsumes(b) && shouldConsumeCovalent(b)) {
                needed += covalentConsumption(b) * b.delta();
            }
        }

        //se calcula una sola vez por frame y se reutiliza (antes se recalculaba 2 veces más)
        float stored = getStored();
        float capacity = getCapacity();

        lastProduced = produced;
        lastNeeded = needed;
        lastStored = stored;
        lastCapacity = capacity;

        boolean charged = false;

        if (!Mathf.equal(needed, produced)) {
            if (needed > produced) {
                float fromStorage = useStorages(needed - produced, stored);
                produced += fromStorage;
                lastProduced += fromStorage;
            } else if (produced > needed) {
                charged = true;
                produced -= chargeStorages(produced - needed, capacity);
            }
        }

        distribute(needed, produced, charged);
    }

    /** Reparte la cobertura de energía entre los consumidores, de forma análoga a PowerGraph.distributePower(). */
    public void distribute(float needed, float produced, boolean charged) {
        float coverage = Mathf.zero(needed) && Mathf.zero(produced) && !charged && Mathf.zero(lastStored) ? 0f :
            Mathf.zero(needed) ? 1f : Math.min(1f, produced / needed);

        for (Building b : all) {
            if (covalentConsumes(b)) {
                if (shouldConsumeCovalent(b)) {
                    covalent(b).status = coverage;
                } else {
                    //consumidores inactivos obtienen una estimación por si se activaran
                    covalent(b).status = Math.min(1f, produced / (needed + Math.max(0.0001f, covalentConsumption(b) * b.delta())));
                    if (Float.isNaN(covalent(b).status)) covalent(b).status = 0f;
                }
            }
        }

        satisfaction = coverage;
    }

    public float getSatisfaction() {
        if (Mathf.zero(lastProduced)) {
            return 0f;
        } else if (Mathf.zero(lastNeeded)) {
            return 1f;
        }
        return Mathf.clamp(lastProduced / lastNeeded);
    }

    /** Drena energía de las celdas de almacenamiento, en proporción a su carga. */
    public float useStorages(float needed, float stored) {
        if (Mathf.equal(stored, 0f)) return 0f;

        float used = Math.min(stored, needed);
        float consumedPercentage = Math.min(1f, needed / stored);
        for (Building b : all) {
            if (covalentCapacity(b) > 0 && b.enabled) {
                covalent(b).status *= (1f - consumedPercentage);
            }
        }
        return used;
    }

    /** Carga las celdas de almacenamiento con el exceso de energía producido. */
    public float chargeStorages(float excess, float capacity) {
        if (Mathf.equal(capacity, 0f)) return 0f;

        float chargedPercent = Math.min(excess / capacity, 1f);
        for (Building b : all) {
            if (covalentCapacity(b) > 0 && b.enabled) {
                covalent(b).status += (1f - covalent(b).status) * chargedPercent;
            }
        }
        return Math.min(excess, capacity);
    }

    public float getStored() {
        float total = 0f;
        for (Building b : all) {
            if (covalentCapacity(b) > 0 && b.enabled) {
                total += covalent(b).status * covalentCapacity(b);
            }
        }
        return total;
    }

    public float getCapacity() {
        float total = 0f;
        for (Building b : all) {
            if (covalentCapacity(b) > 0 && b.enabled) {
                total += covalentCapacity(b);
            }
        }
        return total;
    }

    public void addGraph(CovalentGraph graph) {
        if (graph == this) return;

        //se fusiona dentro del grafo más grande
        if (graph.all.size > all.size) {
            graph.addGraph(this);
            return;
        }

        for (Building tile : graph.all) {
            add(tile);
        }

        graph.all.clear();
    }

    public void add(Building build) {
        if (build == null || covalent(build) == null) return;

        if (covalent(build).graph != this || !covalent(build).init) {
            covalent(build).graph = this;
            covalent(build).init = true;
            all.add(build);
        }
    }

    public void clear() {
        all.clear();
    }

    /** Reconstruye la red desde un edificio usando BFS (conexiones de proximidad + enlaces de nodo). */
    public void reflow(Building tile) {
        queue.clear();
        queue.addLast(tile);
        closedSet.clear();
        while (queue.size > 0) {
            Building child = queue.removeFirst();
            add(child);
            for (Building next : covalentConnections(child, outArray2)) {
                if (closedSet.add(next.pos())) {
                    queue.addLast(next);
                }
            }
        }
    }

    /**
     * Retira un edificio de la red. Los edificios aún conectados se separan en nuevos grafos
     * mediante BFS, de forma análoga a PowerGraph.remove().
     */
    public void remove(Building tile) {
        for (Building other : covalentConnections(tile, outArray1)) {
            if (covalent(other).graph != this) continue;

            CovalentGraph graph = new CovalentGraph();
            graph.add(other);
            queue.clear();
            queue.addLast(other);
            while (queue.size > 0) {
                Building child = queue.removeFirst();
                graph.add(child);
                for (Building next : covalentConnections(child, outArray2)) {
                    if (next != tile && covalent(next).graph != graph) {
                        graph.add(next);
                        queue.addLast(next);
                    }
                }
            }
            graph.update();
        }

        clear();
    }

    @Override
    public String toString() {
        return "CovalentGraph{" +
            "all=" + all +
            '}';
    }
}
