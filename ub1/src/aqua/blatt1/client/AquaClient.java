package aqua.blatt1.client;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface AquaClient extends Remote {
    void registerResponse(String clientId) throws RemoteException;
    void handoffRequest(FishModel fish) throws RemoteException;
    void neighborRegister(AquaClient neighbor, Direction direction) throws RemoteException;
    void neighborUnregister(Direction direction) throws RemoteException;
}
