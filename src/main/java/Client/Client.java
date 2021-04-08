package Client;

import Server.Server;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import proto.*;
import proto.ServerGrpc;
import util.Coords;

import java.io.IOException;
import java.util.Scanner;

public class Client {

    private static ClientLogic clientLogic;
    private io.grpc.Server server;
    public static void main(String[] args) throws IOException {

        if(args.length != 3){
            System.err.println("Invalid args. Try -> Client username gridFilePath privKeyFilePath certFilePath");
            return;
        }
        String username = args[0];
        String gridFilePath = args[1];
        String clientAddrMappingsFile = args[2];





        clientLogic = new ClientLogic(username,gridFilePath,clientAddrMappingsFile);

        final Client client = new Client();
        client.start();

    }

    private void start() throws IOException {
        server = ServerBuilder
                .forPort(8080)
                .addService(new ClientToClientImp())
                .build();

        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Use stderr here since the logger may have been reset by its JVM shutdown hook.
            System.err.println("*** shutting down gRPC server since JVM is shutting down");
            Client.this.stop();
            System.err.println("*** server shut down");
        }));
    }





    private void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    static class ClientToClientImp extends ClientToClientGrpc.ClientToClientImplBase {


        public ClientToClientImp() {

        }

        @Override
        public void requestLocationProof(RequestLocationProofRequest request, StreamObserver<RequestLocationProofReply> responseObserver) {
            super.requestLocationProof(request, responseObserver);

            clientLogic.generateLocationProof(new Coords(request.getX(), request.getY()), request.getUsername(), request.getEpoch());


        }
    }
}
