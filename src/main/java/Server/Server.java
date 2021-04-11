package Server;

import Client.ClientLogic;
import Server.database.Connector;
import com.google.protobuf.ByteString;
import io.grpc.ServerBuilder;

import io.grpc.stub.StreamObserver;
import proto.*;
import proto.HA.HAToServerGrpc;
import proto.HA.ObtainUsersAtLocationReply;
import proto.HA.ObtainUsersAtLocationRequest;
import util.EncryptionLogic;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Objects;



public class Server {

    private Connector connector;
    private ServerLogic serverLogic;


    private io.grpc.Server server;

    public Server(String user, String pass) throws SQLException {
        this.connector = new Connector(user,pass);
        this.serverLogic = new ServerLogic(this.connector.getConnection());
    }

    public static void main(String[] args) throws Exception {

        if(args.length != 2){
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

        private ServerLogic serverLogic;

        public ServerImp(ServerLogic serverLogic) {
            this.serverLogic = serverLogic;

        }

        @Override
        public void submitLocationReport(SubmitLocationReportRequest request, StreamObserver<SubmitLocationReportReply> responseObserver) {
            System.out.println("Received submit location report request from " + request.getUsername());

            serverLogic.submitReport(request.getEncryptedMessage(), request.getEncryptedSessionKey(), request.getSignature(), request.getIv());

            SubmitLocationReportReply reply = SubmitLocationReportReply.newBuilder().build();

            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }

        @Override
        public void obtainLocationReport(ObtainLocationReportRequest request, StreamObserver<ObtainLocationReportReply> responseObserver) {
            //TODO verify signature
            //if(!serverLogic.verifySignature(username,request.getMessage(),request.getSignature()))
            //

            //String messageJson = encryptionLogic.decryptWithRSA(request.getMessage(),request.getSessionKey());
        }
    }

    static class HAToServerImp extends HAToServerGrpc.HAToServerImplBase{
        private ServerLogic serverLogic;

        public HAToServerImp(ServerLogic serverLogic){
            this.serverLogic = serverLogic;
        }

        @Override
        public void obtainLocationReport(proto.HA.ObtainLocationReportRequest request, StreamObserver<proto.HA.ObtainLocationReportReply> responseObserver) {

        }

        @Override
        public void obtainUsersAtLocation(ObtainUsersAtLocationRequest request, StreamObserver<ObtainUsersAtLocationReply> responseObserver) {
            super.obtainUsersAtLocation(request, responseObserver);
        }
    }
}

