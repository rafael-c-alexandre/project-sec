package Client;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.json.JSONArray;
import org.json.JSONObject;
import proto.ClientToClientGrpc;
import proto.ClientToServerGrpc;
import proto.RequestLocationProofReply;
import proto.RequestLocationProofRequest;
import util.Coords;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientToClientFrontend {
    private Map<String,ManagedChannel> channelMap = new HashMap<>();
    private Map<String,ClientToClientGrpc.ClientToClientStub> stubMap = new HashMap<>();
    private ClientToServerGrpc.ClientToServerBlockingStub serverStub;
    private ClientToServerFrontend serverFrontend;
    private ClientLogic clientLogic;
    private int responseQuorum = 2; //TODO: hardcoded value

    public ClientToClientFrontend(ClientToServerFrontend serverFrontend, ClientLogic clientLogic) {
        this.clientLogic = clientLogic;
        this.serverFrontend = serverFrontend;
    }

    public void addUser(String username, String host, int port){
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host,port).usePlaintext().build();
        channelMap.put(
                username,
                channel
        );


        stubMap.put(username,ClientToClientGrpc.newStub(channel));
    }

    public void broadcastProofRequest(String username, Coords coords, int epoch, List<String> closePeers){
        final int[] numberResponses = {0};

        List<JSONObject> proofs = new ArrayList<>();

        /* Request proof of location to other close clients */
        for(String user : closePeers ){
            /* Create location proof request */
            stubMap.get(user).requestLocationProof(RequestLocationProofRequest.newBuilder().
                setUsername(username).
                setX(coords.getX()).
                setY(coords.getY()).
                setEpoch(epoch)
                .build(), new StreamObserver<RequestLocationProofReply>() {

                    @Override
                    public void onNext(RequestLocationProofReply requestLocationProofReply) {
                        System.out.println("Received proof reply");
                        proofs.add(new JSONObject(new String(requestLocationProofReply.getProof().toByteArray())));

                        if (proofs.size() == responseQuorum) {
                            System.out.println("Got response quorum. Producing report...");
                            byte[][] message = clientLogic.createLocationProof(proofs);
                            serverFrontend.submitReport(message[0], message[1], message[2]);
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {

                    }

                    @Override
                    public void onCompleted() {

                    }
                }
            );
        }
    }


}
