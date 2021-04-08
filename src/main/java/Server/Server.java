package Server;

import io.grpc.ServerBuilder;

import io.grpc.stub.StreamObserver;
import proto.*;
import proto.HA.HAToServerGrpc;
import proto.HA.ObtainUsersAtLocationReply;
import proto.HA.ObtainUsersAtLocationRequest;

import java.io.IOException;


public class Server {

    private String dbuser;
    private String dbpassword;

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

        server.start();

        System.out.println("Server Started");

        server.start();

    }

    private void start() throws IOException {
        server = ServerBuilder
                .forPort(8080)
                .addService(new ServerImp())
                .addService(new HAToServerImp())
                .build();

        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Use stderr here since the logger may have been reset by its JVM shutdown hook.
            System.err.println("*** shutting down gRPC server since JVM is shutting down");
            Server.this.stop();
            System.err.println("*** server shut down");
        }));
    }





    private void stop() {
        if (server != null) {
            server.shutdown();
        }
    }


    static class ServerImp extends ServerGrpc.ServerImplBase {


        public ServerImp() {

        }

        @Override
        public void submitLocationReport(SubmitLocationReportRequest request, StreamObserver<SubmitLocationReportReply> responseObserver) {
            super.submitLocationReport(request, responseObserver);
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

