package aqua.blatt1.common.msgtypes;

import aqua.blatt1.common.Direction;

import java.net.InetSocketAddress;
import java.io.Serializable;

@SuppressWarnings("serial")
public class NeighborRegisterUpdate implements Serializable {
    private final InetSocketAddress neighbor;
    private final Direction direction;

    public NeighborRegisterUpdate(InetSocketAddress neighbor, Direction direction) {
        this.neighbor = neighbor;
        this.direction = direction;
    }

    public InetSocketAddress getNeighbor() {
        return neighbor;
    }

    public Direction getDirection() {
        return direction;
    }
}
