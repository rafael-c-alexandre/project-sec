package util;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    public static void main(String[] args){

    }

    public static KeyPair generateUserKeyPair() {
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

    public static byte[] generateSecurePassword(String password, byte[] salt) {
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

    public static byte[] createDigitalSignature(byte[] fileBytes, PrivateKey privateKey) {
        //Creating a Signature object
        Signature sign = null;
        try {
            sign = Signature.getInstance("SHA256withRSA");
            //Initialize the signature
            sign.initSign(privateKey);

            //Adding data to the signature
            sign.update(fileBytes);
            //Calculating the signature

            return sign.sign();
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static boolean verifyDigitalSignature(byte[] message, byte[] signature, PublicKey publicKey) {
        //Creating a Signature object
        Signature sign = null;
        try {
            sign = Signature.getInstance("SHA256withRSA");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        //Initializing the signature

        try {
            Objects.requireNonNull(sign).initVerify(publicKey);
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
        try {
            Objects.requireNonNull(sign).update(message);
        } catch (SignatureException e) {
            e.printStackTrace();
        }

        //Verifying the signature
        try {
            return Objects.requireNonNull(sign).verify(signature);
        } catch (SignatureException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static List<byte[]> getOthersAESEncrypted(List<byte[]> othersPubKeys, byte[] aesKey) {
        List<byte[]> othersAESEncrypted = new ArrayList<>();
        for (byte[] bytes : othersPubKeys) {
            othersAESEncrypted.add(encryptWithRSA(bytesToPubKey(bytes), aesKey));
        }
        return othersAESEncrypted;
    }

    /*public byte[] getAESKeyBytes(byte[] AESEncryptedBytes, String username, KeyStore keyStore) {
        return decryptWithRSA(getPrivateKey(username, keyStore), AESEncryptedBytes);
    }*/

    public static SecretKey generateAESKey() {

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

   /* public byte[] decryptSecureFile(byte[] file_bytes, byte[] AESEncrypted, byte[] iv, String username, KeyStore keyStore) throws BadPaddingException, IllegalBlockSizeException {
        byte[] aesKeybytes = getAESKeyBytes(AESEncrypted, username, keyStore);
        SecretKey aesKey = bytesToAESKey(aesKeybytes);
        return decryptWithAES(aesKey, file_bytes, iv);
    }*/


    public static byte[] decryptWithAES(SecretKey secretKey, byte[] bytes, byte[] iv) {
        Cipher cipher;
        try {
            IvParameterSpec ivParams = new IvParameterSpec(iv);
            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParams);
            return cipher.doFinal(bytes);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException
                | IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
        }
        return null;

    }

    public static byte[] encryptWithAES(SecretKey secretKey, byte[] message_bytes, byte[] iv) {
        Cipher cipher;

        try {
            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");

            IvParameterSpec ivParams = new IvParameterSpec(iv);

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParams);
            return cipher.doFinal(message_bytes);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] generateIV() {
        //Generate new IV
        Cipher cipher = null;
        try {
            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            e.printStackTrace();
        }
        SecureRandom secureRandom = new SecureRandom();
        byte[] iv = new byte[Objects.requireNonNull(cipher).getBlockSize()];
        secureRandom.nextBytes(iv);
        return iv;
    }

    public static byte[] decryptWithRSA(Key decryptionKey, byte[] bytes) {
        try {
            Cipher rsa;
            rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            rsa.init(Cipher.DECRYPT_MODE, decryptionKey);
            return rsa.doFinal(bytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] encryptWithRSA(Key encryptionKey, byte[] bytes) {
        try {
            Cipher rsa;
            rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");

            rsa.init(Cipher.ENCRYPT_MODE, encryptionKey);
            return rsa.doFinal(bytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Key bytesToPubKey(byte[] bytes) {
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(new X509EncodedKeySpec(bytes));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static SecretKey bytesToAESKey(byte[] bytes) {
        return new SecretKeySpec(bytes, 0, bytes.length, "AES");
    }

    public static boolean verifyProofOfWork(long nonce, String data, String difficulty){
        try{
            String toDigest = data + nonce;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(toDigest.getBytes(StandardCharsets.UTF_8));

            toDigest = new String(encodedHash);
            return toDigest.startsWith(difficulty);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return true;
    }

    public static long generateProofOfWork(String data, String difficulty){
        try {

            long nonce = 0;
            while(true) {
                String toDigest = data + nonce;
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] encodedHash = digest.digest(toDigest.getBytes(StandardCharsets.UTF_8));

                toDigest = new String(encodedHash);

                if(toDigest.startsWith(difficulty))
                    return nonce;

                nonce++;
            }

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static PublicKey getPublicKey(String username) {
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

    public static PrivateKey getPrivateKey(String username, String keystorePasswd) {
        try {
            PrivateKey privateKey;
            KeyStore ks = KeyStore.getInstance("PKCS12");

            ks.load(new FileInputStream("./src/main/assets/keyStores/keyStore.p12"), keystorePasswd.toCharArray());
            privateKey = (PrivateKey) ks.getKey(  username + "_privkey", keystorePasswd.toCharArray());
            return privateKey;
        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException | IOException | CertificateException e) {
            e.printStackTrace();
        }
        return null;
    }

}
