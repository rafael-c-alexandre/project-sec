package util;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
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
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A simple client that requests a greeting from the {@link Server} with TLS.
 */
public class EncryptionLogic {
    private final static int ITERATIONS = 10000;
    private final static String CRYPTO_FOLDER_PATH = "src/main/assets/crypto/";

    public EncryptionLogic() {
    }

    public KeyPair generateUserKeyPair() {
        KeyPair keyPair = null;
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
            keyGen.initialize(2048, random);
            keyPair = keyGen.genKeyPair();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return keyPair;
    }

    public byte[] generateSecurePassword(String password, byte[] salt) {
        byte[] key = null;
        try {
            char[] chars = password.toCharArray();
            //rafa edit: this is just to demonstrate how to generate a PBKDF2 password-based kdf
            // because the salt needs to be the same
            //byte[] salt = PBKDF2Main.getNextSalt();

            PBEKeySpec spec = new PBEKeySpec(chars, salt, EncryptionLogic.ITERATIONS, 256 * 8);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            key = skf.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
        }
        return key;
    }

    public byte[] createDigitalSignature(byte[] fileBytes, PrivateKey privateKey) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        //Creating a Signature object
        Signature sign = Signature.getInstance("SHA256withRSA");

        //Initialize the signature
        sign.initSign(privateKey);

        //Adding data to the signature
        sign.update(fileBytes);
        //Calculating the signature

        return sign.sign();
    }

    public boolean verifyDigitalSignature(byte[] message, byte[] signature, PublicKey publicKey) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        //Creating a Signature object
        Signature sign = Signature.getInstance("SHA256withRSA");
        //Initializing the signature

        sign.initVerify(publicKey);
        sign.update(message);

        //Verifying the signature
        return sign.verify(signature);
    }

    public PublicKey getPublicKey(byte[] ownerPublicKey) {
        return (PublicKey) bytesToPubKey(ownerPublicKey);
    }

    public List<byte[]> getOthersAESEncrypted(List<byte[]> othersPubKeys, byte[] aesKey) {
        List<byte[]> othersAESEncrypted = new ArrayList<>();
        for (byte[] bytes : othersPubKeys) {
            othersAESEncrypted.add(encryptWithRSA(bytesToPubKey(bytes), aesKey));
        }
        return othersAESEncrypted;
    }

    public SecretKey generateAESKey() {

        KeyGenerator keyGen = null;
        try {
            keyGen = KeyGenerator.getInstance("AES");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        SecureRandom secRandom = new SecureRandom();

        Objects.requireNonNull(keyGen).init(256, secRandom);

        return keyGen.generateKey();

    }


    public byte[] decryptWithAES(SecretKey secretKey, byte[] file_bytes, byte[] iv) throws BadPaddingException, IllegalBlockSizeException {
        Cipher cipher;
        try {
            IvParameterSpec ivParams = new IvParameterSpec(iv);
            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParams);
            return cipher.doFinal(file_bytes);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }
        return null;

    }

    public byte[] encryptWithAES(SecretKey secretKey, byte[] file_bytes, byte[] iv) {
        Cipher cipher;
        try {
            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            IvParameterSpec ivParams = new IvParameterSpec(iv);

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParams);
            return cipher.doFinal(file_bytes);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }
        return null;
    }

    public byte[] decryptWithRSA(Key decryptionKey, byte[] file_bytes) {
        try {
            Cipher rsa;
            rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            rsa.init(Cipher.DECRYPT_MODE, decryptionKey);
            return rsa.doFinal(file_bytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public byte[] encryptWithRSA(Key encryptionKey, byte[] file_bytes) {
        try {
            Cipher rsa;
            rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");

            rsa.init(Cipher.ENCRYPT_MODE, encryptionKey);
            return rsa.doFinal(file_bytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public Key bytesToPubKey(byte[] bytes) {
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(new X509EncodedKeySpec(bytes));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
        }
        return null;
    }

    public SecretKey bytesToAESKey(byte[] bytes) {
        return new SecretKeySpec(bytes, 0, bytes.length, "AES");
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
