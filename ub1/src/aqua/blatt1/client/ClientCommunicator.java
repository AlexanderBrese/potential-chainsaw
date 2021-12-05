package aqua.blatt1.client;

import java.net.InetSocketAddress;
import java.util.Timer;
import java.util.TimerTask;

import aqua.blatt1.common.msgtypes.*;
import messaging.Endpoint;
import messaging.Message;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.Properties;

public class ClientCommunicator {
    private final Endpoint endpoint;
    private final Timer timer;

    public ClientCommunicator() {
        endpoint = new Endpoint();
        timer = new Timer();
    }

    public class ClientForwarder {
        private final InetSocketAddress broker;

        private ClientForwarder() {
            this.broker = new InetSocketAddress(Properties.HOST, Properties.PORT);
        }

        public void register() {
            endpoint.send(broker, new RegisterRequest());
        }

        public void deregister(String id) {
            endpoint.send(broker, new DeregisterRequest(id));
        }

        public void handOff(FishModel fish, InetSocketAddress neighbor) {
            endpoint.send(neighbor, new HandoffRequest(fish));
        }

        public void sendSnapshotMarker(InetSocketAddress neighbor) {
            System.out.println("sent snapshot marker");
            endpoint.send(neighbor, new SnapshotMarker());
        }

        public void sendSnapshotToken(InetSocketAddress neighbor, SnapshotToken snapshotToken) {
            System.out.println("sent snapshot token");
            endpoint.send(neighbor, snapshotToken);
        }

        public void sendToken(InetSocketAddress neighbor) {
            endpoint.send(neighbor, new Token());
        }
    }

    public class ClientReceiver extends Thread {
        private final TankModel tankModel;

        private ClientReceiver(TankModel tankModel) {
            this.tankModel = tankModel;
        }

        @Override
        public void run() {
            while (!isInterrupted()) {
                Message msg = endpoint.blockingReceive();
                InetSocketAddress sender = msg.getSender();
                String messageType = msg.getPayload().getClass().getSimpleName();

                switch (messageType) {
                    case "RegisterResponse" -> {
                        tankModel.onRegistration(((RegisterResponse) msg.getPayload()).getId());
                        TimerTask task = new TimerTask() {
                            @Override
                            public void run() {
                                System.out.println("Client reregistering with id " + ((RegisterResponse) msg.getPayload()).getId());
                                tankModel.forwarder.register();
                            }
                        };
                        timer.schedule(task, 0, ((RegisterResponse) msg.getPayload()).getLeaseDuration());
                    }
                    case "HandoffRequest" -> {
                        tankModel.receiveHandoffMessage(msg);
                        tankModel.receiveFish(((HandoffRequest) msg.getPayload()).getFish());
                    }

                    case "NeighborRegisterUpdate" -> {
                        NeighborRegisterUpdate neighborRegisterUpdate = (NeighborRegisterUpdate) msg.getPayload();
                        tankModel.addNeighbor(neighborRegisterUpdate.getNeighbor(), neighborRegisterUpdate.getDirection());
                    }

                    case "NeighborDeregisterUpdate" -> {
                        tankModel.removeNeighbor(((NeighborDeregisterUpdate) msg.getPayload()).getDirection());
                    }

                    case "Token" -> {
                        tankModel.receiveToken();
                    }

                    case "SnapshotMarker" -> {
                        System.out.println("received snapshot marker");
                        tankModel.receiveSnapshotMarker(sender);
                    }

                    case "SnapshotToken" -> {
                        System.out.println("received snapshot token");
                        tankModel.receiveSnapshotToken((SnapshotToken) msg.getPayload());
                    }
                }

            }
            System.out.println("Receiver stopped.");
        }
    }

    public ClientForwarder newClientForwarder() {
        return new ClientForwarder();
    }

    public ClientReceiver newClientReceiver(TankModel tankModel) {
        return new ClientReceiver(tankModel);
    }

}
