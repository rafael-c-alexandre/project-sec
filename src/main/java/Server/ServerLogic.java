package Server;

import Exceptions.InvalidNumberOfProofsException;
import Exceptions.InvalidSignatureException;
import Exceptions.NoSuchCoordsException;
import Server.database.UserReportsRepository;
import com.google.protobuf.ByteString;
import javafx.util.Pair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import util.Coords;
import util.EncryptionLogic;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.sql.Connection;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;


public class ServerLogic {

    private HashMap<String, Pair<SecretKey, byte[]>> sessionKeys = new HashMap<>();
    final int f_line = 0;
    private final CopyOnWriteArrayList<UserReport> reportList;
    private final UserReportsRepository reportsRepository;

    public ServerLogic(Connection Connection) {
        reportsRepository = new UserReportsRepository(Connection);
        this.reportList = reportsRepository.getAllUserReports();
        this.reportList.add(new UserReport(1,"user1",new Coords(1,1)));
        this.reportList.add(new UserReport(1,"user2",new Coords(1,1)));
        this.reportList.add(new UserReport(1,"user3",new Coords(1,1)));
        this.reportList.add(new UserReport(2,"user4",new Coords(1,2)));
    }

    public Coords obtainLocationReport(String username, int epoch) throws NoSuchCoordsException {
        for (UserReport report : this.reportList) {
            if (report.getUsername().equals(username) && report.getEpoch() == epoch)
                return report.getCoords();
        }
        throw new NoSuchCoordsException();
    }

    public byte[][] generateObtainLocationReportResponse(byte[] encryptedData, byte[] encryptedSessionKey, byte[] signature, byte[] iv, boolean isHA) throws NoSuchCoordsException, InvalidSignatureException {
        //Decrypt session key
        byte[] sessionKeyBytes = EncryptionLogic.decryptWithRSA(EncryptionLogic.getPrivateKey("server"), encryptedSessionKey);
        SecretKey sessionKey = EncryptionLogic.bytesToAESKey(sessionKeyBytes);


        //Decrypt data
        byte[] decryptedData = EncryptionLogic.decryptWithAES(sessionKey, encryptedData, iv);
        String jsonString = new String(decryptedData);
        JSONObject jsonObject = new JSONObject(jsonString);
        JSONObject message = jsonObject.getJSONObject("message");
        String username = message.getString("username");
        int epoch = message.getInt("epoch");


        //Verify signature
        if (isHA) {
            if (!EncryptionLogic.verifyDigitalSignature(decryptedData, signature, EncryptionLogic.getPublicKey("ha"))) {
                System.out.println("Invalid signature!");
                throw new InvalidSignatureException();
            } else
                System.out.println("Valid signature!");
        } else {
            if (!EncryptionLogic.verifyDigitalSignature(decryptedData, signature, EncryptionLogic.getPublicKey(username))) {
                System.out.println("Invalid signature!");
                throw new InvalidSignatureException();
            } else
                System.out.println("Valid signature!");
        }


        //process request
        Coords coords = obtainLocationReport(username, epoch);

        JSONObject jsonResponse = new JSONObject();
        JSONObject jsonResponseMessage = new JSONObject();

        jsonResponseMessage.put("x", coords.getX());
        jsonResponseMessage.put("y", coords.getY());

        jsonResponse.put("message", jsonResponseMessage);


        //encrypt response
        byte[] responseIv = EncryptionLogic.generateIV();
        byte[] encryptedResponse = EncryptionLogic.encryptWithAES(sessionKey, jsonResponse.toString().getBytes(), responseIv);


        //generate signature
        System.out.println(jsonResponse);
        byte[] responseSignature = EncryptionLogic.createDigitalSignature(jsonResponse.toString().getBytes(), EncryptionLogic.getPrivateKey("server"));

        byte[][] ret = new byte[3][];
        ret[0] = encryptedResponse;
        ret[1] = responseSignature;
        ret[2] = responseIv;

        return ret;
    }

    public List<String> obtainUsersAtLocation(int x, int y, int epoch) {
        return this.reportList.stream()
                .filter(report -> report.getCoords().getY() == y && report.getCoords().getX() == x && report.getEpoch() == epoch)
                .map(report -> report.getUsername())
                .collect(Collectors.toList());
    }

    public void submitReport(ByteString encryptedMessage, ByteString encryptedSessionKey, ByteString digitalSignature, ByteString iv) throws InvalidNumberOfProofsException {

        PrivateKey serverPrivKey = EncryptionLogic.getPrivateKey("server");
        //decipher session key
        byte[] sessionKeyBytes = EncryptionLogic.decryptWithRSA(serverPrivKey, encryptedSessionKey.toByteArray());
        SecretKey sessionKey = null;
        if (sessionKeyBytes != null) {
            sessionKey = new SecretKeySpec(sessionKeyBytes, 0, sessionKeyBytes.length, "AES");
        }

        //decipher message to get report as JSON
        byte[] decipheredMessage = EncryptionLogic.decryptWithAES(sessionKey, encryptedMessage.toByteArray(), iv.toByteArray());
        JSONObject reportJSON = new JSONObject(new String(decipheredMessage));

        //verify message integrity
        verifyMessage(decipheredMessage, digitalSignature);

        //verify proofs integrity and throw exception if not enough proofs (2f' + 1)
        boolean validProofs = verifyProofs(reportJSON);
        if (!validProofs) {
            throw new InvalidNumberOfProofsException();
        }

        //this.reportList.add(new UserReport());

        //Add to database
        UserReport userReport = new UserReport(reportJSON);
        reportsRepository.submitUserReport(userReport);
    }

    public void verifyMessage(byte[] decipheredMessage, ByteString digitalSignature) {

        //get username and respective public key
        JSONObject obj = new JSONObject(new String(decipheredMessage));
        String username = obj.getString("username");

        PublicKey userPubKey = EncryptionLogic.getPublicKey(username);

        //verify digital signature
        //TODO
        System.out.println("Message digital signature valid? " + EncryptionLogic.verifyDigitalSignature(decipheredMessage, digitalSignature.toByteArray(), userPubKey));

    }


    public boolean verifyProofs(JSONObject reportJSON) {

        try {
            //get proofs array
            JSONArray proofs = (JSONArray) reportJSON.get("proofs");
            if (proofs.length() < (2 * f_line + 1)) {
                System.out.println("Not enough proofs: only " + proofs.length() + " submitted");
                return false;
            }

            String proverUsername = reportJSON.getString("username");
            int proverEpoch = reportJSON.getInt("epoch");

            int numberOfValidProofs = 0;
            for (int i = 0; i < proofs.length(); i++) {
                JSONObject proof = proofs.getJSONObject(i);
                //get message to verify
                byte[] message = Base64.getDecoder().decode(proof.getString("message"));
                //get digital signature of the proof
                byte[] digitalSignature = Base64.getDecoder().decode(proof.getString("digital_signature"));

                //get JSON object to retrieve username
                JSONObject messageJSON = new JSONObject(new String(message));
                String proofProverUsername = messageJSON.getString("proverUsername");
                String proofWitnessUsername = messageJSON.getString("witnessUsername");
                int epoch = messageJSON.getInt("epoch");

                //get user public key
                PublicKey userPubKey = EncryptionLogic.getPublicKey(proofWitnessUsername);

                //verify digital signature validity
                boolean valid = EncryptionLogic.verifyDigitalSignature(message, digitalSignature, userPubKey);
                System.out.println(proverUsername + "  " + proofProverUsername + "  " + proverEpoch + "  " + epoch);

                //Verify if this proof is really for this prover and epoch
                if (valid && proverUsername.equals(proofProverUsername) && proverEpoch == epoch) {
                    numberOfValidProofs++;
                }
                System.out.println("Proof digital signature from " + messageJSON.getString("witnessUsername") + " valid? " + valid);

            }
            return numberOfValidProofs > (2 * f_line + 1);

        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }

    }

    public byte[][] generateObtainUsersAtLocationReportResponse(byte[] encryptedData, byte[] encryptedSessionKey, byte[] signature, byte[] iv) throws InvalidSignatureException {
        //Decrypt session key
        byte[] sessionKeyBytes = EncryptionLogic.decryptWithRSA(EncryptionLogic.getPrivateKey("server"), encryptedSessionKey);
        SecretKey sessionKey = EncryptionLogic.bytesToAESKey(sessionKeyBytes);


        //Decrypt data
        byte[] decryptedData = EncryptionLogic.decryptWithAES(sessionKey, encryptedData, iv);
        String jsonString = new String(decryptedData);
        JSONObject jsonObject = new JSONObject(jsonString);
        JSONObject message = jsonObject.getJSONObject("message");
        int epoch = message.getInt("epoch");
        int x = message.getInt("x");
        int y = message.getInt("y");


        //Verify signature

        if (!EncryptionLogic.verifyDigitalSignature(decryptedData, signature, EncryptionLogic.getPublicKey("ha"))) {
            System.out.println("Invalid signature!");
            throw new InvalidSignatureException();
        } else
            System.out.println("Valid signature!");



        //process request
        List<String> users = obtainUsersAtLocation(x,y, epoch);

        JSONObject jsonResponse = new JSONObject();
        JSONObject jsonResponseMessage = new JSONObject();

        jsonResponseMessage.put("users", users);

        jsonResponse.put("message", jsonResponseMessage);


        //encrypt response
        byte[] responseIv = EncryptionLogic.generateIV();
        byte[] encryptedResponse = EncryptionLogic.encryptWithAES(sessionKey, jsonResponse.toString().getBytes(), responseIv);


        //generate signature
        System.out.println(jsonResponse);
        byte[] responseSignature = EncryptionLogic.createDigitalSignature(jsonResponse.toString().getBytes(), EncryptionLogic.getPrivateKey("server"));

        byte[][] ret = new byte[3][];
        ret[0] = encryptedResponse;
        ret[1] = responseSignature;
        ret[2] = responseIv;

        return ret;
    }

    public boolean handshake( byte[] encryptedUsernameSessionKeyJSON, byte[] signature,  byte[] iv) {
        String jsonString = new String(encryptedUsernameSessionKeyJSON);
        JSONObject message = new JSONObject(jsonString);
        byte[] encryptedUsername = Base64.getDecoder().decode(message.getString("encryptedUsername"));
        byte[] encryptedSessionKey = Base64.getDecoder().decode(message.getString("encryptedSessionKey"));

        //Decrypt session key
        byte[] sessionKeyBytes = EncryptionLogic.decryptWithRSA(EncryptionLogic.getPrivateKey("server"), encryptedSessionKey);
        SecretKey sessionKey = EncryptionLogic.bytesToAESKey(sessionKeyBytes);

        //Decrypt username
        byte[] decryptedData = EncryptionLogic.decryptWithAES(sessionKey, encryptedUsername, iv);
        assert decryptedData != null;
        String username = new String(decryptedData);

        //Verify signature
        //TODO invalid signature response, throw error
        if (!EncryptionLogic.verifyDigitalSignature(jsonString.getBytes(), signature, EncryptionLogic.getPublicKey(username))){
            System.out.println("Invalid signature!");
            return false;
        } else {
            System.out.println("Valid signature!");
            sessionKeys.put(username, new Pair<>(sessionKey, iv));
            return true;
        }

    }

}
