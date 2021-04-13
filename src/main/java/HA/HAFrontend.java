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

        ObtainUsersAtLocationReply reply = this.blockingStub.obtainUsersAtLocation(
                ObtainUsersAtLocationRequest.newBuilder()
                        .setUsername("ha")
                        .setMessage(ByteString.copyFrom(encryptedData))
                        .setSignature(ByteString.copyFrom(digitalSignature))
                        .build()
        );

        byte[] encryptedResponse = reply.getMessage().toByteArray();
        byte[] responseSignature = reply.getSignature().toByteArray();

        return this.haLogic.getUsersFromReply(encryptedResponse, responseSignature);

    }

    public Coords obtainLocationReport(String username, int epoch) {

        byte[][] params = this.haLogic.generateObtainLocationRequestParameters(username, epoch);

        byte[] encryptedData = params[0];
        byte[] digitalSignature = params[1];

        ObtainLocationReportReply reply = this.blockingStub.obtainLocationReport(
                ObtainLocationReportRequest.newBuilder()
                        .setUsername("ha")
                        .setMessage(ByteString.copyFrom(encryptedData))
                        .setSignature(ByteString.copyFrom(digitalSignature))
                        .build()
        );

        byte[] encryptedResponse = reply.getMessage().toByteArray();
        byte[] responseSignature = reply.getSignature().toByteArray();

        return this.haLogic.getCoordsFromReply( encryptedResponse, responseSignature);

    }


    public void handshake(byte[] encryptedUsernameSessionKey, byte[] digitalSignature, byte[] iv){
        try{
            HandshakeReply reply = this.blockingStub.handshake(
                    HandshakeRequest.newBuilder()
                            .setEncryptedUsernameSessionKey(ByteString.copyFrom(encryptedUsernameSessionKey))
                            .setSignature(ByteString.copyFrom(digitalSignature))
                            .setIv(ByteString.copyFrom(iv))
                            .build()
            );
        } catch (Exception e){
            io.grpc.Status status = io.grpc.Status.fromThrowable(e);
            System.out.println("Exception received from server:" + status.getDescription());
        }

    }

    public void shutdown() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
