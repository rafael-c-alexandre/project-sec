package Server;

import Exceptions.*;
import Server.database.Connector;
import com.google.protobuf.ByteString;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import proto.*;
import proto.HA.HAToServerGrpc;
import proto.HA.ObtainUsersAtLocationReply;
import proto.HA.ObtainUsersAtLocationRequest;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Scanner;


public class Server {

    private final Connector connector;
    private final ServerLogic serverLogic;
    final String ADDR_MAPPINGS_FILE = "src/main/assets/mappings/mappings.txt";

    private io.grpc.Server server;

    public Server(String user, String pass, String f, String keystorePasswd) throws SQLException {
        this(user, pass, f, keystorePasswd, "server");
    }

    public Server(String user, String pass, String f, String keystorePasswd, String serverName) throws SQLException {
        this.connector = new Connector(user, pass,serverName);
        this.serverLogic = new ServerLogic(this.connector.getConnection(), f, keystorePasswd, serverName);
    }

    public static void main(String[] args) throws Exception {


        if (args.length != 4 && args.length != 5) {
            System.err.println("Invalid args. Try -> dbuser dbpassword numberOfByzantines keystorePasswd servername");
            System.exit(0);
        }

        final Server server;
        if(args.length == 4) {
            server = new Server(args[0], args[1], args[2], args[3]);
            System.out.println("Server Started");
        } else {
            server = new Server(args[0], args[1], args[2], args[3], args[4]);
            System.out.println(args[4] + " Started");
        }

        server.start();


        server.blockUntilShutdown();
    }

    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }


    private void start() throws IOException {
        server = ServerBuilder
                .forPort(getServerPort(serverLogic.getServerName()))
                .addService(new ServerImp(this.serverLogic))
                .addService(new HAToServerImp(this.serverLogic))
                .build();

        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Use stderr here since the logger may have been reset by its JVM shutdown hook.
            System.err.println("*** shutting down gRPC server since JVM is shutting down");
            server.shutdown();
            System.err.println("*** server shut down");
        }));
    }

    private int getServerPort(String serverName){
        Scanner scanner;
        try {
            scanner = new Scanner(new File(ADDR_MAPPINGS_FILE));
        } catch (FileNotFoundException e) {
            System.out.println("No such client mapping file!");
            return 0;
        }

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            // process the line
            String[] parts = line.split(",");
            String mappingsUser = parts[0].trim();
            String mappingsHost = parts[1].trim();
            int mappingsPort = Integer.parseInt(parts[2].trim());
            //SERVER
            if (mappingsUser.equals(serverName)) {
                return mappingsPort;
            }
        }
        return 0;
    }

    private void stop() {
        if (server != null) {
            server.shutdown();
        }
    }


    static class ServerImp extends ClientToServerGrpc.ClientToServerImplBase {

        private final ServerLogic serverLogic;

        public ServerImp(ServerLogic serverLogic) {
            this.serverLogic = serverLogic;

        }

        @Override
        public void submitLocationReport(SubmitLocationReportRequest request, StreamObserver<SubmitLocationReportReply> responseObserver) {
            try {
                serverLogic.submitReport(request.getEncryptedSessionKey().toByteArray(),request.getEncryptedMessage().toByteArray(), request.getSignature().toByteArray(),request.getIv().toByteArray(), request.getProofOfWork());

                SubmitLocationReportReply reply = SubmitLocationReportReply.newBuilder().build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();

            } catch ( InvalidReportException e) {
                System.out.println("InvalidReportException: " + e.getMessage());
                Status status = Status.ABORTED.withDescription(e.getMessage());
                responseObserver.onError(status.asRuntimeException());
            } catch (InvalidSignatureException e) {
                System.out.println("InvalidSignatureException: " + e.getMessage());
                Status status = Status.ABORTED.withDescription(e.getMessage());
                responseObserver.onError(status.asRuntimeException());
            } catch (ReportAlreadyExistsException e) {
                System.out.println("ReportAlreadyExistsException: " + e.getMessage());
                Status status = Status.ALREADY_EXISTS
                        .withDescription(e.getMessage());
                responseObserver.onError(status.asRuntimeException());
            } catch (InvalidProofOfWorkException e) {
                System.out.println("InvalidProofOfWorkException: " + e.getMessage());
                Status status = Status.ABORTED
                        .withDescription(e.getMessage());
                responseObserver.onError(status.asRuntimeException());
            }
        }

        @Override
        public void submitLocationProof(SubmitLocationProofRequest request, StreamObserver<SubmitLocationProofReply> responseObserver) {
            try {
                boolean reachedQuorum = serverLogic.submitProof(request.getWitnessSessionKey().toByteArray(),
                        request.getWitnessIv().toByteArray()
                        , request.getEncryptedSessionKey().toByteArray()
                        ,request.getEncryptedProof().toByteArray()
                        ,request.getSignature().toByteArray()
                        ,request.getIv().toByteArray()
                        );

                SubmitLocationProofReply reply = SubmitLocationProofReply.newBuilder().setReachedQuorum(reachedQuorum).build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();

            } catch (InvalidProofException e) {
                System.out.println("InvalidProofException: " + e.getMessage());
                Status status = Status.ABORTED.withDescription(e.getMessage());
                responseObserver.onError(status.asRuntimeException());
            } catch (NoReportFoundException e) {
                System.out.println("NoReportFoundException: " + e.getMessage());
                Status status = Status.NOT_FOUND.withDescription(e.getMessage());
                responseObserver.onError(status.asRuntimeException());
            } catch (AlreadyConfirmedReportException e) {
                System.out.println("AlreadyConfirmedReportException: " + e.getMessage());
                Status status = Status.ABORTED.withDescription(e.getMessage());
                responseObserver.onError(status.asRuntimeException());
            }
        }

        @Override
        public void obtainLocationReport(ObtainLocationReportRequest request, StreamObserver<ObtainLocationReportReply> responseObserver) {
            try {
                byte[] encryptedData = request.getMessage().toByteArray();
                byte[] encryptedSessionKey = request.getEncryptedSessionKey().toByteArray();
                byte[] signature = request.getSignature().toByteArray();
                byte[] iv = request.getIv().toByteArray();

                byte[][] response = serverLogic.generateObtainLocationReportResponse(encryptedData, encryptedSessionKey, signature, iv, false, request.getTimestamp());

                //Create reply
                ObtainLocationReportReply reply = ObtainLocationReportReply.newBuilder()
                        .setMessage(ByteString.copyFrom(response[0]))
                        .setSignature(ByteString.copyFrom(response[1]))
                        .setIv(ByteString.copyFrom(response[2]))
                        .build();

                responseObserver.onNext(reply);
                responseObserver.onCompleted();

            } catch (NoSuchCoordsException | NoReportFoundException e) {
                Status status = Status.NOT_FOUND.withDescription(e.getMessage());
                responseObserver.onError(status.asRuntimeException());
            } catch (InvalidSignatureException | InvalidFreshnessToken e) {
                Status status = Status.ABORTED.withDescription(e.getMessage());
                responseObserver.onError(status.asRuntimeException());
            }
        }
    }

    static class HAToServerImp extends HAToServerGrpc.HAToServerImplBase {
        private final ServerLogic serverLogic;

        public HAToServerImp(ServerLogic serverLogic) {
            this.serverLogic = serverLogic;
        }

        @Override
        public void obtainLocationReport(proto.HA.ObtainLocationReportRequest request, StreamObserver<proto.HA.ObtainLocationReportReply> responseObserver) {
            try {
                byte[] encryptedData = request.getMessage().toByteArray();
                byte[] encryptedSessionKey = request.getEncryptedSessionKey().toByteArray();
                byte[] signature = request.getSignature().toByteArray();
                byte[] iv = request.getIv().toByteArray();

                byte[][] response = serverLogic.generateObtainLocationReportResponse(encryptedData, encryptedSessionKey, signature, iv, true, request.getTimestamp());

                //Create reply
                proto.HA.ObtainLocationReportReply reply = proto.HA.ObtainLocationReportReply.newBuilder()
                        .setMessage(ByteString.copyFrom(response[0]))
                        .setSignature(ByteString.copyFrom(response[1]))
                        .setIv(ByteString.copyFrom(response[2]))
                        .build();

                responseObserver.onNext(reply);
                responseObserver.onCompleted();

            } catch (NoSuchCoordsException | NoReportFoundException e) {
                Status status = Status.NOT_FOUND.withDescription(e.getMessage());
                responseObserver.onError(status.asRuntimeException());
            } catch (InvalidSignatureException | InvalidFreshnessToken e) {
                Status status = Status.ABORTED.withDescription(e.getMessage());
                responseObserver.onError(status.asRuntimeException());
            }
        }

        @Override
        public void obtainUsersAtLocation(ObtainUsersAtLocationRequest request, StreamObserver<ObtainUsersAtLocationReply> responseObserver) {
            try {
                byte[] encryptedData = request.getMessage().toByteArray();
                byte[] encryptedSessionKey = request.getEncryptedSessionKey().toByteArray();
                byte[] signature = request.getSignature().toByteArray();
                byte[] iv = request.getIv().toByteArray();

                byte[][] response = serverLogic.generateObtainUsersAtLocationReportResponse(encryptedData, encryptedSessionKey, signature, iv, request.getTimestamp());

                //Create reply
                ObtainUsersAtLocationReply reply = ObtainUsersAtLocationReply.newBuilder()
                        .setMessage(ByteString.copyFrom(response[0]))
                        .setSignature(ByteString.copyFrom(response[1]))
                        .setIv(ByteString.copyFrom(response[2]))
                        .build();

                responseObserver.onNext(reply);
                responseObserver.onCompleted();

            } catch (InvalidSignatureException | InvalidFreshnessToken e) {
                Status status = Status.ABORTED.withDescription(e.getMessage());
                responseObserver.onError(status.asRuntimeException());
            }

        }

    }
}

