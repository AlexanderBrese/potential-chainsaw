package aqua.blatt1.client;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.RecordState;
import aqua.blatt1.common.msgtypes.SnapshotMarker;
import aqua.blatt1.common.msgtypes.SnapshotToken;
import messaging.Message;

public class TankModel extends Observable implements Iterable<FishModel> {
    private class Snapshot {
        public int state;
        public Queue<Message> leftInputChannel = new LinkedList<>();
        public Queue<Message> rightInputChannel = new LinkedList<>();

        public Snapshot() {
        }
    }


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
    private RecordState recordState = RecordState.IDLE;
    private LinkedList<Snapshot> snapshots = new LinkedList<>();
    private Snapshot currentSnapshot = new Snapshot();
    private SnapshotToken snapshotToken;
    private boolean initiator = false;

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

        if (recordState != RecordState.IDLE) {
            currentSnapshot.state = fishies.size();
        }
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
                if (hasToken()) {
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

    public void initiateSnapshot() {
        initiator = true;
        this.snapshotToken = null;
        currentSnapshot.state = fishies.size();
        recordState = RecordState.BOTH;
        SnapshotToken snapshotToken = new SnapshotToken(currentSnapshot.state);

        sendSnapshotMarker();
        forwarder.sendSnapshotToken(leftNeighbor, snapshotToken);

    }

    private void sendSnapshotMarker() {
        forwarder.sendSnapshotMarker(leftNeighbor);
        forwarder.sendSnapshotMarker(rightNeighbor);
    }

    public void receiveSnapshotMarker(InetSocketAddress sender) {
        System.out.println("RECORD STATE = " + recordState);
        if (recordState == RecordState.IDLE) {
            currentSnapshot.state = fishies.size();
            snapshots.add(currentSnapshot);
            if (sender.equals(leftNeighbor)) {
                recordState = RecordState.RIGHT;
                sendSnapshotMarker();
                currentSnapshot.leftInputChannel = new LinkedList<>();
            } else if (sender.equals(rightNeighbor)) {
                recordState = RecordState.LEFT;
                sendSnapshotMarker();
                currentSnapshot.rightInputChannel = new LinkedList<>();
            } else {
                System.err.println("error: unknown sender sent snapshot marker");
            }
        } else {
            if (recordState == RecordState.BOTH) {
                if (sender.equals(leftNeighbor)) {
                    recordState = RecordState.RIGHT;
                } else if (sender.equals(rightNeighbor)) {
                    recordState = RecordState.LEFT;
                }
            } else {
                recordState = RecordState.IDLE;
                /*
                if (snapshotToken != null) {
                    forwarder.sendSnapshotToken(leftNeighbor, new SnapshotToken(currentSnapshot.state + this.snapshotToken.getSnapshot()));
                    snapshotToken = null;
                }
                 */
                //currentSnapshot = new Snapshot();
            }
            snapshots.add(currentSnapshot);
            // snapshots.add(currentSnapshot);
        }
    }

    public void receiveHandoffMessage(Message msg) {
        System.out.println("handoff sender " + msg.getSender());
        System.out.println("record state " + recordState);
        if (recordState == RecordState.IDLE) {
            return;
        }

        if ((recordState == RecordState.BOTH || recordState == RecordState.LEFT) && msg.getSender().equals(leftNeighbor)) {
            currentSnapshot.leftInputChannel.add(msg);
        } else if ((recordState == RecordState.BOTH || recordState == RecordState.RIGHT) && msg.getSender().equals(rightNeighbor)) {
            currentSnapshot.rightInputChannel.add(msg);
        }
    }

    public void receiveSnapshotToken(SnapshotToken snapshotToken) {
        System.out.println("RECORD STATE = " + recordState);
        this.snapshotToken = snapshotToken;
        if (!this.isInitiator() && this.recordState == RecordState.IDLE) {
            System.out.println(currentSnapshot.rightInputChannel);
            System.out.println(currentSnapshot.rightInputChannel.size());
            System.out.println(currentSnapshot.leftInputChannel);
            System.out.println(currentSnapshot.leftInputChannel.size());
            forwarder.sendSnapshotToken(leftNeighbor, new SnapshotToken(currentSnapshot.state + this.snapshotToken.getSnapshot()));
            this.snapshotToken = null;
        } else if (this.isInitiator()) {
            snapshots.forEach(snapshot -> {
                System.out.println("right channel messages:" + snapshot.rightInputChannel.size());
                System.out.println("left channel messages:" + snapshot.leftInputChannel.size());
            });
            System.out.println(snapshots.size());
        }
    }

    public boolean isInitiator() {
        return initiator;
    }

    public boolean hasSnapshotToken() {
        return snapshotToken != null;
    }

    public int getSnapshot() {
        return snapshotToken.getSnapshot();
    }

    public void unsetInitiator() {
        initiator = false;
    }
}