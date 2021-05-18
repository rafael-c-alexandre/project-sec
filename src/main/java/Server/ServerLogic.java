package Server;

import Exceptions.*;
import Server.database.UserReportsRepository;
import com.google.protobuf.ByteString;
import javafx.util.Pair;
import org.json.JSONArray;
import org.json.JSONObject;
import util.Coords;
import util.EncryptionLogic;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class ServerLogic {

    private CopyOnWriteArrayList<UserReport> reportList;
    private  UserReportsRepository reportsRepository;
    private final int responseQuorum;
    private String keystorePasswd;
    private String serverName;

    public ServerLogic(Connection Connection, String f, String keystorePasswd, String serverName) {
        reportsRepository = new UserReportsRepository(Connection);
        this.reportList = reportsRepository.getAllUserReports();
        this.responseQuorum = Integer.parseInt(f);
        this.keystorePasswd = keystorePasswd;
        this.serverName = serverName;
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


    public  byte[][] generateObtainLocationReportResponseHA(byte[] encryptedData, byte[] encryptedSessionKey, byte[] signature, byte[] iv, long proofOfWork, long timestamp) throws InvalidSignatureException, NoReportFoundException {

        //Decrypt session key
        byte[] sessionKeyBytes = EncryptionLogic.decryptWithRSA(EncryptionLogic.getPrivateKey(serverName, keystorePasswd), encryptedSessionKey);
        SecretKey sessionKey = EncryptionLogic.bytesToAESKey(sessionKeyBytes);


        //Decrypt data
        byte[] decryptedData = EncryptionLogic.decryptWithAES(sessionKey, encryptedData, iv);
        String jsonString = new String(decryptedData);
        JSONObject jsonObject = new JSONObject(jsonString);
        JSONObject message = jsonObject.getJSONObject("message");
        String username = message.getString("username");
        int epoch = message.getInt("epoch");
        String requestUid = message.getString("uid");

        String data = jsonString + timestamp + proofOfWork;

        //Verify signature
        if (!EncryptionLogic.verifyDigitalSignature(data.getBytes(), signature, EncryptionLogic.getPublicKey("ha"))) {
            System.out.println("Invalid signature for HA request");
            throw new InvalidSignatureException();
        } else
            System.out.println("Valid signature for HA request!");


        //process request
        UserReport report = obtainClosedLocationReport(username, epoch);

        JSONObject jsonResponse = new JSONObject();
        
        jsonResponse.put("report",report.getReportJSON());
        jsonResponse.put("uid", requestUid);


        //add proofs to response
        JSONArray proofs = new JSONArray();

        for (int i = 0; i < report.getProofsList().size(); i++) {
            proofs.put(report.getProofsList().get(i).getProofJSON());
        }

        //add array to json response
        jsonResponse.put("proofs", proofs);


        //encrypt response
        byte[] responseIv = EncryptionLogic.generateIV();
        byte[] encryptedResponse = EncryptionLogic.encryptWithAES(sessionKey, jsonResponse.toString().getBytes(), responseIv);


        //generate signature
        byte[] responseSignature = EncryptionLogic.createDigitalSignature(jsonResponse.toString().getBytes(), EncryptionLogic.getPrivateKey(serverName, keystorePasswd));

        byte[][] ret = new byte[4][];
        ret[0] = encryptedResponse;
        ret[1] = responseSignature;
        ret[2] = responseIv;
        ret[3] = EncryptionLogic.encryptWithRSA(EncryptionLogic.getPublicKey("ha"), sessionKeyBytes);

        return ret;
    }

    public  byte[][] generateObtainLocationReportResponse(byte[] encryptedData, byte[] encryptedSessionKey, byte[] signature, byte[] iv, long timestamp, long proofOfWork) throws  InvalidSignatureException, NoReportFoundException, InvalidFreshnessToken, InvalidProofOfWorkException {

        //max skew assumed: 30s
        if (timestamp > System.currentTimeMillis() + 30000 || timestamp < System.currentTimeMillis() - 30000  ) {
            throw new InvalidFreshnessToken();
        }
        System.out.println("Valid freshness token");

        //Decrypt session key
        byte[] sessionKeyBytes = EncryptionLogic.decryptWithRSA(EncryptionLogic.getPrivateKey(serverName, keystorePasswd), encryptedSessionKey);
        SecretKey sessionKey = EncryptionLogic.bytesToAESKey(sessionKeyBytes);


        //Decrypt data
        byte[] decryptedData = EncryptionLogic.decryptWithAES(sessionKey, encryptedData, iv);
        String jsonString = new String(decryptedData);
        JSONObject jsonObject = new JSONObject(jsonString);
        JSONObject message = jsonObject.getJSONObject("message");
        String username = message.getString("username");
        int epoch = message.getInt("epoch");
        String requestUid = message.getString("uid");

        String data = jsonString + timestamp + proofOfWork;


        //Verify proof of work
        if(!EncryptionLogic.verifyProofOfWork(proofOfWork,jsonString + timestamp))
            throw new InvalidProofOfWorkException();



        if (!EncryptionLogic.verifyDigitalSignature(data.getBytes(), signature, EncryptionLogic.getPublicKey(username))) {
            System.out.println("Invalid signature for client" + username + "!");
            throw new InvalidSignatureException();
        } else
            System.out.println("Valid signature for client" + username + "!");

        //process request
        UserReport report = obtainClosedLocationReport(username, epoch);

        JSONObject jsonResponse = new JSONObject();

        jsonResponse.put("report",report.getReportJSON());
        jsonResponse.put("uid", requestUid);

        //add proofs to response
        JSONArray proofs = new JSONArray();

        for (int i = 0; i < report.getProofsList().size(); i++) {
            proofs.put(report.getProofsList().get(i).getProofJSON());
        }

        //add array to json response
        jsonResponse.put("proofs", proofs);

        //encrypt response
        byte[] responseIv = EncryptionLogic.generateIV();
        byte[] encryptedResponse = EncryptionLogic.encryptWithAES(sessionKey, jsonResponse.toString().getBytes(), responseIv);

        //put back session key into response
        byte[] responseEncryptedSessionKey = EncryptionLogic.encryptWithRSA(EncryptionLogic.getPublicKey(username), sessionKeyBytes);


        //generate signature
        byte[] responseSignature = EncryptionLogic.createDigitalSignature(jsonResponse.toString().getBytes(), EncryptionLogic.getPrivateKey(serverName, keystorePasswd));

        byte[][] ret = new byte[4][];
        ret[0] = encryptedResponse;
        ret[1] = responseSignature;
        ret[2] = responseEncryptedSessionKey;
        ret[3] = responseIv;

        return ret;
    }

    public JSONArray obtainUsersAtLocation(int x, int y, int epoch)  {

        JSONArray userReports = new JSONArray();
        for(UserReport report: this.reportList){
            Coords coords = report.getCoords();
            if(report.isClosed() && coords.getX()==x && coords.getY()==y && report.getEpoch()==epoch){
                JSONObject r = new JSONObject();
                r.put("report", report.getReportJSON());

                //add proofs to response
                JSONArray proofs = new JSONArray();
                for (int i = 0; i < report.getProofsList().size(); i++) {
                    proofs.put(report.getProofsList().get(i).getProofJSON());
                }
                //add array to json response
                r.put("proofs", proofs);
                userReports.put(r);
            }
        }
        return userReports;
    }

    public synchronized void submitReport(byte[] encryptedSessionKey, byte[] encryptedMessage, byte[] digitalSignature, byte[] iv, long proofOfWork, long timestamp, boolean isHA) throws InvalidReportException, ReportAlreadyExistsException, InvalidSignatureException, InvalidProofOfWorkException, InvalidFreshnessToken {

        //max skew assumed: 30s
        if (timestamp > System.currentTimeMillis() + 30000 || timestamp < System.currentTimeMillis() - 30000  ) {
            throw new InvalidFreshnessToken();
        }

        //Decrypt session key
        byte[] sessionKeyBytes = EncryptionLogic.decryptWithRSA(EncryptionLogic.getPrivateKey(serverName, keystorePasswd), encryptedSessionKey);
        SecretKey sessionKey = EncryptionLogic.bytesToAESKey(sessionKeyBytes);

        //decipher message to get report as JSON
        byte[] decipheredMessage = EncryptionLogic.decryptWithAES(sessionKey, encryptedMessage, iv);
        JSONObject reportJSON = new JSONObject(new String(decipheredMessage));

        String data = reportJSON.toString() + timestamp + proofOfWork;

        //Verify proof of work
        if(!EncryptionLogic.verifyProofOfWork(proofOfWork,reportJSON.toString() + timestamp))
            throw new InvalidProofOfWorkException();

        //verify message integrity
        byte[] reportSignature = verifyMessage(data.getBytes(), digitalSignature, isHA);

        if (reportSignature != null) {
            UserReport userReport = new UserReport(reportJSON.getJSONObject("report_info"), reportSignature);


            if(duplicateReport(userReport)) {
                System.out.println("Received duplicate report, ignoring!");
                return;
            }
            //try to replace report
            if(!replaceReport(userReport)) this.reportList.add(userReport);

            System.out.println("Initial Report received from " + userReport.getUsername() + " from epoch " + userReport.getEpoch() + " report verified");
            //Add to database
            reportsRepository.submitUserReport(userReport, reportSignature);
        } else {
            throw new InvalidReportException();
        }
    }

    // when a user submits a new report from a epoch that has not a confirmed report
    public synchronized boolean duplicateReport(UserReport newReport) {

        for (int i = 0; i < reportList.size(); i++) {
            if (reportList.get(i).getUsername().equals(newReport.getUsername())
                    && reportList.get(i).getEpoch() == newReport.getEpoch()
                    && !reportList.get(i).isClosed()
                    && reportList.get(i).getCoords() == newReport.getCoords()) {
                return true;
            }
        }
        return false;
    }

    // when a user submits a new report from a epoch that has not a confirmed report
    public synchronized boolean replaceReport(UserReport newReport) {

        for (int i = 0; i < reportList.size(); i++) {
            if (reportList.get(i).getUsername().equals(newReport.getUsername())
                    && reportList.get(i).getEpoch() == newReport.getEpoch()
                    && !reportList.get(i).isClosed()) {
                reportList.set(i, newReport);
                System.out.println("Replaced in memory!");
                reportsRepository.deleteReport(newReport.getUsername(),newReport.getEpoch());
                reportsRepository.submitUserReport(newReport,newReport.getSignature());
                System.out.println("Replace in DB!");
                return true;
            }
        }
        return false;
    }

    public byte[] verifyMessage(byte[] decipheredMessage, byte[] digitalSignature, boolean isHA) throws ReportAlreadyExistsException, InvalidSignatureException {


        //get username and respective public key
        JSONObject obj = new JSONObject(new String(decipheredMessage));
        JSONObject report = obj.getJSONObject("report_info");
        String username = report.getString("username");
        int epoch = report.getInt("epoch");

        byte[] reportDS = Base64.getDecoder().decode(new String(obj.getString("report_digital_signature")));

        PublicKey userPubKey = null;

        if (isHA)
            userPubKey = EncryptionLogic.getPublicKey("ha");
        else
            userPubKey = EncryptionLogic.getPublicKey(username);

        //verify digital signature
        boolean isValid = EncryptionLogic.verifyDigitalSignature(decipheredMessage, digitalSignature, userPubKey);

        //System.out.println("Prover: " + username + " epoch: " +  epoch + " + Message digital signature valid? " + isValid);
        if (isValid) {
            try {
                obtainClosedLocationReport(username, epoch);
                throw new ReportAlreadyExistsException(username, epoch);
            } catch (NoReportFoundException e) {
                //return true if there is no report for that epoch
                return reportDS;
            }
        } else
            throw new InvalidSignatureException();


    }

    public synchronized boolean submitProof(byte[] witnessEncryptedSessionKey, byte[] witnessIv, byte[] encryptedSessionKey, byte[] encryptedProof, byte[] signature, byte[] iv, long timestamp, long proofOfWork) throws InvalidProofException, NoReportFoundException, AlreadyConfirmedReportException, InvalidFreshnessToken {

        //max skew assumed: 30s
        if (timestamp > System.currentTimeMillis() + 30000 || timestamp < System.currentTimeMillis() - 30000  ) {
            throw new InvalidFreshnessToken();
        }

        //Decrypt session key
        byte[] sessionKeyBytes = EncryptionLogic.decryptWithRSA(EncryptionLogic.getPrivateKey(serverName, keystorePasswd), encryptedSessionKey);
        SecretKey sessionKey = EncryptionLogic.bytesToAESKey(sessionKeyBytes);

        //decrypt proof
        byte[] decryptedProof = EncryptionLogic.decryptWithAES(sessionKey,encryptedProof,iv);

        //Decrypt witnesssession key
        byte[] witnessSessionKeyBytes = EncryptionLogic.decryptWithRSA(EncryptionLogic.getPrivateKey(serverName, keystorePasswd), witnessEncryptedSessionKey);

        String data = new String(decryptedProof) + timestamp + proofOfWork;

        //verify proofs integrity
        Proof newProof = verifyProof(witnessSessionKeyBytes,decryptedProof, signature,witnessIv,data, false);


        UserReport report = obtainLocationReport(newProof.getProverUsername(), newProof.getEpoch());
        System.out.println("\t Proof received from " + newProof.getWitnessUsername() + " to " + newProof.getProverUsername() + " report verified");
        report.addProof(newProof);
        reportsRepository.submitProof(newProof);
        if (report.getProofsList().size() >= responseQuorum && !report.isClosed()) {
            System.out.println("Reached quorum of proofs for report of user " + report.getUsername() + " for epoch " + report.getEpoch());
            report.setClosed(true);
            reportsRepository.closeUserReport(report);
            return true;
        }
        return false;
    }

    public boolean validEpoch(String username, int epoch) {
        for (UserReport report : this.reportList) {
            if (report.getUsername().equals(username) && report.getEpoch() == epoch)
                return true;
        }
        return false;
    }

    public List<Proof> getUserProofs(String username, List<Integer> epochs){
        List<Proof> ret = new ArrayList<>();


        for(int epoch : epochs){
            Stream<Proof> result = reportList.stream()
                    .filter(report -> report.getEpoch() == epoch)
                    .map(report -> report.getProofsList())
                    .flatMap(Collection::stream)
                    .filter(proof -> proof.getWitnessUsername().equals(username));

            ret = Stream.concat(result,ret.stream()).collect(Collectors.toList());
        }

        return ret;

    }

    public boolean isDuplicateProof(UserReport report, String witness) {

        for (Proof proof : report.getProofsList()) {
            return proof.getWitnessUsername().equals(witness);
        }
        return false;
    }


    public Proof verifyProof(byte[] witnessSessionKeyBytes,byte[] proof, byte[] signature, byte[] witnessIv, String data, boolean isHA) throws InvalidProofException, NoReportFoundException, AlreadyConfirmedReportException {

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
        boolean validSignatureMessage;
        if (isHA)
            validSignatureMessage = EncryptionLogic.verifyDigitalSignature(data.getBytes(), signature, EncryptionLogic.getPublicKey("ha"));
        else
            validSignatureMessage = EncryptionLogic.verifyDigitalSignature(data.getBytes(), signature, proverPubKey);

        //verify witness proof
        boolean validSignatureProof = EncryptionLogic.verifyDigitalSignature(proofJSON.toString().getBytes(), witnessSignature, witnessPubKey);

        if(!validSignatureMessage || !validSignatureProof)
            throw new InvalidProofException("Invalid Signature");

        int epoch = proofJSON.getInt("epoch");

        UserReport report = obtainLocationReport(proverUser, epoch);
        //System.out.println("Proof signature valid from witness " + witnessUser + " to prover " + proverUser);

        //prover cannot be a witness
        if (report.getUsername().equals(witnessUser))
            throw new InvalidProofException("Prover cannot be a witness");

        //byzantine user set wrong epoch
        if (! validEpoch(proverUser, epoch))
            throw new InvalidProofException("Wrong epoch in proof");

        //check if witness has already submitted a proof for this report
        if (isDuplicateProof(report, witnessUser))
            throw new InvalidProofException("Witness already submitted a valid proof for this location report");

        //decrypt witness location
        byte[] witnessLocationBytes = Base64.getDecoder().decode(proofJSON.getString("encrypted_location"));

        SecretKey witnessSessionKey = EncryptionLogic.bytesToAESKey(witnessSessionKeyBytes);
        byte[] witnessLocation = EncryptionLogic.decryptWithAES(witnessSessionKey, witnessLocationBytes, witnessIv);

        JSONObject locationJSON = new JSONObject(new String(Objects.requireNonNull(witnessLocation)));

        String username = report.getUsername();

        //check if report is not closed yet
        //if (report.isClosed())
            //throw new AlreadyConfirmedReportException(username, report.getEpoch());

        // users are not close to
        Coords witnessCoords = new Coords(locationJSON.getInt("x"), locationJSON.getInt("y"));
        if (!isClose(witnessCoords, report.getCoords())) {
            throw new InvalidProofException("Prover and witness are not close enough");
        }

        return new Proof(proofJSON, locationJSON, proof, witnessSessionKey.getEncoded(), witnessIv);

    }



    public byte[][] generateObtainUsersAtLocationReportResponse(byte[] encryptedData, byte[] encryptedSessionKey, byte[] signature, byte[] iv, long proofOfWork, long timestamp) throws InvalidSignatureException, InvalidFreshnessToken, InvalidProofOfWorkException {

        //max skew assumed: 30s
        if (timestamp > System.currentTimeMillis() + 30000 || timestamp < System.currentTimeMillis() - 30000  ) {
            throw new InvalidFreshnessToken();
        }

        //Decrypt session key
        byte[] sessionKeyBytes = EncryptionLogic.decryptWithRSA(EncryptionLogic.getPrivateKey(serverName, keystorePasswd), encryptedSessionKey);
        SecretKey sessionKey = EncryptionLogic.bytesToAESKey(sessionKeyBytes);


        //Decrypt data
        byte[] decryptedData = EncryptionLogic.decryptWithAES(sessionKey, encryptedData, iv);
        String jsonString = new String(decryptedData);
        JSONObject jsonObject = new JSONObject(jsonString);
        JSONObject message = jsonObject.getJSONObject("message");

        String data = jsonString + timestamp + proofOfWork;

        //Verify proof of work
        if(!EncryptionLogic.verifyProofOfWork(proofOfWork,jsonString + timestamp))
            throw new InvalidProofOfWorkException();

        //Verify signature
        if (!EncryptionLogic.verifyDigitalSignature(data.getBytes(), signature, EncryptionLogic.getPublicKey("ha"))) {
            System.out.println("Invalid signature for HA request!");
            throw new InvalidSignatureException();
        } else
            System.out.println("Valid signature for HA request!");

        int epoch = message.getInt("epoch");
        int x = message.getInt("x");
        int y = message.getInt("y");

        //process request
        JSONArray userReports = obtainUsersAtLocation(x,y, epoch);
        JSONObject jsonResponseMessage = new JSONObject();

        jsonResponseMessage.put("userReports", userReports);
        jsonResponseMessage.put("readId", message.getString("readId"));

        //encrypt response
        byte[] responseIv = EncryptionLogic.generateIV();
        byte[] encryptedResponse = EncryptionLogic.encryptWithAES(sessionKey, jsonResponseMessage.toString().getBytes(), responseIv);
        byte[] newEncryptedSessionKey = EncryptionLogic.encryptWithRSA(EncryptionLogic.getPublicKey("ha"),sessionKeyBytes);

        //generate signature
        byte[] responseSignature = EncryptionLogic.createDigitalSignature(jsonResponseMessage.toString().getBytes(), EncryptionLogic.getPrivateKey(serverName, keystorePasswd));

        byte[][] ret = new byte[4][];
        ret[0] = encryptedResponse;
        ret[1] = responseSignature;
        ret[2] = responseIv;
        ret[3] = newEncryptedSessionKey;
        return ret;
    }




    public boolean isClose(Coords c1, Coords c2) {

        //let's assume a radius of 5
        int radius = 5;
        return (Math.pow(c2.getX() - c1.getX(), 2)) + (Math.pow(c2.getY() - c1.getY(), 2)) < Math.pow(radius, 2);
    }

    public String getServerName(){
        return this.serverName;
    }

    public byte[][] requestMyProofs(byte[] encryptedData, byte[] encryptedSessionKey, byte[] signature, byte[] iv, long proofOfWork, long timestamp) throws InvalidProofOfWorkException, InvalidSignatureException, ReportAlreadyExistsException, InvalidFreshnessToken {


        //max skew assumed: 30s
        if (timestamp > System.currentTimeMillis() + 30000 || timestamp < System.currentTimeMillis() - 30000  ) {
            throw new InvalidFreshnessToken();
        }

        //Decrypt session key
        byte[] sessionKeyBytes = EncryptionLogic.decryptWithRSA(EncryptionLogic.getPrivateKey(serverName, keystorePasswd), encryptedSessionKey);
        SecretKey sessionKey = EncryptionLogic.bytesToAESKey(sessionKeyBytes);


        //Decrypt data
        byte[] decryptedData = EncryptionLogic.decryptWithAES(sessionKey, encryptedData, iv);
        String jsonString = new String(decryptedData);
        JSONObject jsonObject = new JSONObject(jsonString);
        JSONObject message = jsonObject.getJSONObject("message");

        String data = jsonString + timestamp + proofOfWork;

        //Verify proof of work
        if(!EncryptionLogic.verifyProofOfWork(proofOfWork,jsonString + timestamp))
            throw new InvalidProofOfWorkException();


        List<Integer> epochs = message.getJSONArray("epochs")
                .toList()
                .stream()
                .map(o -> (Integer) o)
                .collect(Collectors.toList());

        String username = message.getString("username");

        //verify message integrity
        if(!EncryptionLogic.verifyDigitalSignature(data.getBytes(),signature,EncryptionLogic.getPublicKey(username)))
            throw new InvalidSignatureException();

        List<Proof> proofs = getUserProofs(username,epochs);

        JSONObject jsonResponseMessage = new JSONObject();
        List<JSONObject> proofList = proofs.stream().map(proof -> proof.getProofJSON()).collect(Collectors.toList());

        jsonResponseMessage.put("proofList", proofList);
        jsonResponseMessage.put("readId", message.getString("readId"));

        //encrypt response
        byte[] responseIv = EncryptionLogic.generateIV();
        byte[] encryptedResponse = EncryptionLogic.encryptWithAES(sessionKey, jsonResponseMessage.toString().getBytes(), responseIv);
        byte[] newEncryptedSessionKey = EncryptionLogic.encryptWithRSA(EncryptionLogic.getPublicKey(username),sessionKeyBytes);

        //generate signature
        byte[] responseSignature = EncryptionLogic.createDigitalSignature(jsonResponseMessage.toString().getBytes(), EncryptionLogic.getPrivateKey(serverName, keystorePasswd));

        byte[][] ret = new byte[4][];
        ret[0] = encryptedResponse;
        ret[1] = responseSignature;
        ret[2] = responseIv;
        ret[3] = newEncryptedSessionKey;

        return ret;
    }

    public void writeback(byte[] encryptedData, byte[] encryptedSessionKey, byte[] signature, byte[] iv, long proofOfWork, long timestamp, boolean isHA) throws InvalidSignatureException, InvalidReportException, InvalidProofOfWorkException, AlreadyConfirmedReportException, NoReportFoundException, InvalidFreshnessToken {

        //submit report if it does not exist
        try {
            submitReport(encryptedSessionKey, encryptedData, signature,iv, proofOfWork, timestamp, isHA);
        } catch (ReportAlreadyExistsException e) {
            System.err.println(e.getMessage());
        }

        //Decrypt session key
        byte[] sessionKeyBytes = EncryptionLogic.decryptWithRSA(EncryptionLogic.getPrivateKey(serverName, keystorePasswd), encryptedSessionKey);
        SecretKey sessionKey = EncryptionLogic.bytesToAESKey(sessionKeyBytes);

        //Decrypt data
        byte[] decryptedData = EncryptionLogic.decryptWithAES(sessionKey, encryptedData, iv);

        JSONObject obj = new JSONObject(new String(decryptedData));
        JSONArray proofs = obj.getJSONArray("proofs");


        //submit proofs if they do not exist
        for (Object o : proofs) {
            JSONObject proof = (JSONObject) o;

            byte[] witnessIv = Base64.getDecoder().decode(proof.getString("witness_iv"));
            byte[] witnessSessionKey = Base64.getDecoder().decode(proof.getString("witness_session_key_bytes"));


            //verify proofs integrity
            Proof newProof = null;
            try {
                String data = new String(decryptedData)+ timestamp + proofOfWork;

                newProof = verifyProof(witnessSessionKey, proof.toString().getBytes(), signature, witnessIv, data, isHA);
            } catch (InvalidProofException e) {
                System.err.println(e.getMessage());
                continue;
            }

            UserReport report = obtainLocationReport(newProof.getProverUsername(), newProof.getEpoch());
            System.out.println("\t Proof received from " + newProof.getWitnessUsername() + " to " + newProof.getProverUsername() + " report verified");
            report.addProof(newProof);
            reportsRepository.submitProof(newProof);
            if (report.getProofsList().size() >= responseQuorum && !report.isClosed()) {
                System.out.println("Reached quorum of proofs for report of user " + report.getUsername() + " for epoch " + report.getEpoch());
                report.setClosed(true);
                reportsRepository.closeUserReport(report);
                return;
            }
        }

    }
}
