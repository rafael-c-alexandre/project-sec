package Client;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import proto.ClientToServerGrpc;
import proto.ObtainLocationReportRequest;
import proto.SubmitLocationReportReply;
import proto.SubmitLocationReportRequest;

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
        try{
        SubmitLocationReportReply reply = this.blockingStub.submitLocationReport(
                SubmitLocationReportRequest.newBuilder()
                        .setEncryptedMessage(ByteString.copyFrom(encryptedMessage))
                        .setEncryptedSessionKey(ByteString.copyFrom(encryptedSessionKey))
                        .setSignature(ByteString.copyFrom(digitalSignature))
                        .setIv(ByteString.copyFrom(iv))
                        .build()
        );
        } catch (Exception e){
            io.grpc.Status status = io.grpc.Status.fromThrowable(e);
            System.out.println("Exception received from server:" + status.getDescription());
        }

    }

    public void obtainLocationReport(byte[] message, byte[] signature, byte[] sessionKey){
        this.blockingStub.obtainLocationReport(
            ObtainLocationReportRequest.newBuilder()
                    .setMessage(ByteString.copyFrom(message))
                    .setSignature(ByteString.copyFrom(signature))
                    .setSessionKey(ByteString.copyFrom(sessionKey))
                    .build()
        );
    }
}
