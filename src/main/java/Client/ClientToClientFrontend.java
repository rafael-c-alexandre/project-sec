package Client;

import Exceptions.ProverNotCloseEnoughException;
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
    private volatile boolean gotQuorum = false;
    private volatile boolean timeoutExpired = false;

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

        try {
            //Wait for all users to connect
            Thread.sleep(5000);
            for(Integer ep : clientLogic.getGrid().get(username).keySet()){
                System.out.println("Broadcast request for epoch " + ep +"\n");
                broadcastProofRequest(ep);
                Thread.sleep(5000);
                }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void broadcastProofRequest(int epoch) {


        //Coords coords = clientLogic.getCoords(epoch);
        List<String> closePeers = clientLogic.closePeers(epoch);


        /*send location report directly to server*/
        byte[][] message = clientLogic.generateLocationReport(epoch);

        //TODO: return
        serverFrontend.submitReport(message[0], message[1],message[2],message[3]);
        System.out.println("Report sent to server");

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
                    if (!closePeers.contains(proofJSON.getString("witnessUsername"))) {
                        System.out.println("Witness " + proofJSON.getString("witnessUsername") + " was not asked for a proof, is not a close peer");
                        return;
                    }

                    byte[] witnessDigitalSignature = requestLocationProofReply.getDigitalSignature().toByteArray();

                    JSONObject proofObject = new JSONObject();
                    //create a proof json object
                    proofObject.put("proof", proofJSON);
                    proofObject.put("digital_signature", Base64.getEncoder().encodeToString(witnessDigitalSignature));

                    //encrypt proof
                    byte[][] result = clientLogic.encryptProof(proofObject.toString().getBytes());
                    byte[] encryptedProof = result[0];
                    byte[] encryptedSessionKey = result[1];
                    byte[] iv = result[2];
                    byte[] digitalSignature = result[3];

                    byte[] witnessIv = requestLocationProofReply.getWitnessIv().toByteArray();
                    byte[] witnessSessionKey = requestLocationProofReply.getWitnessSessionKey().toByteArray();

                    gotQuorum = serverFrontend.submitProof(encryptedProof, digitalSignature, encryptedSessionKey, iv, witnessSessionKey, witnessIv);
                }

                @Override
                public void onError(Throwable throwable) {
                    io.grpc.Status status = io.grpc.Status.fromThrowable(throwable);
                    System.out.println("Exception received from server: " + status.getDescription());
                }

                @Override
                public void onCompleted() {

                }
            });
        }

        System.out.println("Waiting for proofs quorum...");

        long start = System.currentTimeMillis();
        while (!gotQuorum && !timeoutExpired ) {
            long delta = System.currentTimeMillis() - start;
            if(delta > 5000){
                timeoutExpired = true;
                break;
            }
        }

        if (gotQuorum)
            System.out.println("Got response quorum");
        else if (timeoutExpired)
            System.err.println("Couldn't prove location within the time limit");
        gotQuorum = false;
        timeoutExpired = false;
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
