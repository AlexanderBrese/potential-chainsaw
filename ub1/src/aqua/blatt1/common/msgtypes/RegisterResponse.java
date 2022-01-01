package aqua.blatt1.common.msgtypes;

import java.io.Serializable;

@SuppressWarnings("serial")
public final class RegisterResponse implements Serializable {
    private final String id;
    private final Integer leaseDuration;

    public RegisterResponse(String id, Integer leaseDuration) {
        this.id = id;
        this.leaseDuration = leaseDuration;
    }

    public String getId() {
        return id;
    }

    public Integer getLeaseDuration() {
        return leaseDuration;
    }

}
