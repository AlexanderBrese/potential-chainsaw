package aqua.blatt1.common;

import aqua.blatt1.common.msgtypes.KeyExchangeMessage;
import messaging.Endpoint;
import messaging.Message;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.*;
import java.util.*;
// Asymmetric key RSA groessere keysize, oder verschluesselter symmetric key
public class SecureEndpoint extends Endpoint {
    final Endpoint endpoint;
    KeyPair keyPair;
    Cipher privateKeyCipher;
    Cipher encryptDataCipher;
    SecretKey symmetricKey;
    int symmetricKeyLength = 128;
    Map<InetSocketAddress, PublicKey> communicationPartnerPublicKeys = new HashMap<>();

    public SecureEndpoint(Endpoint endpoint) {
        this.endpoint = endpoint;
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(this.symmetricKeyLength);
            this.symmetricKey = generator.generateKey();
            this.encryptDataCipher = Cipher.getInstance("AES");
            this.encryptDataCipher.init(Cipher.ENCRYPT_MODE, this.symmetricKey);
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            this.keyPair = kpg.generateKeyPair();
            this.privateKeyCipher = Cipher.getInstance("RSA");
            this.privateKeyCipher.init(Cipher.PRIVATE_KEY, this.keyPair.getPrivate());
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e) {
            e.printStackTrace();
        }
    }

    public void send(InetSocketAddress receiver, Serializable payload) {
        if (!communicationPartnerPublicKeys.containsKey(receiver)) {
            System.out.println("sending public key");
            endpoint.send(receiver, new KeyExchangeMessage(keyPair.getPublic()));
            blockingReceive();
        }
        System.out.println("done sending");
        try {
            Cipher publicKeyCipher = Cipher.getInstance("RSA");
            publicKeyCipher.init(Cipher.PUBLIC_KEY, communicationPartnerPublicKeys.get(receiver));
            byte[] converted = Objects.requireNonNull(convertToBytes(payload));
            byte[] encryptedData = this.encryptDataCipher.doFinal(converted);
            byte[] encryptedSymmetricKey = publicKeyCipher.doFinal(this.symmetricKey.getEncoded());
            System.out.println("symmetric key length: " + encryptedSymmetricKey.length);
            byte[] encryptedPayload = new byte[encryptedSymmetricKey.length + encryptedData.length];
            ByteBuffer buff = ByteBuffer.wrap(encryptedPayload);
            buff.put(encryptedSymmetricKey);
            buff.put(encryptedData);
            endpoint.send(receiver, buff.array());
        } catch (IllegalBlockSizeException | BadPaddingException | InvalidKeyException | NoSuchPaddingException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    private byte[] convertToBytes(Object object) {

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ObjectOutput out;
            out = new ObjectOutputStream(bos);
            out.writeObject(object);
            out.flush();
            return bos.toByteArray();

        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private Object convertToObject(byte[] bytes) {
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);

        try (ObjectInput in = new ObjectInputStream(bis)) {

            return in.readObject();

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        return null;
    }

    public Message nonBlockingReceive() {
        Message message = endpoint.nonBlockingReceive();
        if(message == null) return null;
        Serializable payload = message.getPayload();
        System.out.println("payload instance " +payload.getClass());
        if (payload instanceof KeyExchangeMessage) {
            if(communicationPartnerPublicKeys.containsKey(message.getSender())) {
                return new Message(payload, message.getSender());
            }
            communicationPartnerPublicKeys.put(message.getSender(), ((KeyExchangeMessage) payload).publicKey);
            endpoint.send(message.getSender(), new KeyExchangeMessage(keyPair.getPublic()));
        } else {
            try {
                byte[] converted = Objects.requireNonNull(convertToBytes(payload));
                for (byte b : converted) {
                    System.out.print(b + " ");
                }
                byte[] encryptedKey = new byte[this.symmetricKeyLength];
                byte[] encryptedData = new byte[converted.length - this.symmetricKeyLength];
                encryptedKey = Arrays.copyOfRange(converted, 0, this.symmetricKeyLength);
                encryptedData = Arrays.copyOfRange(converted, this.symmetricKeyLength, converted.length);
                byte[] decryptedKey = privateKeyCipher.doFinal(encryptedKey);
                SecretKey key = new SecretKeySpec(decryptedKey, 0, decryptedKey.length, "AES");
                Cipher decryptDataCipher = Cipher.getInstance("AES");
                decryptDataCipher.init(Cipher.DECRYPT_MODE, key);
                payload = decryptDataCipher.doFinal(encryptedData);
            } catch (IllegalBlockSizeException | BadPaddingException | NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException e) {
                e.printStackTrace();
            }
        }

        return new Message(payload, message.getSender());
    }

    public Message blockingReceive() {
        Message message = endpoint.blockingReceive();

        Serializable payload = message.getPayload();
        System.out.println("payload instance " +payload.getClass());
        if (payload instanceof KeyExchangeMessage) {
            if(communicationPartnerPublicKeys.containsKey(message.getSender())) {
                return new Message(payload, message.getSender());
            }
            communicationPartnerPublicKeys.put(message.getSender(), ((KeyExchangeMessage) payload).publicKey);
            endpoint.send(message.getSender(), new KeyExchangeMessage(keyPair.getPublic()));
        } else {
            try {
                byte[] converted = Objects.requireNonNull(convertToBytes(payload));
                for (byte b : converted) {
                    System.out.print(b + " ");
                }
                byte[] encryptedKey = new byte[this.symmetricKeyLength];
                byte[] encryptedData = new byte[converted.length - this.symmetricKeyLength];
                encryptedKey = Arrays.copyOfRange(converted, 0, this.symmetricKeyLength);
                encryptedData = Arrays.copyOfRange(converted, this.symmetricKeyLength, converted.length);
                byte[] decryptedKey = privateKeyCipher.doFinal(encryptedKey);
                SecretKey key = new SecretKeySpec(decryptedKey, 0, decryptedKey.length, "AES");
                Cipher decryptDataCipher = Cipher.getInstance("AES");
                decryptDataCipher.init(Cipher.DECRYPT_MODE, key);
                payload = decryptDataCipher.doFinal(encryptedData);
            } catch (IllegalBlockSizeException | BadPaddingException | NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException e) {
                e.printStackTrace();
            }
        }

        return new Message(payload, message.getSender());
    }
}
