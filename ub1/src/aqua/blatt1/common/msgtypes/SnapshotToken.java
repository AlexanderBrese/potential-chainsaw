package aqua.blatt1.common.msgtypes;

import java.io.Serializable;

@SuppressWarnings("serial")
public class SnapshotToken implements Serializable {
    private final int snapshot;

    public SnapshotToken(int snapshot) {
        this.snapshot = snapshot;
    }

    public int getSnapshot() {
        return snapshot;
    }
}
