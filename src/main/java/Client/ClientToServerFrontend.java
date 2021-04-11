package Client;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.json.JSONObject;
import proto.ClientToServerGrpc;
import proto.ObtainLocationReportReply;
import proto.ObtainLocationReportRequest;
import proto.SubmitLocationReportRequest;
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

    public void obtainLocationReport(String username, int epoch){

        EncryptionLogic encryptionLogic = new EncryptionLogic();
        JSONObject object = new JSONObject();
        JSONObject message = new JSONObject();

        //Generate a session Key
        SecretKey sessionKey = encryptionLogic.generateAESKey();
        byte[] sessionKeyBytes = sessionKey.getEncoded();

        //Encrypt session jey with server public key
        byte[] encryptedSessionKey = encryptionLogic.encryptWithRSA(encryptionLogic.getPublicKey("server"),sessionKeyBytes);

        //Pass data to json
        message.put("username",username);
        message.put("epoch",epoch);

        object.put("message",message);

        //Encrypt data with session key
        byte[] encryptedData = encryptionLogic.encryptWithAES(
                sessionKey,
                object.toString().getBytes()
        );

        //Generate digital signature
        byte[] digitalSignature = encryptionLogic.createDigitalSignature(
                object.toString().getBytes(),
                encryptionLogic.getPrivateKey(username)
        );


        ObtainLocationReportReply reply = this.blockingStub.obtainLocationReport(
            ObtainLocationReportRequest.newBuilder()
                    .setMessage(ByteString.copyFrom(encryptedData))
                    .setSignature(ByteString.copyFrom(digitalSignature))
                    .setSessionKey(ByteString.copyFrom(encryptedSessionKey))
                    .build()
        );


    }
}
