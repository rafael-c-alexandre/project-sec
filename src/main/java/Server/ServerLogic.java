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

    private CopyOnWriteArrayList<UserReport> reportList;
    private  UserReportsRepository reportsRepository;
    private final int responseQuorum;

    public ServerLogic(Connection Connection, String f) {
        reportsRepository = new UserReportsRepository(Connection);
        this.reportList = reportsRepository.getAllUserReports();
        this.reportList.add(new UserReport(1,"user1",new Coords(1,1)));
        this.reportList.add(new UserReport(1,"user2",new Coords(1,1)));
        this.reportList.add(new UserReport(1,"user3",new Coords(1,1)));

        this.responseQuorum = Integer.parseInt(f) + 1;
    }

    public UserReport obtainLocationReport(String username, int epoch) throws NoReportFoundException {
        for (UserReport report : this.reportList) {
            if (report.getUsername().equals(username) && report.getEpoch() == epoch)
                return report;
        }
        throw new NoReportFoundException();
    }


    public byte[][] generateObtainLocationReportResponse(byte[] encryptedData, byte[] encryptedSessionKey, byte[] signature, byte[] iv, boolean isHA) throws NoSuchCoordsException, InvalidSignatureException, NoReportFoundException {
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
        Coords coords = obtainLocationReport(username, epoch).getCoords();

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

    public void submitReport(byte[] encryptedSessionKey, byte[] encryptedMessage, byte[] digitalSignature, byte[] iv) throws InvalidReportException, ReportAlreadyExistsException, InvalidSignatureException {

        //Decrypt session key
        byte[] sessionKeyBytes = EncryptionLogic.decryptWithRSA(EncryptionLogic.getPrivateKey("server"), encryptedSessionKey);
        SecretKey sessionKey = EncryptionLogic.bytesToAESKey(sessionKeyBytes);

        //decipher message to get report as JSON
        byte[] decipheredMessage = EncryptionLogic.decryptWithAES(sessionKey, encryptedMessage, iv);
        JSONObject reportJSON = new JSONObject(new String(decipheredMessage));

        //verify message integrity
        if(verifyMessage(decipheredMessage, digitalSignature)) {
            UserReport userReport = new UserReport(reportJSON);

            System.out.println("Report from " + userReport.getUsername() + " from epoch " + userReport.getEpoch() + " report verified");
            this.reportList.add(userReport);
            //Add to database
            reportsRepository.submitUserReport(userReport);
        } else {
            //TODO: Custom exception handle
            throw new InvalidReportException();
        }

    }

    public boolean verifyMessage(byte[] decipheredMessage, byte[] digitalSignature) throws ReportAlreadyExistsException, InvalidSignatureException {


        //get username and respective public key
        JSONObject obj = new JSONObject(new String(decipheredMessage));
        String username = obj.getString("username");
        int epoch = obj.getInt("epoch");

        PublicKey userPubKey = EncryptionLogic.getPublicKey(username);

        //verify digital signature
        boolean isValid = EncryptionLogic.verifyDigitalSignature(decipheredMessage, digitalSignature, userPubKey);

        System.out.println("Message digital signature valid? " + isValid);
        if (isValid) {

            try {
                obtainLocationReport(username, epoch);
                throw new ReportAlreadyExistsException();
            } catch (NoReportFoundException e) {
                //return true if there is no report for that epoch
                return true;
            }
        } else
            throw new InvalidSignatureException();


    }

    public void submitProof(byte[] witnessEncryptedSessionKey, byte[] witnessIv, byte[] encryptedSessionKey, byte[] encryptedProof, byte[] signature, byte[] iv) throws InvalidProofException, NoReportFoundException, AlreadyConfirmedReportException {

        //Decrypt session key
        byte[] sessionKeyBytes = EncryptionLogic.decryptWithRSA(EncryptionLogic.getPrivateKey("server"), encryptedSessionKey);
        SecretKey sessionKey = EncryptionLogic.bytesToAESKey(sessionKeyBytes);

        //decrypt proof
        byte[] decryptedProof = EncryptionLogic.decryptWithAES(sessionKey,encryptedProof,iv);

        //verify proofs integrity
        Proof newProof = verifyProof(witnessEncryptedSessionKey,decryptedProof, signature,witnessIv);

        UserReport report = obtainLocationReport(newProof.getProverUsername(), newProof.getEpoch());
        System.out.println("Proof from " + newProof.getWitnessUsername() + " to " + newProof.getProverUsername() + " report verified");
        report.addProof(newProof);
        reportsRepository.submitProof(newProof);
        if (report.getProofsList().size() == responseQuorum) {
            System.out.println("Reached quorum of proofs");
            report.setClosed(true);
            reportsRepository.closeUserReport(report);
        }
    }

    public boolean validEpoch(String username, int epoch) {
        for (UserReport report : this.reportList) {
            if (report.getUsername().equals(username) && report.getEpoch() == epoch && !report.isClosed())
                return true;
        }
        return false;
    }


    public Proof verifyProof(byte[] witnessEncryptedSessionKey,byte[] proof, byte[] signature, byte[] witnessIv) throws InvalidProofException, NoReportFoundException, AlreadyConfirmedReportException {
        JSONObject proofJSON = new JSONObject(new String(proof));
        String proverUser = proofJSON.getString("proverUsername");
        String witnessUser = proofJSON.getString("witnessUsername");

        //get server public key
        PublicKey proverPubKey = EncryptionLogic.getPublicKey(proverUser);

        boolean validSignature = EncryptionLogic.verifyDigitalSignature(proof, signature, proverPubKey);

        if(!validSignature)
            throw new InvalidProofException();
        int epoch = proofJSON.getInt("epoch");
        System.out.println("Proof signature valid from witness " + witnessUser + "to prover " + proverUser);

        //byzantine user set wrong epoch
        if (! validEpoch(proverUser, epoch))
            throw new InvalidProofException();

        //decrypt witness location
        byte[] witnessLocationBytes = Base64.getDecoder().decode(proofJSON.getString("encrypted_location"));

        //Decrypt session key
        byte[] sessionKeyBytes = EncryptionLogic.decryptWithRSA(EncryptionLogic.getPrivateKey("server"), witnessEncryptedSessionKey);
        SecretKey witnessSessionKey = EncryptionLogic.bytesToAESKey(sessionKeyBytes);
        byte[] witnessLocation = EncryptionLogic.decryptWithAES(witnessSessionKey, witnessLocationBytes, witnessIv);

        JSONObject locationJSON = new JSONObject(new String(Objects.requireNonNull(witnessLocation)));

        UserReport report = obtainLocationReport(proverUser, epoch);
        String username = report.getUsername();

        if (report.isClosed())
            throw new AlreadyConfirmedReportException(username, report.getEpoch());

        //byzantine user is not close to prover
        Coords witnessCoords = new Coords(locationJSON.getInt("x"), locationJSON.getInt("y"));
        if (!isClose(witnessCoords, report.getCoords())) {
            throw new InvalidProofException();
        }

        return new Proof(proofJSON, locationJSON,signature);

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




    public boolean isClose(Coords c1, Coords c2) {

        //let's assume a radius of 5
        int radius = 5;
        return (Math.pow(c2.getX() - c1.getX(), 2)) + (Math.pow(c2.getY() - c1.getY(), 2)) < Math.pow(radius, 2);
    }

}
