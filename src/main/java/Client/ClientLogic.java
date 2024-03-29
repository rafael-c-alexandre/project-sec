package Client;

import Exceptions.InvalidSignatureException;
import Exceptions.ProverNotCloseEnoughException;
import Server.Proof;
import javafx.util.Pair;
import org.json.JSONArray;
import org.json.JSONObject;
import proto.ObtainLocationReportReply;
import util.Coords;
import util.EncryptionLogic;

import javax.crypto.SecretKey;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientLogic {

    private final String username;
    private final int numberOfByzantineClients;
    private final int numberOfByzantineServers;
    private final Map<String, Map<Integer, Coords>> grid = new HashMap<>();
    private List<String> serverNames = new ArrayList<>();
    private String keystorePasswd;
    public ConcurrentHashMap<Integer, CopyOnWriteArrayList<String>> gotReportQuorums = new ConcurrentHashMap<>();
    public ConcurrentHashMap<Integer, CopyOnWriteArrayList<String>> gotProofQuorums = new ConcurrentHashMap<>();
    public ConcurrentHashMap<String, CopyOnWriteArrayList<String>> gotReadQuorum = new ConcurrentHashMap<>();
    public ConcurrentHashMap<String, CopyOnWriteArrayList<String>> gotWriteBackQuorum = new ConcurrentHashMap<>();
    public ConcurrentHashMap<String, CopyOnWriteArrayList<String>> gotMyProofsQuorum = new ConcurrentHashMap<>();
    public ConcurrentHashMap<String, CopyOnWriteArrayList<JSONObject>> myProofsResponses = new ConcurrentHashMap<>(); //<Epoch, <ProverUsername, ProofJSON
    public ConcurrentHashMap<String, JSONObject> readRequests = new ConcurrentHashMap<>();
    private final int byzantineMode;
    public final int serverQuorum; //quorum of responses of servers needed



    public ClientLogic(String username, String gridFilePath, String keystorePasswd, int numberOfByzantineClients, int numberOfByzantineServers, int mode) {

        this.username = username;
        this.keystorePasswd = keystorePasswd;
        this.numberOfByzantineClients = numberOfByzantineClients;
        this.numberOfByzantineServers = numberOfByzantineServers;
        serverQuorum = (this.numberOfByzantineServers * 2) + 1;
        byzantineMode = mode;
        populateGrid(gridFilePath);
    }

    public void addServer(String name) {
        serverNames.add(name);
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

    public List<byte[][]> generateLocationReport(int epoch) {
        Coords coords = getCoords(epoch);


        List<byte[][]> reports = new ArrayList<>();

        for (String server : serverNames) {

            byte[][] result = new byte[7][];

            JSONObject message = new JSONObject();
            message.put("username", username);
            message.put("epoch", epoch);
            if(byzantineMode==1) {
                //byzantine user changes its coordinates to prove he is not inside the range
                message.put("x", 200);
                message.put("y", 300);
            } else {
                message.put("x", coords.getX());
                message.put("y", coords.getY());
            }

            JSONObject report = new JSONObject();
            report.put("report_info", message);

            //Generate digital signature
            byte[] reportDigitalSignature = EncryptionLogic.createDigitalSignature(
                    message.toString().getBytes(),
                    EncryptionLogic.getPrivateKey(username, keystorePasswd)
            );

            report.put("report_digital_signature", Base64.getEncoder().encodeToString(reportDigitalSignature));

            //Generate a session Key
            SecretKey sessionKey = EncryptionLogic.generateAESKey();
            byte[] sessionKeyBytes = sessionKey.getEncoded();

            //Encrypt session jey with server public key
            byte[] iv = EncryptionLogic.generateIV();
            byte[] encryptedSessionKey = EncryptionLogic.encryptWithRSA(EncryptionLogic.getPublicKey(server), sessionKeyBytes);
            byte[] encryptedMessage = EncryptionLogic.encryptWithAES(sessionKey, report.toString().getBytes(), iv);
            result[0] = encryptedMessage;

            long timestamp = System.currentTimeMillis();
            long proofOfWork = EncryptionLogic.generateProofOfWork(report.toString() + timestamp);


            String data = report.toString() + timestamp + proofOfWork;

            //Generate digital signature
            byte[] digitalSignature = EncryptionLogic.createDigitalSignature(
                    data.getBytes(),
                    EncryptionLogic.getPrivateKey(username, keystorePasswd)
            );



            result[1] = digitalSignature;
            result[2] = encryptedSessionKey;
            result[3] = iv;
            result[4] = server.getBytes(StandardCharsets.UTF_8);

            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
            buffer.putLong(proofOfWork);
            result[5] = buffer.array();

            ByteBuffer buffer2 = ByteBuffer.allocate(Long.BYTES);
            buffer2.putLong(timestamp);
            result[6] = buffer2.array();

            reports.add(result);
        }

        return reports;
    }

    public List<byte[][]> generateLocationProof(String proverUsername, int epoch, byte[] request, byte[] digitalSignature) throws ProverNotCloseEnoughException, InvalidSignatureException {

        Coords currentUserCoords = grid.get(this.username).get(epoch);
        Coords proverCoords = grid.get(proverUsername).get(epoch);


        if (!EncryptionLogic.verifyDigitalSignature(request, digitalSignature, EncryptionLogic.getPublicKey(proverUsername)))
            throw new InvalidSignatureException();

        System.out.println("Valid request digital signature");

        if(!(this.byzantineMode==4)) {
            //look at the grid to check if prover is nearby
            if (!isClose(currentUserCoords, proverCoords))
                throw new ProverNotCloseEnoughException();
        }

        List<byte[][]> proofs = new ArrayList<>();

        for (String server : serverNames) {

            JSONObject jsonProof = new JSONObject();

            byte[][] result = new byte[5][];

            // Create response message
            if(this.byzantineMode==5)//Simulation of replaying a proof from user1
                jsonProof.put("witnessUsername", "user1");
            else
                jsonProof.put("witnessUsername", this.username);

            jsonProof.put("proverUsername", proverUsername);

            if(this.byzantineMode==2){
                jsonProof.put("epoch", 999);
            } else {
                jsonProof.put("epoch", epoch);
            }

            JSONObject jsonCoords = new JSONObject();

            if(this.byzantineMode==4 || this.byzantineMode==5) {
                jsonCoords.put("x", proverCoords.getX());
                jsonCoords.put("y", proverCoords.getY());
            } else {
                jsonCoords.put("x", currentUserCoords.getX());
                jsonCoords.put("y", currentUserCoords.getY());
            }

            //Generate a session Key
            SecretKey sessionKey = EncryptionLogic.generateAESKey();
            byte[] sessionKeyBytes = sessionKey.getEncoded();

            //Encrypt session jey with server public key
            byte[] iv = EncryptionLogic.generateIV();
            byte[] encryptedSessionKey = EncryptionLogic.encryptWithRSA(EncryptionLogic.getPublicKey(server), sessionKeyBytes);


            byte[] encryptedLocation = EncryptionLogic.encryptWithAES(sessionKey, jsonCoords.toString().getBytes(), iv);
            jsonProof.put("encrypted_location", Base64.getEncoder().encodeToString(encryptedLocation));
            result[0] = jsonProof.toString().getBytes();

            //generate proof digital signature
            if(this.byzantineMode==5)//Simulation of replaying a proof from user1
                result[1] = EncryptionLogic.createDigitalSignature(jsonProof.toString().getBytes(), EncryptionLogic.getPrivateKey("user1", keystorePasswd));
            else
                result[1] = EncryptionLogic.createDigitalSignature(jsonProof.toString().getBytes(), EncryptionLogic.getPrivateKey(this.username, keystorePasswd));

            result[2] = encryptedSessionKey;
            result[3] = iv;
            result[4] = server.getBytes(StandardCharsets.UTF_8);
            proofs.add(result);
        }

        return proofs;

    }


    public List<String> closePeers(int epoch) {
        List<String> peers = new ArrayList<>();

        for (String user : grid.keySet()) {
            if(!user.equals(this.username) && this.byzantineMode==3){ //mode 3 sends proof request to every witness available
                peers.add(user);
            }
            else if (!user.equals(this.username) && isClose(grid.get(this.username).get(epoch), grid.get(user).get(epoch))) {
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


    public List<byte[][]> generateObtainLocationRequestParameters(String username, int epoch, String requestUid) {

        List<byte[][]> requests = new ArrayList<>();

        for (String server : serverNames) {

            byte[][] ret = new byte[7][];
            JSONObject object = new JSONObject();
            JSONObject message = new JSONObject();

            //Generate a session Key
            SecretKey sessionKey = EncryptionLogic.generateAESKey();
            byte[] sessionKeyBytes = sessionKey.getEncoded();

            //Encrypt session jey with server public key
            byte[] encryptedSessionKey = EncryptionLogic.encryptWithRSA(EncryptionLogic.getPublicKey(server), sessionKeyBytes);

            //Pass data to json
            message.put("username", username);
            message.put("epoch", epoch);
            message.put("uid", requestUid);

            object.put("message", message);
            long timestamp = System.currentTimeMillis();
            long proofOfWork = EncryptionLogic.generateProofOfWork(object.toString() + timestamp);

            String data = object.toString() + timestamp + proofOfWork;

            //Generate digital signature
            byte[] digitalSignature = EncryptionLogic.createDigitalSignature(
                    data.getBytes(),
                    EncryptionLogic.getPrivateKey(username, keystorePasswd)
            );

            //Encrypt data with session key
            byte[] iv = EncryptionLogic.generateIV();
            byte[] encryptedData = EncryptionLogic.encryptWithAES(
                    sessionKey,
                    object.toString().getBytes(),
                    iv
            );


            ret[0] = encryptedData;
            ret[1] = digitalSignature;
            ret[2] = encryptedSessionKey;
            ret[3] = iv;
            ret[4] = server.getBytes(StandardCharsets.UTF_8);
            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
            buffer.putLong(proofOfWork);
            ret[5] = buffer.array();

            ByteBuffer buffer2 = ByteBuffer.allocate(Long.BYTES);
            buffer2.putLong(timestamp);
            ret[6] = buffer2.array();

            requests.add(ret);
        }

        return requests;

    }

    public Coords getCoordsFromReply(byte[] sessionKeyBytes, byte[] encryptedResponse, byte[] responseSignature, byte[] responseIv) {

        SecretKey sessionKey = EncryptionLogic.bytesToAESKey(sessionKeyBytes);
        //Decrypt response
        byte[] response = EncryptionLogic.decryptWithAES(sessionKey, encryptedResponse, responseIv);

        //Verify response signature
        if (!EncryptionLogic.verifyDigitalSignature(response, responseSignature, EncryptionLogic.getPublicKey("server")))
            System.err.println("Invalid signature from response");
        else
            System.err.println("Valid signature from response");

        //process response and return coords
        String jsonString = new String(response);
        JSONObject jsonObject = new JSONObject(jsonString);
        JSONObject msg = jsonObject.getJSONObject("message");

        return new Coords(msg.getInt("x"), msg.getInt("y"));
    }

    public byte[][] encryptProof(byte[] proof, String server) {
        byte [][] res = new byte[6][];
        //Generate a session Key
        SecretKey sessionKey = EncryptionLogic.generateAESKey();
        byte[] sessionKeyBytes = sessionKey.getEncoded();

        //Encrypt session jey with server public key
        byte[] iv = EncryptionLogic.generateIV();
        byte[] encryptedSessionKey = EncryptionLogic.encryptWithRSA(EncryptionLogic.getPublicKey(server), sessionKeyBytes);

        byte[] encryptedProof = EncryptionLogic.encryptWithAES(sessionKey, proof, iv);

        long timestamp = System.currentTimeMillis();
        long proofOfWork = EncryptionLogic.generateProofOfWork(new String(proof) + timestamp);


        String data = new String(proof) + timestamp + proofOfWork;

        //Generate digital signature
        byte[] digitalSignature = EncryptionLogic.createDigitalSignature(
                data.getBytes(),
                EncryptionLogic.getPrivateKey(username, keystorePasswd)
        );

        //generate proof digital signature
        res[3] = digitalSignature;

        res[0] = encryptedProof;
        res[1] = encryptedSessionKey;
        res[2] = iv;

        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(proofOfWork);
        res[4] = buffer.array();

        ByteBuffer buffer2 = ByteBuffer.allocate(Long.BYTES);
        buffer2.putLong(timestamp);
        res[5] = buffer2.array();

        return res;
    }

    public byte[] generateDigitalSignature(byte[] message) {
        return EncryptionLogic.createDigitalSignature(message, EncryptionLogic.getPrivateKey(this.username, keystorePasswd));
    }

    public Map<String, Map<Integer, Coords>> getGrid() {
        return grid;
    }

    public List<byte[][]> requestMyProofs(String readId, String username, List<Integer> epochs) {
        List<byte[][]> proofRequests = new ArrayList<>();

        for (String server : serverNames) {

            byte[][] ret = new byte[8][];
            JSONObject object = new JSONObject();
            JSONObject message = new JSONObject();

            //Generate a session Key
            SecretKey sessionKey = EncryptionLogic.generateAESKey();
            byte[] sessionKeyBytes = sessionKey.getEncoded();

            //Encrypt session jey with server public key
            byte[] encryptedSessionKey = EncryptionLogic.encryptWithRSA(EncryptionLogic.getPublicKey(server), sessionKeyBytes);

            //Pass data to json
            message.put("username", username);
            message.put("epochs", epochs);
            message.put("readId", readId);

            object.put("message", message);

            //Encrypt data with session key
            byte[] iv = EncryptionLogic.generateIV();
            byte[] encryptedData = EncryptionLogic.encryptWithAES(
                    sessionKey,
                    object.toString().getBytes(),
                    iv
            );

            long timestamp = System.currentTimeMillis();
            long proofOfWork = EncryptionLogic.generateProofOfWork(object.toString() + timestamp);


            String data = object.toString() + timestamp + proofOfWork;

            //Generate digital signature
            byte[] digitalSignature = EncryptionLogic.createDigitalSignature(
                    data.getBytes(),
                    EncryptionLogic.getPrivateKey(username, keystorePasswd)
            );

            ret[0] = encryptedData;
            ret[1] = digitalSignature;
            ret[2] = encryptedSessionKey;
            ret[3] = iv;
            ret[4] = sessionKeyBytes;
            ret[5] = server.getBytes(StandardCharsets.UTF_8);

            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
            buffer.putLong(proofOfWork);
            ret[6] = buffer.array();

            ByteBuffer buffer2 = ByteBuffer.allocate(Long.BYTES);
            buffer2.putLong(timestamp);
            ret[7] = buffer2.array();

            proofRequests.add(ret);
        }

        return proofRequests;
    }

    public synchronized JSONObject verifyLocationReportResponse(byte[] encryptedMessage, byte[] signature, byte[] encryptedSessionKey, byte[] iv, String serverName, int epoch, String requestUid) {

        byte[] sessionKeyBytes = EncryptionLogic.decryptWithRSA(EncryptionLogic.getPrivateKey(username, keystorePasswd),encryptedSessionKey);

        SecretKey sessionKey = EncryptionLogic.bytesToAESKey(sessionKeyBytes);

        //Decrypt response
        byte[] response = EncryptionLogic.decryptWithAES(sessionKey, encryptedMessage, iv);

        //Verify response signature
        if (!EncryptionLogic.verifyDigitalSignature(response, signature, EncryptionLogic.getPublicKey(serverName))) {
            System.err.println("Invalid signature from response");
            return null;
        }


        //process response and return coords
        String jsonString = new String(response);
        JSONObject jsonObject = new JSONObject(jsonString);
        JSONObject report = jsonObject.getJSONObject("report");
        byte[] reportSignature = Base64.getDecoder().decode(report.getString("report_digital_signature"));

        //verify location report
        if (!isValidReport(report.getJSONObject("report_info"),reportSignature, epoch)) {
            System.err.println("Invalid user report");
            return null;
        }

        String uid = jsonObject.getString("uid");

        //verify if uid corresponds to request uid
        if (!uid.equals(requestUid)) {
            System.err.println("Invalid request id");
            return null;
        }

        JSONArray proofs = jsonObject.getJSONArray("proofs");

        int validProofs = 0;
        boolean isValid = false;

        List<String> proofUsers = new ArrayList<>();

        for (Object proof : proofs) {
            JSONObject p = (JSONObject) proof;

            JSONObject proofJson  = p.getJSONObject("proof");
            byte[] proofSignature = Base64.getDecoder().decode(p.getString("digital_signature"));


            String witnessUsername = proofJson.getString("witnessUsername");
            if (proofUsers.contains(witnessUsername)) {
                System.err.println("Duplicate user proof");
            } else {

                proofUsers.add(witnessUsername);

                if (isValidProof( proofJson, proofSignature, epoch))
                    validProofs++;

                //got the needed proofs
                if (validProofs == numberOfByzantineClients)
                    isValid = true;
            }
        }

        if (isValid)
            return jsonObject;
        else
            return null;

    }

    public boolean isValidReport(JSONObject reportJson, byte[] signature, int epoch) {

        int reportEpoch = reportJson.getInt("epoch");
        String proverUsername = reportJson.getString("username");

        if (!username.equals(proverUsername)) {
            System.err.println("Invalid report prover");
            return false;
        }


        if (reportEpoch != epoch) {
            System.err.println("Invalid report epoch");
            return false;
        }


        //verify witness proof
        return EncryptionLogic.verifyDigitalSignature(reportJson.toString().getBytes(),
                signature, EncryptionLogic.getPublicKey(username));

    }

    public boolean isValidProof(JSONObject proofJson, byte[] signature, int epoch) {

        int proofEpoch = proofJson.getInt("epoch");
        String witnessUsername = proofJson.getString("witnessUsername");
        String proverUsername = proofJson.getString("proverUsername");


        if (proofEpoch != epoch) {
            System.err.println("Invalid proof epoch");
            return false;
        }

        if (witnessUsername.equals(proverUsername) || !proverUsername.equals(username)) {
            System.err.println("Invalid proof user");
            return false;
        }

        //verify witness proof
       return EncryptionLogic.verifyDigitalSignature(proofJson.toString().getBytes(),
                       signature, EncryptionLogic.getPublicKey(witnessUsername));

    }

    public List<byte[][]> generateWritebackMessage(JSONObject jsonObject, int epoch) {

        List<byte[][]> response = new ArrayList<>();

        JSONObject reportJson = jsonObject.getJSONObject("report");

        //report section

        JSONObject report = new JSONObject();
        report.put("report_info", reportJson.getJSONObject("report_info"));
        report.put("report_digital_signature", reportJson.getString("report_digital_signature"));

        //proofs section
        JSONArray proofs = jsonObject.getJSONArray("proofs");

        report.put("proofs", proofs);

        long timestamp = System.currentTimeMillis();
        long proofOfWork = EncryptionLogic.generateProofOfWork(report.toString() + timestamp);

        String data = report.toString() + timestamp + proofOfWork;

        //Generate digital signature
        byte[] digitalSignature = EncryptionLogic.createDigitalSignature(
                data.getBytes(),
                EncryptionLogic.getPrivateKey(username, keystorePasswd)
        );


        for (String server : serverNames) {
            //Generate a session Key
            SecretKey sessionKey = EncryptionLogic.generateAESKey();
            byte[] sessionKeyBytes = sessionKey.getEncoded();

            //Encrypt session key with server public key
            byte[] encryptedSessionKey = EncryptionLogic.encryptWithRSA(EncryptionLogic.getPublicKey(server), sessionKeyBytes);

            //Encrypt data with session key
            byte[] iv = EncryptionLogic.generateIV();
            byte[] encryptedData = EncryptionLogic.encryptWithAES(
                    sessionKey,
                    report.toString().getBytes(),
                    iv
            );


            byte[][] ret = new byte[7][];
            ret[0] = encryptedData;
            ret[1] = digitalSignature;
            ret[2] = encryptedSessionKey;
            ret[3] = iv;
            ret[4] = server.getBytes(StandardCharsets.UTF_8);
            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
            buffer.putLong(proofOfWork);
            ret[5] = buffer.array();

            ByteBuffer buffer2 = ByteBuffer.allocate(Long.BYTES);
            buffer2.putLong(timestamp);
            ret[6] = buffer2.array();


            response.add(ret);
        }



       return response;
    }

    public List<Proof> getMyProofs(String readId, List<Integer> epochs) {
        Map<Integer, Map<String, Proof>> uniqueProofMap = new HashMap<>(); //<epoch, <ProverUsername, Proof>>

        for(JSONObject jsonObject: myProofsResponses.get(readId)){
            JSONObject proofJson = jsonObject.getJSONObject("proof");
            byte[] proofSignature = Base64.getDecoder().decode(jsonObject.getString("digital_signature"));

            String witnessUsername = proofJson.getString("witnessUsername");
            String proverUsername = proofJson.getString("proverUsername");
            //verify if proof witness is the current user
            if(!witnessUsername.equals(username)){
                System.out.println("Wrong proof witness, should be: " + username + " but is: " + witnessUsername);
                continue;
            }

            //verify if proof is signed by the current user
            if(!EncryptionLogic.verifyDigitalSignature(proofJson.toString().getBytes(),
                    proofSignature, EncryptionLogic.getPublicKey(witnessUsername))){
                System.out.println("Wrong proof signature, not signed by the current user");
                continue;
            }

            //verify if epoch is inside the requested epoch range
            int proofEpoch = proofJson.getInt("epoch");
            if(!epochs.contains(proofEpoch)){
                System.out.println("Proof received is not is the requested epoch range");
            }

            //Get x and y encryped with session key
            byte[] witnessIv = Base64.getDecoder().decode(jsonObject.getString("witness_iv"));
            byte[] witnessSessionKeyBytes = Base64.getDecoder().decode(jsonObject.getString("witness_session_key_bytes"));
            byte[] witnessLocationBytes = Base64.getDecoder().decode(proofJson.getString("encrypted_location"));

            SecretKey witnessSessionKey = EncryptionLogic.bytesToAESKey(witnessSessionKeyBytes);
            byte[] witnessLocation = EncryptionLogic.decryptWithAES(witnessSessionKey, witnessLocationBytes, witnessIv);
            JSONObject locationJSON = new JSONObject(new String(Objects.requireNonNull(witnessLocation)));

            //Add to map, to guarantee unique proofs, only at most one proof per epoch and prover
            uniqueProofMap.putIfAbsent(proofEpoch, new HashMap<String, Proof>());
            uniqueProofMap.get(proofEpoch).putIfAbsent(proverUsername, new Proof(proofJson, locationJSON));
        }
        ArrayList<Proof> proofs = new ArrayList<>();
        for(Map<String, Proof> map: uniqueProofMap.values()){
            for(Proof p: map.values())
                proofs.add(p);
        }
        return proofs;
    }
}
