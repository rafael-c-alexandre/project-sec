package Client;

import Server.Proof;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import javafx.util.Pair;
import org.json.JSONArray;
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
    private volatile boolean writebackTimeoutExpired = false;

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
            byte[] proofOfWork = report[4];
            String server = new String(report[5], StandardCharsets.UTF_8);

            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
            buffer.put(proofOfWork);
            buffer.flip();//need flip
            long nonce = buffer.getLong();

            clientLogic.gotReportQuorums.putIfAbsent(epoch, new CopyOnWriteArrayList<>());
            ClientToServerGrpc.ClientToServerStub serverStub = stubMap.get(server);
            try {
                serverStub.submitLocationReport(SubmitLocationReportRequest.newBuilder()
                                .setEncryptedMessage(ByteString.copyFrom(encryptedMessage))
                                .setSignature(ByteString.copyFrom(digitalSignature))
                                .setIv(ByteString.copyFrom(iv))
                                .setEncryptedSessionKey(ByteString.copyFrom(encryptedSessionKey))
                                .setProofOfWork(nonce)
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

    public void submitProof(int epoch, byte[] encryptedProof, byte[] digitalSignature,byte[] encryptedSessionKey, byte[] iv, byte[] witnessSessionKey, byte[] witnessIv, String server) {
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

        for (Map.Entry<String,ClientToServerGrpc.ClientToServerStub> server: stubMap.entrySet()) {

            server.getValue().requestMyProofs(
                    RequestMyProofsRequest.newBuilder()
                            .setEncryptedMessage(ByteString.copyFrom(encryptedData))
                            .setSignature(ByteString.copyFrom(digitalSignature))
                            .setTimestamp(System.currentTimeMillis())
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

    public Coords obtainLocationReport(String username, int epoch, String requestUid) {

        List<byte[][]> requests = this.clientLogic.generateObtainLocationRequestParameters(username, epoch, requestUid);

        clientLogic.gotReadQuorum.putIfAbsent(requestUid, new CopyOnWriteArrayList<>());

        for (byte[][] params : requests) {

            byte[] encryptedData = params[0];
            byte[] digitalSignature = params[1];
            byte[] encryptedSessionKey = params[2];
            byte[] iv = params[3];
            byte[] sessionKeyBytes = params[4];
            String server = new String(params[5], StandardCharsets.UTF_8);

            ClientToServerGrpc.ClientToServerStub serverStub = stubMap.get(server);
            serverStub.obtainLocationReport(
                        ObtainLocationReportRequest.newBuilder()
                                .setMessage(ByteString.copyFrom(encryptedData))
                                .setSignature(ByteString.copyFrom(digitalSignature))
                                .setTimestamp(System.currentTimeMillis())
                                .setEncryptedSessionKey(ByteString.copyFrom(encryptedSessionKey))
                                .setIv(ByteString.copyFrom(iv))
                                .build(), new StreamObserver<ObtainLocationReportReply>() {
                            @Override
                            public void onNext(ObtainLocationReportReply obtainLocationReportReply) {

                                JSONObject report = clientLogic.verifyLocationReportResponse(obtainLocationReportReply.getMessage().toByteArray(),
                                        obtainLocationReportReply.getSignature().toByteArray(),
                                        obtainLocationReportReply.getEncryptedSessionKey().toByteArray(),
                                        obtainLocationReportReply.getIv().toByteArray(), server, epoch, requestUid);

                                clientLogic.gotReadQuorum.get(requestUid).add(server);

                                //report is != null only if it is balid
                                if (report != null && !clientLogic.readRequests.containsKey(requestUid))
                                    clientLogic.readRequests.put(requestUid, report);
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                System.err.println("Caught '" + throwable.getMessage() + "' from server " + server);
                            }

                            @Override
                            public void onCompleted() {

                            }
                        }
                );
        }

        System.out.println("Obtaining location reports...");

        long start = System.currentTimeMillis();
        while (!clientLogic.readRequests.containsKey(requestUid) && !readTimeoutExpired ) {
            long delta = System.currentTimeMillis() - start;
            if (delta > 5000) {
                readTimeoutExpired = true;
                break;
            }
        }
        if (clientLogic.readRequests.containsKey(requestUid)){
            System.out.println("Got valid response for request with id: " + requestUid);
        }
        else if (readTimeoutExpired) {
            System.err.println("Couldn't obtain location report within the time limit");
            clientLogic.gotReadQuorum.get(requestUid).clear();
            readTimeoutExpired = false;
            return null;
        }

        if (!this.clientLogic.readRequests.containsKey(requestUid)) {
            System.err.println("Something went wrong");
            return null;
        }
        
        // writeback phase atomic operation
        writeBackToServers(this.clientLogic.readRequests.get(requestUid), epoch);

        return new Coords(this.clientLogic.readRequests.get(requestUid).getInt("x"),
                this.clientLogic.readRequests.get(requestUid).getInt("y"));

    }

    private void writeBackToServers(JSONObject jsonObject, int epoch) {


        List<byte[][]> response = this.clientLogic.generateWritebackMessage(jsonObject, epoch);

        System.out.println("Sending writeback request...");
        //use the same uid for the writeback phase as for the obtain report request
        String uid = jsonObject.getString("uid");


        for (byte[][] report : response) {
            byte[] encryptedMessage = report[0];
            byte[] digitalSignature = report[1];
            byte[] encryptedSessionKey = report[2];
            byte[] iv = report[3];
            String server = new String(report[4], StandardCharsets.UTF_8);

            //byte[] proofOfWork = report[4];

            //ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
            //buffer.put(proofOfWork);
            //buffer.flip();//need flip
            //long nonce = buffer.getLong();

            clientLogic.gotReportQuorums.putIfAbsent(epoch, new CopyOnWriteArrayList<>());
            ClientToServerGrpc.ClientToServerStub serverStub = stubMap.get(server);
            try {
                serverStub.writeBack(WriteBackRequest.newBuilder()
                                .setMessage(ByteString.copyFrom(encryptedMessage))
                                .setSignature(ByteString.copyFrom(digitalSignature))
                                .setIv(ByteString.copyFrom(iv))
                                .setEncryptedSessionKey(ByteString.copyFrom(encryptedSessionKey))
                                //.setProofOfWork(nonce)
                                .build(),
                        new StreamObserver<WriteBackReply>() {
                            @Override
                            public void onNext(WriteBackReply writeBackReply) {
                                if (clientLogic.gotWriteBackQuorum.get(uid).contains(server)) {
                                    System.err.println("WARNING: gotReportQuorums replayed");
                                } else {
                                    clientLogic.gotWriteBackQuorum.get(uid).add(server);
                                }
                            }

                            @Override
                            public void onError(Throwable throwable) {

                            }

                            @Override
                            public void onCompleted() {

                            }
                        });
                System.out.println("Submited writeback location report to server " + server);
            } catch (StatusRuntimeException e) {
                io.grpc.Status status = io.grpc.Status.fromThrowable(e);
                System.err.println("Exception received from server: " + status.getDescription());
            }
        }


        System.out.println("Waiting for writeback submit quorum...");

        long start = System.currentTimeMillis();
        while ((clientLogic.gotWriteBackQuorum.get(uid).size() != clientLogic.serverQuorum) && !writebackTimeoutExpired) {
            long delta = System.currentTimeMillis() - start;
            if (delta > 10000) {
                writebackTimeoutExpired = true;
                break;
            }
        }
        if (clientLogic.gotWriteBackQuorum.get(uid).size() == clientLogic.serverQuorum) {
            for (String name : clientLogic.gotWriteBackQuorum.get(uid))
                System.out.println("Got response quorum from server " + name + " for report submission, for epoch " + epoch);
        } else if (writebackTimeoutExpired)
            System.err.println("Couldn't submit report within the time limit");
        else {
            System.err.println("SOMETHING IS WRONG");
        }
        clientLogic.gotWriteBackQuorum.get(uid).clear();
        writebackTimeoutExpired = false;


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
