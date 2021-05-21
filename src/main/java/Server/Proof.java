package Server;

import org.json.JSONArray;
import org.json.JSONObject;
import util.Coords;
import util.EncryptionLogic;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

public class Proof {
    private int epoch;
    private String witnessUsername;
    private String proverUsername;
    private Coords coords;
    private byte[] signature;
    private byte[] proofBytes;
    private byte[] witnessSessionKeyBytes;
    private byte[] witnessIV;

    public Proof() {
    }

    public Proof(JSONObject proofJSON, JSONObject locationJSON){
        this.epoch = proofJSON.getInt("epoch");
        this.witnessUsername = proofJSON.getString("witnessUsername");
        this.proverUsername = proofJSON.getString("proverUsername");

        this.coords = new Coords(
                locationJSON.getInt("x"),
                locationJSON.getInt("y")
        );
    }

    public Proof(JSONObject proofJSON, JSONObject locationJSON, byte[] proofBytes, byte[] witnessSessionKeyBytes, byte[] witnessIV) {

        this.epoch = proofJSON.getInt("epoch");
        this.witnessUsername = proofJSON.getString("witnessUsername");
        this.proverUsername = proofJSON.getString("proverUsername");
        this.coords = new Coords(
                locationJSON.getInt("x"),
                locationJSON.getInt("y")
        );
        this.proofBytes = proofBytes;
        this.witnessSessionKeyBytes = witnessSessionKeyBytes;
        this.witnessIV = witnessIV;
    }

    public JSONObject getProofJSON(){

        JSONObject proof = new JSONObject();
        JSONObject message = new JSONObject(new String(proofBytes));

        //we only need the proof itself and its digital signature,
        // not the outer original request digital signature and json
        proof.put("proof", message.getJSONObject("proof"));
        proof.put("digital_signature", message.getString("digital_signature"));
        proof.put("witness_session_key_bytes", Base64.getEncoder().encodeToString(this.witnessSessionKeyBytes));
        proof.put("witness_iv", Base64.getEncoder().encodeToString(witnessIV));

        return proof;

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

    public byte[] getProofBytes() {
        return proofBytes;
    }

    public void setProofBytes(byte[] proofBytes) {
        this.proofBytes = proofBytes;
    }

    public byte[] getWitnessSessionKeyBytes() {
        return witnessSessionKeyBytes;
    }

    public void setWitnessSessionKeyBytes(byte[] witnessSessionKeyBytes) {
        this.witnessSessionKeyBytes = witnessSessionKeyBytes;
    }

    public byte[] getWitnessIV() {
        return witnessIV;
    }

    public void setWitnessIV(byte[] witnessIV) {
        this.witnessIV = witnessIV;
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
