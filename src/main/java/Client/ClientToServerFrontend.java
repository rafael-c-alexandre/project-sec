package Client;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import proto.*;
import org.json.JSONObject;
import util.Coords;

import java.util.concurrent.TimeUnit;

public class ClientToServerFrontend {
    private final String username;
    private final ManagedChannel channel;
    private final ClientToServerGrpc.ClientToServerBlockingStub blockingStub;
    private final ClientLogic clientLogic;

    public ClientToServerFrontend(String username, String host, int port, ClientLogic clientLogic) {
        this.username = username;
        this.channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        this.blockingStub = ClientToServerGrpc.newBlockingStub(channel);
        this.clientLogic = clientLogic;
    }

    public void submitReport(byte[] encryptedMessage,byte[] digitalSignature) {
        try {
            SubmitLocationReportReply reply = this.blockingStub.submitLocationReport(
                    SubmitLocationReportRequest.newBuilder()
                            .setUsername(username)
                            .setEncryptedMessage(ByteString.copyFrom(encryptedMessage))
                            .setSignature(ByteString.copyFrom(digitalSignature))
                            .build()
            );
        } catch (Exception e) {
            io.grpc.Status status = io.grpc.Status.fromThrowable(e);
            System.out.println("Exception received from server: " + status.getDescription());
        }

    }

    public void submitProof(byte[] encryptedProof, byte[] digitalSignature) {
        try {
            SubmitLocationProofReply reply = this.blockingStub.submitLocationProof(
                    SubmitLocationProofRequest.newBuilder()
                            .setUsername(username)
                            .setEncryptedProof(ByteString.copyFrom(encryptedProof))
                            .setSignature(ByteString.copyFrom(digitalSignature))
                            .build()
            );
        } catch (Exception e) {
            io.grpc.Status status = io.grpc.Status.fromThrowable(e);
            System.out.println("Exception received from server: " + status.getDescription());
        }

    }


    public void handshake(byte[] encryptedUsernameSessionKey, byte[] digitalSignature, byte[] iv){
        try{
            HandshakeReply reply = this.blockingStub.handshake(
                    HandshakeRequest.newBuilder()
                            .setEncryptedUsernameSessionKey(ByteString.copyFrom(encryptedUsernameSessionKey))
                            .setSignature(ByteString.copyFrom(digitalSignature))
                            .setIv(ByteString.copyFrom(iv))
                            .build()
            );
        } catch (Exception e){
            io.grpc.Status status = io.grpc.Status.fromThrowable(e);
            System.out.println("Exception received from server: " + status.getDescription());
        }

    }

    public Coords obtainLocationReport(String username, int epoch){

        byte[][] params = this.clientLogic.generateObtainLocationRequestParameters(username, epoch);

        byte[] encryptedData = params[0];
        byte[] digitalSignature = params[1];

        ObtainLocationReportReply reply = this.blockingStub.obtainLocationReport(
                ObtainLocationReportRequest.newBuilder()
                        .setUsername(username)
                        .setMessage(ByteString.copyFrom(encryptedData))
                        .setSignature(ByteString.copyFrom(digitalSignature))
                        .build()
        );

        byte[] encryptedResponse = reply.getMessage().toByteArray();
        byte[] responseSignature = reply.getSignature().toByteArray();

        return this.clientLogic.getCoordsFromReply (encryptedResponse, responseSignature);

    }

    public void shutdown() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
