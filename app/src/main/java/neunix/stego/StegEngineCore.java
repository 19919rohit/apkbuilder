package neunix.stego;

import android.graphics.Bitmap;
import android.util.Log;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Random;

import javax.crypto.*;
import javax.crypto.spec.*;

public final class StegEngineCore {

    private static final String TAG = "StegoraEngine";

    private static final byte[] MAGIC =
            "NXSTEG".getBytes(StandardCharsets.US_ASCII);

    private static final byte VERSION = 1;

    private static final int HASH_LEN = 32;
    private static final int SALT_LEN = 16;
    private static final int IV_LEN = 16;

    private static final int PBKDF2_ITER = 65536;
    private static final int LSB = 2;

    private static final SecureRandom RAND = new SecureRandom();

    /* ------------------------------------------------ */
    /* CAPACITY                                         */
    /* ------------------------------------------------ */

    public static int getMaxPayloadSize(Bitmap bmp, String name) {

        int pixels = bmp.getWidth() * bmp.getHeight();
        int bits = pixels * 3 * LSB;

        int header =
                MAGIC.length +
                1 +
                1 +
                2 +
                name.getBytes(StandardCharsets.UTF_8).length +
                4 +
                HASH_LEN;

        return Math.max(0, (bits / 8) - header);
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
            OutputStream out) throws Exception {

        Bitmap result = embed(carrier, payload, fileName, password);

        if (!result.compress(Bitmap.CompressFormat.PNG, 100, out))
            throw new IOException("PNG write failed");
    }

    private static Bitmap embed(
            Bitmap carrier,
            byte[] payload,
            String fileName,
            String password) throws Exception {

        if (carrier.getConfig() != Bitmap.Config.ARGB_8888)
            carrier = carrier.copy(Bitmap.Config.ARGB_8888, true);

        boolean encrypted = password != null && !password.isEmpty();

        byte[] finalPayload =
                encrypted ? encrypt(payload, password) : payload;

        byte[] hash = sha256(finalPayload);

        byte[] packed =
                pack(fileName, finalPayload, encrypted, hash);

        int nameLen = fileName.getBytes(StandardCharsets.UTF_8).length;

        int headerLen =
                MAGIC.length +
                1 +
                1 +
                2 +
                nameLen +
                4 +
                HASH_LEN;

        byte[] header = Arrays.copyOfRange(packed, 0, headerLen);
        byte[] body = Arrays.copyOfRange(packed, headerLen, packed.length);

        int headerPixels = pixelsNeeded(header.length);

        Bitmap bmp = hideSequential(carrier, header);

        long seed = makeSeed(password, hash);

        bmp = hideRandom(bmp, body, seed, headerPixels);

        return bmp;
    }

    /* ------------------------------------------------ */
    /* EXTRACT                                          */
    /* ------------------------------------------------ */

    public static ExtractedData extract(Bitmap bmp, String password)
            throws Exception {

        byte[] sequential = revealSequential(bmp);

        PackedData header = unpack(sequential);

        int nameLen = header.fileName.getBytes(StandardCharsets.UTF_8).length;

        int headerLen =
                MAGIC.length +
                1 +
                1 +
                2 +
                nameLen +
                4 +
                HASH_LEN;

        int headerPixels = pixelsNeeded(headerLen);

        long seed = makeSeed(password, header.hash);

        byte[] body = revealRandom(bmp, seed, headerPixels);

        byte[] full = new byte[header.payload.length + body.length];

        System.arraycopy(header.payload, 0, full, 0, header.payload.length);
        System.arraycopy(body, 0, full, header.payload.length, body.length);

        PackedData p = unpack(full);

        byte[] data;

        if (p.encrypted) {

            if (password == null || password.isEmpty())
                throw new SecurityException("Password required");

            data = decrypt(p.payload, password);

        } else {
            data = p.payload;
        }

        if (!Arrays.equals(sha256(data), p.hash))
            throw new SecurityException("Integrity check failed");

        return new ExtractedData(p.fileName, data);
    }

    /* ------------------------------------------------ */
    /* PIXEL UTILS                                      */
    /* ------------------------------------------------ */

    private static int pixelsNeeded(int bytes) {

        int bits = bytes * 8;
        int perPixel = 3 * LSB;

        return (bits + perPixel - 1) / perPixel;
    }

    /* ------------------------------------------------ */
    /* SEQUENTIAL HIDE                                  */
    /* ------------------------------------------------ */

    private static Bitmap hideSequential(Bitmap bmp, byte[] data) {

        int w = bmp.getWidth();
        int h = bmp.getHeight();

        Bitmap copy = bmp.copy(Bitmap.Config.ARGB_8888, true);

        int[] px = new int[w * h];

        copy.getPixels(px, 0, w, 0, 0, w, h);

        int maskClear = ~((1 << LSB) - 1);

        int byteIndex = 0;
        int bitIndex = 0;

        outer:
        for (int i = 0; i < px.length; i++) {

            int p = px[i];

            int[] rgb = {
                    (p >> 16) & 0xFF,
                    (p >> 8) & 0xFF,
                    p & 0xFF
            };

            for (int c = 0; c < 3; c++) {

                int bits = 0;

                for (int b = 0; b < LSB; b++) {

                    bits <<= 1;
                    bits |= ((data[byteIndex] >> (7 - bitIndex)) & 1);

                    bitIndex++;

                    if (bitIndex == 8) {

                        bitIndex = 0;
                        byteIndex++;

                        if (byteIndex >= data.length)
                            break outer;
                    }
                }

                rgb[c] = (rgb[c] & maskClear) | bits;
            }

            px[i] =
                    (p & 0xFF000000)
                            | (rgb[0] << 16)
                            | (rgb[1] << 8)
                            | rgb[2];
        }

        copy.setPixels(px, 0, w, 0, 0, w, h);

        return copy;
    }

    private static byte[] revealSequential(Bitmap bmp) {

        int w = bmp.getWidth();
        int h = bmp.getHeight();

        int[] px = new int[w * h];

        bmp.getPixels(px, 0, w, 0, 0, w, h);

        byte[] out = new byte[(w * h * 3 * LSB) / 8];

        int bytePos = 0;
        int cur = 0;
        int bits = 0;

        for (int p : px) {

            int[] rgb = {
                    (p >> 16) & 0xFF,
                    (p >> 8) & 0xFF,
                    p & 0xFF
            };

            for (int c : rgb) {

                for (int k = LSB - 1; k >= 0; k--) {

                    cur = (cur << 1) | ((c >> k) & 1);

                    bits++;

                    if (bits == 8) {

                        if (bytePos < out.length)
                            out[bytePos++] = (byte) cur;

                        cur = 0;
                        bits = 0;
                    }
                }
            }
        }

        return Arrays.copyOf(out, bytePos);
    }

    /* ------------------------------------------------ */
    /* RANDOM HIDE / REVEAL                             */
    /* ------------------------------------------------ */

    private static Bitmap hideRandom(
            Bitmap bmp,
            byte[] data,
            long seed,
            int skipPixels) {

        int w = bmp.getWidth();
        int h = bmp.getHeight();

        int total = w * h;

        Bitmap copy = bmp.copy(Bitmap.Config.ARGB_8888, true);

        int[] px = new int[total];

        copy.getPixels(px, 0, w, 0, 0, w, h);

        int usable = total - skipPixels;

        int[] indices = new int[usable];

        for (int i = 0; i < usable; i++)
            indices[i] = i + skipPixels;

        Random rand = new Random(seed);

        for (int i = usable - 1; i > 0; i--) {

            int j = rand.nextInt(i + 1);

            int tmp = indices[i];
            indices[i] = indices[j];
            indices[j] = tmp;
        }

        int maskClear = ~((1 << LSB) - 1);

        int byteIndex = 0;
        int bitIndex = 0;

        outer:
        for (int idx : indices) {

            int p = px[idx];

            int[] rgb = {
                    (p >> 16) & 0xFF,
                    (p >> 8) & 0xFF,
                    p & 0xFF
            };

            for (int c = 0; c < 3; c++) {

                int bits = 0;

                for (int b = 0; b < LSB; b++) {

                    bits <<= 1;
                    bits |= ((data[byteIndex] >> (7 - bitIndex)) & 1);

                    bitIndex++;

                    if (bitIndex == 8) {

                        bitIndex = 0;
                        byteIndex++;

                        if (byteIndex >= data.length)
                            break outer;
                    }
                }

                rgb[c] = (rgb[c] & maskClear) | bits;
            }

            px[idx] =
                    (p & 0xFF000000)
                            | (rgb[0] << 16)
                            | (rgb[1] << 8)
                            | rgb[2];
        }

        copy.setPixels(px, 0, w, 0, 0, w, h);

        return copy;
    }

    private static byte[] revealRandom(
            Bitmap bmp,
            long seed,
            int skipPixels) {

        int w = bmp.getWidth();
        int h = bmp.getHeight();

        int total = w * h;

        int[] px = new int[total];

        bmp.getPixels(px, 0, w, 0, 0, w, h);

        int usable = total - skipPixels;

        int[] indices = new int[usable];

        for (int i = 0; i < usable; i++)
            indices[i] = i + skipPixels;

        Random rand = new Random(seed);

        for (int i = usable - 1; i > 0; i--) {

            int j = rand.nextInt(i + 1);

            int tmp = indices[i];
            indices[i] = indices[j];
            indices[j] = tmp;
        }

        byte[] out = new byte[(usable * 3 * LSB) / 8];

        int bytePos = 0;
        int cur = 0;
        int bits = 0;

        for (int idx : indices) {

            int p = px[idx];

            int[] rgb = {
                    (p >> 16) & 0xFF,
                    (p >> 8) & 0xFF,
                    p & 0xFF
            };

            for (int c : rgb) {

                for (int k = LSB - 1; k >= 0; k--) {

                    cur = (cur << 1) | ((c >> k) & 1);

                    bits++;

                    if (bits == 8) {

                        out[bytePos++] = (byte) cur;

                        cur = 0;
                        bits = 0;
                    }
                }
            }
        }

        return Arrays.copyOf(out, bytePos);
    }

    /* ------------------------------------------------ */
    /* PACK / UNPACK                                    */
    /* ------------------------------------------------ */

    private static byte[] pack(
            String name,
            byte[] payload,
            boolean enc,
            byte[] hash) throws IOException {

        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        out.write(MAGIC);
        out.write(VERSION);
        out.write(enc ? 1 : 0);

        out.write(ByteBuffer.allocate(2)
                .putShort((short) nameBytes.length)
                .array());

        out.write(nameBytes);

        out.write(ByteBuffer.allocate(4)
                .putInt(payload.length)
                .array());

        out.write(payload);
        out.write(hash);

        return out.toByteArray();
    }

    private static PackedData unpack(byte[] raw) throws IOException {

        ByteArrayInputStream in = new ByteArrayInputStream(raw);

        byte[] magic = in.readNBytes(MAGIC.length);

        if (!Arrays.equals(magic, MAGIC))
            throw new SecurityException("Not a Stegora image");

        in.read();

        boolean enc = in.read() == 1;

        int nameLen =
                ByteBuffer.wrap(in.readNBytes(2)).getShort() & 0xFFFF;

        String name =
                new String(in.readNBytes(nameLen), StandardCharsets.UTF_8);

        int payloadLen =
                ByteBuffer.wrap(in.readNBytes(4)).getInt();

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

        byte[] iv =
                Arrays.copyOfRange(data, SALT_LEN, SALT_LEN + IV_LEN);

        byte[] enc =
                Arrays.copyOfRange(data, SALT_LEN + IV_LEN, data.length);

        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");

        c.init(
                Cipher.DECRYPT_MODE,
                key(password, salt),
                new IvParameterSpec(iv)
        );

        return c.doFinal(enc);
    }

    private static SecretKey key(String pw, byte[] salt) throws Exception {

        KeySpec spec =
                new PBEKeySpec(pw.toCharArray(), salt, PBKDF2_ITER, 256);

        byte[] k =
                SecretKeyFactory
                        .getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(spec)
                        .getEncoded();

        return new SecretKeySpec(k, "AES");
    }

    private static byte[] sha256(byte[] data) throws Exception {

        return MessageDigest.getInstance("SHA-256").digest(data);
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

    private static long makeSeed(String password, byte[] hash)
            throws Exception {

        MessageDigest md =
                MessageDigest.getInstance("SHA-256");

        if (password != null && !password.isEmpty())
            md.update(password.getBytes(StandardCharsets.UTF_8));
        else
            md.update(hash);

        return ByteBuffer.wrap(md.digest()).getLong();
    }

    /* ------------------------------------------------ */
    /* DATA CLASSES                                     */
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