package aqua.blatt1.broker;

import aqua.blatt1.client.AquaClient;
import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.Properties;
import messaging.Endpoint;
import messaging.Message;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class Broker implements AquaBroker {
    private final Endpoint endpoint;
    private final ClientCollection<AquaClient> clients;

    public Broker() {
        endpoint = new Endpoint(Properties.PORT);
        clients = new ClientCollection<>();
    }

    @Override
    public void registerRequest(AquaClient client) throws RemoteException {
        registerClient(client);
        updateNeighborsOnRegister(client);
    }

    private void registerClient(AquaClient client) {
        String CLIENT_PREFIX = "client";
        int clientSize = clients.synchronizedClientSize();
        String clientId = CLIENT_PREFIX + "_" + clientSize + 1;

        clients.addClientSynchronously(clientId, client);
        try {
            client.registerResponse(clientId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void updateNeighborsOnRegister(AquaClient client) {
        int clientIdx = clients.synchronizedClientIdx(client);
        AquaClient leftNeighbor = clients.synchronizedLeftNeighbor(clientIdx);
        AquaClient rightNeighbor = clients.synchronizedRightNeighbor(clientIdx);
        try {
            leftNeighbor.neighborRegister(client, Direction.RIGHT);
            rightNeighbor.neighborRegister(client, Direction.LEFT);
            client.neighborRegister(leftNeighbor, Direction.LEFT);
            client.neighborRegister(rightNeighbor, Direction.RIGHT);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void unregisterRequest(AquaClient client) throws RemoteException {
        deregisterClient(client);
        updateNeighborsOnDeregister(client);
    }

    private void deregisterClient(AquaClient client) {
        int clientIdx = clients.synchronizedClientIdx(client);
        clients.removeClientSynchronously(clientIdx);
    }

    private void updateNeighborsOnDeregister(AquaClient client) {
        int clientIdx = clients.synchronizedClientIdx(client);
        AquaClient leftNeighbor = clients.synchronizedLeftNeighbor(clientIdx);
        AquaClient rightNeighbor = clients.synchronizedRightNeighbor(clientIdx);
        try {
            leftNeighbor.neighborUnregister(Direction.RIGHT);
            rightNeighbor.neighborUnregister(Direction.LEFT);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void handoff(AquaClient client, FishModel fish) throws RemoteException {
        int clientIdx = clients.synchronizedClientIdx(client);
        AquaClient neighbor;
        if (fish.getDirection() == Direction.LEFT) {
            neighbor = clients.synchronizedLeftNeighbor(clientIdx);
        } else {
            neighbor = clients.synchronizedRightNeighbor(clientIdx);
        }

        neighbor.handoffRequest(fish);
    }

    @Override
    public void brokerWithPoisonPill() throws RemoteException {
        while (true) {
            try {
                Message message = endpoint.blockingReceive();
                if (message.getPayload().getClass().getSimpleName().equals("PoisonPill")) {
                    break;
                }
            } catch (RuntimeException e) {
                System.err.println(e.getMessage());
                break;
            }
        }
    }

    public static void main(String[] args) {
        try {
            Registry registry = LocateRegistry.createRegistry(
                    Registry.REGISTRY_PORT);
            AquaBroker stub = (AquaBroker)
                    UnicastRemoteObject.exportObject(new Broker(), 0);
            registry.rebind(Properties.BROKER_NAME, stub);
            //stub.brokerWithPoisonPill();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}
