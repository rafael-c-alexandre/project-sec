package Client;

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

    public void obtainLocationReport(){
        this.blockingStub.obtainLocationReport(
            ObtainLocationReportRequest.newBuilder()
                    .build()
        );
    }
}
