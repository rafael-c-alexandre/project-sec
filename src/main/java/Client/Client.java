package Client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import proto.*;
import proto.ServerGrpc;

import java.util.Scanner;

public class Client {

    private static ClientLogic clientLogic;

    public static void main(String[] args){

        if(args.length != 3){
            System.err.println("Invalid args. Try -> Client username gridFilePath privKeyFilePath certFilePath");
            return;
        }
        String username = args[0];
        String gridFilePath = args[1];
        String clientAddrMappingsFile = args[2];





        clientLogic = new ClientLogic(username,gridFilePath,clientAddrMappingsFile);

    }

    static class ClientToClientImp extends ClientToClientGrpc.ClientToClientImplBase {


        public ClientToClientImp() {

        }

        @Override
        public void requestLocationProof(RequestLocationProofRequest request, StreamObserver<RequestLocationProofReply> responseObserver) {
            super.requestLocationProof(request, responseObserver);
        }
    }
}
