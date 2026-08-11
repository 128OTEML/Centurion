package centurion.world.blocks.alchemy.energy;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import centurion.utils.CPal;
import mindustry.ui.Bar;

/**
 * Batería de energía covalente: almacena E.C. (análogo a Battery).
 * La carga se guarda como fracción (0..1) en {@code covalent.status}.
 */
public class CovalentCell extends CovalentBlock {

    public TextureRegion topRegion;
    public Color emptyLightColor = CPal.covalentBack;
    public Color fullLightColor = CPal.covalentLight;
    public Color emptyLiquidColor = CPal.covalentBack;
    public Color fullLiquidColor = CPal.covalent;

    public CovalentCell(String name) {
        super(name);
        noUpdateDisabled = true;
    }

    @Override
    public void setBars() {
        super.setBars();
        addBar("covalent-storage", (CovalentCellBuild entity) -> new Bar(
            () -> Core.bundle.format("bar.covalentstored", (int)(entity.covalent.status * ((CovalentCell)entity.block).covalentCapacity), (int)((CovalentCell)entity.block).covalentCapacity),
            () -> CPal.covalent,
            () -> entity.covalent.status
        ));
    }

    @Override
    public void load() {
        super.load();
        topRegion = Core.atlas.find(name + "-top", Core.atlas.find("power-cell-top"));
    }

    public class CovalentCellBuild extends CovalentBuild {

        @Override
        public void draw() {
            Draw.rect(block.region, x, y);
            if (topRegion.found()) {
                Draw.color(emptyLiquidColor, fullLiquidColor, covalent.status);
                Draw.rect(topRegion, x, y);
                Draw.color();

                if (covalent.status > 0.001f) {
                    Draw.color(emptyLightColor, fullLightColor, covalent.status);
                    Draw.alpha(0.5f);
                    Draw.rect(topRegion, x, y);
                    Draw.color();
                    Draw.alpha(1f);
                }
            }
        }
    }
}
