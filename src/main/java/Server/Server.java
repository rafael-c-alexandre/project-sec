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
import util.EncryptionLogic;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
import java.util.Base64;


public class Server {

    private final Connector connector;
    private final ServerLogic serverLogic;


    private io.grpc.Server server;

    public Server(String user, String pass) throws SQLException {
        this.connector = new Connector(user, pass);
        this.serverLogic = new ServerLogic(this.connector.getConnection());
    }

    public static void main(String[] args) throws Exception {

        if (args.length != 2) {
            System.err.println("Invalid args. Try -> dbuser dbpassword");
            System.exit(0);
        }

        final Server server = new Server(
                args[0],
                args[1]
        );


        server.start();
        System.out.println("Server Started");

        server.blockUntilShutdown();
    }

    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }


    private void start() throws IOException {
        server = ServerBuilder
                .forPort(8084)
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
        public void handshake(HandshakeRequest request, StreamObserver<HandshakeReply> responseObserver){
            byte[] encryptedUsernameSessionKeyJSON = request.getEncryptedUsernameSessionKey().toByteArray();
            byte[] signature = request.getSignature().toByteArray();
            byte[] iv = request.getIv().toByteArray();

            if(serverLogic.handshake(encryptedUsernameSessionKeyJSON, signature, iv)){
                HandshakeReply reply = HandshakeReply.newBuilder().build();
                responseObserver.onNext(reply);
            } else {
                Status status = Status.FAILED_PRECONDITION.withDescription("Failed handshake");
                responseObserver.onError(status.asRuntimeException());
            }

            responseObserver.onCompleted();
        }

        @Override
        public void submitLocationReport(SubmitLocationReportRequest request, StreamObserver<SubmitLocationReportReply> responseObserver) {
            System.out.println("Received submit location report request from " + request.getUsername());
            try {
                serverLogic.submitReport(request.getUsername(),request.getEncryptedMessage(), request.getSignature());

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
            }
        }

        @Override
        public void submitLocationProof(SubmitLocationProofRequest request, StreamObserver<SubmitLocationProofReply> responseObserver) {
            System.out.println("Received submit location proof request from " + request.getUsername());
            try {
                serverLogic.submitProof(request.getUsername(),request.getEncryptedProof(), request.getSignature());

                SubmitLocationProofReply reply = SubmitLocationProofReply.newBuilder().build();
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
                byte[] signature = request.getSignature().toByteArray();
                String username = request.getUsername();

                byte[][] response = serverLogic.generateObtainLocationReportResponse(username, encryptedData, signature, false);

                //Create reply
                ObtainLocationReportReply reply = ObtainLocationReportReply.newBuilder()
                        .setMessage(ByteString.copyFrom(response[0]))
                        .setSignature(ByteString.copyFrom(response[1]))
                        .build();

                responseObserver.onNext(reply);
                responseObserver.onCompleted();

            } catch (NoSuchCoordsException e) {
                Status status = Status.NOT_FOUND.withDescription(e.getMessage());
                responseObserver.onError(status.asRuntimeException());
            } catch (InvalidSignatureException e) {
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
                byte[] signature = request.getSignature().toByteArray();
                String username = request.getUsername();

                byte[][] response = serverLogic.generateObtainLocationReportResponse(username, encryptedData, signature, true);

                //Create reply
                proto.HA.ObtainLocationReportReply reply = proto.HA.ObtainLocationReportReply.newBuilder()
                        .setMessage(ByteString.copyFrom(response[0]))
                        .setSignature(ByteString.copyFrom(response[1]))
                        .build();

                responseObserver.onNext(reply);
                responseObserver.onCompleted();

            } catch (NoSuchCoordsException e) {
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
                byte[] signature = request.getSignature().toByteArray();
                String username = request.getUsername();

                byte[][] response = serverLogic.generateObtainUsersAtLocationReportResponse(username, encryptedData, signature);

                //Create reply
                ObtainUsersAtLocationReply reply = ObtainUsersAtLocationReply.newBuilder()
                        .setMessage(ByteString.copyFrom(response[0]))
                        .setSignature(ByteString.copyFrom(response[1]))
                        .build();

                responseObserver.onNext(reply);
                responseObserver.onCompleted();

            } catch (InvalidSignatureException e) {
                Status status = Status.ABORTED.withDescription(e.getMessage());
                responseObserver.onError(status.asRuntimeException());
            }
        }

        @Override
        public void handshake(proto.HA.HandshakeRequest request, StreamObserver<proto.HA.HandshakeReply> responseObserver){
            byte[] encryptedUsernameSessionKeyJSON = request.getEncryptedUsernameSessionKey().toByteArray();
            byte[] signature = request.getSignature().toByteArray();
            byte[] iv = request.getIv().toByteArray();

            if(serverLogic.handshake(encryptedUsernameSessionKeyJSON, signature, iv)){
                proto.HA.HandshakeReply reply = proto.HA.HandshakeReply.newBuilder().build();
                responseObserver.onNext(reply);
            } else {
                Status status = Status.FAILED_PRECONDITION.withDescription("Failed handshake");
                responseObserver.onError(status.asRuntimeException());
            }

            responseObserver.onCompleted();
        }
    }
}

