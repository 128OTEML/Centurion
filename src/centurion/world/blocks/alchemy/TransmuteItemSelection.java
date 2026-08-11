package centurion.world.blocks.alchemy;

import arc.Core;
import arc.func.Cons;
import arc.func.Prov;
import arc.math.Mathf;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.ImageButton;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import centurion.utils.CPal;
import centurion.utils.stats.CenturyItems;
import mindustry.core.UI;
import mindustry.gen.Tex;
import mindustry.type.Item;
import mindustry.ui.Styles;
import mindustry.world.Block;

import static mindustry.Vars.control;
import static mindustry.Vars.state;

/**
 * Tabla de selección de ítems transmutables (usada por la cámara y la forja de
 * transmutación). Al pasar el ratón por un ítem se muestra su coste o ganancia
 * de energía covalente (E.C.).
 */
public class TransmuteItemSelection {

    /**
     * Construye una tabla con los ítems transmutables del mod.
     *
     * @param showCost si true muestra el coste E.C. (forja); si false muestra la
     *                 ganancia E.C. (cámara).
     */
    public static void buildTable(Block block, Table table, Seq<Item> items, Prov<Item> holder, Cons<Item> consumer, boolean showCost) {
        ButtonGroup<ImageButton> group = new ButtonGroup<>();
        group.setMinCheckCount(0);

        Table cont = new Table().top();
        cont.defaults().size(40);

        int i = 0;
        for (Item item : items) {
            if (!(item instanceof CenturyItems ci) || !ci.isTransmutable) continue;
            if (!item.unlockedNow() || !item.isOnPlanet(state.getPlanet()) || item.isHidden()) continue;

            Cell<ImageButton> cell = cont.button(Tex.whiteui, Styles.clearNoneTogglei, Mathf.clamp(item.selectionSize, 0f, 40f), () -> {
                control.input.config.hideConfig();
            }).tooltip(t -> buildTooltip(t, item, showCost)).group(group);
            ImageButton button = cell.get();

            button.changed(() -> consumer.get(button.isChecked() ? item : null));
            button.getStyle().imageUp = new TextureRegionDrawable(item.uiIcon);
            button.update(() -> button.setChecked(holder.get() == item));

            if (i++ % 4 == 3) cont.row();
        }

        Table main = new Table().background(Styles.black6);
        ScrollPane pane = new ScrollPane(cont, Styles.smallPane);
        pane.setScrollingDisabled(true, false);
        pane.setOverscroll(false, false);
        main.add(pane).maxHeight(40 * 5f);

        table.top().add(main);
    }

    private static void buildTooltip(Table t, Item item, boolean showCost) {
        t.background(Styles.black6);
        t.add(item.localizedName).row();
        if (item instanceof CenturyItems ci) {
            String line = showCost
                ? Core.bundle.format("transmute.cost", UI.formatAmount((long)ci.covalentEnergyCost()))
                : Core.bundle.format("transmute.gained", UI.formatAmount((long)ci.covalentEnergy));
            t.add(line).color(CPal.covalent);
        }
    }
}
