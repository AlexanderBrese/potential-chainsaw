package aqua.blatt1.broker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/*
 * This class is not thread-safe and hence must be used in a thread-safe way, e.g. thread confined or
 * externally synchronized.
 */

public class ClientCollection<T> {
    private class Client {
        final String id;
        final T client;

        Client(String id, T client) {
            this.id = id;
            this.client = client;
        }
    }

    private final List<Client> clients;
    private final ReadWriteLock lock;

    public ClientCollection() {
        clients = new ArrayList<Client>();
        lock = new ReentrantReadWriteLock();
    }

    public ClientCollection<T> add(String id, T client) {
        clients.add(new Client(id, client));
        return this;
    }

    public ClientCollection<T> remove(int index) {
        clients.remove(index);
        return this;
    }

    public int indexOf(String id) {
        for (int i = 0; i < clients.size(); i++)
            if (clients.get(i).id.equals(id))
                return i;
        return -1;
    }

    public int indexOf(T client) {
        for (int i = 0; i < clients.size(); i++)
            if (clients.get(i).client.equals(client))
                return i;
        return -1;
    }

    public T getClient(int index) {
        return clients.get(index).client;
    }

    public String getClientId(int index) { return clients.get(index).id; }

    public int size() {
        return clients.size();
    }

    public T getLeftNeighorOf(int index) {
        return index == 0 ? clients.get(clients.size() - 1).client : clients.get(index - 1).client;
    }

    public T getRightNeighorOf(int index) {
        return index < clients.size() - 1 ? clients.get(index + 1).client : clients.get(0).client;
    }

    public int synchronizedClientSize() {
        int clientSize;
        lock.readLock().lock();
        clientSize = size();
        lock.readLock().unlock();
        return clientSize;
    }

    public int synchronizedClientIdx(T sender) {
        int clientIdx;
        lock.readLock().lock();
        clientIdx = indexOf(sender);
        lock.readLock().unlock();
        return clientIdx;
    }

    public int synchronizedClientIdx(String clientId) {
        int clientIdx;
        lock.readLock().lock();
        clientIdx = indexOf(clientId);
        lock.readLock().unlock();
        return clientIdx;
    }

    public String synchronizedClientId(T client) {
        String clientId;
        lock.readLock().lock();
        clientId = getClientId(indexOf(client));
        lock.readLock().unlock();
        return clientId;
    }

    public T synchronizedLeftNeighbor(int clientIdx) {
        T leftNeighbor;
        lock.readLock().lock();
        leftNeighbor = getLeftNeighorOf(clientIdx);
        lock.readLock().unlock();
        return leftNeighbor;
    }

    public T synchronizedRightNeighbor(int clientIdx) {
        T rightNeighbor;
        lock.readLock().lock();
        rightNeighbor = getRightNeighorOf(clientIdx);
        lock.readLock().unlock();
        return rightNeighbor;
    }

    public void removeClientSynchronously(int clientIdx) {
        lock.writeLock().lock();
        remove(clientIdx);
        lock.writeLock().unlock();
    }

    public void addClientSynchronously(String clientId, T client) {
        lock.writeLock().lock();
        add(clientId, client);
        lock.writeLock().unlock();
    }

}
