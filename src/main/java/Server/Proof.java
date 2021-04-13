package Server;

import org.json.JSONObject;
import util.Coords;

import java.util.Base64;

public class Proof {
    private final int epoch;
    private final String witnessUsername;
    private final String proverUsername;
    private final Coords coords;

    public Proof(JSONObject proofJSON, JSONObject locationJSON) {

        this.epoch = proofJSON.getInt("epoch");
        this.witnessUsername = proofJSON.getString("witnessUsername");
        this.proverUsername = proofJSON.getString("proverUsername");
        this.coords = new Coords(
                locationJSON.getInt("x"),
                locationJSON.getInt("y")
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

    @Override
    public String toString() {
        return "Proof{" +
                "epoch=" + epoch +
                ", witnessUsername='" + witnessUsername + '\'' +
                ", proverUsername='" + proverUsername + '\'' +
                ", coords=" + coords +
                '}';
    }
}
