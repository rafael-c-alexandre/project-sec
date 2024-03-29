package Server;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import util.Coords;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Objects;

public class UserReport {
    private int epoch;
    private String username;
    private Coords coords;
    private boolean closed = false;
    private byte[] signature;
    private ArrayList<Proof> proofsList = new ArrayList<Proof>();


    public UserReport() {
    }

    public UserReport(int epoch, String username, Coords coords) {
        this.epoch = epoch;
        this.username = username;
        this.coords = coords;
    }

    public UserReport(JSONObject reportJSON, byte[] signature) {

        try {
            this.epoch = reportJSON.getInt("epoch");
            this.username = reportJSON.getString("username");
            this.coords = new Coords(
                    reportJSON.getInt("x"),
                    reportJSON.getInt("y")
            );
            this.signature = signature;

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public JSONObject getReportJSON() {
        JSONObject reportInfo = new JSONObject();
        reportInfo.put("username", username);
        reportInfo.put("epoch", epoch);
        reportInfo.put("x", coords.getX());
        reportInfo.put("y", coords.getY());

        JSONObject report = new JSONObject();
        report.put("report_info", reportInfo);
        report.put("report_digital_signature", Base64.getEncoder().encodeToString(this.signature));

        return report;

    }


    public int getEpoch() {
        return epoch;
    }

    public void setEpoch(int epoch) {
        this.epoch = epoch;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Coords getCoords() {
        return coords;
    }

    public void setCoords(Coords coords) {
        this.coords = coords;
    }

    public ArrayList<Proof> getProofsList() {
        return proofsList;
    }

    public void addProof(Proof proof) {
        this.proofsList.add(proof);
    }

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public void setProofsList(ArrayList<Proof> proofsList) {
        this.proofsList = proofsList;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserReport that = (UserReport) o;
        return epoch == that.epoch && Objects.equals(username, that.username) && Objects.equals(coords, that.coords);
    }

    @Override
    public String toString() {
        return "UserReport{" +
                "epoch=" + epoch +
                ", username='" + username + '\'' +
                ", coords=" + coords +
                '}';
    }
}
