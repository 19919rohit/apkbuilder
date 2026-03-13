package neunix.stego;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Random;

public class StegEngineCore {

    private static final String TAG = "StegoraEngine";

    private static final byte[] MAGIC =
            "NXSTEG".getBytes(StandardCharsets.UTF_8);

    private static final int LSB = 2;

    private static final int HEADER_SIZE = 6 + 4 + 2 + 4;
    // magic + payload length + filename length + seed

    // =============================
    // DATA CLASS
    // =============================

    public static class ExtractedData {

        public byte[] data;
        public String fileName;

        public ExtractedData(byte[] data, String fileName) {

            this.data = data;
            this.fileName = fileName;
        }
    }

    // =============================
    // EMBED
    // =============================

    public static Bitmap embed(
            Bitmap carrier,
            byte[] payload,
            String fileName,
            String password
    ) throws Exception {

        try {

            if (carrier == null)
                throw new Exception("Carrier null");

            if (payload == null)
                throw new Exception("Payload null");

            if (fileName == null)
                fileName = "file.bin";

            int width = carrier.getWidth();
            int height = carrier.getHeight();

            int totalPixels = width * height;

            Bitmap out =
                    carrier.copy(Bitmap.Config.ARGB_8888, true);

            int seed = deriveSeed(password);

            byte[] fileNameBytes =
                    fileName.getBytes(StandardCharsets.UTF_8);

            ByteBuffer header =
                    ByteBuffer.allocate(
                            HEADER_SIZE + fileNameBytes.length
                    );

            header.put(MAGIC);
            header.putInt(payload.length);
            header.putShort((short) fileNameBytes.length);
            header.putInt(seed);
            header.put(fileNameBytes);

            byte[] headerBytes = header.array();

            int capacityBits =
                    totalPixels * 3 * LSB;

            int requiredBits =
                    (headerBytes.length + payload.length) * 8;

            if (requiredBits > capacityBits)
                throw new Exception("Payload too large");

            embedSequential(out, headerBytes);

            embedRandom(out, payload, seed, headerBytes.length);

            return out;

        } catch (Exception e) {

            Log.e(TAG, "Embed failed", e);
            throw e;
        }
    }

    // =============================
    // EXTRACT
    // =============================

    public static ExtractedData extract(
            Bitmap bmp,
            String password
    ) throws Exception {

        try {

            if (bmp == null)
                throw new Exception("Bitmap null");

            byte[] header =
                    revealSequential(bmp, HEADER_SIZE);

            if (!Arrays.equals(
                    Arrays.copyOfRange(header, 0, 6),
                    MAGIC
            )) {

                throw new Exception("Not a Stegora image");
            }

            ByteBuffer meta =
                    ByteBuffer.wrap(header);

            byte[] magic = new byte[6];
            meta.get(magic);

            int payloadLen = meta.getInt();

            int nameLen = meta.getShort();

            int seed = meta.getInt();

            byte[] fullHeader =
                    revealSequential(
                            bmp,
                            HEADER_SIZE + nameLen
                    );

            ByteBuffer meta2 =
                    ByteBuffer.wrap(fullHeader);

            meta2.position(HEADER_SIZE);

            byte[] nameBytes =
                    new byte[nameLen];

            meta2.get(nameBytes);

            String fileName =
                    new String(nameBytes,
                            StandardCharsets.UTF_8);

            byte[] payload =
                    revealRandom(
                            bmp,
                            payloadLen,
                            seed,
                            HEADER_SIZE + nameLen
                    );

            return new ExtractedData(payload, fileName);

        } catch (Exception e) {

            Log.e(TAG, "Extract failed", e);
            throw e;
        }
    }

    // =============================
    // SEQUENTIAL EMBED
    // =============================

    private static void embedSequential(
            Bitmap bmp,
            byte[] data
    ) {

        try {

            int width = bmp.getWidth();

            int bitIndex = 0;

            for (int i = 0; i < data.length * 8; i++) {

                int bit =
                        (data[i / 8] >> (7 - (i % 8))) & 1;

                int pixel =
                        i / (3 * LSB);

                int x = pixel % width;
                int y = pixel / width;

                int color =
                        bmp.getPixel(x, y);

                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);

                int channel =
                        (i / LSB) % 3;

                if (channel == 0)
                    r = writeLSB(r, bit);

                else if (channel == 1)
                    g = writeLSB(g, bit);

                else
                    b = writeLSB(b, bit);

                bmp.setPixel(
                        x,
                        y,
                        Color.rgb(r, g, b)
                );
            }

        } catch (Exception e) {

            Log.e(TAG, "Sequential embed error", e);
        }
    }

    // =============================
    // RANDOM EMBED
    // =============================

    private static void embedRandom(
            Bitmap bmp,
            byte[] data,
            int seed,
            int headerBytes
    ) {

        try {

            int width = bmp.getWidth();
            int height = bmp.getHeight();

            int total = width * height;

            int headerPixels =
                    (headerBytes * 8 + (3 * LSB - 1))
                            / (3 * LSB);

            Random rand =
                    new Random(seed);

            for (int i = 0; i < data.length * 8; i++) {

                int bit =
                        (data[i / 8] >> (7 - (i % 8))) & 1;

                int pixel =
                        headerPixels +
                                rand.nextInt(
                                        total - headerPixels
                                );

                int x = pixel % width;
                int y = pixel / width;

                int color =
                        bmp.getPixel(x, y);

                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);

                int channel = rand.nextInt(3);

                if (channel == 0)
                    r = writeLSB(r, bit);

                else if (channel == 1)
                    g = writeLSB(g, bit);

                else
                    b = writeLSB(b, bit);

                bmp.setPixel(
                        x,
                        y,
                        Color.rgb(r, g, b)
                );
            }

        } catch (Exception e) {

            Log.e(TAG, "Random embed error", e);
        }
    }

    // =============================
    // SEQUENTIAL REVEAL
    // =============================

    private static byte[] revealSequential(
            Bitmap bmp,
            int length
    ) {

        try {

            int width = bmp.getWidth();

            byte[] out =
                    new byte[length];

            for (int i = 0; i < length * 8; i++) {

                int pixel =
                        i / (3 * LSB);

                int x = pixel % width;
                int y = pixel / width;

                int color =
                        bmp.getPixel(x, y);

                int channel =
                        (i / LSB) % 3;

                int val;

                if (channel == 0)
                    val = Color.red(color);

                else if (channel == 1)
                    val = Color.green(color);

                else
                    val = Color.blue(color);

                int bit =
                        val & 1;

                out[i / 8] |=
                        bit << (7 - (i % 8));
            }

            return out;

        } catch (Exception e) {

            Log.e(TAG, "Sequential reveal error", e);

            return new byte[0];
        }
    }

    // =============================
    // RANDOM REVEAL
    // =============================

    private static byte[] revealRandom(
            Bitmap bmp,
            int length,
            int seed,
            int headerBytes
    ) {

        try {

            int width = bmp.getWidth();
            int height = bmp.getHeight();

            int total = width * height;

            int headerPixels =
                    (headerBytes * 8 + (3 * LSB - 1))
                            / (3 * LSB);

            Random rand =
                    new Random(seed);

            byte[] out =
                    new byte[length];

            for (int i = 0; i < length * 8; i++) {

                int pixel =
                        headerPixels +
                                rand.nextInt(
                                        total - headerPixels
                                );

                int x = pixel % width;
                int y = pixel / width;

                int color =
                        bmp.getPixel(x, y);

                int channel =
                        rand.nextInt(3);

                int val;

                if (channel == 0)
                    val = Color.red(color);

                else if (channel == 1)
                    val = Color.green(color);

                else
                    val = Color.blue(color);

                int bit =
                        val & 1;

                out[i / 8] |=
                        bit << (7 - (i % 8));
            }

            return out;

        } catch (Exception e) {

            Log.e(TAG, "Random reveal error", e);

            return new byte[0];
        }
    }

    // =============================
    // LSB WRITE
    // =============================

    private static int writeLSB(
            int value,
            int bit
    ) {

        try {

            value &= ~1;
            value |= bit;

            return value;

        } catch (Exception e) {

            return value;
        }
    }

    // =============================
    // PASSWORD SEED
    // =============================

    private static int deriveSeed(
            String password
    ) {

        try {

            if (password == null ||
                    password.isEmpty())
                return 1337;

            MessageDigest md =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash =
                    md.digest(
                            password.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            ByteBuffer bb =
                    ByteBuffer.wrap(hash);

            return bb.getInt();

        } catch (Exception e) {

            return password.hashCode();
        }
    }
}