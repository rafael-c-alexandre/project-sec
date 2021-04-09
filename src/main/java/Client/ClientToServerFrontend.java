package Client;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import proto.ObtainLocationReportRequest;
import proto.ServerGrpc;
import proto.SubmitLocationReportRequest;

public class ClientToServerFrontend {
    private ManagedChannel channel ;
    private ServerGrpc.ServerBlockingStub blockingStub;

    public ClientToServerFrontend(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host,port).usePlaintext().build();
        this.blockingStub = ServerGrpc.newBlockingStub(channel);
    }

    public void submitReport(){
        this.blockingStub.submitLocationReport(
                SubmitLocationReportRequest.newBuilder()
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
