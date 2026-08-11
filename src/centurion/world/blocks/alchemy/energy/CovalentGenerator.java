package centurion.world.blocks.alchemy.energy;

import arc.Core;
import arc.util.Strings;
import arc.util.io.Reads;
import arc.util.io.Writes;
import centurion.utils.CPal;
import centurion.utils.stats.CenturyStats;
import mindustry.ui.Bar;
import mindustry.world.meta.StatUnit;

/**
 * Base para bloques que PRODUCEN energía covalente (E.C.).
 * La producción por tick es {@code covalentProduction * productionEfficiency}.
 */
public class CovalentGenerator extends CovalentBlock {

    /** Energía covalente producida por tick a eficiencia 1.0. */
    public float covalentProduction = 0f;

    public CovalentGenerator(String name) {
        super(name);
        outputsCovalent = true;
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(CenturyStats.covalentProduction, covalentProduction * 60f, StatUnit.none);
    }

    @Override
    public void setBars() {
        super.setBars();
        addBar("covalent-output", (GeneratorBuild entity) -> new Bar(
            () -> Core.bundle.format("bar.covalentoutput", Strings.fixed(entity.getCovalentProduction() * 60f * entity.timeScale(), 1)),
            () -> CPal.covalent,
            () -> entity.productionEfficiency
        ));
    }

    public class GeneratorBuild extends CovalentBuild {

        public float productionEfficiency = 0f;

        @Override
        public float getCovalentProduction() {
            return enabled ? covalentProduction * productionEfficiency : 0f;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.f(productionEfficiency);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            productionEfficiency = read.f();
        }
    }
}
