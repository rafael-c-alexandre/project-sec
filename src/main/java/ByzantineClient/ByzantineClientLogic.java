package ByzantineClient;

import Exceptions.ProverNotCloseEnoughException;
import org.json.JSONArray;
import org.json.JSONObject;
import util.Coords;
import util.EncryptionLogic;

import javax.crypto.SecretKey;
import java.io.File;
import java.io.FileNotFoundException;
import java.security.Key;
import java.util.*;

public class ByzantineClientLogic {

    private final String username;
    private final Map<String, Map<Integer, Coords>> grid = new HashMap<>();

    public ByzantineClientLogic(String username, String gridFilePath) {
        this.username = username;
        populateGrid(gridFilePath);
    }

    public void populateGrid(String gridFilePath) {
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

            if (!this.grid.containsKey(user))
                this.grid.put(user, new HashMap<>());

            this.grid.get(user).put(epoch, new Coords(x, y));

        }
    }

    public Coords getCoords(int epoch) {
        return grid.get(username).get(epoch);
    }

    public byte[][] generateLocationReport(int epoch) {
        Coords coords = getCoords(epoch);

        JSONObject message = new JSONObject();
        message.put("username", username);
        message.put("epoch", epoch);

        //byzantine user changes its coordinates to prove he is not inside the range
        message.put("x", 200);
        message.put("y", 300);

        byte[][] result = new byte[4][];

        //Generate a session Key
        SecretKey sessionKey = EncryptionLogic.generateAESKey();
        byte[] sessionKeyBytes = sessionKey.getEncoded();

        //Encrypt session jey with server public key
        byte[] iv = EncryptionLogic.generateIV();
        byte[] encryptedSessionKey = EncryptionLogic.encryptWithRSA(EncryptionLogic.getPublicKey("server"), sessionKeyBytes);
        byte[] encryptedMessage = EncryptionLogic.encryptWithAES(sessionKey, message.toString().getBytes(), iv);
        result[0] = encryptedMessage;

        //sign message
        byte[] digitalSignature = EncryptionLogic.createDigitalSignature(message.toString().getBytes(),
                EncryptionLogic.getPrivateKey(this.username));

        result[1] = digitalSignature;
        result[2] = encryptedSessionKey;
        result[3] = iv;




        return result;
    }

    public byte[][] generateLocationProof(String proverUsername, int epoch) throws ProverNotCloseEnoughException {

        Coords currentUserCoords = grid.get(this.username).get(epoch);
        Coords proverCoords = grid.get(proverUsername).get(epoch);

        //look at the grid to check if prover is nearby
        if (!isClose(currentUserCoords, proverCoords))
            throw new ProverNotCloseEnoughException();


        JSONObject jsonProof = new JSONObject();
        byte[][] result = new byte[4][];

        // Create response message
        jsonProof.put("witnessUsername", this.username);
        jsonProof.put("proverUsername", proverUsername);
        jsonProof.put("epoch", epoch);

        JSONObject jsonCoords = new JSONObject();
        jsonCoords.put("x", currentUserCoords.getX());
        jsonCoords.put("y", currentUserCoords.getY());

        //Generate a session Key
        SecretKey sessionKey = EncryptionLogic.generateAESKey();
        byte[] sessionKeyBytes = sessionKey.getEncoded();

        //Encrypt session jey with server public key
        byte[] iv = EncryptionLogic.generateIV();
        byte[] encryptedSessionKey = EncryptionLogic.encryptWithRSA(EncryptionLogic.getPublicKey("server"), sessionKeyBytes);


        byte[] encryptedLocation = EncryptionLogic.encryptWithAES(sessionKey, jsonCoords.toString().getBytes(), iv);
        jsonProof.put("encrypted_location", Base64.getEncoder().encodeToString(encryptedLocation));
        result[0] = jsonProof.toString().getBytes();

        //generate proof digital signature
        result[1] = EncryptionLogic.createDigitalSignature(jsonProof.toString().getBytes(), EncryptionLogic.getPrivateKey(this.username));
        result[2] = encryptedSessionKey;
        result[3] = iv;

        return result;

    }


    public List<String> closePeers(int epoch) {
        List<String> peers = new ArrayList<>();

        for (String user : grid.keySet()) {
            if (!user.equals(this.username) && isClose(grid.get(this.username).get(epoch), grid.get(user).get(epoch))) {
                peers.add(user);
            }
        }
        return peers;

    }

    public boolean isClose(Coords c1, Coords c2) {

        //let's assume a radius of 5
        int radius = 5;
        return (Math.pow(c2.getX() - c1.getX(), 2)) + (Math.pow(c2.getY() - c1.getY(), 2)) < Math.pow(radius, 2);
    }

    public String getUsername() {
        return username;
    }


    public byte[][] generateObtainLocationRequestParameters(String username, int epoch) {
        byte[][] ret = new byte[5][];
        JSONObject object = new JSONObject();
        JSONObject message = new JSONObject();

        //Generate a session Key
        SecretKey sessionKey = EncryptionLogic.generateAESKey();
        byte[] sessionKeyBytes = sessionKey.getEncoded();

        //Encrypt session jey with server public key
        byte[] encryptedSessionKey = EncryptionLogic.encryptWithRSA(EncryptionLogic.getPublicKey("server"), sessionKeyBytes);

        //Pass data to json
        message.put("username", username);
        message.put("epoch", epoch);

        object.put("message", message);

        //Encrypt data with session key
        byte[] iv = EncryptionLogic.generateIV();
        byte[] encryptedData = EncryptionLogic.encryptWithAES(
                sessionKey,
                object.toString().getBytes(),
                iv
        );

        //Generate digital signature
        byte[] digitalSignature = EncryptionLogic.createDigitalSignature(
                object.toString().getBytes(),
                EncryptionLogic.getPrivateKey(username)
        );

        ret[0] = encryptedData;
        ret[1] = digitalSignature;
        ret[2] = encryptedSessionKey;
        ret[3] = iv;
        ret[4] = sessionKeyBytes;

        return ret;
    }

    public Coords getCoordsFromReply(byte[] sessionKeyBytes, byte[] encryptedResponse, byte[] responseSignature, byte[] responseIv) {

        SecretKey sessionKey = EncryptionLogic.bytesToAESKey(sessionKeyBytes);
        //Decrypt response
        byte[] response = EncryptionLogic.decryptWithAES(sessionKey, encryptedResponse, responseIv);

        //Verify response signature
        if (!EncryptionLogic.verifyDigitalSignature(response, responseSignature, EncryptionLogic.getPublicKey("server")))
            System.out.println("Invalid signature from response");
        else
            System.out.println("Valid signature from response");

        //process response and return coords
        String jsonString = new String(response);
        JSONObject jsonObject = new JSONObject(jsonString);
        JSONObject msg = jsonObject.getJSONObject("message");

        return new Coords(msg.getInt("x"), msg.getInt("y"));
    }

    public byte[][] encryptProof(byte[] proof) {
        byte [][] res = new byte[4][];
        //Generate a session Key
        SecretKey sessionKey = EncryptionLogic.generateAESKey();
        byte[] sessionKeyBytes = sessionKey.getEncoded();

        //Encrypt session jey with server public key
        byte[] iv = EncryptionLogic.generateIV();
        byte[] encryptedSessionKey = EncryptionLogic.encryptWithRSA(EncryptionLogic.getPublicKey("server"), sessionKeyBytes);

        byte[] encryptedProof = EncryptionLogic.encryptWithAES(sessionKey, proof, iv);

        //generate proof digital signature
        res[3] = EncryptionLogic.createDigitalSignature(proof, EncryptionLogic.getPrivateKey(this.username));

        res[0] = encryptedProof;
        res[1] = encryptedSessionKey;
        res[2] = iv;
        return res;
    }

    public Map<String, Map<Integer, Coords>> getGrid() {
        return grid;
    }
}
