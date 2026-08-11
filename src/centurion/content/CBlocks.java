package centurion.content;

import centurion.world.blocks.distribution.computer;
import centurion.world.blocks.distribution.SafetyArm;
import centurion.world.blocks.distribution.computer;
import centurion.world.blocks.production.RuneAltar;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;

public class CBlocks {

    public static Block runeAltar, safetyArm, computerArm;

    public static void load() {

        /*runeAltar = new RuneAltar("rune-altar") {{
            requirements(Category.crafting, ItemStack.with(
                    CItems.cecilion, 10
            ));
            size = 2;
            craftTime = 180f;
        }};*/

        safetyArm = new SafetyArm("safety-arm") {{
            requirements(Category.distribution, ItemStack.with(
                    CItems.cecilion, 100
            ));
            description = "brazo de seguridad para agarrar items y llevarlos de forma segura a otros lugares";
            health = 500;
            armor = 1.5f;
            size = 2;
            buildTime = 100f;

        }};

        // Agrega la Computadora de Control
        computerArm = new computer("computer") {{
            requirements(Category.distribution, ItemStack.with(
                    CItems.cecilion, 8
            ));
            description = "Permite filtrar y controlar de mejor forma al brazo de seguridad";
            size = 1;
            armor = 1f;
            health = 300;
            rangeTiles = 6;
        }};
    }
}