package HA;

import org.json.JSONObject;
import util.Coords;
import util.EncryptionLogic;

import javax.crypto.SecretKey;

public class HALogic {
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
                EncryptionLogic.getPrivateKey("ha")
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
        System.out.println("json -> " + jsonString);
        JSONObject jsonObject = new JSONObject(jsonString);
        JSONObject msg = jsonObject.getJSONObject("message");

        return new Coords(msg.getInt("x"), msg.getInt("y"));
    }
}
