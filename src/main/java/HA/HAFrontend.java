package HA;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.json.JSONObject;
import proto.ClientToServerGrpc;
import proto.HA.*;
import proto.ObtainLocationReportReply;
import proto.WriteBackReply;
import proto.WriteBackRequest;
import util.Coords;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

public class HAFrontend {
    private final Map<String, ManagedChannel> channelMap = new HashMap<>();
    private final Map<String, HAToServerGrpc.HAToServerStub> stubMap = new HashMap<>();
    private final HALogic haLogic;
    private volatile boolean readTimeoutExpired = false;
    private volatile boolean writebackTimeoutExpired = false;

    public HAFrontend(HALogic haLogic) {
        this.haLogic = haLogic;
    }

    public void addServer(String username, String host, int port) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        channelMap.put(
                username,
                channel
        );
        stubMap.put(username, HAToServerGrpc.newStub(channel));
    }


    public List<String> obtainUsersAtLocation(int x, int y, int epoch) {
        /*byte[][] params = this.haLogic.generateObtainUsersAtLocationRequestParameters(x, y, epoch);

        byte[] encryptedData = params[0];
        byte[] digitalSignature = params[1];
        byte[] encryptedSessionKey = params[2];
        byte[] iv = params[3];
        byte[] sessionKeyBytes = params[4];

        ObtainUsersAtLocationReply reply = this.blockingStub.obtainUsersAtLocation(
                ObtainUsersAtLocationRequest.newBuilder()
                        .setMessage(ByteString.copyFrom(encryptedData))
                        .setSignature(ByteString.copyFrom(digitalSignature))
                        .setTimestamp(System.currentTimeMillis())
                        .setEncryptedSessionKey(ByteString.copyFrom(encryptedSessionKey))
                        .setIv(ByteString.copyFrom(iv))
                        .build()
        );

        byte[] encryptedResponse = reply.getMessage().toByteArray();
        byte[] responseSignature = reply.getSignature().toByteArray();
        byte[] responseIv = reply.getIv().toByteArray();

        return this.haLogic.getUsersFromReply(sessionKeyBytes, encryptedResponse, responseSignature, responseIv);*/
        return new ArrayList<>();

    }

    public Coords obtainLocationReport(String username, int epoch, String requestUid) {

        List<byte[][]> requests = this.haLogic.generateObtainLocationRequestParameters(username, epoch, requestUid);


        haLogic.gotReadQuorum.putIfAbsent(requestUid, new CopyOnWriteArrayList<>());


        for (byte[][] params : requests) {

            byte[] encryptedData = params[0];
            byte[] digitalSignature = params[1];
            byte[] encryptedSessionKey = params[2];
            byte[] iv = params[3];
            String server = new String(params[4], StandardCharsets.UTF_8);

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

            HAToServerGrpc.HAToServerStub serverStub = stubMap.get(server);
            serverStub.obtainLocationReport(
                    proto.HA.ObtainLocationReportRequest.newBuilder()
                            .setMessage(ByteString.copyFrom(encryptedData))
                            .setSignature(ByteString.copyFrom(digitalSignature))
                            .setTimestamp(timestamp)
                            .setProofOfWork(proofOfWork)
                            .setEncryptedSessionKey(ByteString.copyFrom(encryptedSessionKey))
                            .setIv(ByteString.copyFrom(iv))
                            .build(), new StreamObserver<proto.HA.ObtainLocationReportReply>() {
                        @Override
                        public void onNext(proto.HA.ObtainLocationReportReply obtainLocationReportReply) {

                            JSONObject report = haLogic.verifyLocationReportResponse(obtainLocationReportReply.getMessage().toByteArray(),
                                    obtainLocationReportReply.getSignature().toByteArray(),
                                    obtainLocationReportReply.getEncryptedSessionKey().toByteArray(),
                                    obtainLocationReportReply.getIv().toByteArray(), server, epoch, requestUid, username);

                            haLogic.gotReadQuorum.get(requestUid).add(server);

                            //report is != null only if it is balid
                            if (report != null && !haLogic.readRequests.containsKey(requestUid))
                                haLogic.readRequests.put(requestUid, report);
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
        while (!haLogic.readRequests.containsKey(requestUid) && !readTimeoutExpired ) {
            long delta = System.currentTimeMillis() - start;
            if (delta > 5000) {
                readTimeoutExpired = true;
                break;
            }
        }
        if (haLogic.readRequests.containsKey(requestUid)){
            System.out.println("Got valid response for request with id: " + requestUid);
        }
        else if (readTimeoutExpired) {
            System.err.println("Couldn't obtain location report within the time limit");
            haLogic.gotReadQuorum.get(requestUid).clear();
            readTimeoutExpired = false;
            return null;
        }

        if (!this.haLogic.readRequests.containsKey(requestUid)) {
            System.err.println("Something went wrong");
            return null;
        }



        new Thread(new Runnable() {
            @Override
            public void run() {
                // writeback phase atomic operation
                writeBackToServers(haLogic.readRequests.get(requestUid), epoch, username);
            }
        }).start();

        JSONObject reportObject = this.haLogic.readRequests.get(requestUid).getJSONObject("report").getJSONObject("report_info");

        return new Coords(reportObject.getInt("x"),
                reportObject.getInt("y"));

    }

    private void writeBackToServers(JSONObject jsonObject, int epoch, String username) {

        List<byte[][]> response = this.haLogic.generateWritebackMessage(jsonObject, epoch);

        System.out.println("Sending writeback request...");
        //use the same uid for the writeback phase as for the obtain report request
        String uid = jsonObject.getString("uid");


        for (byte[][] report : response) {
            byte[] encryptedMessage = report[0];
            byte[] digitalSignature = report[1];
            byte[] encryptedSessionKey = report[2];
            byte[] iv = report[3];
            String server = new String(report[4], StandardCharsets.UTF_8);

            byte[] proofOfWorkBytes = report[5];
            byte[] timestampBytes = report[6];

            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
            buffer.put(proofOfWorkBytes);
            buffer.flip();//need flip
            long proofOfWork = buffer.getLong();

            ByteBuffer buffer2 = ByteBuffer.allocate(Long.BYTES);
            buffer2.put(timestampBytes);
            buffer2.flip();//need flip
            long timestamp = buffer2.getLong();


            haLogic.gotWriteBackQuorum.putIfAbsent(uid, new CopyOnWriteArrayList<>());
            HAToServerGrpc.HAToServerStub serverStub = stubMap.get(server);
            try {
                serverStub.writeBack(proto.HA.WriteBackRequest.newBuilder()
                                .setMessage(ByteString.copyFrom(encryptedMessage))
                                .setSignature(ByteString.copyFrom(digitalSignature))
                                .setIv(ByteString.copyFrom(iv))
                                .setEncryptedSessionKey(ByteString.copyFrom(encryptedSessionKey))
                                .setProofOfWork(proofOfWork)
                                .setTimestamp(timestamp)
                                .build(),
                        new StreamObserver<proto.HA.WriteBackReply>() {
                            @Override
                            public void onNext(proto.HA.WriteBackReply writeBackReply) {
                                if (haLogic.gotWriteBackQuorum.get(uid).contains(server)) {
                                    System.err.println("WARNING: gotReportQuorums replayed");
                                } else {
                                    haLogic.gotWriteBackQuorum.get(uid).add(server);
                                }
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                System.err.println("Caught '" + throwable.getMessage() + "' from server " + server);
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
        while ((haLogic.gotWriteBackQuorum.get(uid).size() != haLogic.serverQuorum) && !writebackTimeoutExpired) {
            long delta = System.currentTimeMillis() - start;
            if (delta > 10000) {
                writebackTimeoutExpired = true;
                break;
            }
        }
        if (haLogic.gotWriteBackQuorum.get(uid).size() == haLogic.serverQuorum) {
            for (String name : haLogic.gotWriteBackQuorum.get(uid))
                System.out.println("Got response quorum from server " + name + " for report submission, for epoch " + epoch);
        } else if (writebackTimeoutExpired)
            System.err.println("Couldn't submit report within the time limit");
        else {
            System.err.println("SOMETHING IS WRONG");
        }
        haLogic.gotWriteBackQuorum.get(uid).clear();
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
