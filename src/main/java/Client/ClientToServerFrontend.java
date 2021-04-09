package Client;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import proto.ClientToServerGrpc;
import proto.ObtainLocationReportRequest;
import proto.SubmitLocationReportRequest;

import java.util.Arrays;

public class ClientToServerFrontend {
    private ManagedChannel channel ;
    private ClientToServerGrpc.ClientToServerBlockingStub blockingStub;

    public ClientToServerFrontend(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host,port).usePlaintext().build();
        this.blockingStub = ClientToServerGrpc.newBlockingStub(channel);
    }

    public void submitReport(byte[] encryptedMessage, byte[] encryptedSessionKey, byte[] digitalSignature){
        this.blockingStub.submitLocationReport(
                SubmitLocationReportRequest.newBuilder()
                        .setEncryptedMessage(ByteString.copyFrom(encryptedMessage))
                        .setEncryptedSessionKey(ByteString.copyFrom(encryptedSessionKey))
                        .setSignature(ByteString.copyFrom(digitalSignature))
                        .build()
        );
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
