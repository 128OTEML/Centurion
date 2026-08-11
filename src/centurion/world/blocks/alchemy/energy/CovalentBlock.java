package centurion.world.blocks.alchemy.energy;

import arc.Core;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.io.Reads;
import arc.util.io.Writes;
import centurion.utils.CPal;
import centurion.utils.stats.CenturyStats;
import mindustry.core.UI;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.ui.Bar;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.world;
// si preguntas si es un copy paste del sistema de energia de los bloques normales
/**
 * Bloque base del sistema de energía covalente (E.C.).
 * Los bloques derivados pueden producir (outputsCovalent), consumir (consumesCovalent),
 * almacenar (covalentCapacity > 0) o simplemente transportar energía. Todos poseen un
 * {@link CovalentNetwork} y se conectan entre sí por proximidad o mediante {@link CovalentNode}
 * y derivados.
 */
public class CovalentBlock extends Block {

    public boolean outputsCovalent = false;
    public boolean consumesCovalent = false;
    /** Si false, los nodos no pueden enlazarse a este bloque. */
    public boolean connectedCovalent = true;
    /** Capacidad de almacenamiento de E.C. (0 = sin almacenamiento propio). */
    public float covalentCapacity = 0f;

    public CovalentBlock(String name) {
        super(name);
        update = true;
        solid = true;
        sync = true;
    }

    @Override
    public void setStats() {
        super.setStats();
        if (covalentCapacity > 0) {
            stats.add(CenturyStats.covalentCapacity, covalentCapacity, StatUnit.none);
        }
    }

    @Override
    public void setBars() {
        super.setBars();
        addBar("covalent", (CovalentBuild entity) -> new Bar(
            () -> Core.bundle.format("bar.covalentstored",
                UI.formatAmount((long)entity.covalent.graph.lastStored),
                UI.formatAmount((long)entity.covalent.graph.lastCapacity)),
            () -> CPal.covalent,
            () -> entity.covalent.graph.lastCapacity <= 0f ? 0f : Mathf.clamp(entity.covalent.graph.lastStored / entity.covalent.graph.lastCapacity)
        ));
    }

    @Override
    public void init() {
        super.init();
        if (outputsCovalent || consumesCovalent) {
            update = true;
        }
    }

    //accesores estáticos usados por CovalentGraph (ya que covalente no existe en Building)

    public static CovalentNetwork covalent(Building b) {
        return b instanceof CovalentBuild c ? c.covalent : null;
    }

    public static boolean covalentOutputs(Building b) {
        return b.block instanceof CovalentBlock c && c.outputsCovalent;
    }

    public static boolean covalentConsumes(Building b) {
        return b.block instanceof CovalentBlock c && c.consumesCovalent;
    }

    public static float covalentCapacity(Building b) {
        return b.block instanceof CovalentBlock c ? c.covalentCapacity : 0f;
    }

    public static float covalentProduction(Building b) {
        return b instanceof CovalentBuild c ? c.getCovalentProduction() : 0f;
    }

    public static float covalentConsumption(Building b) {
        return b instanceof CovalentBuild c ? c.getCovalentConsumption() : 0f;
    }

    public static boolean shouldConsumeCovalent(Building b) {
        return b instanceof CovalentBuild c && c.shouldConsumeCovalent();
    }

    public static Seq<Building> covalentConnections(Building b, Seq<Building> out) {
        return b instanceof CovalentBuild c ? c.getCovalentConnections(out) : out;
    }

    /** Edificio base del sistema covalente. */
    public class CovalentBuild extends Building {

        public CovalentNetwork covalent = new CovalentNetwork();

        static final Seq<Building> tempCovalentBuilds = new Seq<>();

        @Override
        public Building create(Block block, Team team) {
            super.create(block, team);
            covalent = new CovalentNetwork();
            covalent.graph.add(this);
            return this;
        }

        @Override
        public void updateTile() {
            covalent.graph.update();
        }

        @Override
        public void onProximityAdded() {
            if (covalent != null) {
                updateCovalentGraph();
            }
        }

        @Override
        public void onProximityRemoved() {
            if (covalent != null) {
                covalentGraphRemoved();
            }
        }

        @Override
        public void placed() {
            super.placed();
            updateCovalentGraph();
        }

        @Override
        public void afterPickedUp() {
            super.afterPickedUp();
            if (covalent != null) {
                covalent.graph = new CovalentGraph();
                covalent.links.clear();
            }
        }

        /** Fusiona los grafos de todas las conexiones de este edificio */
        public void updateCovalentGraph() {
            for (Building other : getCovalentConnections(tempCovalentBuilds)) {
                CovalentNetwork otherNetwork = covalent(other);
                if (otherNetwork != null) {
                    otherNetwork.graph.addGraph(covalent.graph);
                }
            }
        }

        /** Retira este edificio de su red y limpia sus enlaces. */
        public void covalentGraphRemoved() {
            if (covalent == null) return;
            covalent.graph.remove(this);
            for (int i = 0; i < covalent.links.size; i++) {
                Tile other = world.tile(covalent.links.get(i));
                if (other != null && other.build != null && covalent(other.build) != null) {
                    covalent(other.build).links.removeValue(pos());
                }
            }
            covalent.links.clear();
        }

        /** Devuelve todas las conexiones covalentes: vecinos por proximidad + enlaces de nodo. */
        public Seq<Building> getCovalentConnections(Seq<Building> out) {
            out.clear();
            if (covalent == null) return out;

            for (Building other : proximity) {
                if (other != null && covalent(other) != null && other.team == team
                    && !covalent.links.contains(other.pos())
                    && conductsCovalent(other) && (!(other instanceof CovalentBuild cb) || cb.conductsCovalent(this))) {
                    out.add(other);
                }
            }

            for (int i = 0; i < covalent.links.size; i++) {
                Tile link = world.tile(covalent.links.get(i));
                if (link != null && link.build != null && covalent(link.build) != null && link.build.team == team) {
                    out.add(link.build);
                }
            }
            return out;
        }

        /** Si este bloque conduce E.C. hacia otro (por proximidad). */
        public boolean conductsCovalent(Building other) {
            return true;
        }

        /** Energía covalente producida por este edificio (por tick). 0 si no es productor. */
        public float getCovalentProduction() {
            return 0f;
        }

        /** Energía covalente demandada por este edificio (por tick). 0 si no es consumidor. */
        public float getCovalentConsumption() {
            return 0f;
        }

        /** Si este edificio está demandando energía actualmente. */
        public boolean shouldConsumeCovalent() {
            return enabled;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            covalent.write(write);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            covalent.read(read, revision >= 1);
        }
    }
}
