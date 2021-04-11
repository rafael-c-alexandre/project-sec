package Client;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.json.JSONObject;
import proto.ClientToServerGrpc;
import proto.ObtainLocationReportReply;
import proto.ObtainLocationReportRequest;
import proto.SubmitLocationReportRequest;
import util.Coords;
import util.EncryptionLogic;

import javax.crypto.SecretKey;
import java.util.Arrays;

public class ClientToServerFrontend {
    private String username;
    private ManagedChannel channel ;
    private ClientToServerGrpc.ClientToServerBlockingStub blockingStub;

    public ClientToServerFrontend( String username, String host, int port) {
        this.username = username;
        this.channel = ManagedChannelBuilder.forAddress(host,port).usePlaintext().build();
        this.blockingStub = ClientToServerGrpc.newBlockingStub(channel);
    }

    public void submitReport(byte[] encryptedMessage, byte[] encryptedSessionKey, byte[] digitalSignature, byte[] iv){
        this.blockingStub.submitLocationReport(
                SubmitLocationReportRequest.newBuilder()
                        .setEncryptedMessage(ByteString.copyFrom(encryptedMessage))
                        .setEncryptedSessionKey(ByteString.copyFrom(encryptedSessionKey))
                        .setSignature(ByteString.copyFrom(digitalSignature))
                        .setIv(ByteString.copyFrom(iv))
                        .build()
        );
    }

    public Coords obtainLocationReport(String username, int epoch){
        
        JSONObject object = new JSONObject();
        JSONObject message = new JSONObject();

        //Generate a session Key
        SecretKey sessionKey = EncryptionLogic.generateAESKey();
        byte[] sessionKeyBytes = sessionKey.getEncoded();

        //Encrypt session jey with server public key
        byte[] encryptedSessionKey = EncryptionLogic.encryptWithRSA(EncryptionLogic.getPublicKey("server"),sessionKeyBytes);

        //Pass data to json
        message.put("username",username);
        message.put("epoch",epoch);

        object.put("message",message);

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
                EncryptionLogic.getPrivateKey(username)
        );


        ObtainLocationReportReply reply = this.blockingStub.obtainLocationReport(
            ObtainLocationReportRequest.newBuilder()
                    .setMessage(ByteString.copyFrom(encryptedData))
                    .setSignature(ByteString.copyFrom(digitalSignature))
                    .setSessionKey(ByteString.copyFrom(encryptedSessionKey))
                    .setIv(ByteString.copyFrom(iv))
                    .build()
        );

        byte[] encryptedResponse = reply.getMessage().toByteArray();
        byte[] responseSignature = reply.getSignature().toByteArray();
        byte[] responseIv = reply.getIv().toByteArray();

        //Decrypt response
        byte[] response = EncryptionLogic.decryptWithAES(sessionKey,encryptedResponse,responseIv);

        //Verify response signature
        if(!EncryptionLogic.verifyDigitalSignature(response,responseSignature,EncryptionLogic.getPublicKey("server")))
            System.out.println("Invalid signature");
        else
            System.out.println("Valid signature");

        //process response and return coords
        String jsonString = new String(response);
        System.out.println("json -> " + jsonString);
        JSONObject jsonObject = new JSONObject(jsonString);
        JSONObject msg = jsonObject.getJSONObject("message");

        return new Coords(msg.getInt("x"),msg.getInt("y"));

    }
}
