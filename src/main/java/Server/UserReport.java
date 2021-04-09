package Server;

import com.google.protobuf.ByteString;
import com.mysql.cj.xdevapi.JsonArray;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import util.Coords;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class UserReport {
    private int epoch;
    private String username;
    private Coords coords;
    //TODO MAYBE PROOFS


    public UserReport() {
    }

    public UserReport(int epoch, String username, Coords coords) {
        this.epoch = epoch;
        this.username = username;
        this.coords = coords;
    }

    public UserReport(String JsonUserReport){

        try {
            JSONObject obj = new JSONObject(JsonUserReport);
            this.epoch = obj.getInt("epoch");
            this.username = obj.getString("username");
            this.coords = new Coords(
                    obj.getInt("x"),
                    obj.getInt("y")
            );

            JSONArray proofs = (JSONArray) obj.get("reports");
            for (int i = 0; i < proofs.length(); i++) {
                JSONObject proof = proofs.getJSONObject(i);
                //TODO
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
