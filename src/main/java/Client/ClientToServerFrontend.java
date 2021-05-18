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
import util.EncryptionLogic;

import javax.crypto.SecretKey;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ClientToServerFrontend {
    private final String username;
    private final Map<String, ManagedChannel> channelMap = new HashMap<>();
    private final Map<String, ClientToServerGrpc.ClientToServerStub> stubMap = new HashMap<>();
    private final ClientLogic clientLogic;

    private ArrayList<ObtainLocationReportReply> readListLocationReport = new ArrayList<ObtainLocationReportReply>(); //List of read replies while waiting for reply quorum
    private volatile boolean reportTimeoutExpired = false;
    private volatile boolean readTimeoutExpired = false;
    private volatile boolean myProofsTimeoutExpired = false;
    private volatile boolean writebackTimeoutExpired = false;

    private Map<String, ArrayList<Proof>> myProofsReceived = new HashMap<>();


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
                            System.err.println("Caught '" + throwable.getMessage() + "' from server " + server);
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

    public List<Proof> requestMyProofs(String readId, String username,List<Integer> epochs){

        List<byte[][]> proofList = this.clientLogic.requestMyProofs(readId, username,epochs);

        for(byte[][] params: proofList){
            byte[] encryptedData = params[0];
            byte[] digitalSignature = params[1];
            byte[] encryptedSessionKey = params[2];
            byte[] iv = params[3];
            byte[] sessionKeyBytes = params[4];
            byte[] proofOfWorkBytes = params[6];
            byte[] timestampBytes = params[7];

            String server = new String(params[5], StandardCharsets.UTF_8);

            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
            buffer.put(proofOfWorkBytes);
            buffer.flip();//need flip
            long proofOfWork = buffer.getLong();

            ByteBuffer buffer2 = ByteBuffer.allocate(Long.BYTES);
            buffer2.put(timestampBytes);
            buffer2.flip();//need flip
            long timestamp = buffer2.getLong();

            clientLogic.gotMyProofsQuorum.putIfAbsent(readId, new CopyOnWriteArrayList<>());
            clientLogic.myProofsResponses.putIfAbsent(readId, new CopyOnWriteArrayList<>());
            ClientToServerGrpc.ClientToServerStub serverStub = stubMap.get(server);
            try{
                serverStub.requestMyProofs(
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
                            //Verify if read ID is the same the expected readID
                            byte[] encryptedResponse = requestMyProofsReply.getMessage().toByteArray();
                            byte[] responseSignature = requestMyProofsReply.getSignature().toByteArray();
                            byte[] responseIv = requestMyProofsReply.getIv().toByteArray();

                            //Decrypt response
                            SecretKey sessionKey = EncryptionLogic.bytesToAESKey(sessionKeyBytes);
                            byte[] response = EncryptionLogic.decryptWithAES(sessionKey, encryptedResponse, responseIv);

                            //Verify response signature
                            if (!EncryptionLogic.verifyDigitalSignature(response, responseSignature, EncryptionLogic.getPublicKey(server)))
                                System.err.println("Invalid signature from My Proofs response");
                            else
                                System.err.println("Valid signature from My Proofs response");

                            //process response to get read ID
                            String jsonString = new String(response);
                            JSONObject jsonObject = new JSONObject(jsonString);
                            String receivedReadId = jsonObject.getString("readId");

                            if (receivedReadId.equals(readId)) {
                                //Add proofs received
                                JSONArray receivedProofsJson = jsonObject.getJSONArray("proofList");
                                for(int i = 0; i < receivedProofsJson.length(); i++){
                                    JSONObject proofJSON = receivedProofsJson.getJSONObject(i);
                                    clientLogic.myProofsResponses.get(readId).add(proofJSON);
                                }
                                clientLogic.gotMyProofsQuorum.get(readId).add(server);

                            } else {
                                System.out.println("Received WRONG read ID in myproof request");
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

        System.out.println("Waiting for my proofs quorum...");

        long start = System.currentTimeMillis();
        while ((clientLogic.gotMyProofsQuorum.get(readId).size() != clientLogic.serverQuorum) && !myProofsTimeoutExpired) {
            long delta = System.currentTimeMillis() - start;
            if (delta > 10000) {
                myProofsTimeoutExpired = true;
                break;
            }
        }
        if (clientLogic.gotMyProofsQuorum.get(readId).size() == clientLogic.serverQuorum) {
            System.out.println("Got response quorum for my proofs request");
        } else if (myProofsTimeoutExpired)
            System.err.println("Couldn't get my proofs the time limit");
        else {
            System.err.println("SOMETHING IS WRONG");
        }
        //Get proofs
        List<Proof> proofs = clientLogic.getMyProofs(readId, epochs);

        clientLogic.gotMyProofsQuorum.get(readId).clear();
        myProofsTimeoutExpired = false;

        return proofs;
    }

    public Coords obtainLocationReport(String username, int epoch, String requestUid) {

        List<byte[][]> requests = this.clientLogic.generateObtainLocationRequestParameters(username, epoch, requestUid);


        clientLogic.gotReadQuorum.putIfAbsent(requestUid, new CopyOnWriteArrayList<>());


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

            ClientToServerGrpc.ClientToServerStub serverStub = stubMap.get(server);
            serverStub.obtainLocationReport(
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



        new Thread(new Runnable() {
            @Override
            public void run() {
                // writeback phase atomic operation
                writeBackToServers(clientLogic.readRequests.get(requestUid), epoch);
            }
        }).start();

        JSONObject reportObject = this.clientLogic.readRequests.get(requestUid).getJSONObject("message");

        return new Coords(reportObject.getInt("x"),
                reportObject.getInt("y"));

    }

    private void writeBackToServers(JSONObject jsonObject, int epoch) {


        List<byte[][]> response = this.clientLogic.generateWritebackMessage(jsonObject, epoch);

        System.out.println("Sending writeback request...");
        //use the same uid for the writeback phase as for the obtain report request
        String uid = jsonObject.getJSONObject("message").getString("uid");


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


            clientLogic.gotWriteBackQuorum.putIfAbsent(uid, new CopyOnWriteArrayList<>());
            ClientToServerGrpc.ClientToServerStub serverStub = stubMap.get(server);
            try {
                serverStub.writeBack(WriteBackRequest.newBuilder()
                                .setMessage(ByteString.copyFrom(encryptedMessage))
                                .setSignature(ByteString.copyFrom(digitalSignature))
                                .setIv(ByteString.copyFrom(iv))
                                .setEncryptedSessionKey(ByteString.copyFrom(encryptedSessionKey))
                                .setProofOfWork(proofOfWork)
                                .setTimestamp(timestamp)
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
