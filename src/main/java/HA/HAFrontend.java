package HA;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.json.JSONObject;
import proto.ClientToServerGrpc;
import proto.HA.HAToServerGrpc;
import proto.HA.ObtainLocationReportReply;
import proto.HA.ObtainLocationReportRequest;
import proto.SubmitLocationReportRequest;
import util.Coords;
import util.EncryptionLogic;

import javax.crypto.SecretKey;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class HAFrontend {
    private ManagedChannel channel ;
    private HAToServerGrpc.HAToServerBlockingStub blockingStub;

    public HAFrontend(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host,port).usePlaintext().build();
        this.blockingStub = HAToServerGrpc.newBlockingStub(channel);
    }

    public List<String> obtainUsersAtLocation(int x, int y, int epoch){
        return null;
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
                EncryptionLogic.getPrivateKey("HA")
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

    public void shutdown() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
