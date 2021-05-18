package Client;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import proto.*;
import org.json.JSONObject;
import util.Coords;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ClientToServerFrontend {
    private final String username;
    private final Map<String, ManagedChannel> channelMap = new HashMap<>();
    private final Map<String, ClientToServerGrpc.ClientToServerStub> stubMap = new HashMap<>();
    private final ClientLogic clientLogic;

    private ArrayList<ObtainLocationReportReply> readListLocationReport = new ArrayList<ObtainLocationReportReply>(); //List of read replies while waiting for reply quorum
    private ArrayList<String> gotQuorums = new ArrayList<String>(); //Name of the server from who we received a gotQuorum for the current report
    private final int serverQuorum = 2; //minimum number of servers that need to accept the
    private volatile boolean timeoutExpired = false;

    public ClientToServerFrontend(String username, ClientLogic clientLogic) {
        this.username = username;
        //this.channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        //this.blockingStub = ClientToServerGrpc.newBlockingStub(channel);
        this.clientLogic = clientLogic;
    }

    public void addServer(String username, String host, int port) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        channelMap.put(
                username,
                channel
        );
        stubMap.put(username, ClientToServerGrpc.newStub(channel));
    }

    public void submitReport(byte[] encryptedMessage,byte[] digitalSignature,byte[] encryptedSessionKey, byte[] iv) {
        for (Map.Entry<String,ClientToServerGrpc.ClientToServerStub> server: stubMap.entrySet()) {
            try {
                server.getValue().submitLocationReport(SubmitLocationReportRequest.newBuilder()
                                .setEncryptedMessage(ByteString.copyFrom(encryptedMessage))
                                .setSignature(ByteString.copyFrom(digitalSignature))
                                .setIv(ByteString.copyFrom(iv))
                                .setEncryptedSessionKey(ByteString.copyFrom(encryptedSessionKey))
                                .build(),
                        new StreamObserver<SubmitLocationReportReply>() {
                            @Override
                            public void onNext(SubmitLocationReportReply submitLocationReportReply) {

                            }

                            @Override
                            public void onError(Throwable throwable) {

                            }

                            @Override
                            public void onCompleted() {

                            }
                        });
                System.out.println("Submited location report to server " + server.getKey());
            } catch (StatusRuntimeException e) {
                io.grpc.Status status = io.grpc.Status.fromThrowable(e);
                System.err.println("Exception received from server: " + status.getDescription());
            }
        }
    }


    public void submitProof(byte[] encryptedProof, byte[] digitalSignature,byte[] encryptedSessionKey, byte[] iv, byte[] witnessSessionKey, byte[] witnessIv) {
        //Submit proofs to every server
        for (Map.Entry<String,ClientToServerGrpc.ClientToServerStub> server: stubMap.entrySet()) {
            try {
                server.getValue().submitLocationProof(
                        SubmitLocationProofRequest.newBuilder()
                                .setEncryptedProof(ByteString.copyFrom(encryptedProof))
                                .setSignature(ByteString.copyFrom(digitalSignature))
                                .setIv(ByteString.copyFrom(iv))
                                .setEncryptedSessionKey(ByteString.copyFrom(encryptedSessionKey))
                                .setWitnessIv(ByteString.copyFrom(witnessIv))
                                .setWitnessSessionKey(ByteString.copyFrom(witnessSessionKey))
                                .build(), new StreamObserver<SubmitLocationProofReply>() {
                            @Override
                            public void onNext(SubmitLocationProofReply submitLocationProofReply) {
                                if (submitLocationProofReply.getReachedQuorum()) {
                                    gotQuorums.add(server.getKey());
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
            } catch (StatusRuntimeException e) {
                io.grpc.Status status = io.grpc.Status.fromThrowable(e);
                System.err.println("Exception received from server: " + status.getDescription());
            }
        }

        System.out.println("Waiting for proofs quorum...");

        long start = System.currentTimeMillis();
        while (!(gotQuorums.size()!=serverQuorum) && !timeoutExpired) {
            long delta = System.currentTimeMillis() - start;
            if (delta > 5000) {
                timeoutExpired = true;
                break;
            }
        }
        if (gotQuorums.size() == serverQuorum){
            for(String name: gotQuorums)
                System.out.println("Got response quorum from server "+ name + ", location report confirmed");// for epoch " + epoch + " by the server");
        }
        else if (timeoutExpired)
            System.err.println("Couldn't prove location within the time limit");
        gotQuorums.clear();
        timeoutExpired = false;
    }

    public Coords obtainLocationReport(String username, int epoch) {

        byte[][] params = this.clientLogic.generateObtainLocationRequestParameters(username, epoch);

        byte[] encryptedData = params[0];
        byte[] digitalSignature = params[1];
        byte[] encryptedSessionKey = params[2];
        byte[] iv = params[3];
        byte[] sessionKeyBytes = params[4];

        for (Map.Entry<String,ClientToServerGrpc.ClientToServerStub> server: stubMap.entrySet()) {
            server.getValue().obtainLocationReport(
                    ObtainLocationReportRequest.newBuilder()
                            .setMessage(ByteString.copyFrom(encryptedData))
                            .setSignature(ByteString.copyFrom(digitalSignature))
                            .setEncryptedSessionKey(ByteString.copyFrom(encryptedSessionKey))
                            .setIv(ByteString.copyFrom(iv))
                            .build(), new StreamObserver<ObtainLocationReportReply>() {
                        @Override
                        public void onNext(ObtainLocationReportReply obtainLocationReportReply) {
                            //TODO
                            //Validate signature
                            //if valid add to the readList
                            //When readlist > quorum return the value with the highest timestamp to the client
                            readListLocationReport.add(obtainLocationReportReply);

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

        System.out.println("Obtaining location reports...");

        long start = System.currentTimeMillis();
        while (!(gotQuorums.size()!=serverQuorum) && !timeoutExpired) {
            long delta = System.currentTimeMillis() - start;
            if (delta > 5000) {
                timeoutExpired = true;
                break;
            }
        }
        if (gotQuorums.size() == serverQuorum){
            for(String name: gotQuorums)
                System.out.println("Got response quorum from server "+ name + ", obtained location report");
        }
        else if (timeoutExpired)
            System.err.println("Couldn't obtain location report within the time limit");
        gotQuorums.clear();
        timeoutExpired = false;

        //TODO currently retuning first from readList
        ObtainLocationReportReply r = readListLocationReport.get(0);
        byte[] encryptedResponse = r.getMessage().toByteArray();
        byte[] responseSignature = r.getSignature().toByteArray();
        byte[] responseIv = r.getIv().toByteArray();
        return this.clientLogic.getCoordsFromReply(sessionKeyBytes, encryptedResponse, responseSignature, responseIv);

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
