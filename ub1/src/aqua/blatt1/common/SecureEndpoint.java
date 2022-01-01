package aqua.blatt1.common;

import messaging.Endpoint;
import messaging.Message;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.InetSocketAddress;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class SecureEndpoint extends Endpoint {
    final Endpoint endpoint;
    final SecretKeySpec symmetricKey;
    Cipher encrypt;
    Cipher decrypt;

    SecureEndpoint(Endpoint endpoint) {
        this.endpoint = endpoint;
        this.symmetricKey = new SecretKeySpec("CAFEBABECAFEBABE".getBytes(), "AES");
        try {
            this.encrypt = Cipher.getInstance("AES");
            this.decrypt = Cipher.getInstance("AES");
            this.encrypt.init(Cipher.ENCRYPT_MODE, this.symmetricKey);
            this.decrypt.init(Cipher.DECRYPT_MODE, this.symmetricKey);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e) {
            e.printStackTrace();
        }
    }

    public void send(InetSocketAddress receiver, Serializable payload) {
        try {
            byte[] encrypted = encrypt.doFinal(fromPayload(payload));
            endpoint.send(receiver, toPayload(encrypted));
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
        }
    }

    private byte[] fromPayload(Serializable payload) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out;
        byte[] bytes = null;
        try {
            out = new ObjectOutputStream(bos);
            out.writeObject(payload);
            out.flush();
            bytes = bos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                bos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return bytes;
    }

    private Serializable toPayload(byte[] payload) {
        ByteArrayInputStream bis = new ByteArrayInputStream(payload);
        ObjectInput in = null;
        Serializable out = null;
        try {
            in = new ObjectInputStream(bis);
            out = (Serializable) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return out;
    }



    public Message nonBlockingReceive() {

    }

    public Message blockingReceive() {

    }
}
