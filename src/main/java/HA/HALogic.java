package HA;

import Server.Proof;
import org.json.JSONArray;
import org.json.JSONObject;
import util.Coords;
import util.EncryptionLogic;

import javax.crypto.SecretKey;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class HALogic {
    private SecretKey sessionKey;
    private byte[] iv;
    private final int numberOfByzantineClients;
    private final int numberOfByzantineServers;
    private String keystorePasswd;
    private List<String> serverNames = new ArrayList<>();
    public ConcurrentHashMap<String, CopyOnWriteArrayList<String>> gotReadQuorum = new ConcurrentHashMap<>();
    public ConcurrentHashMap<String, CopyOnWriteArrayList<String>> gotWriteBackQuorum = new ConcurrentHashMap<>();
    public ConcurrentHashMap<String, JSONObject> readRequests = new ConcurrentHashMap<>();

    public ConcurrentHashMap<String, CopyOnWriteArrayList<String>> gotUsersQuorum = new ConcurrentHashMap<>();
    public ConcurrentHashMap<String, CopyOnWriteArrayList<JSONObject>> gotUsersResponses = new ConcurrentHashMap<>(); //<Epoch, <ProverUsername, ProofJSON
    public final int serverQuorum; //quorum of responses of servers needed

    public HALogic(String keystorePasswd, int numberOfByzantineClients, int numberOfByzantineServers)  {

        this.keystorePasswd = keystorePasswd;
        this.numberOfByzantineServers = numberOfByzantineServers;
        this.numberOfByzantineClients = numberOfByzantineClients;
        serverQuorum = (this.numberOfByzantineServers * 2) + 1;
    }


    public void addServer(String name) {
        serverNames.add(name);
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
                EncryptionLogic.getPrivateKey("ha", keystorePasswd)
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
            System.out.println("Invalid signature");
        else
            System.out.println("Valid signature");

        //process response and return coords
        String jsonString = new String(response);
        JSONObject jsonObject = new JSONObject(jsonString);
        JSONObject msg = jsonObject.getJSONObject("message");

        return new Coords(msg.getInt("x"), msg.getInt("y"));
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
                    EncryptionLogic.getPrivateKey("ha", keystorePasswd)
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

    public List<String> getUsersFromReply(String readId, int x, int y, int epoch) {
        ArrayList<String> uniqueUsers = new ArrayList<>();

        for(JSONObject jsonObject: gotUsersResponses.get(readId)){
            //JSONObject proofJson = jsonObject.getJSONObject()

            //Verify Location report
            //process response and return coords
            JSONObject report = jsonObject.getJSONObject("report");
            byte[] reportSignature = Base64.getDecoder().decode(report.getString("report_digital_signature"));

            //verify location report
            JSONObject reportJson = report.getJSONObject("report_info");
            if (!isValidReport(reportJson,reportSignature, epoch, reportJson.getString("username"))) {
                System.err.println("Invalid user report");
                return null;
            }
            if(reportJson.getInt("x") != x || reportJson.getInt("y") != y){
                System.err.println("Invalid coordinates in the report");
                return null;
            }


            //verify if proofs are valid and enough to prove report
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

                    if (isValidProof( proofJson, proofSignature, epoch, proofJson.getString("proverUsername")))
                        validProofs++;

                    //got the needed proofs
                    if (validProofs >= numberOfByzantineClients)
                        isValid = true;
                }
            }

            if(isValid){
                uniqueUsers.add(reportJson.getString("username"));
            }

        }

        return uniqueUsers;
    }

    public synchronized JSONObject verifyLocationReportResponse(byte[] encryptedMessage, byte[] signature, byte[] encryptedSessionKey, byte[] iv, String serverName, int epoch, String requestUid, String username) {

        byte[] sessionKeyBytes = EncryptionLogic.decryptWithRSA(EncryptionLogic.getPrivateKey("ha", keystorePasswd),encryptedSessionKey);

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
        if (!isValidReport(report.getJSONObject("report_info"),reportSignature, epoch, username)) {
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

                if (isValidProof( proofJson, proofSignature, epoch, username))
                    validProofs++;

                //got the needed proofs
                if (validProofs >= numberOfByzantineClients)
                    isValid = true;
            }
        }

        if (isValid)
            return jsonObject;
        else
            return null;

    }

    public boolean isValidReport(JSONObject reportJson, byte[] signature, int epoch, String username) {

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

    public boolean isValidProof(JSONObject proofJson, byte[] signature, int epoch, String username) {

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
                EncryptionLogic.getPrivateKey("ha", keystorePasswd)
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

    public List<byte[][]> generateObtainUsersAtLocationRequestParameters(String readId, int x, int y, int epoch) {
        List<byte[][]> usersRequests = new ArrayList<>();

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
            message.put("x", x);
            message.put("y", y);
            message.put("epoch", epoch);
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
                    EncryptionLogic.getPrivateKey("ha", keystorePasswd)
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

            usersRequests.add(ret);
        }
        return usersRequests;
    }
}
