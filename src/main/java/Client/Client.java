package Client;

import com.google.protobuf.ByteString;
import io.grpc.ServerBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import proto.ClientToClientGrpc;
import proto.RequestLocationProofReply;
import proto.RequestLocationProofRequest;
import util.Coords;
import util.EncryptionLogic;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Scanner;

public class Client {

    final String GRID_FILE_PATH = "src/main/assets/grid_examples/grid1.txt";
    final String CLIENT_ADDR_MAPPINGS_FILE = "src/main/assets/mappings/mappings.txt";
    protected String username;
    private ClientToClientFrontend clientToClientFrontend;
    private ClientToServerFrontend clientToServerFrontend;
    private final ClientLogic clientLogic;
    private io.grpc.Server server;
    private int port;

    public Client(String username) throws IOException, InterruptedException {
        this.username = username;

        /* Initialize client logic */
        clientLogic = new ClientLogic(username, GRID_FILE_PATH);
        /* Import users and server from mappings */
        importAddrMappings();
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 1) {
            System.err.println("Invalid args. Try -> Client username ");
            return;
        }
        String username = args[0];
        Client client = new Client(username);

        client.start(client.port);
        System.out.println(username + " Started");

        try {
            //Handshake with server to agree on session key
            byte[][] result = client.clientLogic.generateHandshakeMessage();
            client.clientToServerFrontend.handshake(result[0], result[1], result[2]);
            client.clientToClientFrontend.broadcastAllInGrid();


            Scanner in = new Scanner(System.in);
            boolean running = true;
            while (running) {
                String cmd = in.nextLine();
                switch (cmd) {
                    case "obtain_report" -> client.obtainReport();
                    case "exit" -> running = false;
                    case "help" -> client.displayHelp();
                    default -> System.err.println("Error: Command not recognized");
                }
            }
        } finally {
            client.clientToClientFrontend.shutdown();
            client.clientToServerFrontend.shutdown();
            client.blockUntilShutdown();
        }

    }

    public void obtainReport() {
        try {
            Scanner in = new Scanner(System.in);
            System.out.print("From which epoch do you wish to get your location report? ");
            int epoch = Integer.parseInt(in.nextLine());
            Coords result = clientToServerFrontend.obtainLocationReport(this.username, epoch);
            System.out.println("User " + username + " was at position (" + result.getX() + "," + result.getY() + ") at epoch " + epoch);
        } catch (StatusRuntimeException e) {
            System.out.println(e.getMessage());
        }
    }

    public void displayHelp() {
        System.out.println("obtain_report - obtain my location report from a specific epoch");
        System.out.println("help - displays help message");
        System.out.println("exit - exits client");

    }

    private void importAddrMappings() {
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
            if (mappingsUser.equals(username)) {
                port = mappingsPort;
            }
            //SERVER
            if (mappingsUser.equals("server")) {
                clientToServerFrontend = new ClientToServerFrontend(username, mappingsHost, mappingsPort, clientLogic);
                clientToClientFrontend = new ClientToClientFrontend(username, clientToServerFrontend, clientLogic);
                continue;
            }

            //CLIENTS
            clientToClientFrontend.addUser(mappingsUser, mappingsHost, mappingsPort);
        }
    }

    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.shutdown();
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
            clientToClientFrontend.shutdown();
            clientToServerFrontend.shutdown();
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
        public void requestLocationProof(RequestLocationProofRequest request, StreamObserver<RequestLocationProofReply> responseObserver) {

            System.out.println("\nReceived location report request from " + request.getUsername());

            /* Create response message is user requesting is nearby*/
            byte[][] response = clientLogic.generateLocationProof(
                    request.getUsername(),
                    request.getEpoch());

            /* If the user is not close enough don't reply with a proof */
            if (response == null) return;
            /* Create digital signature and reply*/
            byte[] responseJSON = response[0];
            byte[] digitalSignature = response[1];

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
