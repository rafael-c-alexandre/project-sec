package Server;

import Server.database.UserReportsRepository;
import com.google.protobuf.ByteString;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import util.Coords;
import util.EncryptionLogic;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.sql.Connection;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class ServerLogic {

    private CopyOnWriteArrayList<UserReport> reportList;
    private UserReportsRepository reportsRepository;


    public ServerLogic(Connection Connection) {
        reportsRepository = new UserReportsRepository(Connection);
        this.reportList = reportsRepository.getAllUserReports();
    }

    public Coords obtainLocationReport(String username, int epoch){
        for(UserReport report : this.reportList){
            if(report.getUsername().equals(username) && report.getEpoch() == epoch)
                return report.getCoords();
        }
        return null;
    }

    public List<String> obtainUsersAtLocation(int x, int y, int epoch){
        return this.reportList.stream()
                .filter(report -> report.getCoords().getY() == y && report.getCoords().getX() == x && report.getEpoch() == epoch)
                .map(report -> report.getUsername())
                .collect(Collectors.toList());
    }

    public void submitReport( ByteString encryptedMessage, ByteString encryptedSessionKey, ByteString digitalSignature, ByteString iv){

        PrivateKey serverPrivKey = EncryptionLogic.getPrivateKey("server");
        //decipher session key
        byte[] sessionKeyBytes =  EncryptionLogic.decryptWithRSA(serverPrivKey, encryptedSessionKey.toByteArray());
        SecretKey sessionKey = null;
        if (sessionKeyBytes != null) {
            sessionKey = new SecretKeySpec(sessionKeyBytes, 0, sessionKeyBytes.length, "AES");
        }

        //decipher message
        byte[] decipheredMessage = EncryptionLogic.decryptWithAES(sessionKey, encryptedMessage.toByteArray(), iv.toByteArray());

        //verify message integrity
        verifyMessage( decipheredMessage, digitalSignature);

        //verify proofs integrity
        verifyProofs(decipheredMessage);

        this.reportList.add(new UserReport());

        //Add to database
        //reportsRepository.submitUserReport(userReport);
    }

    public void verifyMessage( byte[] decipheredMessage, ByteString digitalSignature) {

        //get username and respective public key
        JSONObject obj = new JSONObject(new String(decipheredMessage));
        String username = obj.getString("username");

        PublicKey userPubKey = EncryptionLogic.getPublicKey(username);

        //verify digital signature
        //TODO
        System.out.println("Message digital signature valid? " + EncryptionLogic.verifyDigitalSignature(decipheredMessage, digitalSignature.toByteArray(), userPubKey));

    }


    public void verifyProofs(byte[] decipheredMessage) {

        try {
            JSONObject obj = new JSONObject(new String(decipheredMessage));

            //get proofs array
            JSONArray proofs = (JSONArray) obj.get("proofs");

            for (int i = 0; i < proofs.length(); i++) {
                JSONObject proof = proofs.getJSONObject(i);


                //get message to verify
                byte[] message = Base64.getDecoder().decode(proof.getString("message"));
                //get digital signature of the proof
                byte[] digitalSignature = Base64.getDecoder().decode(proof.getString("digital_signature"));

                //get JSON object to retrieve username
                JSONObject messageJSON = new JSONObject(new String(message));
                String username = messageJSON.getString("username");

                //get user public key
                PublicKey userPubKey = EncryptionLogic.getPublicKey(username);

                //verify digital signature
                //TODO
                System.out.println("Proof digital signature from " + messageJSON.getString("username") + " valid? " + EncryptionLogic.verifyDigitalSignature(message, digitalSignature, userPubKey));
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

    }


    public boolean verifyProofs(){

        return true;
    }
}
