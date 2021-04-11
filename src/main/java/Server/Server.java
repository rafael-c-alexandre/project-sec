package Server;

import Client.ClientLogic;
import Server.database.Connector;
import com.google.protobuf.ByteString;
import io.grpc.ServerBuilder;

import io.grpc.stub.StreamObserver;
import org.json.JSONObject;
import proto.*;
import proto.HA.HAToServerGrpc;
import proto.HA.ObtainUsersAtLocationReply;
import proto.HA.ObtainUsersAtLocationRequest;
import util.Coords;
import util.EncryptionLogic;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
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

        }

        @Override
        public void obtainLocationReport(ObtainLocationReportRequest request, StreamObserver<ObtainLocationReportReply> responseObserver) {

            EncryptionLogic encryptionLogic = new EncryptionLogic();

            byte[] encryptedData = request.getMessage().toByteArray();
            byte[] encryptedSessionKey = request.getSessionKey().toByteArray();
            byte[] signature = request.getSignature().toByteArray();
            byte[] iv = request.getIv().toByteArray();

            //Decrypt session key
            byte[] sessionKeyBytes = encryptionLogic.decryptWithRSA(encryptionLogic.getPrivateKey("server"),encryptedSessionKey);
            SecretKey sessionKey = encryptionLogic.bytesToAESKey(sessionKeyBytes);


            //Decrypt data
            try {
                byte[] decryptedData = encryptionLogic.decryptWithAES(sessionKey,encryptedData,iv);
                String jsonString = new String(decryptedData);
                JSONObject jsonObject = new JSONObject(jsonString);
                JSONObject message = jsonObject.getJSONObject("message");
                String username = message.getString("username");
                int epoch = message.getInt("epoch");


                //Verify signature
                //TODO invalid signature response
                if(!encryptionLogic.verifyDigitalSignature(decryptedData,signature,encryptionLogic.getPublicKey(username)))
                    System.out.println("Invalid signature!");
                else
                    System.out.println("Valid signature!");

                //process request
                Coords coords = serverLogic.obtainLocationReport(username,epoch);
                JSONObject jsonResponse = new JSONObject();
                JSONObject jsonResponseMessage = new JSONObject();

                jsonResponseMessage.put("x",coords.getX());
                jsonResponseMessage.put("y",coords.getY());

                jsonResponse.put("message",jsonResponseMessage);

                //encrypt response
                byte[] encryptedResponse = encryptionLogic.encryptWithAES(sessionKey,jsonResponse.toString().getBytes());


                //generate signature
                byte[] responseSignature =  encryptionLogic.createDigitalSignature(jsonResponse.toString().getBytes(),encryptionLogic.getPrivateKey(username));

                //Create reply
                ObtainLocationReportReply reply = ObtainLocationReportReply.newBuilder()
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();


            } catch (BadPaddingException e) {
                e.printStackTrace();
            } catch (IllegalBlockSizeException e) {
                e.printStackTrace();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (SignatureException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            }
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


        }
    }
}

