package Client;

import util.Coords;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;

public class ClientLogic {

    private final static String CRYPTO_FOLDER_PATH = "src/main/assets/crypto/";

    private String username;
    private Map<String, Map<Integer, Coords>> grid = new HashMap<>();
    private ClientToClientFrontend clientToClientFrontend = new ClientToClientFrontend();
    private ClientToServerFrontend clientToServerFrontend;

    public ClientLogic(String username,String gridFilePath, String clientAddrMappingsFile) {

        this.username = username;
        Scanner scanner = null;

        //Build frontends
        try {
            scanner = new Scanner(new File(clientAddrMappingsFile));
        } catch (FileNotFoundException e) {
            System.out.println("No such client mapping file!");
            return;
        }

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            // process the line
            String[] parts = line.split(",");
            String user = parts[0].trim();
            String host = parts[1].trim();
            int port = Integer.parseInt(parts[2].trim());

            //SERVER
            if(user.equals("#SERVER#")){
                this.clientToServerFrontend = new ClientToServerFrontend(host,port);
                continue;
            }

            //CLIENTS
            clientToClientFrontend.addUser(user,host,port);


        }

        //Populate the grid
        try {
            scanner = new Scanner(new File(gridFilePath));
        } catch (FileNotFoundException e) {
            System.out.println("No such grid file!");
            return;
        }

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            // process the line
            String[] parts = line.split(",");
            String user = parts[0].trim();
            int epoch = Integer.parseInt(parts[1].trim());
            int x = Integer.parseInt(parts[2].trim());
            int y = Integer.parseInt(parts[3].trim());



            if(!this.grid.containsKey(user))
                this.grid.put(user, new HashMap<>());

            this.grid.get(user).put(epoch,new Coords(x,y));


        }



    }

    public PublicKey getUserPublicKey(String username ){
        try {
            CertificateFactory fact = CertificateFactory.getInstance("X.509");
            FileInputStream is = new FileInputStream(CRYPTO_FOLDER_PATH + username + "/" + username + ".pem");
            X509Certificate cer = (X509Certificate) fact.generateCertificate(is);
            return cer.getPublicKey();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public PrivateKey getUserPrivateKey(String username){
        try {
            byte[] keyBytes = Files.readAllBytes(Paths.get(CRYPTO_FOLDER_PATH + username + "/" + username + ".key"));

            PKCS8EncodedKeySpec spec =
                    new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(spec);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
        }
        return null;
    }


}
