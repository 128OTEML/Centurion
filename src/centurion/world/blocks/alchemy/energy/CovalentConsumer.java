package centurion.world.blocks.alchemy.energy;

import arc.Core;
import centurion.utils.CPal;
import centurion.utils.stats.CenturyStats;
import mindustry.ui.Bar;
import mindustry.world.meta.StatUnit;

/**
 * Base para bloques que CONSUMEN energía covalente (E.C.), análogo a PowerConsumer.
 * La demanda por tick es {@code covalentConsumption}; la demanda real se obtiene
 * sobreescribiendo {@code getCovalentConsumption()} en el build.
 */
public class CovalentConsumer extends CovalentBlock {

    public float covalentConsumption = 0f;

    public CovalentConsumer(String name) {
        super(name);
        consumesCovalent = true;
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(CenturyStats.covalentConsumption, covalentConsumption * 60f, StatUnit.none);
    }

    @Override
    public void setBars() {
        super.setBars();
        addBar("covalent-status", (ConsumerBuild entity) -> new Bar(
            () -> Core.bundle.format("bar.covalventstatus", (int)(entity.covalent.status * 100f)),
            () -> CPal.covalent,
            () -> entity.covalent.status
        ));
    }

    public class ConsumerBuild extends CovalentBuild {

        @Override
        public float getCovalentConsumption() {
            return enabled ? covalentConsumption : 0f;
        }

        @Override
        public boolean shouldConsumeCovalent() {
            return enabled;
        }
    }
}
