package Client;

import org.json.JSONArray;
import org.json.JSONObject;
import util.Coords;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

public class ClientLogic {
    private String username;
    private Map<String, Map<Integer, Coords>> grid = new HashMap<>();
    private int epoch = 0;

    public ClientLogic(String username,String gridFilePath) {
        this.username = username;
        populateGrid(gridFilePath);
    }

    public void populateGrid(String gridFilePath){
        Scanner scanner;
        //Populate the grid
        try {
            scanner = new Scanner(new File(gridFilePath));
        } catch (FileNotFoundException e) {
            System.out.println("No such grid file!");
            return;
        }

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            // process the line
            String[] parts = line.split(",");
            String user = parts[0].trim();
            int epoch = Integer.parseInt(parts[1].trim());
            int x = Integer.parseInt(parts[2].trim());
            int y = Integer.parseInt(parts[3].trim());

            if(!this.grid.containsKey(user))
                this.grid.put(user, new HashMap<>());

            this.grid.get(user).put(epoch,new Coords(x,y));

        }
    }

    public Coords getCoords(int epoch){
        return grid.get(username).get(epoch);
    }

    public byte[] generateLocationProof(Coords userCoords, String user, int epoch) {

        Coords currentUserCoords = grid.get(this.username).get(epoch);
        JSONObject jsonProof = new JSONObject();

        if (isClose(currentUserCoords,userCoords )) {

            // Create response message
            jsonProof.put("username", this.username);
            jsonProof.put("x", currentUserCoords.getX());
            jsonProof.put("y", currentUserCoords.getY());
            jsonProof.put("epoch", epoch);
            jsonProof.put("isNear", true);
        }
        else
            jsonProof.put("isNear", false);

        return jsonProof.toString().getBytes();

    }


    public List<String> closePeers( int epoch) {
        List<String> peers = new ArrayList<>();

        for (String user : grid.keySet()) {
            if (!user.equals(this.username) && isClose(grid.get(this.username).get(epoch),grid.get(user).get(epoch))) {
                peers.add(user);
            }
        }
        return peers;

    }

    public boolean isClose(Coords c1, Coords c2) {

        //let's assume a radius of 5
        int radius = 5;
        return (Math.pow(c2.getX() - c1.getX(),2)) + (Math.pow(c2.getY() - c1.getY(),2)) < Math.pow(radius,2);
    }

    public String getUsername() {
        return username;
    }

    public JSONObject createLocationProof(List<JSONObject> proofs) {
        Coords coords = getCoords(epoch);

        JSONObject message = new JSONObject();
        message.put("username", username);
        message.put("epoch", epoch);
        message.put("x", coords.getX());
        message.put("y", coords.getY());
        JSONArray ja = new JSONArray(proofs);
        message.put("reports", ja);

        return message;
    }
}
