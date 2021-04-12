package Server;

import org.json.JSONObject;
import util.Coords;

import java.util.Base64;

public class Proof {
    private final int epoch;
    private final String witnessUsername;
    private final String proverUsername;
    private final Coords coords;

    public Proof(JSONObject proofJSON) {
        byte[] proofMessageBase64 = Base64.getDecoder().decode(proofJSON.getString("message"));
        JSONObject proofMessageJSON = new JSONObject(new String(proofMessageBase64));
        this.epoch = proofMessageJSON.getInt("epoch");
        this.witnessUsername = proofMessageJSON.getString("witnessUsername");
        this.proverUsername = proofMessageJSON.getString("proverUsername");
        this.coords = new Coords(
                proofMessageJSON.getInt("x"),
                proofMessageJSON.getInt("y")
        );
    }

    public int getEpoch() {
        return epoch;
    }

    public String getWitnessUsername() {
        return witnessUsername;
    }

    public String getProverUsername() {
        return proverUsername;
    }

    public Coords getCoords() {
        return coords;
    }
}
