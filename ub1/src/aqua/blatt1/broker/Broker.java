package aqua.blatt1.broker;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.Properties;
import aqua.blatt1.common.msgtypes.*;
import messaging.Endpoint;
import messaging.Message;

import javax.swing.*;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Broker {
    private final Endpoint endpoint;
    private final ClientCollection<InetSocketAddress> clients;
    private final ExecutorService executor;
    private static volatile boolean stopRequested = false;

    public Broker() {
        endpoint = new Endpoint(Properties.PORT);
        clients = new ClientCollection<>();
        executor = Executors.newFixedThreadPool(Properties.THREAD_POOL_SIZE);
    }

    private class BrokerTask implements Runnable {
        private final Message message;


        public BrokerTask(Message message) {
            this.message = message;

        }

        @Override
        public void run() {
            String messageType = message.getPayload().getClass().getSimpleName();

            switch (messageType) {
                case "RegisterRequest" -> {
                    register();
                }
                case "DeregisterRequest" -> {
                    deregister();
                }
                case "HandoffRequest" -> {
                    int clientIdx = clients.synchronizedClientIdx(message.getSender());
                    HandoffRequest req = (HandoffRequest) message.getPayload();
                    InetSocketAddress neighbor;
                    if (req.getFish().getDirection() == Direction.LEFT) {
                        neighbor = clients.synchronizedLeftNeighbor(clientIdx);
                    } else {
                        neighbor = clients.synchronizedRightNeighbor(clientIdx);
                    }

                    endpoint.send(neighbor, req);
                }
            }
        }

        private void register() {

            InetSocketAddress client = message.getSender();

            registerClient(client);
            updateNeighborsOnRegister(client);
        }

        private void registerClient(InetSocketAddress client) {
            String CLIENT_PREFIX = "client";
            int clientSize = clients.synchronizedClientSize();
            String clientId = CLIENT_PREFIX + "_" + clientSize + 1;

            clients.addClientSynchronously(clientId, client);
            endpoint.send(client, new RegisterResponse(clientId));
        }

        private void updateNeighborsOnRegister(InetSocketAddress client) {
            int clientIdx = clients.synchronizedClientIdx(client);
            InetSocketAddress leftNeighbor = clients.synchronizedLeftNeighbor(clientIdx);
            InetSocketAddress rightNeighbor = clients.synchronizedRightNeighbor(clientIdx);

            endpoint.send(leftNeighbor, new NeighborRegisterUpdate(client, Direction.RIGHT));
            endpoint.send(rightNeighbor, new NeighborRegisterUpdate(client, Direction.LEFT));
            endpoint.send(client, new NeighborRegisterUpdate(leftNeighbor, Direction.LEFT));
            endpoint.send(client, new NeighborRegisterUpdate(rightNeighbor, Direction.RIGHT));
        }

        private void deregister() {
            deregisterClient();
            updateNeighborsOnDeregister();
        }

        private void deregisterClient() {
            String clientId = ((DeregisterRequest) message.getPayload()).getId();
            int clientIdx = clients.synchronizedClientIdx(clientId);

            clients.removeClientSynchronously(clientIdx);
        }

        private void updateNeighborsOnDeregister() {
            InetSocketAddress client = message.getSender();
            int clientIdx = clients.synchronizedClientIdx(client);
            InetSocketAddress leftNeighbor = clients.synchronizedLeftNeighbor(clientIdx);
            InetSocketAddress rightNeighbor = clients.synchronizedRightNeighbor(clientIdx);
            endpoint.send(leftNeighbor, new NeighborDeregisterUpdate(Direction.RIGHT));
            endpoint.send(rightNeighbor, new NeighborDeregisterUpdate(Direction.LEFT));
        }
    }

    public void brokerWithFlagStop() {
        Thread backgroundThread = new Thread(() -> {
            JOptionPane.showMessageDialog(null, "Press OK button to stop server");
            System.out.println("server stopped");
            stopRequested = true;
        });
        backgroundThread.start();
        while (!stopRequested) {
            try {
                Message message = endpoint.blockingReceive();
                BrokerTask task = new BrokerTask(message);
                executor.execute(task);
            } catch (RuntimeException e) {
                System.err.println(e.getMessage());
                break;
            }
        }
        executor.shutdown();
    }

    public void brokerWithPoisonPill() {
        while (true) {
            try {
                Message message = endpoint.blockingReceive();
                if (message.getPayload().getClass().getSimpleName().equals("PoisonPill")) {
                    System.out.println("Received poison pill");
                    executor.shutdownNow();
                    break;
                }
                BrokerTask task = new BrokerTask(message);
                executor.execute(task);
            } catch (RuntimeException e) {
                System.err.println(e.getMessage());
                break;
            }
        }
    }

    public static void main(String[] args) {
        Broker broker = new Broker();
        //broker.brokerWithFlagStop();
        broker.brokerWithPoisonPill();
    }
}
