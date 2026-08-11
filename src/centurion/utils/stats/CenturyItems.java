package centurion.utils.stats;

import arc.graphics.Color;
import mindustry.type.Item;
import mindustry.world.meta.StatUnit;

public class CenturyItems extends Item {

    public float magicCharge = 0f;
    //energia covalente (E.C.) obtenida al transmutar el item en una cámara de transmutación
    public float covalentEnergy = 100f;
    /** Coste de fabricación en E.C. para crear este item. -1 = auto: se deriva del valor y la rareza. */
    public float covalentEnergyCostOverride = -1f;
    /** Si es true, este item puede ser transmutado para obtener E.C. Se auto-calculan en setStats() salvo que overrideTransmutable sea true. */
    public boolean isTransmutable = true; //TODO arreglar para cambios manuales
    public boolean overrideTransmutable = false;

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

    /** Coste en E.C. necesario para fabricar este item por transmutación. */
    public float covalentEnergyCost() {
        if (covalentEnergyCostOverride >= 0f) return covalentEnergyCostOverride;
        return covalentEnergy * (Math.max(1f, hardness) * (Math.max(1f, cost)));
    }

    @Override
    public void setStats() {
        super.setStats();
        if (!overrideTransmutable) {
            isTransmutable = covalentEnergy > 0f && radioactivity < 0.10f && hardness < 3;
        }
        stats.add(CenturyStats.magicCharge, magicCharge, StatUnit.none);
        stats.add(CenturyStats.covalentEnergy, covalentEnergy, StatUnit.none);
        stats.add(CenturyStats.covalentEnergyCost, covalentEnergyCost(), StatUnit.none);
    }
}
