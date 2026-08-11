package centurion.utils.stats;

import mindustry.world.meta.Stat;
import mindustry.world.meta.StatCat;

public class CenturyStats {

    public static Stat magicCharge;
    public static Stat requiredMagicCharge;
    public static Stat runeTiers;

    public static void load() {
        magicCharge = new Stat("magic-charge", StatCat.items);
        requiredMagicCharge = new Stat("required-magic-charge", StatCat.crafting);
        runeTiers = new Stat("rune-tiers", StatCat.items);
    }
}
