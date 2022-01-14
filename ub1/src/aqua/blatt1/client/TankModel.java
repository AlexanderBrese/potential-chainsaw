package aqua.blatt1.client;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import aqua.blatt1.broker.AquaBroker;
import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;

public class TankModel extends Observable implements Iterable<FishModel>, AquaClient {

    public static final int WIDTH = 600;
    public static final int HEIGHT = 350;
    protected static final int MAX_FISHIES = 5;
    protected static final Random rand = new Random();
    protected volatile String id;
    protected final Set<FishModel> fishies;
    protected int fishCounter = 0;
    private AquaClient leftNeighbor;
    private AquaClient rightNeighbor;
    private final AquaBroker broker;
    private AquaClient clientStub;

    public TankModel(AquaBroker broker) {
        fishies = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.broker = broker;
        try {
            this.clientStub = (AquaClient) UnicastRemoteObject.exportObject(this, 0);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
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

    public void addNeighbor(AquaClient neighbor, Direction direction) {
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
                if (fish.getDirection().equals(Direction.LEFT) && leftNeighbor != null) {
                    try {
                        leftNeighbor.handoffRequest(fish);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                } else if (fish.getDirection().equals(Direction.RIGHT) && rightNeighbor != null) {
                    try {
                        rightNeighbor.handoffRequest(fish);
                    } catch (RemoteException e) {
                        e.printStackTrace();
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
        try {
            broker.registerRequest(clientStub);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

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
        try {
            broker.unregisterRequest(clientStub);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void registerResponse(String clientId) throws RemoteException {
        onRegistration(clientId);
    }

    @Override
    public void handoffRequest(FishModel fish) throws RemoteException {
        receiveFish(fish);
    }

    @Override
    public void neighborRegister(AquaClient neighbor, Direction direction) throws RemoteException {
        addNeighbor(neighbor, direction);
    }

    @Override
    public void neighborUnregister(Direction direction) throws RemoteException {
        removeNeighbor(direction);
    }
}