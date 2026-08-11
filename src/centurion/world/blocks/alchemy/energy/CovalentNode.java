package centurion.world.blocks.alchemy.energy;

import arc.Core;
import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.struct.IntSeq;
import arc.struct.Seq;
import arc.util.Tmp;
import arc.util.Time;
import centurion.utils.CPal;
import centurion.utils.stats.CenturyStats;
import mindustry.core.Renderer;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.world.Tile;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.net;
import static mindustry.Vars.player;
import static mindustry.Vars.tilesize;
import static mindustry.Vars.world;

/**
 * Nodo de energía covalente: enlaza de forma inalámbrica (por rango) a los bloques
 * covalentes dentro de su alcance, creando/uniendo redes. Es el análogo de PowerNode.
 */
public class CovalentNode extends CovalentBlock {

    public TextureRegion laser, laserEnd;
    public float laserRange = 6f;
    public int maxNodes = 10;
    public boolean autolink = true, drawRange = true;
    public float laserScale = 0.25f;
    public Color laserColor1 = Color.white;
    public Color laserColor2 = CPal.covalent;

    public CovalentNode(String name) {
        super(name);
        configurable = true;
        saveConfig = true;
        destructible = true;
        schematicPriority = -10;
        drawDisabled = false;

        config(Integer.class, (CovalentNodeBuild entity, Integer value) -> {
            CovalentNetwork network = entity.covalent;
            Building other = world.build(value);
            boolean contains = network.links.contains(value), valid = other != null && covalent(other) != null;

            if (contains) {
                //desenlazar
                network.links.removeValue(value);
                if (valid) covalent(other).links.removeValue(entity.pos());

                CovalentGraph newgraph = new CovalentGraph();
                newgraph.reflow(entity);
                newgraph.update();

                if (valid && covalent(other).graph != newgraph) {
                    CovalentGraph og = new CovalentGraph();
                    og.reflow(other);
                    og.update();
                }
            } else if (linkValid(entity, other) && valid && network.links.size < maxNodes) {
                network.links.addUnique(other.pos());

                if (other.team == entity.team) {
                    covalent(other).links.addUnique(entity.pos());
                }

                network.graph.addGraph(covalent(other).graph);
            }
        });

        config(Point2[].class, (CovalentNodeBuild tile, Point2[] value) -> {
            IntSeq old = new IntSeq(tile.covalent.links);

            //limpiar los enlaces viejos
            for (int i = 0; i < old.size; i++) {
                configurations.get(Integer.class).get(tile, old.get(i));
            }

            //enlazar los nuevos
            for (Point2 p : value) {
                configurations.get(Integer.class).get(tile, Point2.pack(p.x + tile.tileX(), p.y + tile.tileY()));
            }
        });
    }

    @Override
    public void load() {
        super.load();
        laser = Core.atlas.find(name + "-laser", Core.atlas.find("laser"));
        laserEnd = Core.atlas.find(name + "-laser-end", Core.atlas.find("laser-end"));
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(CenturyStats.covalentRange, laserRange, StatUnit.blocks);
        stats.add(CenturyStats.covalentConnections, maxNodes, StatUnit.none);
    }

    @Override
    public void init() {
        super.init();
        clipSize = Math.max(clipSize, laserRange * tilesize);
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid) {
        Tile tile = world.tile(x, y);
        if (tile == null || !autolink) return;

        Lines.stroke(1f);
        Draw.color(Pal.placing);
        Drawf.circles(x * tilesize + offset, y * tilesize + offset, laserRange * tilesize);

        getPotentialLinks(tile, player.team(), other -> {
            Draw.color(laserColor1, Renderer.laserOpacity * 0.5f);
            drawLaser(x * tilesize + offset, y * tilesize + offset, other.x, other.y, size, other.block.size);
            Drawf.square(other.x, other.y, other.block.size * tilesize / 2f + 2f, Pal.place);
        });

        Draw.reset();
    }

    protected void setupColor(float satisfaction) {
        Draw.color(Tmp.c1.set(laserColor1).lerp(laserColor2, (1f - satisfaction) * 0.86f + Mathf.absin(3f, 0.1f)).a(Renderer.laserOpacity));
    }

    public void drawLaser(float x1, float y1, float x2, float y2, int size1, int size2) {
        float angle1 = Angles.angle(x1, y1, x2, y2),
            vx = Mathf.cosDeg(angle1), vy = Mathf.sinDeg(angle1),
            len1 = size1 * tilesize / 2f - 1.5f, len2 = size2 * tilesize / 2f - 1.5f;

        Drawf.laser(laser, laserEnd, laserEnd, x1 + vx * len1, y1 + vy * len1, x2 - vx * len2, y2 - vy * len2, laserScale, true, false);
    }

    /** Distancia simple entre centros para decidir si dos bloques pueden enlazarse. */
    public boolean linkValid(Building tile, Building link) {
        return linkValid(tile, link, true);
    }

    public boolean linkValid(Building tile, Building link, boolean checkMaxNodes) {
        if (tile == link || link == null || !(link.block instanceof CovalentBlock) || !((CovalentBlock)link.block).connectedCovalent
            || (tile != null && tile.team != link.team)) return false;

        float range = laserRange * tilesize + link.block.size * tilesize / 2f;
        if (Mathf.dst(tile.x, tile.y, link.x, link.y) <= range) {
            if (checkMaxNodes && link.block instanceof CovalentNode node) {
                return tile != null && (covalent(link).links.size < node.maxNodes || covalent(link).links.contains(tile.pos()));
            }
            return true;
        }
        return false;
    }

    /** Escanea un cuadrado alrededor del nodo buscando bloques covalentes enlazables. */
    protected void getPotentialLinks(Tile tile, Team team, Cons<Building> others) {
        if (!autolink) return;

        int range = (int)(laserRange + 2);
        float rangeW = laserRange * tilesize;
        Building src = tile.build;

        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                Tile otherTile = world.tile(tile.x + dx, tile.y + dy);
                if (otherTile == null) continue;
                Building other = otherTile.build;
                if (other == null || other == src || other.team != team || covalent(other) == null) continue;

                //comprobar alcance desde la posición real del nodo, o desde la casilla si aún no está colocado (preview)
                if (src != null) {
                    if (!linkValid(src, other, false)) continue;
                } else {
                    if (Mathf.dst(tile.drawx(), tile.drawy(), other.x, other.y) > rangeW + other.block.size * tilesize / 2f) continue;
                }

                others.get(other);
            }
        }
    }

    public class CovalentNodeBuild extends CovalentBuild {

        @Override
        public void placed() {
            if (net.client() || covalent.links.size > 0) return;

            getPotentialLinks(tile, team, other -> {
                if (!covalent.links.contains(other.pos())) {
                    configureAny(other.pos());
                }
            });

            super.placed();
        }

        @Override
        public void dropped() {
            covalent.links.clear();
            updateCovalentGraph();
        }

        @Override
        public boolean onConfigureBuildTapped(Building other) {
            if (linkValid(this, other)) {
                configure(other.pos());
                return false;
            }

            if (this == other) {
                if (covalent.links.size == 0) {
                    Seq<Point2> points = new Seq<>();
                    getPotentialLinks(tile, team, link -> {
                        if (points.size < maxNodes) {
                            points.add(new Point2(link.tileX() - tile.x, link.tileY() - tile.y));
                        }
                    });
                    configure(points.toArray(Point2.class));
                } else {
                    configure(new Point2[0]);
                }
                deselect();
                return false;
            }

            return true;
        }

        @Override
        public void drawSelect() {
            super.drawSelect();

            if (!drawRange) return;

            Lines.stroke(1f);
            Draw.color(Pal.accent);
            Drawf.circles(x, y, laserRange * tilesize);
            Draw.reset();
        }

        @Override
        public void drawConfigure() {
            Drawf.circles(x, y, tile.block().size * tilesize / 2f + 1f + Mathf.absin(Time.time, 4f, 1f));

            if (drawRange) {
                Drawf.circles(x, y, laserRange * tilesize);

                for (int x = (int)(tile.x - laserRange - 2); x <= tile.x + laserRange + 2; x++) {
                    for (int y = (int)(tile.y - laserRange - 2); y <= tile.y + laserRange + 2; y++) {
                        Building link = world.build(x, y);

                        if (link != this && linkValid(this, link, false) && covalent.links.contains(link.pos())) {
                            Drawf.square(link.x, link.y, link.block.size * tilesize / 2f + 1f, Pal.place);
                        }
                    }
                }

                Draw.reset();
            } else {
                covalent.links.each(i -> {
                    Building link = world.build(i);
                    if (link != null && linkValid(this, link, false)) {
                        Drawf.square(link.x, link.y, link.block.size * tilesize / 2f + 1f, Pal.place);
                    }
                });
            }
        }

        @Override
        public void draw() {
            super.draw();

            if (Mathf.zero(Renderer.laserOpacity) || team == Team.derelict) return;

            Draw.z(Layer.power);
            setupColor(covalent.graph.getSatisfaction());

            for (int i = 0; i < covalent.links.size; i++) {
                Building link = world.build(covalent.links.get(i));

                if (!linkValid(this, link)) continue;

                if (link.block instanceof CovalentNode && link.id >= id) continue;

                drawLaser(x, y, link.x, link.y, size, link.block.size);
            }

            Draw.reset();
        }

        @Override
        public Point2[] config() {
            Point2[] out = new Point2[covalent.links.size];
            for (int i = 0; i < out.length; i++) {
                out[i] = Point2.unpack(covalent.links.get(i)).sub(tile.x, tile.y);
            }
            return out;
        }
    }
}
