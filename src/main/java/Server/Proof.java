package Server;

import org.json.JSONObject;
import util.Coords;

import java.util.Base64;

public class Proof {
    private int epoch;
    private String witnessUsername;
    private String proverUsername;
    private Coords coords;
    private byte[] signature;

    public Proof() {
    }

    public Proof(JSONObject proofJSON, JSONObject locationJSON, byte[] signature) {

        this.epoch = proofJSON.getInt("epoch");
        this.witnessUsername = proofJSON.getString("witnessUsername");
        this.proverUsername = proofJSON.getString("proverUsername");
        this.coords = new Coords(
                locationJSON.getInt("x"),
                locationJSON.getInt("y")
        );
        this.signature = signature;
    }

    public JSONObject getProofJSON(){
        JSONObject ret = new JSONObject();
        ret.put("epoch",this.epoch);
        ret.put("witnessUsername",this.witnessUsername);
        ret.put("proverUsername",this.epoch);
        ret.put("x",this.epoch);
        ret.put("y",this.epoch);
        ret.put("signature",this.signature);
        return ret;

    }

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
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

    public void setEpoch(int epoch) {
        this.epoch = epoch;
    }

    public void setWitnessUsername(String witnessUsername) {
        this.witnessUsername = witnessUsername;
    }

    public void setProverUsername(String proverUsername) {
        this.proverUsername = proverUsername;
    }

    public void setCoords(Coords coords) {
        this.coords = coords;
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
