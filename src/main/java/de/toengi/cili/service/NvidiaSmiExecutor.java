package de.toengi.cili.service;

import java.util.Optional;

/** Führt nvidia-smi aus und liefert die erste Ausgabezeile (CSV) oder empty bei Fehler. */
public interface NvidiaSmiExecutor {
    Optional<String> execute();
}
