package neunix.stego;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtils {

    private static final int KEY_LENGTH = 32; // 256-bit AES
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_SIZE = 12;
    private static final int SALT_SIZE = 16;

    // ================= RANDOM =================

    public static byte[] randomBytes(int size) {
        byte[] b = new byte[size];
        new SecureRandom().nextBytes(b);
        return b;
    }

    public static byte[] generateSalt() {
        return randomBytes(SALT_SIZE);
    }

    public static byte[] generateIV() {
        return randomBytes(IV_SIZE);
    }

    // ================= ARGON2 KEY DERIVATION =================

    public static SecretKey deriveKey(String password, byte[] salt) {

        if (password == null) password = "";

        Argon2Parameters.Builder builder =
                new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                        .withSalt(salt)
                        .withIterations(3)        // time cost
                        .withMemoryAsKB(65536)    // 64 MB
                        .withParallelism(1);

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(builder.build());

        byte[] key = new byte[KEY_LENGTH];
        generator.generateBytes(password.toCharArray(), key);

        return new SecretKeySpec(key, "AES");
    }

    // ================= ENCRYPT =================

    public static byte[] encrypt(byte[] data,
                                 String password,
                                 byte[] salt,
                                 byte[] iv) throws Exception {

        SecretKey key = deriveKey(password, salt);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.ENCRYPT_MODE,
                key,
                new GCMParameterSpec(GCM_TAG_BITS, iv)
        );

        return cipher.doFinal(data);
    }

    // ================= DECRYPT =================

    public static byte[] decrypt(byte[] data,
                                 String password,
                                 byte[] salt,
                                 byte[] iv) throws Exception {

        SecretKey key = deriveKey(password, salt);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                new GCMParameterSpec(GCM_TAG_BITS, iv)
        );

        return cipher.doFinal(data);
    }

    // ================= SAFE WRAPPERS =================

    public static byte[] encryptIfNeeded(byte[] data,
                                          String password,
                                          byte[] salt,
                                          byte[] iv) throws Exception {

        if (password == null || password.isEmpty()) {
            return data; // no encryption
        }

        return encrypt(data, password, salt, iv);
    }

    public static byte[] decryptIfNeeded(byte[] data,
                                          String password,
                                          byte[] salt,
                                          byte[] iv,
                                          boolean passwordProtected) throws Exception {

        if (!passwordProtected) {
            return data; // no decryption
        }

        return decrypt(data, password, salt, iv);
    }
}