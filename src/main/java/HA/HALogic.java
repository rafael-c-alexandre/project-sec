package HA;

import org.json.JSONArray;
import org.json.JSONObject;
import util.Coords;
import util.EncryptionLogic;

import javax.crypto.SecretKey;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class HALogic {
    private SecretKey sessionKey;
    private byte[] iv;
    private final int f;
    private String keystorePasswd;
    private List<String> serverNames = new ArrayList<>();
    public ConcurrentHashMap<String, CopyOnWriteArrayList<String>> gotReadQuorum = new ConcurrentHashMap<>();
    public ConcurrentHashMap<String, CopyOnWriteArrayList<String>> gotWriteBackQuorum = new ConcurrentHashMap<>();
    public ConcurrentHashMap<String, JSONObject> readRequests = new ConcurrentHashMap<>();
    public final int serverQuorum = 2; //quorum of responses of servers needed

    public HALogic(String keystorePasswd, int numberOfByzantines) {

        this.keystorePasswd = keystorePasswd;
        f = numberOfByzantines;
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

    public List<String> getUsersFromReply(byte[] sessionKeyBytes, byte[] encryptedResponse, byte[] responseSignature, byte[] responseIv) {
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

        JSONArray arr = msg.getJSONArray("users");
        List<String> list = new ArrayList<String>();
        for(int i = 0; i < arr.length(); i++){
            list.add(arr.getString(i));
        }
        return list;
    }

    public synchronized JSONObject verifyLocationReportResponse(byte[] encryptedMessage, byte[] signature, byte[] encryptedSessionKey, byte[] iv, String serverName, int epoch, String requestUid) {

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
        JSONObject msg = jsonObject.getJSONObject("message");

        String uid = msg.getString("uid");

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
                if (validProofs == f)
                    isValid = true;
            }
        }

        if (isValid)
            return jsonObject;
        else
            return null;

    }

    public boolean isValidProof(JSONObject proofJson, byte[] signature, int epoch) {

        int proofEpoch = proofJson.getInt("epoch");
        String witnessUsername = proofJson.getString("witnessUsername");
        String proverUsername = proofJson.getString("proverUsername");


        if (proofEpoch != epoch) {
            System.err.println("Invalid proof epoch");
            return false;
        }

        if (witnessUsername.equals(proverUsername)) {
            System.err.println("Invalid proof user");
            return false;
        }

        //verify witness proof
        return EncryptionLogic.verifyDigitalSignature(proofJson.toString().getBytes(),
                signature, EncryptionLogic.getPublicKey(witnessUsername));

    }

    public List<byte[][]> generateWritebackMessage(JSONObject jsonObject, int epoch, String username) {


        List<byte[][]> response = new ArrayList<>();

        JSONObject reportJson = jsonObject.getJSONObject("message");

        //report section
        int x = reportJson.getInt("x");
        int y = reportJson.getInt("y");

        JSONObject report = new JSONObject();
        report.put("x", x);
        report.put("y", y);
        report.put("epoch", epoch);
        report.put("username", username);

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
}
