package neunix.stego;

import android.graphics.Bitmap;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.KeySpec;
import java.util.*;

import javax.crypto.*;
import javax.crypto.spec.*;

public final class StegEngineCore {

    private static final byte[] MAGIC = "NXSTEG".getBytes(StandardCharsets.US_ASCII);
    private static final byte VERSION = 1;

    private static final int HASH_LEN = 32;

    private static final int SALT_LEN = 16;
    private static final int IV_LEN = 16;

    private static final int PBKDF2_ITER = 65536;

    private static final SecureRandom RAND = new SecureRandom();

    /* ------------------------------------------------ */
    /* CAPACITY                                         */
    /* ------------------------------------------------ */

    public static int getMaxPayloadSize(Bitmap bmp, String name) {

        int pixels = bmp.getWidth() * bmp.getHeight();

        // adaptive average ≈ 2.5 bits per channel
        int totalBits = pixels * 3 * 2;

        int header =
                MAGIC.length +
                1 +
                1 +
                2 +
                name.getBytes(StandardCharsets.UTF_8).length +
                4 +
                HASH_LEN;

        return Math.max(0, (totalBits / 8) - header);
    }

    public static int getMaxPayloadSize(Bitmap bmp) {
        return getMaxPayloadSize(bmp, "");
    }

    /* ------------------------------------------------ */
    /* EMBED                                            */
    /* ------------------------------------------------ */

    public static void embed(
            Bitmap carrier,
            byte[] payload,
            String fileName,
            String password,
            OutputStream out
    ) throws Exception {

        if (carrier.getConfig() != Bitmap.Config.ARGB_8888)
            carrier = carrier.copy(Bitmap.Config.ARGB_8888, true);

        boolean encrypted = password != null && !password.isEmpty();

        byte[] finalPayload = encrypted
                ? encrypt(payload, password)
                : payload;

        byte[] hash = sha256(finalPayload);

        long seed = makeSeed(password, hash);

        byte[] packed = pack(fileName, finalPayload, encrypted, hash);

        int capacity = getMaxPayloadSize(carrier, fileName);

        if (packed.length > capacity)
            throw new IllegalArgumentException("Payload too large for this image");

        Bitmap result = hide(carrier, packed, seed);

        if (!result.compress(Bitmap.CompressFormat.PNG, 100, out))
            throw new IOException("PNG write failed");
    }

    /* ------------------------------------------------ */
    /* EXTRACT                                          */
    /* ------------------------------------------------ */

    public static ExtractedData extract(Bitmap bmp, String password) throws Exception {

        byte[] raw = revealSequential(bmp);

        PackedData p = unpack(raw);

        long seed = makeSeed(password, p.hash);

        byte[] shuffled = revealRandom(bmp, seed);

        PackedData finalPack = unpack(shuffled);

        byte[] data;

        if (finalPack.encrypted) {

            if (password == null || password.isEmpty())
                throw new SecurityException("Password required");

            data = decrypt(finalPack.payload, password);

        } else {

            data = finalPack.payload;
        }

        if (!Arrays.equals(sha256(data), finalPack.hash))
            throw new SecurityException("Integrity check failed");

        return new ExtractedData(finalPack.fileName, data);
    }

    /* ------------------------------------------------ */
    /* ADAPTIVE LSB                                     */
    /* ------------------------------------------------ */

    private static int adaptiveLSB(int r, int g, int b) {

        int diff = Math.abs(r - g) + Math.abs(g - b) + Math.abs(b - r);

        if (diff > 100)
            return 3;

        if (diff > 40)
            return 2;

        return 1;
    }

    /* ------------------------------------------------ */
    /* HIDE                                             */
    /* ------------------------------------------------ */

    private static Bitmap hide(Bitmap carrier, byte[] data, long seed) {

        int w = carrier.getWidth();
        int h = carrier.getHeight();
        int n = w * h;

        Bitmap bmp = carrier.copy(Bitmap.Config.ARGB_8888, true);

        int[] px = new int[n];
        bmp.getPixels(px, 0, w, 0, 0, w, h);

        int[] order = shuffledPixels(n, seed);

        int byteIndex = 0;
        int bitIndex = 0;

        outer:
        for (int i : order) {

            int p = px[i];

            int r = (p >> 16) & 255;
            int g = (p >> 8) & 255;
            int b = p & 255;

            int lsb = adaptiveLSB(r, g, b);

            int maskClear = ~((1 << lsb) - 1);

            int[] rgb = {r, g, b};

            for (int c = 0; c < 3; c++) {

                if (byteIndex >= data.length)
                    break outer;

                int bits = 0;

                for (int bit = 0; bit < lsb; bit++) {

                    bits <<= 1;
                    bits |= ((data[byteIndex] >> (7 - bitIndex)) & 1);

                    bitIndex++;

                    if (bitIndex == 8) {

                        bitIndex = 0;
                        byteIndex++;

                        if (byteIndex >= data.length)
                            break;
                    }
                }

                rgb[c] = (rgb[c] & maskClear) | bits;
            }

            px[i] =
                    (p & 0xFF000000) |
                            (rgb[0] << 16) |
                            (rgb[1] << 8) |
                            rgb[2];
        }

        bmp.setPixels(px, 0, w, 0, 0, w, h);

        return bmp;
    }

    /* ------------------------------------------------ */
    /* REVEAL                                           */
    /* ------------------------------------------------ */

    private static byte[] revealRandom(Bitmap bmp, long seed) {

        int w = bmp.getWidth();
        int h = bmp.getHeight();

        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);

        int[] order = shuffledPixels(px.length, seed);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int cur = 0;
        int bits = 0;

        for (int idx : order) {

            int p = px[idx];

            int r = (p >> 16) & 255;
            int g = (p >> 8) & 255;
            int b = p & 255;

            int lsb = adaptiveLSB(r, g, b);

            int[] rgb = {r, g, b};

            for (int c : rgb) {

                for (int i = lsb - 1; i >= 0; i--) {

                    cur = (cur << 1) | ((c >> i) & 1);

                    bits++;

                    if (bits == 8) {

                        out.write(cur);

                        cur = 0;
                        bits = 0;
                    }
                }
            }
        }

        return out.toByteArray();
    }

    private static byte[] revealSequential(Bitmap bmp) {

        int w = bmp.getWidth();
        int h = bmp.getHeight();

        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int cur = 0;
        int bits = 0;

        for (int p : px) {

            int r = (p >> 16) & 255;
            int g = (p >> 8) & 255;
            int b = p & 255;

            int lsb = adaptiveLSB(r, g, b);

            int[] rgb = {r, g, b};

            for (int c : rgb) {

                for (int i = lsb - 1; i >= 0; i--) {

                    cur = (cur << 1) | ((c >> i) & 1);

                    bits++;

                    if (bits == 8) {

                        out.write(cur);

                        cur = 0;
                        bits = 0;
                    }
                }
            }
        }

        return out.toByteArray();
    }

    /* ------------------------------------------------ */
    /* RANDOM PIXELS                                    */
    /* ------------------------------------------------ */

    private static int[] shuffledPixels(int n, long seed) {

        int[] arr = new int[n];

        for (int i = 0; i < n; i++)
            arr[i] = i;

        Random r = new Random(seed);

        for (int i = n - 1; i > 0; i--) {

            int j = r.nextInt(i + 1);

            int t = arr[i];
            arr[i] = arr[j];
            arr[j] = t;
        }

        return arr;
    }

    /* ------------------------------------------------ */
    /* PACK                                             */
    /* ------------------------------------------------ */

    private static byte[] pack(String name, byte[] payload, boolean enc, byte[] hash) throws IOException {

        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        out.write(MAGIC);
        out.write(VERSION);
        out.write(enc ? 1 : 0);

        out.write(ByteBuffer.allocate(2).putShort((short) nameBytes.length).array());

        out.write(nameBytes);

        out.write(ByteBuffer.allocate(4).putInt(payload.length).array());

        out.write(payload);
        out.write(hash);

        return out.toByteArray();
    }

    private static PackedData unpack(byte[] raw) throws IOException {

        ByteArrayInputStream in = new ByteArrayInputStream(raw);

        byte[] magic = in.readNBytes(MAGIC.length);

        if (!Arrays.equals(magic, MAGIC))
            throw new SecurityException("Not a Stegora image");

        int version = in.read();

        if (version != VERSION)
            throw new SecurityException("Unsupported version");

        boolean enc = in.read() == 1;

        int nameLen = ByteBuffer.wrap(in.readNBytes(2)).getShort() & 0xFFFF;

        String name = new String(in.readNBytes(nameLen), StandardCharsets.UTF_8);

        int payloadLen = ByteBuffer.wrap(in.readNBytes(4)).getInt();

        byte[] payload = in.readNBytes(payloadLen);

        byte[] hash = in.readNBytes(HASH_LEN);

        return new PackedData(name, payload, enc, hash);
    }

    /* ------------------------------------------------ */
    /* CRYPTO                                           */
    /* ------------------------------------------------ */

    private static byte[] encrypt(byte[] data, String password) throws Exception {

        byte[] salt = random(SALT_LEN);
        byte[] iv = random(IV_LEN);

        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");

        c.init(
                Cipher.ENCRYPT_MODE,
                key(password, salt),
                new IvParameterSpec(iv)
        );

        return concat(salt, iv, c.doFinal(data));
    }

    private static byte[] decrypt(byte[] data, String password) throws Exception {

        byte[] salt = Arrays.copyOfRange(data, 0, SALT_LEN);
        byte[] iv = Arrays.copyOfRange(data, SALT_LEN, SALT_LEN + IV_LEN);
        byte[] enc = Arrays.copyOfRange(data, SALT_LEN + IV_LEN, data.length);

        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");

        c.init(
                Cipher.DECRYPT_MODE,
                key(password, salt),
                new IvParameterSpec(iv)
        );

        return c.doFinal(enc);
    }

    private static SecretKey key(String pw, byte[] salt) throws Exception {

        KeySpec spec = new PBEKeySpec(pw.toCharArray(), salt, PBKDF2_ITER, 256);

        byte[] k = SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .getEncoded();

        return new SecretKeySpec(k, "AES");
    }

    private static byte[] random(int n) {

        byte[] b = new byte[n];
        RAND.nextBytes(b);
        return b;
    }

    private static byte[] concat(byte[]... arr) throws IOException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        for (byte[] a : arr)
            out.write(a);

        return out.toByteArray();
    }

    private static byte[] sha256(byte[] data) throws Exception {

        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    private static long makeSeed(String password, byte[] hash) throws Exception {

        MessageDigest md = MessageDigest.getInstance("SHA-256");

        if (password != null && !password.isEmpty())
            md.update(password.getBytes(StandardCharsets.UTF_8));
        else
            md.update(hash);

        return ByteBuffer.wrap(md.digest()).getLong();
    }

    /* ------------------------------------------------ */

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
        final byte[] hash;

        PackedData(String n, byte[] p, boolean e, byte[] h) {
            fileName = n;
            payload = p;
            encrypted = e;
            hash = h;
        }
    }
}