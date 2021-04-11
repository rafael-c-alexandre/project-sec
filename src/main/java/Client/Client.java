package Client;

import com.google.protobuf.ByteString;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import proto.ClientToClientGrpc;
import proto.RequestLocationProofReply;
import proto.RequestLocationProofRequest;
import util.Coords;
import util.EncryptionLogic;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Scanner;

public class Client {

    final String GRID_FILE_PATH = "src/main/assets/grid_examples/grid1.txt";
    final String CLIENT_ADDR_MAPPINGS_FILE = "src/main/assets/mappings/mappings.txt";

    private ClientToClientFrontend clientToClientFrontend;
    private ClientToServerFrontend clientToServerFrontend;
    private ClientLogic clientLogic;
    private io.grpc.Server server;
    protected String username;
    private int manualMode;
    private int port;

    public static void main(String[] args) throws IOException, InterruptedException {
        if(args.length != 2){
            System.err.println("Invalid args. Try -> Client username manualMode");
            return;
        }
        String username = args[0];
        int manualMode = Integer.parseInt(args[1]);
        Client client = new Client(username, manualMode);
        client.clientToServerFrontend.obtainLocationReport("user1",0);
        System.out.println("terminou");
        client.blockUntilShutdown();
    }

    public Client(String username, int manualMode) throws IOException, InterruptedException {
        this.username = username;
        this.manualMode = manualMode;

        /* Initialize client logic */
        clientLogic = new ClientLogic(username,GRID_FILE_PATH);


        /* Import users and server from mappings */
        importAddrMappings();

        System.out.println(this.username + " Started");
        start(port);
    }

    private void importAddrMappings(){
        Scanner scanner;
        //Build frontends
        try {
            scanner = new Scanner(new File(CLIENT_ADDR_MAPPINGS_FILE));
        } catch (FileNotFoundException e) {
            System.out.println("No such client mapping file!");
            return;
        }

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            // process the line
            String[] parts = line.split(",");
            String mappingsUser = parts[0].trim();
            String mappingsHost = parts[1].trim();
            int mappingsPort = Integer.parseInt(parts[2].trim());
            if(mappingsUser.equals(username)){
                port = mappingsPort;
            }
            //SERVER
            if(mappingsUser.equals("server")){
                clientToServerFrontend = new ClientToServerFrontend(username,mappingsHost,mappingsPort);
                clientToClientFrontend = new ClientToClientFrontend(clientToServerFrontend, clientLogic);
                continue;
            }

            //CLIENTS
            clientToClientFrontend.addUser(mappingsUser,mappingsHost,mappingsPort);
        }
    }

    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    private void start(int port) throws IOException {
        server = ServerBuilder
                .forPort(port)
                .addService(new ClientToClientImp(clientLogic))
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

        ClientLogic clientLogic;
        public ClientToClientImp(ClientLogic clientLogic) {
            this.clientLogic = clientLogic;
        }


        @Override
        public void requestLocationProof(RequestLocationProofRequest request, StreamObserver<RequestLocationProofReply> responseObserver){

            System.out.println("Received location report request " + request.getUsername());
            String username = clientLogic.getUsername();
            try {
                if(username.equals("user2")){
                    Thread.sleep(2000);
                    System.out.println("user2 reply");
                }
                else{
                    Thread.sleep(5000);
                    System.out.println("user3 reply");
                }
            } catch(Exception e){}

            /* Create response message */
            byte[] responseJSON = clientLogic.generateLocationProof(new Coords(request.getX(),
                                                                            request.getY()),
                                                                            request.getUsername(),
                                                                            request.getEpoch());

            /* Create digital signature and reply*/
            byte[] digitalSignature = EncryptionLogic.createDigitalSignature(responseJSON, EncryptionLogic.getPrivateKey(username));

            RequestLocationProofReply reply = null;
            if (digitalSignature != null) {
                reply = RequestLocationProofReply.newBuilder().setProof(ByteString.copyFrom(responseJSON))
                                                                                        .setDigitalSignature(ByteString.copyFrom(digitalSignature)).build();
            }

            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }
}
