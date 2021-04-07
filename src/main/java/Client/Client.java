package Client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import proto.*;
import proto.ServerGrpc;

import java.util.Scanner;

public class Client {

    private static ClientLogic clientLogic;

    public static void main(String[] args){

        if(args.length != 2){
            System.err.println("Invalid args. Try -> Client username gridFilePath privKeyFilePath certFilePath");
            return;
        }
        String username = args[0];
        String gridFilePath = args[1];


        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost",8080).usePlaintext().build();
        ServerGrpc.ServerBlockingStub blockingStub = ServerGrpc.newBlockingStub(channel);



        clientLogic = new ClientLogic(username,gridFilePath);

        channel.shutdown();

    }

    static class ClientToClientImp extends ClientToClientGrpc.ClientToClientImplBase {


        public ClientToClientImp() {

        }

        @Override
        public void requestLocationProof(RequestLocationProofRequest request, StreamObserver<RequestLocationProofReply> responseObserver) {
            super.requestLocationProof(request, responseObserver);
        }
    }
}
