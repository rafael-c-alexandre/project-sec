package Client;

import Server.Proof;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import proto.*;
import org.json.JSONObject;
import util.Coords;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

public class ClientToServerFrontend {
    private final String username;
    private final Map<String, ManagedChannel> channelMap = new HashMap<>();
    private final Map<String, ClientToServerGrpc.ClientToServerStub> stubMap = new HashMap<>();
    private final ClientLogic clientLogic;

    private ArrayList<ObtainLocationReportReply> readListLocationReport = new ArrayList<ObtainLocationReportReply>(); //List of read replies while waiting for reply quorum
    private volatile boolean reportTimeoutExpired = false;
    private volatile boolean readTimeoutExpired = false;

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

    public void submitReport(int epoch, List<byte[][]> message) {

        for (byte[][] report: message) {
            byte[] encryptedMessage = report[0];
            byte[] digitalSignature = report[1];
            byte[] encryptedSessionKey = report[2];
            byte[] iv = report[3];
            byte[] proofOfWorkBytes = report[5];
            byte[] timestampBytes = report[6];

            String server = new String(report[4], StandardCharsets.UTF_8);

            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
            buffer.put(proofOfWorkBytes);
            buffer.flip();//need flip
            long proofOfWork = buffer.getLong();

            ByteBuffer buffer2 = ByteBuffer.allocate(Long.BYTES);
            buffer2.put(timestampBytes);
            buffer2.flip();//need flip
            long timestamp = buffer2.getLong();


            clientLogic.gotReportQuorums.putIfAbsent(epoch, new CopyOnWriteArrayList<>());
            ClientToServerGrpc.ClientToServerStub serverStub = stubMap.get(server);
            try {
                serverStub.submitLocationReport(SubmitLocationReportRequest.newBuilder()
                                .setEncryptedMessage(ByteString.copyFrom(encryptedMessage))
                                .setSignature(ByteString.copyFrom(digitalSignature))
                                .setIv(ByteString.copyFrom(iv))
                                .setEncryptedSessionKey(ByteString.copyFrom(encryptedSessionKey))
                                .setProofOfWork(proofOfWork)
                                .setTimestamp(timestamp)
                                .build(),
                        new StreamObserver<SubmitLocationReportReply>() {
                            @Override
                            public void onNext(SubmitLocationReportReply submitLocationReportReply) {
                                if (clientLogic.gotReportQuorums.get(epoch).contains(server)) {
                                    System.err.println("WARNING: gotReportQuorums replayed");
                                } else {
                                    clientLogic.gotReportQuorums.get(epoch).add(server);
                                }
                            }

                            @Override
                            public void onError(Throwable throwable) {

                            }

                            @Override
                            public void onCompleted() {

                            }
                        });
                System.out.println("Submited location report to server " + server);
            } catch (StatusRuntimeException e) {
                io.grpc.Status status = io.grpc.Status.fromThrowable(e);
                System.err.println("Exception received from server: " + status.getDescription());
            }
        }


        System.out.println("Waiting for submit report quorum...");

        long start = System.currentTimeMillis();
        while ((clientLogic.gotReportQuorums.get(epoch).size() != clientLogic.serverQuorum) && !reportTimeoutExpired) {
            long delta = System.currentTimeMillis() - start;
            if (delta > 10000) {
                reportTimeoutExpired = true;
                break;
            }
        }
        if (clientLogic.gotReportQuorums.get(epoch).size() == clientLogic.serverQuorum) {
            for (String name : clientLogic.gotReportQuorums.get(epoch))
                System.out.println("Got response quorum from server " + name + " for report submission, for epoch " + epoch);
        } else if (reportTimeoutExpired)
            System.err.println("Couldn't submit report within the time limit");
        else {
            System.err.println("SOMETHING IS WRONG");
        }
        clientLogic.gotReportQuorums.get(epoch).clear();
        reportTimeoutExpired = false;
    }

    public void submitProof(int epoch, byte[] encryptedProof, byte[] digitalSignature,byte[] encryptedSessionKey, byte[] iv, byte[] witnessSessionKey, byte[] witnessIv, String server, long timestamp, long proofOfWork) {
        //Submit proofs to every server
        ClientToServerGrpc.ClientToServerStub serverStub = stubMap.get(server);

        try {
            serverStub.submitLocationProof(
                    SubmitLocationProofRequest.newBuilder()
                            .setEncryptedProof(ByteString.copyFrom(encryptedProof))
                            .setSignature(ByteString.copyFrom(digitalSignature))
                            .setIv(ByteString.copyFrom(iv))
                            .setEncryptedSessionKey(ByteString.copyFrom(encryptedSessionKey))
                            .setWitnessIv(ByteString.copyFrom(witnessIv))
                            .setWitnessSessionKey(ByteString.copyFrom(witnessSessionKey))
                            .setProofOfWork(proofOfWork)
                            .setTimestamp(timestamp)
                            .build(), new StreamObserver<SubmitLocationProofReply>() {
                        @Override
                        public void onNext(SubmitLocationProofReply submitLocationProofReply) {
                            if (submitLocationProofReply.getReachedQuorum()) {
                                clientLogic.gotProofQuorums.get(epoch).add(server);
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

    public List<Proof> requestMyProofs(String username,List<Integer> epochs){

        byte[][] params = this.clientLogic.requestMyProofs(username,epochs);

        byte[] encryptedData = params[0];
        byte[] digitalSignature = params[1];
        byte[] encryptedSessionKey = params[2];
        byte[] iv = params[3];
        byte[] proofOfWorkBytes = params[4];
        byte[] timestampBytes = params[5];

        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(proofOfWorkBytes);
        buffer.flip();//need flip
        long proofOfWork = buffer.getLong();

        ByteBuffer buffer2 = ByteBuffer.allocate(Long.BYTES);
        buffer2.put(timestampBytes);
        buffer2.flip();//need flip
        long timestamp = buffer2.getLong();

        for (Map.Entry<String,ClientToServerGrpc.ClientToServerStub> server: stubMap.entrySet()) {

            server.getValue().requestMyProofs(
                    RequestMyProofsRequest.newBuilder()
                            .setEncryptedMessage(ByteString.copyFrom(encryptedData))
                            .setSignature(ByteString.copyFrom(digitalSignature))
                            .setTimestamp(timestamp)
                            .setProofOfWork(proofOfWork)
                            .setEncryptedSessionKey(ByteString.copyFrom(encryptedSessionKey))
                            .setIv(ByteString.copyFrom(iv))
                            .build(), new StreamObserver<RequestMyProofsReply>() {
                        @Override
                        public void onNext(RequestMyProofsReply requestMyProofsReply) {
                            //TODO
                            //Handle response
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

        return null;

    }

    public Coords obtainLocationReport(String username, int epoch) {

        byte[][] params = this.clientLogic.generateObtainLocationRequestParameters(username, epoch);

        byte[] encryptedData = params[0];
        byte[] digitalSignature = params[1];
        byte[] encryptedSessionKey = params[2];
        byte[] iv = params[3];
        byte[] sessionKeyBytes = params[4];
        byte[] proofOfWorkBytes = params[5];
        byte[] timestampBytes = params[6];

        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(proofOfWorkBytes);
        buffer.flip();//need flip
        long proofOfWork = buffer.getLong();

        ByteBuffer buffer2 = ByteBuffer.allocate(Long.BYTES);
        buffer2.put(timestampBytes);
        buffer2.flip();//need flip
        long timestamp = buffer2.getLong();



        clientLogic.gotReadQuorum.putIfAbsent(epoch, new CopyOnWriteArrayList<>());
        for (Map.Entry<String,ClientToServerGrpc.ClientToServerStub> server: stubMap.entrySet()) {
            server.getValue().obtainLocationReport(
                    ObtainLocationReportRequest.newBuilder()
                            .setMessage(ByteString.copyFrom(encryptedData))
                            .setSignature(ByteString.copyFrom(digitalSignature))
                            .setTimestamp(timestamp)
                            .setProofOfWork(proofOfWork)
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
                            clientLogic.gotReadQuorum.get(epoch).add(server.getKey());

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
        while ((clientLogic.gotReadQuorum.get(epoch).size()!=clientLogic.serverQuorum) && !readTimeoutExpired) {
            long delta = System.currentTimeMillis() - start;
            if (delta > 5000) {
                readTimeoutExpired = true;
                break;
            }
        }
        if (clientLogic.gotReadQuorum.get(epoch).size() == clientLogic.serverQuorum){
            for(String name: clientLogic.gotReadQuorum.get(epoch))
                System.out.println("Got response quorum from server "+ name + ", obtained location report");
        }
        else if (readTimeoutExpired)
            System.err.println("Couldn't obtain location report within the time limit");
        clientLogic.gotReadQuorum.get(epoch).clear();
        readTimeoutExpired = false;

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
