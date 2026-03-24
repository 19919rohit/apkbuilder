package neunix.stego;

import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtils {

    private static final int KEY_LENGTH = 256; // bits
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_SIZE = 12;
    private static final int SALT_SIZE = 16;

    // 🔥 Tuned for mobile (fast + strong)
    private static final int PBKDF2_ITER = 200000;

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

    // ================= KEY DERIVATION =================

    public static SecretKey deriveKey(String password, byte[] salt) throws Exception {

        if (password == null) password = "";

        SecretKeyFactory factory =
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

        KeySpec spec = new PBEKeySpec(
                password.toCharArray(),
                salt,
                PBKDF2_ITER,
                KEY_LENGTH
        );

        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
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
            return data;
        }

        return encrypt(data, password, salt, iv);
    }

    public static byte[] decryptIfNeeded(byte[] data,
                                         String password,
                                         byte[] salt,
                                         byte[] iv,
                                         boolean passwordProtected) throws Exception {

        if (!passwordProtected) {
            return data;
        }

        return decrypt(data, password, salt, iv);
    }
}