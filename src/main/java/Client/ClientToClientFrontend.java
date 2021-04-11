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
import util.EncryptionLogic;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.*;

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

                        byte[] mes = requestLocationProofReply.getProof().toByteArray();
                        byte[] digitalSignature = requestLocationProofReply.getDigitalSignature().toByteArray();
                        JSONObject proofObject = new JSONObject();
                        //create a proof json object
                        proofObject.put("message", Base64.getEncoder().encodeToString(mes));
                        proofObject.put("digital_signature", Base64.getEncoder().encodeToString(digitalSignature));

                        proofs.add(proofObject);


                        /****** test digital signature validity | REMOVE  ******/
                        JSONObject proof = new JSONObject(new String(requestLocationProofReply.getProof().toByteArray()));
                        System.out.println("Received proof reply from " + proof.getString("username"));

                        boolean result = EncryptionLogic.verifyDigitalSignature(mes, digitalSignature, EncryptionLogic.getPublicKey(proof.getString("username")));
                        System.out.println("CERTIFIED VALID ?" + result);

                        /************/

                        if (proofs.size() == responseQuorum) {
                            System.out.println("Got response quorum. Producing report...");
                            byte[][] message = clientLogic.createLocationReport(proofs);
                            serverFrontend.submitReport(message[0], message[1], message[2], message[3]);
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
