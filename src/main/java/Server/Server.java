package Server;

import io.grpc.ServerBuilder;

import io.grpc.stub.StreamObserver;
import proto.*;
import proto.HA.HAToServerGrpc;
import proto.HA.ObtainUsersAtLocationReply;
import proto.HA.ObtainUsersAtLocationRequest;


public class Server {


    public Server() {

    }

    public static void main(String[] args) throws Exception {

        System.out.println("Server Started");

        io.grpc.Server server = ServerBuilder
                .forPort(8080)
                .addService(new ServerImp())
                .addService(new HAToServerImp())
                .build();

        server.start();
        server.awaitTermination();
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

