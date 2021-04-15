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
    private String keystorePasswd;

    public ServerLogic(Connection Connection, String f, String keystorePasswd) {
        reportsRepository = new UserReportsRepository(Connection);
        this.reportList = reportsRepository.getAllUserReports();
        this.responseQuorum = Integer.parseInt(f);
        this.keystorePasswd = keystorePasswd;
    }

    public UserReport obtainLocationReport(String username, int epoch) throws NoReportFoundException {
        for (UserReport report : this.reportList) {
            if (report.getUsername().equals(username) && report.getEpoch() == epoch)
                return report;
        }
        throw new NoReportFoundException(username, epoch);
    }

    public UserReport obtainClosedLocationReport(String username, int epoch) throws NoReportFoundException {
        for (UserReport report : this.reportList) {
            if (report.getUsername().equals(username) && report.getEpoch() == epoch && report.isClosed())
                return report;
        }
        throw new NoReportFoundException(username, epoch);
    }


    public  byte[][] generateObtainLocationReportResponse(byte[] encryptedData, byte[] encryptedSessionKey, byte[] signature, byte[] iv, boolean isHA) throws NoSuchCoordsException, InvalidSignatureException, NoReportFoundException {
        //Decrypt session key
        byte[] sessionKeyBytes = EncryptionLogic.decryptWithRSA(EncryptionLogic.getPrivateKey("server", keystorePasswd), encryptedSessionKey);
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
        Coords coords = obtainClosedLocationReport(username, epoch).getCoords();

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
        byte[] responseSignature = EncryptionLogic.createDigitalSignature(jsonResponse.toString().getBytes(), EncryptionLogic.getPrivateKey("server", keystorePasswd));

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

    public synchronized void submitReport(byte[] encryptedSessionKey, byte[] encryptedMessage, byte[] digitalSignature, byte[] iv) throws InvalidReportException, ReportAlreadyExistsException, InvalidSignatureException {

        //Decrypt session key
        byte[] sessionKeyBytes = EncryptionLogic.decryptWithRSA(EncryptionLogic.getPrivateKey("server", keystorePasswd), encryptedSessionKey);
        SecretKey sessionKey = EncryptionLogic.bytesToAESKey(sessionKeyBytes);

        //decipher message to get report as JSON
        byte[] decipheredMessage = EncryptionLogic.decryptWithAES(sessionKey, encryptedMessage, iv);
        JSONObject reportJSON = new JSONObject(new String(decipheredMessage));

        //verify message integrity
        if(verifyMessage(decipheredMessage, digitalSignature)) {
            UserReport userReport = new UserReport(reportJSON, digitalSignature);

            //try to replace report
            if(!replaceReport(userReport)) this.reportList.add(userReport);

            System.out.println("Report from " + userReport.getUsername() + " from epoch " + userReport.getEpoch() + " report verified");
            //Add to database
            reportsRepository.submitUserReport(userReport, digitalSignature);
        } else {
            throw new InvalidReportException();
        }
    }

    // when a user submits a new report from a epoch that has not a confirmed report
    public synchronized boolean replaceReport(UserReport newReport) {

        for (int i = 0; i < reportList.size(); i++) {
            if (reportList.get(i).getUsername().equals(newReport.getUsername())
                    && reportList.get(i).getEpoch() == newReport.getEpoch()
                    && !reportList.get(i).isClosed()) {
                reportList.set(i, newReport);
                System.out.println("Replaced in memory!");
                reportsRepository.replaceReport(newReport.getUsername(),newReport.getEpoch());
                return true;
            }
        }
        return false;
    }

    public boolean verifyMessage(byte[] decipheredMessage, byte[] digitalSignature) throws ReportAlreadyExistsException, InvalidSignatureException {


        //get username and respective public key
        JSONObject obj = new JSONObject(new String(decipheredMessage));
        String username = obj.getString("username");
        int epoch = obj.getInt("epoch");

        PublicKey userPubKey = EncryptionLogic.getPublicKey(username);

        //verify digital signature
        boolean isValid = EncryptionLogic.verifyDigitalSignature(decipheredMessage, digitalSignature, userPubKey);

        System.out.println("Prover: " + username + " epoch: " +  epoch + " + Message digital signature valid? " + isValid);
        if (isValid) {
            try {
                obtainClosedLocationReport(username, epoch);
                throw new ReportAlreadyExistsException(username, epoch);
            } catch (NoReportFoundException e) {
                //return true if there is no report for that epoch
                return true;
            }
        } else
            throw new InvalidSignatureException();


    }

    public synchronized boolean submitProof(byte[] witnessEncryptedSessionKey, byte[] witnessIv, byte[] encryptedSessionKey, byte[] encryptedProof, byte[] signature, byte[] iv) throws InvalidProofException, NoReportFoundException, AlreadyConfirmedReportException {

        //Decrypt session key
        byte[] sessionKeyBytes = EncryptionLogic.decryptWithRSA(EncryptionLogic.getPrivateKey("server", keystorePasswd), encryptedSessionKey);
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
            System.out.println("Reached quorum of proofs for report of user " + report.getUsername() + " for epoch " + report.getEpoch());
            report.setClosed(true);
            reportsRepository.closeUserReport(report);
            return true;
        }
        return false;
    }

    public boolean validEpoch(String username, int epoch) {
        for (UserReport report : this.reportList) {
            if (report.getUsername().equals(username) && report.getEpoch() == epoch && !report.isClosed())
                return true;
        }
        return false;
    }

    public boolean isDuplicateProof(UserReport report, String witness) {

        for (Proof proof : report.getProofsList()) {
            return proof.getWitnessUsername().equals(witness);
        }
        return false;
    }


    public Proof verifyProof(byte[] witnessEncryptedSessionKey,byte[] proof, byte[] signature, byte[] witnessIv) throws InvalidProofException, NoReportFoundException, AlreadyConfirmedReportException {

        JSONObject messageJSON = new JSONObject(new String(proof));
        JSONObject proofJSON = messageJSON.getJSONObject("proof");
        String proverUser = proofJSON.getString("proverUsername");
        String witnessUser = proofJSON.getString("witnessUsername");

        byte[] witnessSignature = Base64.getDecoder().decode(messageJSON.getString("digital_signature"));

        //get prover public key
        PublicKey proverPubKey = EncryptionLogic.getPublicKey(proverUser);

        //get witness public key
        PublicKey witnessPubKey = EncryptionLogic.getPublicKey(witnessUser);

        //verify message
        boolean validSignatureMessage = EncryptionLogic.verifyDigitalSignature(proof, signature, proverPubKey);

        //verify witness proof
        boolean validSignatureProof = EncryptionLogic.verifyDigitalSignature(proofJSON.toString().getBytes(), witnessSignature, witnessPubKey);

        if(!validSignatureMessage || !validSignatureProof)
            throw new InvalidProofException();

        int epoch = proofJSON.getInt("epoch");

        UserReport report = obtainLocationReport(proverUser, epoch);
        System.out.println("Proof signature valid from witness " + witnessUser + " to prover " + proverUser);

        //prover cannot be a witness
        if (report.getUsername().equals(witnessUser))
            throw new InvalidProofException();

        //byzantine user set wrong epoch
        if (! validEpoch(proverUser, epoch))
            throw new InvalidProofException();

        //check if witness has already submitted a proof for this report
        if (isDuplicateProof(report, witnessUser))
            throw new InvalidProofException();

        //decrypt witness location
        byte[] witnessLocationBytes = Base64.getDecoder().decode(proofJSON.getString("encrypted_location"));

        //Decrypt session key
        byte[] sessionKeyBytes = EncryptionLogic.decryptWithRSA(EncryptionLogic.getPrivateKey("server", keystorePasswd), witnessEncryptedSessionKey);
        SecretKey witnessSessionKey = EncryptionLogic.bytesToAESKey(sessionKeyBytes);
        byte[] witnessLocation = EncryptionLogic.decryptWithAES(witnessSessionKey, witnessLocationBytes, witnessIv);

        JSONObject locationJSON = new JSONObject(new String(Objects.requireNonNull(witnessLocation)));

        String username = report.getUsername();

        //check if report is not closed yet
        if (report.isClosed())
            throw new AlreadyConfirmedReportException(username, report.getEpoch());

        // users are not close to
        Coords witnessCoords = new Coords(locationJSON.getInt("x"), locationJSON.getInt("y"));
        if (!isClose(witnessCoords, report.getCoords())) {
            throw new InvalidProofException();
        }

        return new Proof(proofJSON, locationJSON,signature);

    }



    public byte[][] generateObtainUsersAtLocationReportResponse(byte[] encryptedData, byte[] encryptedSessionKey, byte[] signature, byte[] iv) throws InvalidSignatureException {
        //Decrypt session key
        byte[] sessionKeyBytes = EncryptionLogic.decryptWithRSA(EncryptionLogic.getPrivateKey("server", keystorePasswd), encryptedSessionKey);
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
        byte[] responseSignature = EncryptionLogic.createDigitalSignature(jsonResponse.toString().getBytes(), EncryptionLogic.getPrivateKey("server", keystorePasswd));

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
