package centurion.utils.stats;

import arc.graphics.Color;
import mindustry.type.Item;
import mindustry.world.meta.StatUnit;

public class CenturyItems extends Item {

    public float magicCharge = 0f;

    public CenturyItems(String name, Color color) {
        super(name, color);
    }

    public CenturyItems(String name, Color color, float magicCharge) {
        super(name, color);
        this.magicCharge = magicCharge;
    }

    public CenturyItems(String name) {
        super(name);
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(CenturyStats.magicCharge, magicCharge, StatUnit.none);
    }
}
