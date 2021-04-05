package Server;

import io.grpc.ServerBuilder;

import proto.HA.HAToServerGrpc;
import proto.ServerGrpc;



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


    }

    static class HAToServerImp extends HAToServerGrpc.HAToServerImplBase{

        public HAToServerImp(){

        }
    }
}

