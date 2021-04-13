package HA;

import org.json.JSONArray;
import org.json.JSONObject;
import util.Coords;
import util.EncryptionLogic;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class HALogic {
    private SecretKey sessionKey;
    private byte[] iv;


    public byte[][] generateObtainLocationRequestParameters(String username, int epoch) {
        byte[][] ret = new byte[2][];
        JSONObject object = new JSONObject();
        JSONObject message = new JSONObject();

        //Pass data to json
        message.put("username", username);
        message.put("epoch", epoch);

        object.put("message", message);

        //Encrypt data with session key
        byte[] encryptedData = EncryptionLogic.encryptWithAES(
                sessionKey,
                object.toString().getBytes(),
                iv
        );

        //Generate digital signature
        byte[] digitalSignature = EncryptionLogic.createDigitalSignature(
                object.toString().getBytes(),
                EncryptionLogic.getPrivateKey("ha")
        );

        ret[0] = encryptedData;
        ret[1] = digitalSignature;

        return ret;
    }

    public Coords getCoordsFromReply( byte[] encryptedResponse, byte[] responseSignature) {

        //Decrypt response
        byte[] response = EncryptionLogic.decryptWithAES(sessionKey, encryptedResponse, iv);

        //Verify response signature
        if (!EncryptionLogic.verifyDigitalSignature(response, responseSignature, EncryptionLogic.getPublicKey("server")))
            System.out.println("Invalid signature");
        else
            System.out.println("Valid signature");

        //process response and return coords
        String jsonString = new String(response);
        System.out.println("json -> " + jsonString);
        JSONObject jsonObject = new JSONObject(jsonString);
        JSONObject msg = jsonObject.getJSONObject("message");

        return new Coords(msg.getInt("x"), msg.getInt("y"));
    }

    public byte[][] generateObtainUsersAtLocationRequestParameters(int x, int y, int epoch) {
        byte[][] ret = new byte[2][];
        JSONObject object = new JSONObject();
        JSONObject message = new JSONObject();

        //Pass data to json
        message.put("x", x);
        message.put("y", y);
        message.put("epoch", epoch);

        object.put("message", message);

        //Encrypt data with session key
        byte[] encryptedData = EncryptionLogic.encryptWithAES(
                sessionKey,
                object.toString().getBytes(),
                iv
        );

        //Generate digital signature
        byte[] digitalSignature = EncryptionLogic.createDigitalSignature(
                object.toString().getBytes(),
                EncryptionLogic.getPrivateKey("ha")
        );

        ret[0] = encryptedData;
        ret[1] = digitalSignature;

        return ret;
    }

    public List<String> getUsersFromReply( byte[] encryptedResponse, byte[] responseSignature) {

        //Decrypt response
        byte[] response = EncryptionLogic.decryptWithAES(sessionKey, encryptedResponse, iv);

        //Verify response signature
        if (!EncryptionLogic.verifyDigitalSignature(response, responseSignature, EncryptionLogic.getPublicKey("server")))
            System.out.println("Invalid signature");
        else
            System.out.println("Valid signature");

        //process response and return coords
        String jsonString = new String(response);
        System.out.println("json -> " + jsonString);
        JSONObject jsonObject = new JSONObject(jsonString);
        JSONObject msg = jsonObject.getJSONObject("message");

        JSONArray arr = msg.getJSONArray("users");
        List<String> list = new ArrayList<String>();
        for(int i = 0; i < arr.length(); i++){
            list.add(arr.getString(i));
        }
        return list;
    }

    public byte[][] generateHandshakeMessage(){
        byte[][] result = new byte[3][];

        //Generate session key
        this.sessionKey = EncryptionLogic.generateAESKey();

        //get server public key
        Key serverPubKey = EncryptionLogic.getPublicKey("server");

        //Generate new IV
        byte[] iv = EncryptionLogic.generateIV();
        this.iv = iv;

        byte[] encryptedUsername = EncryptionLogic.encryptWithAES(sessionKey, "ha".getBytes(), iv);
        //result[0] = encryptedUsername;

        //encrypt session key with server public key
        byte[] encryptedSessionKey = EncryptionLogic.encryptWithRSA(serverPubKey, sessionKey.getEncoded());
        //result[1] = encryptedSessionKey;

        JSONObject message = new JSONObject();
        message.put("encryptedUsername", Base64.getEncoder().encodeToString(encryptedUsername));
        message.put("encryptedSessionKey", Base64.getEncoder().encodeToString(encryptedSessionKey));
        result[0] = message.toString().getBytes();

        //sign encrypted username and encrypted session key
        byte[] digitalSignature = EncryptionLogic.createDigitalSignature(message.toString().getBytes(),
                EncryptionLogic.getPrivateKey("ha"));
        result[1] = digitalSignature;

        result[2] = iv;

        return result;
    }

}
