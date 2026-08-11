package centurion.world.blocks.alchemy.energy;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.math.geom.Point2;
import arc.struct.Seq;
import arc.util.io.Reads;
import arc.util.io.Writes;
import centurion.utils.CPal;
import centurion.utils.stats.CenturyStats;
import mindustry.core.Renderer;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.input.Placement;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.meta.BlockStatus;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.player;
import static mindustry.Vars.tilesize;
import static mindustry.Vars.world;

/**
 * Nodo direccional de energía covalente (E.C.), análogo al BeamNode de Erekir.
 * Escanea SOLO la dirección en la que está rotado (al colocarlo con R) y se
 * enlaza con el PRIMER bloque covalente (productor, consumidor, celda, otro nodo
 * direccional, etc.) que encuentre dentro de su alcance, dibujando un rayo en esa
 * dirección. Los bloques que no interactúan con la red (conductos, paredes, etc.)
 * detienen el rayo.
 */
public class CovalentBeamNode extends CovalentBlock {

    /** Alcance en casillas hacia la dirección de colocación. */
    public int range = 5;

    public TextureRegion laser, laserEnd;

    public Color laserColor1 = Color.white;
    public Color laserColor2 = CPal.covalent;
    public float pulseScl = 7, pulseMag = 0.05f;
    public float laserWidth = 0.4f;

    public CovalentBeamNode(String name) {
        super(name);
        rotate = true;
        drawDisabled = false;
        allowDiagonal = false;
        underBullets = true;
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(CenturyStats.covalentRange, range, StatUnit.blocks);
    }

    @Override
    public void init() {
        super.init();
        updateClipRadius((range + 1) * tilesize);
    }

    @Override
    public void load() {
        super.load();
        laser = Core.atlas.find(name + "-beam", Core.atlas.find("power-beam"));
        laserEnd = Core.atlas.find(name + "-beam-end", Core.atlas.find("power-beam-end"));
    }

    /** Si este bloque detiene el rayo (no interactúa con la red). */
    public boolean blocksBeam(Building other) {
        return other != null && other.isInsulated();
    }

    /** Si este bloque puede ser enlazado por el rayo. */
    public boolean linkable(Building other) {
        //los nodos omni usan su propio sistema de enlaces; no forzarlos
        return other != null && other.block instanceof CovalentBlock cb
            && cb.connectedCovalent && !(other.block instanceof CovalentNode);
    }

    /**
     * Busca el primer bloque enlazable en la dirección indicada y devuelve la casilla del borde del
     * objetivo que el rayo golpea {@code dests[i]}; null si no
     * encuentra nada o algo bloquea el rayo.
     */
    public Tile findTarget(int x, int y, int direction, Team team) {
        var dir = Geometry.d4[direction];
        int offset = size / 2;
        for (int j = 1 + offset; j <= range + offset; j++) {
            Building other = world.build(x + j * dir.x, y + j * dir.y);

            if (blocksBeam(other)) break;

            if (linkable(other) && other.team == team) {
                return world.tile(x + j * dir.x, y + j * dir.y);
            }
        }
        return null;
    }

    @Override
    public void changePlacementPath(Seq<Point2> points, int rotation, boolean diagonal) {
        if (!diagonal) {
            Placement.calculateNodes(points, this, rotation, (point, other) -> Math.max(Math.abs(point.x - other.x), Math.abs(point.y - other.y)) <= range + size - 1);
        }
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid) {
        int maxLen = range + size / 2;
        Building dest = null;
        var dir = Geometry.d4[rotation];
        int dx = dir.x, dy = dir.y;
        int offset = size / 2;
        for (int j = 1 + offset; j <= range + offset; j++) {
            Building other = world.build(x + j * dx, y + j * dy);

            if (blocksBeam(other)) break;

            if (linkable(other) && other.team == player.team()) {
                maxLen = j;
                dest = other;
                break;
            }
        }

        Drawf.dashLine(Pal.placing,
            x * tilesize + dx * (tilesize * size / 2f + 2),
            y * tilesize + dy * (tilesize * size / 2f + 2),
            x * tilesize + dx * (maxLen) * tilesize,
            y * tilesize + dy * (maxLen) * tilesize);

        if (dest != null) {
            Drawf.square(dest.x, dest.y, dest.block.size * tilesize / 2f + 2.5f, 0f);
        }
    }

    public class CovalentBeamNodeBuild extends CovalentBuild {

        /** Bloque enlazado en la dirección de colocación. */
        public Building link;
        /** Casilla destino del rayo. */
        public Tile dest;
        /** Para detectar cambios en el mundo y re-escanear. */
        public int lastChange = -2;
        /** Para re-escanear si el bloque se rota tras ser colocado. */
        public int lastRotation = -1;

        @Override
        public void updateTile() {
            super.updateTile();
            if (lastChange != world.tileChanges || lastRotation != rotation) {
                lastChange = world.tileChanges;
                lastRotation = rotation;
                updateDirection();
            }
        }

        @Override
        public BlockStatus status() {
            float balance = covalent.graph.lastProduced - covalent.graph.lastNeeded;
            if (balance > 0f) return BlockStatus.active;
            if (balance < 0f && covalent.graph.lastStored > 0) return BlockStatus.noOutput;
            return BlockStatus.noInput;
        }

        @Override
        public void draw() {
            super.draw();

            if (Mathf.zero(Renderer.laserOpacity) || team == Team.derelict) return;

            Draw.z(Layer.power);
            Draw.color(laserColor1, laserColor2, (1f - covalent.graph.getSatisfaction()) * 0.86f + Mathf.absin(3f, 0.1f));
            Draw.alpha(Renderer.laserOpacity);
            float w = laserWidth + Mathf.absin(pulseScl, pulseMag);

            if (dest != null && link != null && link.wasVisible && shouldDrawBeam()) {

                int dst = Math.max(Math.abs(dest.x - tile.x), Math.abs(dest.y - tile.y));
                //no dibujar rayos para bloques adyacentes
                if (dst > 1 + size / 2) {
                    var point = Geometry.d4[rotation];
                    float poff = tilesize / 2f;
                    Drawf.laser(laser, laserEnd, x + poff * size * point.x, y + poff * size * point.y,
                        dest.worldx() - poff * point.x, dest.worldy() - poff * point.y, w);
                }
            }

            Draw.reset();
        }

        /*
         * Decide si este nodo dibuja su rayo. El BeamNode de Erekir solo suprime el dibujo cuando dos nodos se
         * apuntan MUTUAMENTE (el de mayor id o mayor alcance dibuja); en un rayo unidireccional este nodo es el
         * único que lo ve y siempre debe dibujarlo, aunque apunte a un nodo colocado antes que él.
         */
        public boolean shouldDrawBeam() {
            if (!(link.block instanceof CovalentBeamNode node)) return true;

            //rayo mutuo: ambos se apuntan; dibuja solo uno
            if (link instanceof CovalentBeamNodeBuild other && other.link == this) {
                return (link.id > id && range >= node.range) || range > node.range;
            }

            //rayo unidireccional hacia otro nodo: siempre se dibuja
            return true;
        }

        @Override
        public void pickedUp() {
            link = null;
            dest = null;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(lastChange);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            lastChange = read.i();
            //la rotación restaurada se compara con lastRotation para forzar un re-escaneo
            lastRotation = -1;
        }

        /** Re-escanea la dirección de colocación, enlazando/desenlazando según el estado actual del mundo. */
        public void updateDirection() {
            Building prev = link;
            int prevPos = prev != null ? prev.pos() : -1;

            dest = findTarget(tile.x, tile.y, rotation, team);
            link = dest != null ? dest.build : null;

            Building next = link;

            if (next != prev) {
                //el enlace viejo se retira SIEMPRE de nuestra lista, aunque el bloque ya no exista
                if (prevPos != -1) {
                    covalent.links.removeValue(prevPos);
                }

                if (prev != null) {
                    if (prev.isAdded()) {
                        //desenlazar la conexión vieja y separar los grafos
                        covalent(prev).links.removeValue(pos());

                        CovalentGraph newgraph = new CovalentGraph();
                        newgraph.reflow(this);

                        if (covalent(prev).graph != newgraph) {
                            CovalentGraph og = new CovalentGraph();
                            og.reflow(prev);
                        }
                    } else {
                        //el objetivo fue destruido: refluir este lado para que el grafo lo suelte
                        //previene null pointer exception
                        CovalentGraph newgraph = new CovalentGraph();
                        newgraph.reflow(this);
                    }
                }

                //enlazar a un bloque nuevo y fusionar grafos
                if (next != null) {
                    covalent.links.addUnique(next.pos());
                    covalent(next).links.addUnique(pos());

                    covalent.graph.addGraph(covalent(next).graph);
                }
            }
        }
    }
}
