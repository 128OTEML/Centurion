package centurion.content;

import arc.graphics.Color;
import centurion.utils.stats.CenturyItems;
import centurion.utils.stats.RuneItem;

public class CItems {

    public static RuneItem emptyRune;

    public static CenturyItems cecilion, bauxite, aluminum,
    //Fluorite Family
    FluoriteA;

    public static void load() {

        emptyRune = new RuneItem("empty-rune", RuneItem.emptyColor) {{
            cost = 1.0f;
            hardness = 1;
            magicCharge = 0f;
        }};

        cecilion = new CenturyItems("cecilion", Color.valueOf("e1e9f1")) {{
            cost = 1.0f;
            hardness = 1;
            explosiveness = 0f;
            magicCharge = 10f;
        }};

        bauxite = new CenturyItems("bauxite", Color.valueOf("c6385a")) {{
            cost = 1.0f;
            hardness = 1;
            explosiveness = 0f;
            magicCharge = 1f;
        }};

        aluminum = new CenturyItems("aluminum", Color.valueOf("686586")) {{
            cost = 1.0f;
            hardness = 1;
            explosiveness = 0f;
            charge = 40f;
            magicCharge = 1f;
        }};

        FluoriteA = new CenturyItems("fluorite-a", Color.valueOf("f5a0f6")) {{
            cost = 1.0f;
            hardness = 1;
            explosiveness = 15f;
            charge = 0f;
            magicCharge = -20f;
        }};

    }
}
