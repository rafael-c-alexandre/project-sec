package Server;

import io.grpc.ManagedChannel;
import io.grpc.ServerBuilder;

import io.grpc.stub.StreamObserver;
import proto.GreetingReply;
import proto.GreetingRequest;
import proto.ServerGrpc;

import java.io.IOException;


public class Server {


    public Server() {

    }

    public static void main(String[] args) throws Exception {

        System.out.println("Server Started");

        io.grpc.Server server = ServerBuilder
                .forPort(8080)
                .addService(new ServerImp()).build();

        server.start();
        server.awaitTermination();
    }


    static class ServerImp extends ServerGrpc.ServerImplBase {


        public ServerImp() {

        }

        @Override
        public void greeting(GreetingRequest request, StreamObserver<GreetingReply> responseObserver) {

            GreetingReply reply = GreetingReply.newBuilder().setMessage("Hello from Server. Received: " + request.getMessage()).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }
}

