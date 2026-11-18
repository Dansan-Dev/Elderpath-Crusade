package io.github.elderpath_crusade.run;

import java.util.Optional;

/** Where an in-progress run is kept between sessions (RUN-004). At most one run is saved. */
public interface RunSaveStore {
    /** The saved run, or empty if there is none or it can't be used (corrupt, or from an incompatible format). */
    Optional<RunSnapshot> load();

    void save(RunSnapshot snapshot);

    /** Forgets the saved run (win, loss, abandon). */
    void clear();
}
