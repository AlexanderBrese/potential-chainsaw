package aqua.blatt1.client;

import java.net.InetSocketAddress;

import aqua.blatt1.common.msgtypes.*;
import messaging.Endpoint;
import messaging.Message;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.Properties;

public class ClientCommunicator {
    private final Endpoint endpoint;

    public ClientCommunicator() {
        endpoint = new Endpoint();
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
                String messageType = msg.getPayload().getClass().getSimpleName();

                switch (messageType) {
                    case "RegisterResponse" -> {
                        tankModel.onRegistration(((RegisterResponse) msg.getPayload()).getId());
                    }
                    case "HandoffRequest" -> {
                        tankModel.receiveFish(((HandoffRequest) msg.getPayload()).getFish());
                    }

                    case "NeighborRegisterUpdate" -> {
                        NeighborRegisterUpdate neighborRegisterUpdate = (NeighborRegisterUpdate) msg.getPayload();
                        tankModel.addNeighbor(neighborRegisterUpdate.getNeighbor(), neighborRegisterUpdate.getDirection());
                    }

                    case "NeighborDeregisterUpdate" -> {
                        tankModel.removeNeighbor(((NeighborDeregisterUpdate)msg.getPayload()).getDirection());
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
