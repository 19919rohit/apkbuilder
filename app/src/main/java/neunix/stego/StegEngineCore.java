package neunix.stego;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.KeySpec;
import java.util.Arrays;

import javax.crypto.*;
import javax.crypto.spec.*;

public class StegEngineCore {

    private static final String AES_MODE = "AES/CBC/PKCS5Padding";
    private static final int AES_KEY_SIZE = 256;
    private static final int PBKDF2_ITER = 65536;
    private static final int SALT_LEN = 16;
    private static final int IV_LEN = 16;

    private static final byte[] MAGIC = "NXSTEG".getBytes(StandardCharsets.US_ASCII);

    /* ================= CAPACITY ================= */

    public static int getMaxPayloadSize(Bitmap carrier) {
        int totalBits = carrier.getWidth() * carrier.getHeight() * 3;
        int header =
                MAGIC.length +     // magic
                1 +                // enc flag
                2 +                // name length
                4 +                // payload length
                SALT_LEN + IV_LEN; // worst case crypto
        return Math.max(0, (totalBits / 8) - header);
    }

    /* ================= PUBLIC API ================= */

    public static void embed(
            Bitmap carrier,
            byte[] payload,
            String originalName,
            String password,
            OutputStream out
    ) throws Exception {

        boolean encrypted = password != null && !password.isEmpty();
        byte[] finalPayload = encrypted ? encrypt(payload, password) : payload;

        byte[] packed = pack(originalName, finalPayload, encrypted);

        if (packed.length > getMaxPayloadSize(carrier))
            throw new IllegalArgumentException("Payload too large");

        Bitmap result = hide(carrier, packed);

        if (!result.compress(Bitmap.CompressFormat.PNG, 100, out))
            throw new IOException("Failed to write PNG");
    }

    public static ExtractedData extract(Bitmap stego, String password) throws Exception {
        byte[] raw = reveal(stego);
        PackedData p = unpack(raw);

        byte[] data;

        if (p.encrypted) {
            if (password == null || password.isEmpty())
                throw new SecurityException("This image is password protected");

            try {
                data = decrypt(p.payload, password);
            } catch (GeneralSecurityException e) {
                throw new SecurityException("Wrong password");
            }
        } else {
            if (password != null && !password.isEmpty())
                throw new SecurityException("Image is not password protected");

            data = p.payload;
        }

        return new ExtractedData(p.fileName, data);
    }

    /* ================= STEGO ================= */

    private static Bitmap hide(Bitmap carrier, byte[] data) {
        Bitmap bmp = carrier.copy(Bitmap.Config.ARGB_8888, true);
        int byteIndex = 0, bitIndex = 0;

        outer:
        for (int y = 0; y < bmp.getHeight(); y++) {
            for (int x = 0; x < bmp.getWidth(); x++) {

                if (byteIndex >= data.length) break outer;

                int px = bmp.getPixel(x, y);
                int[] rgb = {Color.red(px), Color.green(px), Color.blue(px)};

                for (int i = 0; i < 3; i++) {
                    if (byteIndex >= data.length) break;

                    int bit = (data[byteIndex] >> (7 - bitIndex)) & 1;
                    rgb[i] = (rgb[i] & 0xFE) | bit;

                    bitIndex++;
                    if (bitIndex == 8) {
                        bitIndex = 0;
                        byteIndex++;
                    }
                }

                bmp.setPixel(x, y,
                        Color.argb(Color.alpha(px), rgb[0], rgb[1], rgb[2]));
            }
        }
        return bmp;
    }

    private static byte[] reveal(Bitmap bmp) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int cur = 0, bits = 0;

        for (int y = 0; y < bmp.getHeight(); y++) {
            for (int x = 0; x < bmp.getWidth(); x++) {
                int px = bmp.getPixel(x, y);
                int[] rgb = {Color.red(px), Color.green(px), Color.blue(px)};

                for (int c : rgb) {
                    cur = (cur << 1) | (c & 1);
                    bits++;
                    if (bits == 8) {
                        out.write(cur);
                        bits = 0;
                        cur = 0;
                    }
                }
            }
        }
        return out.toByteArray();
    }

    /* ================= PACK ================= */

    private static byte[] pack(String fileName, byte[] payload, boolean encrypted)
            throws IOException {

        byte[] nameBytes = fileName.getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(MAGIC);
        out.write(encrypted ? 1 : 0); // encryption flag
        out.write(ByteBuffer.allocate(2).putShort((short) nameBytes.length).array());
        out.write(nameBytes);
        out.write(ByteBuffer.allocate(4).putInt(payload.length).array());
        out.write(payload);

        return out.toByteArray();
    }

    private static PackedData unpack(byte[] raw) throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(raw);

        byte[] magic = in.readNBytes(MAGIC.length);
        if (!Arrays.equals(magic, MAGIC))
            throw new SecurityException("Not a StegoBox image");

        boolean encrypted = in.read() == 1;

        int nameLen = ByteBuffer.wrap(in.readNBytes(2)).getShort() & 0xFFFF;
        String name = new String(in.readNBytes(nameLen), StandardCharsets.UTF_8);

        int payloadLen = ByteBuffer.wrap(in.readNBytes(4)).getInt();
        byte[] payload = in.readNBytes(payloadLen);

        return new PackedData(name, payload, encrypted);
    }

    /* ================= CRYPTO ================= */

    private static byte[] encrypt(byte[] data, String password) throws Exception {
        byte[] salt = random(SALT_LEN);
        byte[] iv = random(IV_LEN);

        Cipher c = Cipher.getInstance(AES_MODE);
        c.init(Cipher.ENCRYPT_MODE, key(password, salt), new IvParameterSpec(iv));

        return concat(salt, iv, c.doFinal(data));
    }

    private static byte[] decrypt(byte[] data, String password) throws Exception {
        byte[] salt = Arrays.copyOfRange(data, 0, SALT_LEN);
        byte[] iv = Arrays.copyOfRange(data, SALT_LEN, SALT_LEN + IV_LEN);
        byte[] enc = Arrays.copyOfRange(data, SALT_LEN + IV_LEN, data.length);

        Cipher c = Cipher.getInstance(AES_MODE);
        c.init(Cipher.DECRYPT_MODE, key(password, salt), new IvParameterSpec(iv));
        return c.doFinal(enc);
    }

    private static SecretKey key(String pw, byte[] salt) throws Exception {
        KeySpec spec = new PBEKeySpec(pw.toCharArray(), salt, PBKDF2_ITER, AES_KEY_SIZE);
        byte[] k = SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .getEncoded();
        return new SecretKeySpec(k, "AES");
    }

    private static byte[] random(int n) {
        byte[] b = new byte[n];
        new SecureRandom().nextBytes(b);
        return b;
    }

    private static byte[] concat(byte[]... a) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] b : a) out.write(b);
        return out.toByteArray();
    }

    /* ================= MODELS ================= */

    public static class ExtractedData {
        public final String fileName;
        public final byte[] data;
        public ExtractedData(String n, byte[] d) {
            fileName = n;
            data = d;
        }
    }

    private static class PackedData {
        final String fileName;
        final byte[] payload;
        final boolean encrypted;
        PackedData(String n, byte[] p, boolean e) {
            fileName = n;
            payload = p;
            encrypted = e;
        }
    }
}