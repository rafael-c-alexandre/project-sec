package Server;

import Exceptions.InvalidNumberOfProofsException;
import Exceptions.InvalidSignatureException;
import Exceptions.NoSuchCoordsException;
import Server.database.Connector;
import com.google.protobuf.ByteString;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import proto.*;
import proto.HA.HAToServerGrpc;
import proto.HA.ObtainUsersAtLocationReply;
import proto.HA.ObtainUsersAtLocationRequest;

import java.io.IOException;
import java.sql.SQLException;


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
        public void submitLocationReport(SubmitLocationReportRequest request, StreamObserver<SubmitLocationReportReply> responseObserver) {
            System.out.println("Received submit location report request from ");
            try {
                serverLogic.submitReport(request.getEncryptedMessage(), request.getEncryptedSessionKey(), request.getSignature(), request.getIv());

                SubmitLocationReportReply reply = SubmitLocationReportReply.newBuilder().build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();

            } catch (InvalidNumberOfProofsException e) {
                System.out.println("InvalidNumberOfProofsException: " + e.getMessage());
                Status status = Status.FAILED_PRECONDITION.withDescription(e.getMessage());
                responseObserver.onError(status.asRuntimeException());
            }
        }

        @Override
        public void obtainLocationReport(ObtainLocationReportRequest request, StreamObserver<ObtainLocationReportReply> responseObserver) {
            try {
                byte[] encryptedData = request.getMessage().toByteArray();
                byte[] encryptedSessionKey = request.getSessionKey().toByteArray();
                byte[] signature = request.getSignature().toByteArray();
                byte[] iv = request.getIv().toByteArray();

                byte[][] response = serverLogic.generateObtainLocationReportResponse(encryptedData, encryptedSessionKey, signature, iv, false);

                //Create reply
                ObtainLocationReportReply reply = ObtainLocationReportReply.newBuilder()
                        .setMessage(ByteString.copyFrom(response[0]))
                        .setSignature(ByteString.copyFrom(response[1]))
                        .setIv(ByteString.copyFrom(response[2]))
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
                byte[] encryptedSessionKey = request.getSessionKey().toByteArray();
                byte[] signature = request.getSignature().toByteArray();
                byte[] iv = request.getIv().toByteArray();

                byte[][] response = serverLogic.generateObtainLocationReportResponse(encryptedData, encryptedSessionKey, signature, iv, true);

                //Create reply
                proto.HA.ObtainLocationReportReply reply = proto.HA.ObtainLocationReportReply.newBuilder()
                        .setMessage(ByteString.copyFrom(response[0]))
                        .setSignature(ByteString.copyFrom(response[1]))
                        .setIv(ByteString.copyFrom(response[2]))
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


        }
    }
}

