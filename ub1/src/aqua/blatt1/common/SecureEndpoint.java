package aqua.blatt1.common;

import aqua.blatt1.common.msgtypes.KeyExchangeMessage;
import messaging.Endpoint;
import messaging.Message;

import javax.crypto.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.security.*;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SecureEndpoint extends Endpoint {
    final Endpoint endpoint;
    KeyPair keyPair;
    Cipher encrypt;
    Cipher decrypt;
    Map<InetSocketAddress, PublicKey> communicationPartnerPublicKeys = new HashMap<>();

    public SecureEndpoint(Endpoint endpoint) {
        this.endpoint = endpoint;
        try {
            this.keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
            this.encrypt = Cipher.getInstance("RSA");
            this.decrypt = Cipher.getInstance("RSA");
            this.decrypt.init(Cipher.DECRYPT_MODE, this.keyPair.getPrivate());
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e) {
            e.printStackTrace();
        }
    }

    public void send(InetSocketAddress receiver, Serializable payload) {
        if(!communicationPartnerPublicKeys.containsKey(receiver)) {
            endpoint.send(receiver, new KeyExchangeMessage(keyPair.getPublic()));
            Message message = endpoint.blockingReceive();
            communicationPartnerPublicKeys.put(message.getSender(), ((KeyExchangeMessage) payload).publicKey);
        }
        try {
            byte[] converted = Objects.requireNonNull(convertToBytes(payload));
            this.encrypt.init(Cipher.ENCRYPT_MODE, communicationPartnerPublicKeys.get(receiver));
            byte[] encrypted = encrypt.doFinal(converted);
            byte[] encoded = Base64.getEncoder().encode(encrypted);
            endpoint.send(receiver, new String(encoded));
        } catch (IllegalBlockSizeException | BadPaddingException | InvalidKeyException e) {
            e.printStackTrace();
        }
    }

    private byte[] convertToBytes(Object object) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(object);
            return bos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Serializable convertFromBytes(byte[] bytes) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream in = new ObjectInputStream(bis)) {
            return (Serializable) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        return null;
    }

    public Message nonBlockingReceive() {
        Message message = endpoint.nonBlockingReceive();

        Serializable payload = message.getPayload();
        if(payload instanceof KeyExchangeMessage) {
            communicationPartnerPublicKeys.put(message.getSender(), ((KeyExchangeMessage) payload).publicKey);
            endpoint.send(message.getSender(), keyPair.getPublic());
            return null;
        }
        try {
            byte[] converted = Objects.requireNonNull(convertToBytes(payload));
            byte[] decoded = Base64.getDecoder().decode(converted);
            payload = new String(decrypt.doFinal(decoded));
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
        }

        return new Message(payload, message.getSender());
    }

    public Message blockingReceive() {
        Message message = endpoint.blockingReceive();

        Serializable payload = message.getPayload();
        if(payload instanceof KeyExchangeMessage) {
            communicationPartnerPublicKeys.put(message.getSender(), ((KeyExchangeMessage) payload).publicKey);
            endpoint.send(message.getSender(), keyPair.getPublic());
            return null;
        }
        try {
            byte[] converted = Objects.requireNonNull(convertToBytes(payload));
            byte[] decoded = Base64.getDecoder().decode(converted);
            payload = new String(decrypt.doFinal(decoded));
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
        }

        return new Message(payload, message.getSender());
    }
}
