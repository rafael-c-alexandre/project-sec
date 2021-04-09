package HA;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import proto.ClientToServerGrpc;

import java.util.Scanner;

public class HAClient {

    public static void main(String[] args){

        Scanner in = new Scanner(System.in);
        String message = in.nextLine();

        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost",8081).usePlaintext().build();
        ClientToServerGrpc.ClientToServerBlockingStub blockingStub = ClientToServerGrpc.newBlockingStub(channel);

        channel.shutdown();

    }


}
