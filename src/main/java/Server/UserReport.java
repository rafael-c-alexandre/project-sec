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
    private ArrayList<Proof> proofsList = new ArrayList<Proof>() ;
    //TODO MAYBE PROOFS


    public UserReport() {
    }

    public UserReport(int epoch, String username, Coords coords) {
        this.epoch = epoch;
        this.username = username;
        this.coords = coords;
    }

    public UserReport(JSONObject reportJSON){

        try {
            this.epoch = reportJSON.getInt("epoch");
            this.username = reportJSON.getString("username");
            this.coords = new Coords(
                    reportJSON.getInt("x"),
                    reportJSON.getInt("y")
            );

            JSONArray proofs = (JSONArray) reportJSON.get("proofs");
            for (int i = 0; i < proofs.length(); i++) {
                JSONObject proofJSON = proofs.getJSONObject(i);
                Proof proof = new Proof(proofJSON);
                proofsList.add(proof);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
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
