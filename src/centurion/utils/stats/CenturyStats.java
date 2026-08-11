package centurion.utils.stats;

import mindustry.world.meta.Stat;
import mindustry.world.meta.StatCat;

public class CenturyStats {

    public static Stat magicCharge;
    public static Stat requiredMagicCharge;
    public static Stat runeTiers;

    public static Stat covalentEnergy;
    public static Stat covalentEnergyCost;
    public static Stat covalentTransmutable;
    public static Stat covalentProduction;
    public static Stat covalentConsumption;
    public static Stat covalentCapacity;
    public static Stat covalentRange;
    public static Stat covalentConnections;
    public static Stat transmuteTime;

    public static void load() {
        magicCharge = new Stat("magic-charge", StatCat.items);
        requiredMagicCharge = new Stat("required-magic-charge", StatCat.crafting);
        runeTiers = new Stat("rune-tiers", StatCat.items);

        covalentEnergy = new Stat("covalent-energy", StatCat.items);
        covalentEnergyCost = new Stat("covalent-energy-cost", StatCat.items);
        covalentTransmutable = new Stat("covalent-transmutable", StatCat.items);
        covalentProduction = new Stat("covalent-production", StatCat.crafting);
        covalentConsumption = new Stat("covalent-consumption", StatCat.crafting);
        covalentCapacity = new Stat("covalent-capacity", StatCat.crafting);
        covalentRange = new Stat("covalent-range", StatCat.crafting);
        covalentConnections = new Stat("covalent-connections", StatCat.crafting);
        transmuteTime = new Stat("transmute-time", StatCat.crafting);
    }
}
