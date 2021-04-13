package Client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.json.JSONObject;
import proto.ClientToClientGrpc;
import proto.ClientToServerGrpc;
import proto.RequestLocationProofReply;
import proto.RequestLocationProofRequest;
import util.Coords;
import util.EncryptionLogic;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

public class ClientToClientFrontend {
    private final String username;
    private final Map<String, ManagedChannel> channelMap = new HashMap<>();
    private final Map<String, ClientToClientGrpc.ClientToClientStub> stubMap = new HashMap<>();
    private final ClientToServerFrontend serverFrontend;
    private final ClientLogic clientLogic;
    private final int responseQuorum = 2; //TODO: hardcoded value
    private volatile boolean gotQuorum = false;
    private volatile int proofsCount = 0;

    public ClientToClientFrontend(String username, ClientToServerFrontend serverFrontend, ClientLogic clientLogic) {
        this.username = username;
        this.clientLogic = clientLogic;
        this.serverFrontend = serverFrontend;
    }

    public void addUser(String username, String host, int port) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        channelMap.put(
                username,
                channel
        );


        stubMap.put(username, ClientToClientGrpc.newStub(channel));
    }

    public void broadcastAllInGrid(){
        for(Integer ep : clientLogic.getGrid().get(username).keySet()){
            broadcastProofRequest(ep);
        }
    }

    public void broadcastProofRequest(int epoch) {


        Coords coords = clientLogic.getCoords(epoch);
        List<String> closePeers = clientLogic.closePeers(epoch);


        /*send location report directly to server*/
        byte[][] message = clientLogic.generateLocationReport();

        serverFrontend.submitReport(message[0], message[1],message[2],message[3]);
        System.out.println("Report sent");

        /* Request proof of location to other close clients */
        for (String user : closePeers) {
            /* Create location proof request */
            stubMap.get(user).requestLocationProof(RequestLocationProofRequest.newBuilder().
                    setUsername(username)
                    .setEpoch(epoch)
                    .build(), new StreamObserver<RequestLocationProofReply>() {

                @Override
                public void onNext(RequestLocationProofReply requestLocationProofReply) {

                    /* Check if witness is a close peer */
                    byte[] proof = requestLocationProofReply.getProof().toByteArray();
                    JSONObject proofJSON = new JSONObject(new String(proof));
                    System.out.println("Received proof reply from " + proofJSON.getString("witnessUsername"));
                    if(!closePeers.contains(proofJSON.getString("witnessUsername"))){
                        System.out.println("Witness " + proofJSON.getString("witnessUsername") +" was not asked for a proof, is not a close peer");
                        return;
                    }

                    byte[] digitalSignature = requestLocationProofReply.getDigitalSignature().toByteArray();

                    //encrypt proof
                    byte[][] result = clientLogic.encryptProof(proof);
                    byte[] encryptedProof = result[0];
                    byte[] encryptedSessionKey = result[1];
                    byte[] iv = result[2];
                    byte[] witnessIv = requestLocationProofReply.getWitnessIv().toByteArray();
                    byte[] witnessSessionKey = requestLocationProofReply.getWitnessSessionKey().toByteArray();

                    JSONObject proofObject = new JSONObject();
                    //create a proof json object
                    proofObject.put("message", Base64.getEncoder().encodeToString(proof));
                    proofObject.put("digital_signature", Base64.getEncoder().encodeToString(digitalSignature));

                    proofsCount++;
                    /************/

                    //send proof as soon as it arrives
                    //TODO
                    serverFrontend.submitProof(encryptedProof, digitalSignature,encryptedSessionKey,iv,witnessSessionKey,witnessIv);


                    if (proofsCount == responseQuorum) {
                        gotQuorum = true;
                    }
                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onCompleted() {

                }
            });
        }
        System.out.println("Waiting for proofs quorum...");
        while (!gotQuorum) Thread.onSpinWait();

        System.out.println("Got response quorum");
        proofsCount = 0;
        gotQuorum = false;
    }

    public void shutdown() {
        for (ManagedChannel channel : channelMap.values()) {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}
