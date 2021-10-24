package aqua.blatt1.broker;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.Properties;
import aqua.blatt1.common.msgtypes.DeregisterRequest;
import aqua.blatt1.common.msgtypes.HandoffRequest;
import aqua.blatt1.common.msgtypes.RegisterResponse;
import messaging.Endpoint;
import messaging.Message;

import javax.swing.*;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
        private final ReadWriteLock lock;

        public BrokerTask(Message message) {
            this.message = message;
            lock = new ReentrantReadWriteLock();
        }

        @Override
        public void run() {
            String messageType = message.getPayload().getClass().getSimpleName();

            switch (messageType) {
                case "RegisterRequest" -> {
                    String CLIENT_PREFIX = "client";
                    int clientSize = synchronizedClientSize();
                    String clientId = CLIENT_PREFIX + "_" + clientSize + 1;
                    InetSocketAddress client = message.getSender();

                    addClientSynchronously(clientId, client);
                    endpoint.send(client, new RegisterResponse(clientId));
                }
                case "DeregisterRequest" -> {
                    String clientId = ((DeregisterRequest) message.getPayload()).getId();
                    int clientIdx = synchronizedClientIdx(clientId);

                    removeClientSynchronously(clientIdx);
                }
                case "HandoffRequest" -> {
                    int clientIdx = synchronizedClientIdx(message.getSender());
                    HandoffRequest req = (HandoffRequest) message.getPayload();
                    InetSocketAddress neighbor;
                    if (req.getFish().getDirection() == Direction.LEFT) {
                        neighbor = synchronizedLeftNeighbor(clientIdx);
                    } else {
                        neighbor = synchronizedRightNeighbor(clientIdx);
                    }

                    endpoint.send(neighbor, req);
                }
            }
        }

        private int synchronizedClientSize() {
            int clientSize;
            lock.readLock().lock();
            clientSize = clients.size();
            lock.readLock().unlock();
            return clientSize;
        }

        private int synchronizedClientIdx(InetSocketAddress sender) {
            int clientIdx;
            lock.readLock().lock();
            clientIdx = clients.indexOf(sender);
            lock.readLock().unlock();
            return clientIdx;
        }

        private int synchronizedClientIdx(String clientId) {
            int clientIdx;
            lock.readLock().lock();
            clientIdx = clients.indexOf(clientId);
            lock.readLock().unlock();
            return clientIdx;
        }

        private InetSocketAddress synchronizedLeftNeighbor(int clientIdx) {
            InetSocketAddress leftNeighbor;
            lock.readLock().lock();
            leftNeighbor = clients.getLeftNeighorOf(clientIdx);
            lock.readLock().unlock();
            return leftNeighbor;
        }

        private InetSocketAddress synchronizedRightNeighbor(int clientIdx) {
            InetSocketAddress rightNeighbor;
            lock.readLock().lock();
            rightNeighbor = clients.getRightNeighorOf(clientIdx);
            lock.readLock().unlock();
            return rightNeighbor;
        }

        private void removeClientSynchronously(int clientIdx) {
            lock.writeLock().lock();
            clients.remove(clientIdx);
            lock.writeLock().unlock();
        }

        private void addClientSynchronously(String clientId, InetSocketAddress client) {
            lock.writeLock().lock();
            clients.add(clientId, client);
            lock.writeLock().unlock();
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
