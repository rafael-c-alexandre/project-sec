package Client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import proto.ClientToClientGrpc;
import proto.RequestLocationProofRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientToClientFrontend {
    private Map<String,ManagedChannel> channelMap = new HashMap<>();
    private Map<String,ClientToClientGrpc.ClientToClientBlockingStub> stubMap = new HashMap<>();

    public ClientToClientFrontend() {
    }

    public void addUser(String username, String host, int port){
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host,port).usePlaintext().build();
        channelMap.put(
                username,
                channel
        );

        stubMap.put(username,ClientToClientGrpc.newBlockingStub(channel));
    }

    public void broadcastProofRequest(List<String> closePeers){
        for(String user : closePeers ){
            stubMap.get(user).requestLocationProof(
                    RequestLocationProofRequest.newBuilder()
                            .build()
            );
        }
    }


}
