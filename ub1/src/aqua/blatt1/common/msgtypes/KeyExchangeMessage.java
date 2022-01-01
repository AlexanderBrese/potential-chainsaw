package aqua.blatt1.common.msgtypes;

import java.io.Serializable;
import java.security.PublicKey;

public class KeyExchangeMessage implements Serializable {
    public final PublicKey publicKey;

    public KeyExchangeMessage(PublicKey publicKey) {
        this.publicKey = publicKey;
    }
}
