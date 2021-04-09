package Server;

import Server.database.Connector;
import io.grpc.ServerBuilder;

import io.grpc.stub.StreamObserver;
import proto.*;
import proto.HA.HAToServerGrpc;
import proto.HA.ObtainUsersAtLocationReply;
import proto.HA.ObtainUsersAtLocationRequest;
import util.EncryptionLogic;

import java.io.IOException;
import java.sql.SQLException;


public class Server {

    private String dbuser;
    private String dbpassword;
    private Connector connector;
    private ServerLogic serverLogic;
    private EncryptionLogic encryptionLogic;

    private io.grpc.Server server;

    public Server(String user, String pass) throws SQLException {
        this.dbuser = user;
        this.dbpassword = pass;
        this.connector = new Connector(user,pass);
        this.serverLogic = new ServerLogic(this.connector.getConnection());
        this.encryptionLogic = new EncryptionLogic();
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

    }

    private void start() throws IOException {
        server = ServerBuilder
                .forPort(8080)
                .addService(new ServerImp(this.serverLogic,this.encryptionLogic))
                .addService(new HAToServerImp(this.serverLogic,this.encryptionLogic))
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
        private ServerLogic serverLogic;
        private EncryptionLogic encryptionLogic;

        public ServerImp(ServerLogic serverLogic,EncryptionLogic encryptionLogic) {
            this.serverLogic = serverLogic;
            this.encryptionLogic = encryptionLogic;
        }

        @Override
        public void submitLocationReport(SubmitLocationReportRequest request, StreamObserver<SubmitLocationReportReply> responseObserver) {
            super.submitLocationReport(request, responseObserver);
        }

        @Override
        public void obtainLocationReport(ObtainLocationReportRequest request, StreamObserver<ObtainLocationReportReply> responseObserver) {
            //TODO verify signature
            //if(!serverLogic.verifySignature(username,request.getMessage(),request.getSignature()))
            //

            String messageJson = encryptionLogic.decryptWithRSA(request.getMessage(),request.getSessionKey());
        }
    }

    static class HAToServerImp extends HAToServerGrpc.HAToServerImplBase{
        private ServerLogic serverLogic;
        private EncryptionLogic encryptionLogic;

        public HAToServerImp(ServerLogic serverLogic, EncryptionLogic encryptionLogic){
            this.serverLogic = serverLogic;
            this.encryptionLogic = encryptionLogic;
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

