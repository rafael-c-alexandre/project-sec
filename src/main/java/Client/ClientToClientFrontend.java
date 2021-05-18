package Client;

import Exceptions.ProverNotCloseEnoughException;
import com.google.protobuf.ByteString;
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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ClientToClientFrontend {
    private final String username;
    private final Map<String, ManagedChannel> channelMap = new HashMap<>();
    private final Map<String, ClientToClientGrpc.ClientToClientStub> stubMap = new HashMap<>();
    private final ClientToServerFrontend serverFrontend;
    private final ClientLogic clientLogic;
    private volatile boolean proofTimeoutExpired = false;

    private final ArrayList<String> serverNames = new ArrayList<String>();

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

    public void addServer(String serverName) {
        serverNames.add(serverName);
    }

    public void broadcastAllInGrid(){

        try {
            //Wait for all users to connect
            Thread.sleep(5000);
            for(Integer ep : clientLogic.getGrid().get(username).keySet()){
                System.out.println("------\nBroadcast request for epoch " + ep);
                broadcastProofRequest(ep);
            }
            System.out.println("Finished execution, no more location reports left");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void broadcastProofRequest(int epoch) {

        //Coords coords = clientLogic.getCoords(epoch);
        List<String> closePeers = clientLogic.closePeers(epoch);

        /*send location report directly to server*/
        List<byte[][]> message = clientLogic.generateLocationReport(epoch);

        //Submits the report request to the servers, to indicate that the client will start submitting proofs
        serverFrontend.submitReport(epoch, message);

        clientLogic.gotProofQuorums.putIfAbsent(epoch, new CopyOnWriteArrayList<>());

        /* Request proof of location to other close clients */
        for (String user : closePeers) {
            /* Create location proof request */

            JSONObject requestJson = new JSONObject();
            requestJson.put("username", username);
            requestJson.put("epoch", epoch);

            //generate request digital signature
            byte[] ds = clientLogic.generateDigitalSignature(requestJson.toString().getBytes());

            stubMap.get(user).requestLocationProof(RequestLocationProofRequest.newBuilder()
                    .setRequest(requestJson.toString())
                    .setDigitalSignature(ByteString.copyFrom(ds))
                    .build(), new StreamObserver<RequestLocationProofReply>() {

                @Override
                public void onNext(RequestLocationProofReply requestLocationProofReply) {

                    /* Check if witness is a close peer */
                    List<byte[]> proofs = requestLocationProofReply.getProofList().stream().map(ByteString::toByteArray).collect(Collectors.toList());
                    List<byte[]> digitalSignatures = requestLocationProofReply.getDigitalSignatureList().stream().map(ByteString::toByteArray).collect(Collectors.toList());
                    List<byte[]> witnessSessionKeys = requestLocationProofReply.getWitnessSessionKeyList().stream().map(ByteString::toByteArray).collect(Collectors.toList());
                    List<byte[]> witnessIvs = requestLocationProofReply.getWitnessIvList().stream().map(ByteString::toByteArray).collect(Collectors.toList());
                    List<String> servers = requestLocationProofReply.getServerList();


                    for (int i = 0; i < proofs.size(); i++) {
                        JSONObject proofJSON = new JSONObject(new String(proofs.get(i)));
                        System.out.println("Received proof reply from " + proofJSON.getString("witnessUsername") + " for epoch " + epoch + " to server " + serverNames.get(i));
                        if (!closePeers.contains(proofJSON.getString("witnessUsername"))) {
                            System.out.println("Witness " + proofJSON.getString("witnessUsername") + " was not asked for a proof, is not a close peer");
                            return;
                        }

                        byte[] witnessDigitalSignature = digitalSignatures.get(i);

                        //verify proof digital signature
                        if (!EncryptionLogic.verifyDigitalSignature(proofs.get(i), witnessDigitalSignature, EncryptionLogic.getPublicKey(user))) {
                            System.err.println("Error verifying proof's digital signature. Skipped.");
                            return;
                        }


                        JSONObject proofObject = new JSONObject();
                        //create a proof json object
                        proofObject.put("proof", proofJSON);
                        proofObject.put("digital_signature", Base64.getEncoder().encodeToString(witnessDigitalSignature));

                        //encrypt proof
                        byte[][] result = clientLogic.encryptProof(proofObject.toString().getBytes(), servers.get(i));
                        byte[] encryptedProof = result[0];
                        byte[] encryptedSessionKey = result[1];
                        byte[] iv = result[2];
                        byte[] digitalSignature = result[3];
                        byte[] proofOfWorkBytes = result[4];
                        byte[] timestampBytes = result[5];

                        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
                        buffer.put(proofOfWorkBytes);
                        buffer.flip();//need flip
                        long proofOfWork = buffer.getLong();

                        ByteBuffer buffer2 = ByteBuffer.allocate(Long.BYTES);
                        buffer2.put(timestampBytes);
                        buffer2.flip();//need flip
                        long timestamp = buffer2.getLong();

                        byte[] witnessIv = witnessIvs.get(i);
                        byte[] witnessSessionKey = witnessSessionKeys.get(i);

                        //Submit the proof received from the witness to the servers
                        serverFrontend.submitProof(epoch, encryptedProof, digitalSignature, encryptedSessionKey, iv, witnessSessionKey, witnessIv, servers.get(i), timestamp, proofOfWork);
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    io.grpc.Status status = io.grpc.Status.fromThrowable(throwable);
                    System.err.println("Exception received from peer client: " + status.getDescription());
                }

                @Override
                public void onCompleted() {

                }
            });
        }

        //Wait for a quorum of servers that respond with a "gotQuorum", meaning they received enough proofs to accept the report
        System.out.println("Waiting for proofs quorum...");

        long start = System.currentTimeMillis();
        while ((clientLogic.gotProofQuorums.get(epoch).size()!=clientLogic.serverQuorum) && !proofTimeoutExpired) {
            long delta = System.currentTimeMillis() - start;
            if (delta > 10000) {
                proofTimeoutExpired = true;
                break;
            }
        }
        if (clientLogic.gotProofQuorums.get(epoch).size() == clientLogic.serverQuorum){
            for(String name: clientLogic.gotProofQuorums.get(epoch))
                System.out.println("Got proof quorum from server "+ name + ", ");// for epoch " + epoch + " by the server");
        }
        else if (proofTimeoutExpired)
            System.err.println("Couldn't prove location within the time limit");
        clientLogic.gotProofQuorums.get(epoch).clear();
        proofTimeoutExpired = false;

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
