package aqua.blatt1.broker;

import aqua.blatt1.client.AquaClient;
import aqua.blatt1.common.FishModel;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface AquaBroker extends Remote {
    void registerRequest(AquaClient client) throws RemoteException;
    void unregisterRequest(AquaClient client) throws RemoteException;
    void handoff(AquaClient client, FishModel fish) throws RemoteException;
    void brokerWithPoisonPill() throws RemoteException;
}
