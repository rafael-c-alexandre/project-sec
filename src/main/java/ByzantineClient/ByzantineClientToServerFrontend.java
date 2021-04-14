package ByzantineClient;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import proto.*;
import util.Coords;

import java.util.concurrent.TimeUnit;

public class ByzantineClientToServerFrontend {
    private final String username;
    private final ManagedChannel channel;
    private final ClientToServerGrpc.ClientToServerBlockingStub blockingStub;
    private final ByzantineClientLogic clientLogic;

    public ByzantineClientToServerFrontend(String username, String host, int port, ByzantineClientLogic clientLogic) {
        this.username = username;
        this.channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        this.blockingStub = ClientToServerGrpc.newBlockingStub(channel);
        this.clientLogic = clientLogic;
    }

    public void submitReport(byte[] encryptedMessage,byte[] digitalSignature,byte[] encryptedSessionKey, byte[] iv) {
        try {
            SubmitLocationReportReply reply = this.blockingStub.submitLocationReport(
                    SubmitLocationReportRequest.newBuilder()
                            .setEncryptedMessage(ByteString.copyFrom(encryptedMessage))
                            .setSignature(ByteString.copyFrom(digitalSignature))
                            .setIv(ByteString.copyFrom(iv))
                            .setEncryptedSessionKey(ByteString.copyFrom(encryptedSessionKey))
                            .build()
            );
        } catch (StatusRuntimeException e) {
            io.grpc.Status status = io.grpc.Status.fromThrowable(e);
            System.err.println("Exception received from server: " + status.getDescription());
        }

    }

    public boolean submitProof(byte[] encryptedProof, byte[] digitalSignature,byte[] encryptedSessionKey, byte[] iv, byte[] witnessSessionKey, byte[] witnessIv) {
        try {
            SubmitLocationProofReply reply = this.blockingStub.submitLocationProof(
                    SubmitLocationProofRequest.newBuilder()
                            .setEncryptedProof(ByteString.copyFrom(encryptedProof))
                            .setSignature(ByteString.copyFrom(digitalSignature))
                            .setIv(ByteString.copyFrom(iv))
                            .setEncryptedSessionKey(ByteString.copyFrom(encryptedSessionKey))
                            .setWitnessIv(ByteString.copyFrom(witnessIv))
                            .setWitnessSessionKey(ByteString.copyFrom(witnessSessionKey))
                            .build()
            );
            return reply.getReachedQuorum();
        } catch (StatusRuntimeException e) {
            io.grpc.Status status = io.grpc.Status.fromThrowable(e);
            System.err.println("Exception received from server: " + status.getDescription());
        }
        return false;
    }

    public Coords obtainLocationReport(String username, int epoch) {

        byte[][] params = this.clientLogic.generateObtainLocationRequestParameters(username, epoch);

        byte[] encryptedData = params[0];
        byte[] digitalSignature = params[1];
        byte[] encryptedSessionKey = params[2];
        byte[] iv = params[3];
        byte[] sessionKeyBytes = params[4];

        ObtainLocationReportReply reply = this.blockingStub.obtainLocationReport(
                ObtainLocationReportRequest.newBuilder()
                        .setMessage(ByteString.copyFrom(encryptedData))
                        .setSignature(ByteString.copyFrom(digitalSignature))
                        .setEncryptedSessionKey(ByteString.copyFrom(encryptedSessionKey))
                        .setIv(ByteString.copyFrom(iv))
                        .build()
        );

        byte[] encryptedResponse = reply.getMessage().toByteArray();
        byte[] responseSignature = reply.getSignature().toByteArray();
        byte[] responseIv = reply.getIv().toByteArray();

        return this.clientLogic.getCoordsFromReply(sessionKeyBytes, encryptedResponse, responseSignature, responseIv);

    }

    public void shutdown() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
