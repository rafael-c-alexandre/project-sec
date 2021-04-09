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

import java.io.IOException;
import java.sql.SQLException;
import java.util.Objects;


public class Server {

    private String dbuser;
    private String dbpassword;
    private static ServerLogic serverLogic;

    private io.grpc.Server server;

    public Server(String user, String pass) {
        this.dbuser = user;
        this.dbpassword = pass;
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

        Connector connector = null;
        try {
            connector = new Connector(server.dbuser, server.dbpassword);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        serverLogic = new ServerLogic(Objects.requireNonNull(connector).getConnection());

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
                .addService(new ServerImp())
                .addService(new HAToServerImp())
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

        public ServerImp() {

        }

        @Override
        public void submitLocationReport(SubmitLocationReportRequest request, StreamObserver<SubmitLocationReportReply> responseObserver) {
            System.out.println("Received submit location report request");
            serverLogic.submitReport(request.getMessage());

        }

        @Override
        public void obtainLocationReport(ObtainLocationReportRequest request, StreamObserver<ObtainLocationReportReply> responseObserver) {
            super.obtainLocationReport(request, responseObserver);
        }
    }

    static class HAToServerImp extends HAToServerGrpc.HAToServerImplBase{

        public HAToServerImp(){

        }

        @Override
        public void obtainLocationReport(proto.HA.ObtainLocationReportRequest request, StreamObserver<proto.HA.ObtainLocationReportReply> responseObserver) {
            super.obtainLocationReport(request, responseObserver);
        }

        @Override
        public void obtainUsersAtLocation(ObtainUsersAtLocationRequest request, StreamObserver<ObtainUsersAtLocationReply> responseObserver) {
            super.obtainUsersAtLocation(request, responseObserver);
        }
    }
}

