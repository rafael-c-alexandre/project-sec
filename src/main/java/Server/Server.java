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
    private int byzantineMode = 0;

    public Server(String user, String pass, String f, String keystorePasswd,int byzantineMode) throws SQLException {
        this(user, pass, f, keystorePasswd, "server", byzantineMode);
    }

    public Server(String user, String pass, String f, String keystorePasswd, String serverName, int byzantineMode) throws SQLException {
        this.connector = new Connector(user, pass,serverName);
        this.serverLogic = new ServerLogic(this.connector.getConnection(), f, keystorePasswd, serverName, byzantineMode);
        this.byzantineMode = byzantineMode;
    }

    public static void main(String[] args) throws Exception {


        if (args.length != 5 && args.length != 6) {
            System.err.println("Invalid args. Try -> dbuser dbpassword numberOfByzantines keystorePasswd servername byzantineMode");
            System.exit(0);
        }



        final Server server;
        if(args.length == 5) {
            server = new Server(args[0], args[1], args[2], args[3], Integer.parseInt(args[4]));
            System.out.println("Server Started");
        } else {
            server = new Server(args[0], args[1], args[2], args[3], args[4], Integer.parseInt(args[5]));
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
                .addService(new ServerImp(this.serverLogic, this.byzantineMode))
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
        private final int byzantineMode;

        public ServerImp(ServerLogic serverLogic, int byzantineMode) {
            this.serverLogic = serverLogic;
            this.byzantineMode = byzantineMode;

        }

        @Override
        public void submitLocationReport(SubmitLocationReportRequest request, StreamObserver<SubmitLocationReportReply> responseObserver) {
            try {
                serverLogic.submitReport(request.getEncryptedSessionKey().toByteArray()
                        ,request.getEncryptedMessage().toByteArray()
                        , request.getSignature().toByteArray()
                        ,request.getIv().toByteArray()
                        , request.getProofOfWork()
                        , request.getTimestamp(), false);

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
            } catch (InvalidFreshnessToken e) {
                System.out.println("InvalidFreshnessToken: " + e.getMessage());
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
                        ,request.getTimestamp()
                        ,request.getProofOfWork()
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
            } catch (InvalidFreshnessToken e) {
                System.out.println("InvalidFreshnessToken: " + e.getMessage());
                Status status = Status.ABORTED
                        .withDescription(e.getMessage());
                responseObserver.onError(status.asRuntimeException());
            }
        }

        @Override
        public void requestMyProofs(RequestMyProofsRequest request, StreamObserver<RequestMyProofsReply> responseObserver) {
            try {
                byte[] encryptedData = request.getEncryptedMessage().toByteArray();
                byte[] encryptedSessionKey = request.getEncryptedSessionKey().toByteArray();
                byte[] signature = request.getSignature().toByteArray();
                byte[] iv = request.getIv().toByteArray();
                long timestamp = request.getTimestamp();
                long proofOfWork = request.getProofOfWork();

                byte[][] response = serverLogic.requestMyProofs(encryptedData, encryptedSessionKey, signature, iv, proofOfWork, timestamp);

                //Create reply
                RequestMyProofsReply reply = RequestMyProofsReply.newBuilder()
                        .setMessage(ByteString.copyFrom(response[0]))
                        .setSignature(ByteString.copyFrom(response[1]))
                        .setIv(ByteString.copyFrom(response[2]))
                        .setEncryptedSessionKey(ByteString.copyFrom(response[3]))
                        .build();

                responseObserver.onNext(reply);
                responseObserver.onCompleted();

            } catch (InvalidSignatureException | InvalidProofOfWorkException | ReportAlreadyExistsException | InvalidFreshnessToken e) {
                Status status = Status.ABORTED.withDescription(e.getMessage());
                responseObserver.onError(status.asRuntimeException());
            }
        }

        @Override
        public void writeBack(WriteBackRequest request, StreamObserver<WriteBackReply> responseObserver) {
            try {
                byte[] encryptedData = request.getMessage().toByteArray();
                byte[] encryptedSessionKey = request.getEncryptedSessionKey().toByteArray();
                byte[] signature = request.getSignature().toByteArray();
                byte[] iv = request.getIv().toByteArray();
                long proofOfWork = request.getProofOfWork();
                long timestamp = request.getTimestamp();

                serverLogic.writeback(encryptedData, encryptedSessionKey, signature, iv, proofOfWork, timestamp, false);

                //Create reply
                WriteBackReply reply = WriteBackReply.newBuilder()
                        .build();

                responseObserver.onNext(reply);
                responseObserver.onCompleted();

            } catch (InvalidSignatureException | InvalidProofOfWorkException | InvalidFreshnessToken e) {
                Status status = Status.INVALID_ARGUMENT.withDescription(e.getMessage());
                responseObserver.onError(status.asRuntimeException());
            } catch (AlreadyConfirmedReportException e) {
                Status status = Status.ALREADY_EXISTS.withDescription(e.getMessage());
                responseObserver.onError(status.asRuntimeException());
            } catch (NoReportFoundException e) {
                Status status = Status.NOT_FOUND.withDescription(e.getMessage());
                responseObserver.onError(status.asRuntimeException());
            } catch (InvalidReportException e) {
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
                long timestamp = request.getTimestamp();
                long proofOfWork = request.getProofOfWork();

                byte[][] response = serverLogic.generateObtainLocationReportResponse(encryptedData, encryptedSessionKey, signature, iv, timestamp, proofOfWork);

                //Create reply
                ObtainLocationReportReply reply = ObtainLocationReportReply.newBuilder()
                        .setMessage(ByteString.copyFrom(response[0]))
                        .setSignature(ByteString.copyFrom(response[1]))
                        .setEncryptedSessionKey(ByteString.copyFrom(response[2]))
                        .setIv(ByteString.copyFrom(response[3]))
                        .build();

                responseObserver.onNext(reply);
                responseObserver.onCompleted();

            } catch (NoReportFoundException e) {
                Status status = Status.NOT_FOUND.withDescription(e.getMessage());
                responseObserver.onError(status.asRuntimeException());
            } catch (InvalidSignatureException | InvalidFreshnessToken | InvalidProofOfWorkException e) {
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
                long proofOfWork = request.getProofOfWork();
                long timestamp = request.getTimestamp();

                byte[][] response = serverLogic.generateObtainLocationReportResponseHA(encryptedData, encryptedSessionKey, signature, iv, proofOfWork, timestamp);

                //Create reply
                proto.HA.ObtainLocationReportReply reply = proto.HA.ObtainLocationReportReply.newBuilder()
                        .setMessage(ByteString.copyFrom(response[0]))
                        .setSignature(ByteString.copyFrom(response[1]))
                        .setIv(ByteString.copyFrom(response[2]))
                        .setEncryptedSessionKey(ByteString.copyFrom(response[3]))
                        .build();

                responseObserver.onNext(reply);
                responseObserver.onCompleted();

            } catch (NoReportFoundException e) {
                Status status = Status.NOT_FOUND.withDescription(e.getMessage());
                responseObserver.onError(status.asRuntimeException());
            } catch (InvalidSignatureException e) {
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
                long proofOfWork = request.getProofOfWork();
                long timestamp = request.getTimestamp();

                byte[][] response = serverLogic.generateObtainUsersAtLocationReportResponse(encryptedData, encryptedSessionKey, signature, iv, proofOfWork, timestamp);

                //Create reply
                ObtainUsersAtLocationReply reply = ObtainUsersAtLocationReply.newBuilder()
                        .setMessage(ByteString.copyFrom(response[0]))
                        .setSignature(ByteString.copyFrom(response[1]))
                        .setIv(ByteString.copyFrom(response[2]))
                        .setEncryptedSessionKey(ByteString.copyFrom(response[3]))
                        .build();

                responseObserver.onNext(reply);
                responseObserver.onCompleted();

            } catch (InvalidSignatureException | InvalidFreshnessToken | InvalidProofOfWorkException e) {
                Status status = Status.ABORTED.withDescription(e.getMessage());
                responseObserver.onError(status.asRuntimeException());
            }

        }

        @Override
        public void writeBack(proto.HA.WriteBackRequest request, StreamObserver<proto.HA.WriteBackReply> responseObserver) {
            try {
                byte[] encryptedData = request.getMessage().toByteArray();
                byte[] encryptedSessionKey = request.getEncryptedSessionKey().toByteArray();
                byte[] signature = request.getSignature().toByteArray();
                byte[] iv = request.getIv().toByteArray();
                long proofOfWork = request.getProofOfWork();
                long timestamp = request.getTimestamp();

                serverLogic.writeback(encryptedData, encryptedSessionKey, signature, iv, proofOfWork, timestamp, true);

                //Create reply
                proto.HA.WriteBackReply reply = proto.HA.WriteBackReply.newBuilder()
                        .build();

                responseObserver.onNext(reply);
                responseObserver.onCompleted();

            } catch (InvalidSignatureException | InvalidProofOfWorkException | InvalidFreshnessToken e) {
                Status status = Status.INVALID_ARGUMENT.withDescription(e.getMessage());
                responseObserver.onError(status.asRuntimeException());
            } catch (AlreadyConfirmedReportException e) {
                Status status = Status.ALREADY_EXISTS.withDescription(e.getMessage());
                responseObserver.onError(status.asRuntimeException());
            } catch (NoReportFoundException e) {
                Status status = Status.NOT_FOUND.withDescription(e.getMessage());
                responseObserver.onError(status.asRuntimeException());
            } catch (InvalidReportException e) {
                Status status = Status.ABORTED.withDescription(e.getMessage());
                responseObserver.onError(status.asRuntimeException());
            }
        }

    }
}

