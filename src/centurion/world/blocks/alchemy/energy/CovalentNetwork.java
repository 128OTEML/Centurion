package centurion.world.blocks.alchemy.energy;

import arc.struct.IntSeq;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.world.modules.BlockModule;

/**
 * Módulo por-edificio del sistema de energía covalente (E.C.).
 * Análogo a {@code PowerModule} del sistema eléctrico de Mindustry.
 * Cada edificio de un {@link centurion.world.blocks.alchemy.energy.CovalentBlock} posee uno de estos
 * módulos con su estado de red, enlaces de nodo y nivel de satisfacción/carga.
 */
public class CovalentNetwork extends BlockModule {

    /** Grafo (red) al que pertenece este edificio. */
    public CovalentGraph graph = new CovalentGraph();
    /** Si el edificio ya fue inicializado dentro del grafo. */
    public boolean init;
    /** Posiciones (packed) de los enlaces de nodo (usado por CovalentNode). */
    public IntSeq links = new IntSeq();
    /**
     * Para consumidores: satisfacción (0..1) de la energía demandada.
     * Para almacenadores (baterías/celdas): fracción de carga (0..1).
     */
    public float status = 0f;

    @Override
    public void write(Writes write) {
        write.s(links.size);
        for (int i = 0; i < links.size; i++) {
            write.i(links.get(i));
        }
        write.f(status);
    }

    @Override
    public void read(Reads read) {
        links.clear();
        short amount = read.s();
        for (int i = 0; i < amount; i++) {
            links.add(read.i());
        }
        status = read.f();
        if (Float.isNaN(status) || Float.isInfinite(status)) status = 0f;
    }
}
