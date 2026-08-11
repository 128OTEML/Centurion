package centurion;

import arc.Core;
import arc.Events;
import arc.util.Log;
import arc.util.Time;
import centurion.content.CBlocks;
import centurion.content.CItems;
import centurion.utils.stats.CenturyStats;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.BaseDialog;

public class Centurion extends Mod {

    public Centurion(){
        Log.info("Loaded Centurion constructor.");

        Events.on(ClientLoadEvent.class, e -> {
            Time.runTask(10f, () -> {
                BaseDialog dialog = new BaseDialog("frog");
                dialog.cont.add("Pls Sentinel no te chinges el mod TQ ;3").row();
                dialog.cont.add("demasiado tarde, ya me chinge el mod").row();
                dialog.cont.image(Core.atlas.find("calajo")).pad(20f).row();
                dialog.cont.button("ya veo", dialog::hide).size(100f, 50f);
                dialog.show();
            });
        });
    }

    @Override
    public void loadContent(){
        Log.info("Loading Centurion content.");
        CenturyStats.load();
        CItems.load();
        CBlocks.load();
    }

}
