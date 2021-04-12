package HA;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import proto.HA.*;
import util.Coords;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class HAFrontend {
    private final ManagedChannel channel;
    private final HAToServerGrpc.HAToServerBlockingStub blockingStub;
    private final HALogic haLogic;

    public HAFrontend(String host, int port, HALogic haLogic) {
        this.channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        this.blockingStub = HAToServerGrpc.newBlockingStub(channel);
        this.haLogic = haLogic;
    }

    public List<String> obtainUsersAtLocation(int x, int y, int epoch) {
        byte[][] params = this.haLogic.generateObtainUsersAtLocationRequestParameters(x, y, epoch);

        byte[] encryptedData = params[0];
        byte[] digitalSignature = params[1];
        byte[] encryptedSessionKey = params[2];
        byte[] iv = params[3];
        byte[] sessionKeyBytes = params[4];

        ObtainUsersAtLocationReply reply = this.blockingStub.obtainUsersAtLocation(
                ObtainUsersAtLocationRequest.newBuilder()
                        .setMessage(ByteString.copyFrom(encryptedData))
                        .setSignature(ByteString.copyFrom(digitalSignature))
                        .setSessionKey(ByteString.copyFrom(encryptedSessionKey))
                        .setIv(ByteString.copyFrom(iv))
                        .build()
        );

        byte[] encryptedResponse = reply.getMessage().toByteArray();
        byte[] responseSignature = reply.getSignature().toByteArray();
        byte[] responseIv = reply.getIv().toByteArray();

        return this.haLogic.getUsersFromReply(sessionKeyBytes, encryptedResponse, responseSignature, responseIv);

    }

    public Coords obtainLocationReport(String username, int epoch) {

        byte[][] params = this.haLogic.generateObtainLocationRequestParameters(username, epoch);

        byte[] encryptedData = params[0];
        byte[] digitalSignature = params[1];
        byte[] encryptedSessionKey = params[2];
        byte[] iv = params[3];
        byte[] sessionKeyBytes = params[4];

        ObtainLocationReportReply reply = this.blockingStub.obtainLocationReport(
                ObtainLocationReportRequest.newBuilder()
                        .setMessage(ByteString.copyFrom(encryptedData))
                        .setSignature(ByteString.copyFrom(digitalSignature))
                        .setSessionKey(ByteString.copyFrom(encryptedSessionKey))
                        .setIv(ByteString.copyFrom(iv))
                        .build()
        );

        byte[] encryptedResponse = reply.getMessage().toByteArray();
        byte[] responseSignature = reply.getSignature().toByteArray();
        byte[] responseIv = reply.getIv().toByteArray();

        return this.haLogic.getCoordsFromReply(sessionKeyBytes, encryptedResponse, responseSignature, responseIv);


    }

    public void shutdown() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
