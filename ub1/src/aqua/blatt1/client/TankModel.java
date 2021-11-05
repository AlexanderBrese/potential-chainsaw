package aqua.blatt1.client;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;

public class TankModel extends Observable implements Iterable<FishModel> {

    public static final int WIDTH = 600;
    public static final int HEIGHT = 350;
    protected static final int MAX_FISHIES = 5;
    protected static final Random rand = new Random();
    protected volatile String id;
    protected final Set<FishModel> fishies;
    protected int fishCounter = 0;
    protected final ClientCommunicator.ClientForwarder forwarder;
    private InetSocketAddress leftNeighbor;
    private InetSocketAddress rightNeighbor;
    private Boolean token = false;
    private final Timer timer;

    public TankModel(ClientCommunicator.ClientForwarder forwarder) {
        this.forwarder = forwarder;
        fishies = Collections.newSetFromMap(new ConcurrentHashMap<>());
        timer = new Timer();
    }

    synchronized void onRegistration(String id) {
        this.id = id;
        newFish(WIDTH - FishModel.getXSize(), rand.nextInt(HEIGHT - FishModel.getYSize()));
    }

    public synchronized void newFish(int x, int y) {
        if (fishies.size() < MAX_FISHIES) {
            x = x > WIDTH - FishModel.getXSize() - 1 ? WIDTH - FishModel.getXSize() - 1 : x;
            y = y > HEIGHT - FishModel.getYSize() ? HEIGHT - FishModel.getYSize() : y;

            FishModel fish = new FishModel("fish" + (++fishCounter) + "@" + getId(), x, y,
                    rand.nextBoolean() ? Direction.LEFT : Direction.RIGHT);

            fishies.add(fish);
        }
    }

    synchronized void receiveFish(FishModel fish) {
        fish.setToStart();
        fishies.add(fish);
    }

    public void addNeighbor(InetSocketAddress neighbor, Direction direction) {
        if (direction.equals(Direction.LEFT)) {
            leftNeighbor = neighbor;
        } else {
            rightNeighbor = neighbor;
        }

    }

    public void removeNeighbor(Direction direction) {
        if (direction.equals(Direction.LEFT)) {
            leftNeighbor = null;
        } else {
            rightNeighbor = null;
        }
    }

    public String getId() {
        return id;
    }

    public synchronized int getFishCounter() {
        return fishCounter;
    }

    public synchronized Iterator<FishModel> iterator() {
        return fishies.iterator();
    }

    private synchronized void updateFishies() {
        for (Iterator<FishModel> it = iterator(); it.hasNext(); ) {
            FishModel fish = it.next();

            fish.update();

            if (fish.hitsEdge()) {
                if(hasToken()) {
                    if (fish.getDirection().equals(Direction.LEFT) && leftNeighbor != null) {
                        forwarder.handOff(fish, leftNeighbor);
                    } else if (fish.getDirection().equals(Direction.RIGHT) && rightNeighbor != null) {
                        forwarder.handOff(fish, rightNeighbor);
                    }
                } else {
                    fish.reverse();
                }
            }

            if (fish.disappears())
                it.remove();
        }
    }

    private synchronized void update() {
        updateFishies();
        setChanged();
        notifyObservers();
    }

    protected void run() {
        forwarder.register();

        try {
            while (!Thread.currentThread().isInterrupted()) {
                update();
                TimeUnit.MILLISECONDS.sleep(10);
            }
        } catch (InterruptedException consumed) {
            // allow method to terminate
        }
    }

    public synchronized void finish() {
        forwarder.deregister(id);
    }


    public void receiveToken() {
        token = true;

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                token = false;
                forwarder.sendToken(leftNeighbor);
            }
        };
        timer.schedule(task, 2000);
    }

    public Boolean hasToken() {
        return token;
    }
}