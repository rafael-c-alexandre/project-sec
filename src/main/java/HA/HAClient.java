package HA;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import proto.ServerGrpc;

import java.util.Scanner;

public class HAClient {

    public static void main(String[] args){

        Scanner in = new Scanner(System.in);
        String message = in.nextLine();

        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost",8080).usePlaintext().build();
        ServerGrpc.ServerBlockingStub blockingStub = ServerGrpc.newBlockingStub(channel);

        channel.shutdown();

    }


}
