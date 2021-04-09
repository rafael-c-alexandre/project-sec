package Client;

import com.google.protobuf.ByteString;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import proto.ClientToClientGrpc;
import proto.RequestLocationProofReply;
import proto.RequestLocationProofRequest;
import util.Coords;

import java.io.IOException;
import java.util.Scanner;

public class Client {

    private static ClientLogic clientLogic;
    private io.grpc.Server server;


    public static void main(String[] args) throws IOException, InterruptedException {


        if(args.length != 4){
            System.err.println("Invalid args. Try -> Client username port gridFilePath clientAddrMappingsFilePath");
            return;
        }
        String username = args[0];
        int port = Integer.parseInt(args[1]);
        String gridFilePath = args[2];
        String clientAddrMappingsFile = args[3];

        System.out.println("Client Started");

        clientLogic = new ClientLogic(username,gridFilePath,clientAddrMappingsFile);

        Scanner scanner = new Scanner(System.in);

        boolean not = true;

        final Client client = new Client();
        client.start(port);

        //just to test request
        while (not) {
            if (scanner.next() != null) {
                clientLogic.broadcast();
                not = false;
            }
        }
        client.blockUntilShutdown();

    }

    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    private void start(int port) throws IOException {
        server = ServerBuilder
                .forPort(port)
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

            System.out.println("Received location report request");

            byte[] responseJSON = clientLogic.generateLocationProof(new Coords(request.getX(), request.getY()), request.getUsername(), request.getEpoch());

            RequestLocationProofReply reply = RequestLocationProofReply.newBuilder().setProof(ByteString.copyFrom(responseJSON)).build();

            responseObserver.onNext(reply);
            responseObserver.onCompleted();

        }
    }
}
