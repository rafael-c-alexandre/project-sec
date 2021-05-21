package Client;

import Exceptions.InvalidSignatureException;
import Exceptions.ProverNotCloseEnoughException;
import Server.Proof;
import com.google.protobuf.ByteString;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.json.JSONObject;
import proto.ClientToClientGrpc;
import proto.RequestLocationProofReply;
import proto.RequestLocationProofRequest;
import util.Coords;
import util.EncryptionLogic;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.stream.Collectors;

public class Client {

    final String GRID_FILE_PATH = "src/main/assets/grid_examples/grid1-stage1.txt";
    final String CLIENT_ADDR_MAPPINGS_FILE = "src/main/assets/mappings/mappings2.txt";
    protected String username;
    private ClientToClientFrontend clientToClientFrontend;
    private ClientToServerFrontend clientToServerFrontend;
    private final ClientLogic clientLogic;
    private io.grpc.Server server;
    private int port;

    public Client(String username, String grid_file_path, String keystorePasswd, int numberOfByzantineClients, int numberOfByzantineServers, int byzantineMode) throws IOException, InterruptedException {
        this.username = username;

        /* Initialize client logic */
        clientLogic = new ClientLogic(username, grid_file_path, keystorePasswd, numberOfByzantineClients, numberOfByzantineServers, byzantineMode);
        /* Import users and server from mappings */
        importAddrMappings();

    }

    public static void main(String[] args) throws IOException, InterruptedException {
        /* byzantineMode 0 = normal mode
         * byzantineMode 1 = generate wrong location in report when requesting to server
         * byzantineMode 2 = wrong epoch (epoch=999) when creating proofs to other prover
         * byzantineMode 3 = send proof request to every witness available, at any range
         * byzantineMode 4 = give proof to every prover who requests, use prover location as own location in the proof to make sure the server accepts it
         * byzantineMode 5 = witness returns replayed proof, proof with signature of user1 for user2
         * */

        if (args.length > 7 || args.length < 6) {
            System.err.println("Invalid args. Try -> username grid_file_path keystorePasswd numberOfByzantineClients numberOfByzantineServers byzantineMode [commands_file_path] ");
            return;
        }
        String username = args[0];
        String grid_file_path = args[1];
        Client client = new Client(username, grid_file_path, args[2], Integer.parseInt(args[3]), Integer.parseInt(args[4]), Integer.parseInt(args[5]));

        String commandsFilePath = args.length == 7 ? args[6] : null;

        client.start(client.port);
        System.out.println(username + " Started");

        try {
            //run grid broadcasts
            client.clientToClientFrontend.broadcastAllInGrid();

            Scanner in;
            if (commandsFilePath == null) {
                in = new Scanner(System.in);
            } else {
                in = new Scanner(new File(commandsFilePath));
            }

            boolean running = true;
            while (running) {
            String cmd = in.nextLine();
                switch (cmd) {
                    case "obtain_report" -> client.obtainReport(in);
                    case "request_proofs" -> client.requestMyProofs(in);
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

    public void requestMyProofs(Scanner in){
        try {
            System.out.print("From which epochs do you wish to get your proofs? (Separated by commas)");
            String epochs = in.nextLine();
            List<Integer> epochList = Arrays.stream(epochs.split(","))
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());

            String uuid = UUID.randomUUID().toString();
            List<Proof> proofs = clientToServerFrontend.requestMyProofs(uuid, this.username,epochList);

            for(Proof proof : proofs){
                System.out.println("\t Received my proof: " + proof);
            }


        } catch (StatusRuntimeException e) {
            System.err.println(e.getMessage());
        }
    }

    public void obtainReport(Scanner in) {
        try {
            System.out.print("From which epoch do you wish to get your location report? ");
            int epoch = Integer.parseInt(in.nextLine());

            //generate request uid
            String requestUid = UUID.randomUUID().toString();

            Coords result = clientToServerFrontend.obtainLocationReport(this.username, epoch, requestUid);
            if (result != null)
                System.out.println("User " + username + " was at position (" + result.getX() + "," + result.getY() + ") at epoch " + epoch);
        } catch (StatusRuntimeException e) {
            System.err.println(e.getMessage());
        }
    }

    public void displayHelp() {
        System.out.println("obtain_report - obtain my location report from a specific epoch");
        System.out.println("request_proofs - request my proofs for a range of epochs");
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

        clientToServerFrontend = new ClientToServerFrontend(username, clientLogic);
        clientToClientFrontend = new ClientToClientFrontend(username, clientToServerFrontend, clientLogic);
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
            if (mappingsUser.contains("server")) {
                clientToServerFrontend.addServer(mappingsUser, mappingsHost, mappingsPort);
                clientToClientFrontend.addServer(mappingsUser);
                clientLogic.addServer(mappingsUser);
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

            String jsonString = request.getRequest();

            JSONObject jsonObject = new JSONObject(jsonString);
            String username = jsonObject.getString("username");
            int epoch = jsonObject.getInt("epoch");

            System.out.println("Received location report request from " + username);

            /* Create response message is user requesting is nearby*/

            List<byte[][]> response;
            try {
                response = clientLogic.generateLocationProof(
                        username,
                        epoch,
                        request.getRequest().getBytes(),
                        request.getDigitalSignature().toByteArray()
                        );

                /* If the user is not close enough don't reply with a proof */
                if (response == null) return;

                /* Create digital signature and reply*/
                List<byte[]> responseJSONs =  response.stream().map(b -> b[0]).collect(Collectors.toList());
                List<byte[]> digitalSignatures =  response.stream().map(b -> b[1]).collect(Collectors.toList());
                List<byte[]> witnessSessionKeys = response.stream().map(b -> b[2]).collect(Collectors.toList());
                List<byte[]> witnessIvs = response.stream().map(b -> b[3]).collect(Collectors.toList());
                List<String> destServers = response.stream().map(b -> new String(b[4], StandardCharsets.UTF_8)).collect(Collectors.toList());

                RequestLocationProofReply reply = null;

                reply = RequestLocationProofReply.newBuilder().addAllProof(responseJSONs.stream().map(ByteString::copyFrom).collect(Collectors.toList()))
                        .addAllDigitalSignature(digitalSignatures.stream().map(ByteString::copyFrom).collect(Collectors.toList()))
                        .addAllWitnessIv(witnessIvs.stream().map(ByteString::copyFrom).collect(Collectors.toList()))
                        .addAllWitnessSessionKey(witnessSessionKeys.stream().map(ByteString::copyFrom).collect(Collectors.toList()))
                        .addAllServer(destServers)
                        .build();

                responseObserver.onNext(reply);
                responseObserver.onCompleted();
            } catch (ProverNotCloseEnoughException e) {
                System.err.println("ProverNotCloseEnoughException: " + e.getMessage());
                Status status = Status.OUT_OF_RANGE
                        .withDescription(e.getMessage());
                responseObserver.onError(status.asRuntimeException());
            } catch (InvalidSignatureException e) {
                System.out.println("InvalidSignatureException: " + e.getMessage());
                Status status = Status.UNAUTHENTICATED
                        .withDescription(e.getMessage());
                responseObserver.onError(status.asRuntimeException());
            }
        }
    }
}
