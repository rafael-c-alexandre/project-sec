package Server;

import Exceptions.*;
import Server.database.UserReportsRepository;
import com.google.protobuf.ByteString;
import javafx.util.Pair;
import org.json.JSONObject;
import util.Coords;
import util.EncryptionLogic;
import javax.crypto.SecretKey;
import java.security.PublicKey;
import java.sql.Connection;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;


public class ServerLogic {

    private HashMap<String, Pair<SecretKey, byte[]>> sessionKeys = new HashMap<>();
    final int f_line = 0;
    private CopyOnWriteArrayList<UserReport> reportList;
    private  UserReportsRepository reportsRepository;
    private final int responseQuorum = 2;

    public ServerLogic(Connection Connection) {
        reportsRepository = new UserReportsRepository(Connection);
        this.reportList = reportsRepository.getAllUserReports();
        this.reportList.add(new UserReport(1,"user1",new Coords(1,1)));
        this.reportList.add(new UserReport(1,"user2",new Coords(1,1)));
        this.reportList.add(new UserReport(1,"user3",new Coords(1,1)));
        this.reportList.add(new UserReport(2,"user4",new Coords(1,2)));
    }

    public UserReport obtainLocationReport(String username, int epoch) throws NoReportFoundException {
        for (UserReport report : this.reportList) {
            if (report.getUsername().equals(username) && report.getEpoch() == epoch)
                return report;
        }
        throw new NoReportFoundException();
    }


    public byte[][] generateObtainLocationReportResponse(String username, byte[] encryptedData, byte[] signature, boolean isHA) throws NoSuchCoordsException, InvalidSignatureException {

        //Decrypt data
        System.out.println(username);
        byte[] decryptedData = EncryptionLogic.decryptWithAES(sessionKeys.get(username).getKey(), encryptedData, sessionKeys.get(username).getValue());
        String jsonString = new String(decryptedData);
        JSONObject jsonObject = new JSONObject(jsonString);
        JSONObject message = jsonObject.getJSONObject("message");
        String reportUsername = message.getString("username");
        int epoch = message.getInt("epoch");


        //Verify signature

            if (!EncryptionLogic.verifyDigitalSignature(decryptedData, signature, EncryptionLogic.getPublicKey(username))) {
                System.out.println("Invalid signature!");
                throw new InvalidSignatureException();
            } else
                System.out.println("Valid signature!");

        //process request
        Coords coords = null;
        try {
            coords = obtainLocationReport(reportUsername, epoch).getCoords();
        } catch(NoReportFoundException e ) {
            throw new NoSuchCoordsException();
        }

        JSONObject jsonResponse = new JSONObject();
        JSONObject jsonResponseMessage = new JSONObject();

        jsonResponseMessage.put("x", coords.getX());
        jsonResponseMessage.put("y", coords.getY());

        jsonResponse.put("message", jsonResponseMessage);


        //encrypt response
        byte[] encryptedResponse = EncryptionLogic.encryptWithAES(sessionKeys.get(username).getKey(), jsonResponse.toString().getBytes(), sessionKeys.get(username).getValue());


        //generate signature
        System.out.println(jsonResponse);
        byte[] responseSignature = EncryptionLogic.createDigitalSignature(jsonResponse.toString().getBytes(), EncryptionLogic.getPrivateKey("server"));

        byte[][] ret = new byte[2][];
        ret[0] = encryptedResponse;
        ret[1] = responseSignature;


        return ret;
    }

    public List<String> obtainUsersAtLocation(int x, int y, int epoch) {
        return this.reportList.stream()
                .filter(report -> report.getCoords().getY() == y && report.getCoords().getX() == x && report.getEpoch() == epoch)
                .map(report -> report.getUsername())
                .collect(Collectors.toList());
    }

    public void submitReport(String username, ByteString encryptedMessage, ByteString digitalSignature) throws InvalidReportException  {


        SecretKey sessionKey = this.sessionKeys.get(username).getKey();

        //decipher message to get report as JSON
        byte[] decipheredMessage = EncryptionLogic.decryptWithAES(sessionKey, encryptedMessage.toByteArray(), this.sessionKeys.get(username).getValue());
        JSONObject reportJSON = new JSONObject(new String(decipheredMessage));

        //verify message integrity
        if(verifyMessage(decipheredMessage, digitalSignature)) {
            UserReport userReport = new UserReport(reportJSON);

            this.reportList.add(userReport);
            //Add to database
            reportsRepository.submitUserReport(userReport);
        } else {
            //TODO: Custom exception handle
            throw new InvalidReportException();
        }

    }

    public boolean verifyMessage(byte[] decipheredMessage, ByteString digitalSignature) {

        //get username and respective public key
        JSONObject obj = new JSONObject(new String(decipheredMessage));
        String username = obj.getString("username");

        PublicKey userPubKey = EncryptionLogic.getPublicKey(username);

        //verify digital signature
        boolean isValid = EncryptionLogic.verifyDigitalSignature(decipheredMessage, digitalSignature.toByteArray(), userPubKey);

        System.out.println("Message digital signature valid? " + isValid);

        return isValid;

    }

    public void submitProof(String username, ByteString encryptedProof, ByteString signature) throws InvalidProofException, NoReportFoundException {

        //get user session key and iv
        SecretKey sessionKey = sessionKeys.get(username).getKey();
        byte[] iv = sessionKeys.get(username).getValue();

        //convert byte string proof and signature to byte[]
        byte[] encryptedProofBytes = encryptedProof.toByteArray();
        byte[] signatureBytes = signature.toByteArray();

        //decrypt proof
        byte[] decryptedProof = EncryptionLogic.decryptWithAES(sessionKey,encryptedProofBytes,iv);

        //verify proofs integrity
        Proof newProof = verifyProof(decryptedProof, signatureBytes, username);

        UserReport report = obtainLocationReport(newProof.getProverUsername(), newProof.getEpoch());
        report.addProof(newProof);
        reportsRepository.submitProof(newProof);
        if (report.getProofsList().size() == responseQuorum)
            System.out.println("Reached quorum of proofs");
            //TODO do smth
    }

    public boolean validEpoch(String username, int epoch) {
        for (UserReport report : this.reportList) {
            if (report.getUsername().equals(username) && report.getEpoch() == epoch && !report.isClosed())
                return true;
        }
        return false;
    }


    public Proof verifyProof(byte[] proof, byte[] signature, String username) throws InvalidProofException, NoReportFoundException {

        //get server public key
        PublicKey serverPubKey = EncryptionLogic.getPublicKey(username);

        boolean validSignature = EncryptionLogic.verifyDigitalSignature(proof, signature, serverPubKey);

        if (validSignature) {

            JSONObject proofJSON = new JSONObject(new String(proof));
            String proverUser = proofJSON.getString("proverUsername");
            String witnessUser = proofJSON.getString("witnessUsername");

            //byzantine user set the prover username wrongly
            if (!proverUser.equals(username))
                //todo: throw exception
                throw new InvalidProofException();

            int epoch = proofJSON.getInt("epoch");

            //byzantine user set wrong epoch
            if (! validEpoch(proverUser, epoch))
                //todo: throw exception
                throw new InvalidProofException();

            //decrypt witness location
            byte[] witnessLocationBytes = Base64.getDecoder().decode(proofJSON.getString("encrypted_location"));
            SecretKey sessionKey = sessionKeys.get(witnessUser).getKey();
            byte[] iv = sessionKeys.get(witnessUser).getValue();
            byte[] witnessLocation = EncryptionLogic.decryptWithAES(sessionKey, witnessLocationBytes, iv);

            JSONObject locationJSON = new JSONObject(new String(Objects.requireNonNull(witnessLocation)));

            Coords witnessCoords = new Coords(locationJSON.getInt("x"), locationJSON.getInt("y"));

            //byzantine user is not close to prover
            if (!isClose(witnessCoords, obtainLocationReport(proverUser, epoch).getCoords())) {
                //todo: throw custom exception
                throw new InvalidProofException();
            }

            return new Proof(proofJSON, locationJSON);

        } else {
            throw new InvalidProofException();
            //Todo: throw exception
        }

    }



    public byte[][] generateObtainUsersAtLocationReportResponse(String username, byte[] encryptedData, byte[] signature) throws InvalidSignatureException {

        SecretKey sessionKey = sessionKeys.get(username).getKey();
        byte[] iv = sessionKeys.get(username).getValue();

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
            System.out.println("Invalid signature from user ha key");
            throw new InvalidSignatureException();
        } else
            System.out.println("Valid signature from user ha key");



        //process request
        List<String> users = obtainUsersAtLocation(x,y, epoch);

        JSONObject jsonResponse = new JSONObject();
        JSONObject jsonResponseMessage = new JSONObject();

        jsonResponseMessage.put("users", users);

        jsonResponse.put("message", jsonResponseMessage);


        //encrypt response
        byte[] encryptedResponse = EncryptionLogic.encryptWithAES(sessionKey, jsonResponse.toString().getBytes(), iv);


        //generate signature
        System.out.println(jsonResponse);
        byte[] responseSignature = EncryptionLogic.createDigitalSignature(jsonResponse.toString().getBytes(), EncryptionLogic.getPrivateKey("server"));

        byte[][] ret = new byte[2][];
        ret[0] = encryptedResponse;
        ret[1] = responseSignature;

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
            System.out.println("Invalid signature from user " + username + " key");
            return false;
        } else {
            System.out.println("Valid signature from user " + username + " key");
            sessionKeys.put(username, new Pair<>(sessionKey, iv));
            return true;
        }

    }


    public boolean isClose(Coords c1, Coords c2) {

        //let's assume a radius of 5
        int radius = 5;
        return (Math.pow(c2.getX() - c1.getX(), 2)) + (Math.pow(c2.getY() - c1.getY(), 2)) < Math.pow(radius, 2);
    }

}
