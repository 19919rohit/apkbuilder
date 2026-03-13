package neunix.stego;

import android.graphics.Bitmap;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.KeySpec;
import java.util.Arrays;

import javax.crypto.*;
import javax.crypto.spec.*;

public final class StegEngineCore {

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
                MAGIC.length + 1 + 1 + 2 +
                        name.getBytes(StandardCharsets.UTF_8).length +
                        4 + HASH_LEN;

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
            OutputStream out
    ) throws Exception {

        if (carrier.getConfig() != Bitmap.Config.ARGB_8888)
            carrier = carrier.copy(Bitmap.Config.ARGB_8888, true);

        boolean encrypted =
                password != null && !password.isEmpty();

        byte[] finalPayload =
                encrypted ? encrypt(payload, password) : payload;

        byte[] hash = sha256(finalPayload);

        long seed = makeSeed(password, hash);

        byte[] header =
                packHeader(fileName, finalPayload.length, encrypted, hash);

        Bitmap bmp =
                carrier.copy(Bitmap.Config.ARGB_8888, true);

        embedSequential(bmp, header);

        embedRandom(bmp, finalPayload, seed, header.length);

        if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, out))
            throw new IOException("PNG write failed");
    }

    /* ------------------------------------------------ */
    /* EXTRACT                                          */
    /* ------------------------------------------------ */

    public static ExtractedData extract(
            Bitmap bmp,
            String password
    ) throws Exception {

        byte[] sequential = revealSequential(bmp);

        Header header = unpackHeader(sequential);

        long seed = makeSeed(password, header.hash);

        byte[] payload =
                revealRandom(bmp, seed, header.payloadLen, header.headerBytes);

        byte[] data;

        if (header.encrypted) {

            if (password == null || password.isEmpty())
                throw new SecurityException("Password required");

            data = decrypt(payload, password);

        } else {
            data = payload;
        }

        if (!Arrays.equals(sha256(payload), header.hash))
            throw new SecurityException("Integrity check failed");

        return new ExtractedData(header.fileName, data);
    }

    /* ------------------------------------------------ */
    /* SEQUENTIAL HEADER EMBED                          */
    /* ------------------------------------------------ */

    private static void embedSequential(Bitmap bmp, byte[] data) {

        int w = bmp.getWidth();
        int h = bmp.getHeight();

        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);

        int byteIndex = 0;
        int bitIndex = 0;

        int maskClear = ~((1 << LSB) - 1);

        for (int i = 0; i < px.length; i++) {

            int p = px[i];

            int r = (p >> 16) & 255;
            int g = (p >> 8) & 255;
            int b = p & 255;

            int[] rgb = {r, g, b};

            for (int c = 0; c < 3; c++) {

                if (byteIndex >= data.length)
                    break;

                int bits = 0;

                for (int bit = 0; bit < LSB; bit++) {

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
                    (p & 0xFF000000)
                            | (rgb[0] << 16)
                            | (rgb[1] << 8)
                            | rgb[2];

            if (byteIndex >= data.length)
                break;
        }

        bmp.setPixels(px, 0, w, 0, 0, w, h);
    }

    /* ------------------------------------------------ */
    /* RANDOM PAYLOAD EMBED                             */
    /* ------------------------------------------------ */

    private static void embedRandom(
            Bitmap bmp,
            byte[] data,
            long seed,
            int headerBytes
    ) {

        int w = bmp.getWidth();
        int h = bmp.getHeight();

        int total = w * h;

        int[] px = new int[total];
        bmp.getPixels(px, 0, w, 0, 0, w, h);

        int headerPixels =
                (headerBytes * 8 + (3 * LSB - 1)) / (3 * LSB);

        int step = stepFromSeed(seed, total);

        int pos = (int) (seed % total);
        if (pos < headerPixels) pos += headerPixels;

        int byteIndex = 0;
        int bitIndex = 0;

        int maskClear = ~((1 << LSB) - 1);

        for (int i = 0; i < total; i++) {

            if (pos < headerPixels) {

                pos += step;
                if (pos >= total) pos -= total;

                continue;
            }

            int p = px[pos];

            int r = (p >> 16) & 255;
            int g = (p >> 8) & 255;
            int b = p & 255;

            int[] rgb = {r, g, b};

            for (int c = 0; c < 3; c++) {

                if (byteIndex >= data.length)
                    break;

                int bits = 0;

                for (int bit = 0; bit < LSB; bit++) {

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

            px[pos] =
                    (p & 0xFF000000)
                            | (rgb[0] << 16)
                            | (rgb[1] << 8)
                            | rgb[2];

            if (byteIndex >= data.length)
                break;

            pos += step;

            if (pos >= total)
                pos -= total;
        }

        bmp.setPixels(px, 0, w, 0, 0, w, h);
    }

    /* ------------------------------------------------ */
    /* REVEAL                                           */
    /* ------------------------------------------------ */

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

            int[] rgb = {r, g, b};

            for (int c : rgb) {

                for (int k = LSB - 1; k >= 0; k--) {

                    cur = (cur << 1) | ((c >> k) & 1);

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

    private static byte[] revealRandom(
            Bitmap bmp,
            long seed,
            int payloadLen,
            int headerBytes
    ) {

        int w = bmp.getWidth();
        int h = bmp.getHeight();

        int total = w * h;

        int[] px = new int[total];
        bmp.getPixels(px, 0, w, 0, 0, w, h);

        int headerPixels =
                (headerBytes * 8 + (3 * LSB - 1)) / (3 * LSB);

        int step = stepFromSeed(seed, total);

        int pos = (int) (seed % total);
        if (pos < headerPixels) pos += headerPixels;

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int cur = 0;
        int bits = 0;

        for (int i = 0; i < total && out.size() < payloadLen; i++) {

            if (pos < headerPixels) {

                pos += step;
                if (pos >= total) pos -= total;

                continue;
            }

            int p = px[pos];

            int r = (p >> 16) & 255;
            int g = (p >> 8) & 255;
            int b = p & 255;

            int[] rgb = {r, g, b};

            for (int c : rgb) {

                for (int k = LSB - 1; k >= 0; k--) {

                    cur = (cur << 1) | ((c >> k) & 1);

                    bits++;

                    if (bits == 8) {

                        out.write(cur);

                        if (out.size() == payloadLen)
                            return out.toByteArray();

                        cur = 0;
                        bits = 0;
                    }
                }
            }

            pos += step;

            if (pos >= total)
                pos -= total;
        }

        return out.toByteArray();
    }

    /* ------------------------------------------------ */
    /* HEADER                                           */
    /* ------------------------------------------------ */

    private static byte[] packHeader(
            String name,
            int payloadLen,
            boolean enc,
            byte[] hash
    ) throws IOException {

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
                .putInt(payloadLen)
                .array());

        out.write(hash);

        return out.toByteArray();
    }

    private static Header unpackHeader(byte[] raw)
            throws IOException {

        ByteArrayInputStream in =
                new ByteArrayInputStream(raw);

        byte[] magic = in.readNBytes(MAGIC.length);

        if (!Arrays.equals(magic, MAGIC))
            throw new SecurityException("Not a Stegora image");

        in.read();

        boolean enc = in.read() == 1;

        int nameLen =
                ByteBuffer.wrap(in.readNBytes(2))
                        .getShort() & 0xFFFF;

        String name =
                new String(
                        in.readNBytes(nameLen),
                        StandardCharsets.UTF_8);

        int payloadLen =
                ByteBuffer.wrap(in.readNBytes(4))
                        .getInt();

        byte[] hash =
                in.readNBytes(HASH_LEN);

        int headerBytes =
                MAGIC.length + 1 + 1 + 2 +
                        nameLen + 4 + HASH_LEN;

        return new Header(name, payloadLen, enc, hash, headerBytes);
    }

    /* ------------------------------------------------ */
    /* CRYPTO                                           */
    /* ------------------------------------------------ */

    private static byte[] encrypt(byte[] data, String password)
            throws Exception {

        byte[] salt = random(SALT_LEN);
        byte[] iv = random(IV_LEN);

        Cipher c =
                Cipher.getInstance("AES/CBC/PKCS5Padding");

        c.init(Cipher.ENCRYPT_MODE,
                key(password, salt),
                new IvParameterSpec(iv));

        return concat(salt, iv, c.doFinal(data));
    }

    private static byte[] decrypt(byte[] data, String password)
            throws Exception {

        byte[] salt =
                Arrays.copyOfRange(data, 0, SALT_LEN);

        byte[] iv =
                Arrays.copyOfRange(data, SALT_LEN, SALT_LEN + IV_LEN);

        byte[] enc =
                Arrays.copyOfRange(data, SALT_LEN + IV_LEN, data.length);

        Cipher c =
                Cipher.getInstance("AES/CBC/PKCS5Padding");

        c.init(Cipher.DECRYPT_MODE,
                key(password, salt),
                new IvParameterSpec(iv));

        return c.doFinal(enc);
    }

    private static SecretKey key(String pw, byte[] salt)
            throws Exception {

        KeySpec spec =
                new PBEKeySpec(pw.toCharArray(), salt, PBKDF2_ITER, 256);

        byte[] k =
                SecretKeyFactory
                        .getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(spec)
                        .getEncoded();

        return new SecretKeySpec(k, "AES");
    }

    private static byte[] sha256(byte[] data)
            throws Exception {

        return MessageDigest
                .getInstance("SHA-256")
                .digest(data);
    }

    private static byte[] random(int n) {

        byte[] b = new byte[n];
        RAND.nextBytes(b);
        return b;
    }

    private static byte[] concat(byte[]... arr)
            throws IOException {

        ByteArrayOutputStream out =
                new ByteArrayOutputStream();

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

    private static int stepFromSeed(long seed, int n) {

        int step = (int) (Math.abs(seed) % n);

        if (step == 0) step = 1;

        while (gcd(step, n) != 1)
            step++;

        return step;
    }

    private static int gcd(int a, int b) {

        while (b != 0) {

            int t = b;
            b = a % b;
            a = t;
        }

        return a;
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

    private static class Header {

        final String fileName;
        final int payloadLen;
        final boolean encrypted;
        final byte[] hash;
        final int headerBytes;

        Header(String n, int len, boolean e, byte[] h, int hb) {

            fileName = n;
            payloadLen = len;
            encrypted = e;
            hash = h;
            headerBytes = hb;
        }
    }
}