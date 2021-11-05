package aqua.blatt1.common.msgtypes;

import aqua.blatt1.common.Direction;

import java.io.Serializable;

@SuppressWarnings("serial")
public class NeighborDeregisterUpdate implements Serializable {
    private final Direction direction;

    public NeighborDeregisterUpdate(Direction direction) {
        this.direction = direction;
    }

    public Direction getDirection() {
        return direction;
    }
}
