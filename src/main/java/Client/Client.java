package Client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import proto.*;
import proto.ServerGrpc;

import java.util.Scanner;

public class Client {


    public static void main(String[] args){

        Scanner in = new Scanner(System.in);
        String message = in.nextLine();

        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost",8080).usePlaintext().build();
        ServerGrpc.ServerBlockingStub blockingStub = ServerGrpc.newBlockingStub(channel);

        GreetingRequest request = GreetingRequest.newBuilder().setMessage(message).build();

        GreetingReply reply = blockingStub.greeting(request);

        System.out.println("Got message from Server: " + reply.getMessage());

        channel.shutdown();

    }
}
