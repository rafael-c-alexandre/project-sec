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
    private int responseQuorum = 1; //TODO: hardcoded value

    public ClientToClientFrontend() {
        serverFrontend = new ClientToServerFrontend("localhost", 8084);
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

        for(String user : closePeers ){
            stubMap.get(user).requestLocationProof(
                    RequestLocationProofRequest.newBuilder().setUsername(username).setX(coords.getX()).setY(coords.getY()).setEpoch(epoch)
                            .build(), new StreamObserver<RequestLocationProofReply>() {
                        @Override
                        public void onNext(RequestLocationProofReply requestLocationProofReply) {
                            proofs.add(new JSONObject(new String(requestLocationProofReply.getProof().toByteArray())));
                            numberResponses[0]++;

                            if (numberResponses[0] == responseQuorum) {
                                JSONObject message = new JSONObject();
                                message.put("username", username);
                                message.put("epoch", epoch);
                                message.put("x", coords.getX());
                                message.put("y", coords.getY());
                                JSONArray ja = new JSONArray(proofs);
                                message.put("reports", ja);
                                serverFrontend.submitReport(message.toString().getBytes());
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
