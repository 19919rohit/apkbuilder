package neunix.stego;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.io.*;
import java.nio.ByteBuffer;
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
    private static final byte[] MAGIC = "NXSTEG".getBytes();

    /* ================= CAPACITY ================= */

    public static int getMaxPayloadSize(Bitmap carrier) {
        int totalBits = carrier.getWidth() * carrier.getHeight() * 3;
        int header = MAGIC.length + 4 + SALT_LEN + IV_LEN;
        return Math.max(0, (totalBits / 8) - header);
    }

    /* ================= PUBLIC API ================= */

    public static void embed(
            Bitmap carrier,
            byte[] payload,
            String password,
            OutputStream out
    ) throws Exception {

        int max = getMaxPayloadSize(carrier);
        if (payload.length > max)
            throw new IllegalArgumentException(
                    "Payload too large. Max allowed: " + max + " bytes"
            );

        byte[] encrypted = encrypt(payload, password);
        byte[] packed = pack(encrypted);
        Bitmap result = hide(carrier, packed);

        if (!result.compress(Bitmap.CompressFormat.PNG, 100, out))
            throw new IOException("Failed to write PNG");
    }

    public static byte[] extract(Bitmap stego, String password) throws Exception {
        byte[] packed = reveal(stego);
        return decrypt(unpack(packed), password);
    }

    /* ================= STEGO ================= */

    private static Bitmap hide(Bitmap carrier, byte[] data) {
        Bitmap bmp = carrier.copy(Bitmap.Config.ARGB_8888, true);
        int width = bmp.getWidth();
        int height = bmp.getHeight();

        int byteIndex = 0;
        int bitIndex = 0;

        outer:
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {

                if (byteIndex >= data.length) break outer;

                int px = bmp.getPixel(x, y);
                int[] rgb = {
                        Color.red(px),
                        Color.green(px),
                        Color.blue(px)
                };

                for (int c = 0; c < 3; c++) {
                    if (byteIndex >= data.length) break;

                    int bit = (data[byteIndex] >> (7 - bitIndex)) & 1;
                    rgb[c] = (rgb[c] & 0xFE) | bit;

                    bitIndex++;
                    if (bitIndex == 8) {
                        bitIndex = 0;
                        byteIndex++;
                    }
                }

                bmp.setPixel(x, y, Color.argb(Color.alpha(px), rgb[0], rgb[1], rgb[2]));
            }
        }
        return bmp;
    }

    private static byte[] reveal(Bitmap bmp) throws IOException {
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

    private static byte[] pack(byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(MAGIC);
        out.write(ByteBuffer.allocate(4).putInt(data.length).array());
        out.write(data);
        return out.toByteArray();
    }

    private static byte[] unpack(byte[] raw) throws IOException {
        if (raw.length < MAGIC.length + 4)
            throw new SecurityException("No stego data");

        ByteArrayInputStream in = new ByteArrayInputStream(raw);
        byte[] magic = in.readNBytes(MAGIC.length);

        if (!Arrays.equals(magic, MAGIC))
            throw new SecurityException("Invalid stego image");

        int len = ByteBuffer.wrap(in.readNBytes(4)).getInt();
        if (len <= 0 || len > raw.length)
            throw new SecurityException("Corrupted stego payload");

        return in.readNBytes(len);
    }

    /* ================= CRYPTO ================= */

    private static byte[] encrypt(byte[] data, String password) throws Exception {
        if (password == null || password.isEmpty()) return data;

        byte[] salt = random(SALT_LEN);
        byte[] iv = random(IV_LEN);

        Cipher c = Cipher.getInstance(AES_MODE);
        c.init(Cipher.ENCRYPT_MODE, key(password, salt), new IvParameterSpec(iv));

        return concat(salt, iv, c.doFinal(data));
    }

    private static byte[] decrypt(byte[] data, String password) throws Exception {
        if (password == null || password.isEmpty()) return data;

        if (data.length < SALT_LEN + IV_LEN)
            throw new SecurityException("Invalid encrypted payload");

        byte[] salt = Arrays.copyOfRange(data, 0, SALT_LEN);
        byte[] iv = Arrays.copyOfRange(data, SALT_LEN, SALT_LEN + IV_LEN);
        byte[] enc = Arrays.copyOfRange(data, SALT_LEN + IV_LEN, data.length);

        Cipher c = Cipher.getInstance(AES_MODE);
        c.init(Cipher.DECRYPT_MODE, key(password, salt), new IvParameterSpec(iv));
        return c.doFinal(enc);
    }

    private static SecretKey key(String pw, byte[] salt) throws Exception {
        KeySpec spec = new PBEKeySpec(pw.toCharArray(), salt, PBKDF2_ITER, AES_KEY_SIZE);
        byte[] k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).getEncoded();
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
}