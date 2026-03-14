package neunix.stego;

import android.graphics.Bitmap;
import android.util.Log;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/*
=========================================================
 STEG ENGINE CORE
---------------------------------------------------------
 Features
 - LSB steganography
 - Random pixel distribution
 - AES-256 encryption (GCM)
 - PBKDF2 key derivation
 - Compression
 - SHA-256 integrity
 - Filename metadata
=========================================================
*/

public class StegEngineCore {

    private static final String TAG = "StegEngine";

    /* =========================================================
       FILE IDENTIFICATION
       ========================================================= */

    private static final byte[] MAGIC = new byte[]{'S','T','G','O'};
    private static final byte VERSION = 1;

    /* =========================================================
       HEADER CONSTANTS
       ========================================================= */

    private static final int HASH_SIZE = 32;
    private static final int SALT_SIZE = 16;
    private static final int IV_SIZE = 12;

    private static final int HEADER_PREFIX =
            4 + 1 + 1 + 2 + 4 + HASH_SIZE + SALT_SIZE + IV_SIZE;

    /* =========================================================
       ENCRYPTION SETTINGS
       ========================================================= */

    private static final int PBKDF2_ITER = 100000;
    private static final int AES_KEY_BITS = 256;

    /* =========================================================
       PUBLIC EMBED
       ========================================================= */

    public static Bitmap embed(Bitmap bmp,
                               byte[] payload,
                               String filename,
                               String password) throws Exception {

        log("Embed started");

        int width = bmp.getWidth();
        int height = bmp.getHeight();

        int[] pixels = getPixels(bmp);

        /* compress payload */

        byte[] compressed = compress(payload);

        /* generate crypto material */

        byte[] salt = randomBytes(SALT_SIZE);
        byte[] iv = randomBytes(IV_SIZE);

        byte[] encrypted = encrypt(compressed,password,salt,iv);

        byte[] hash = sha256(encrypted);

        byte[] header = buildHeader(filename,
                encrypted.length,
                hash,
                salt,
                iv);

        int capacity = capacityBytes(width,height);

        if(header.length + encrypted.length > capacity)
            throw new RuntimeException("Carrier too small");

        int headerPixels = pixelsForBytes(header.length);

        embedSequential(pixels,header);

        embedRandom(pixels,encrypted,headerPixels,password);

        Bitmap out = Bitmap.createBitmap(width,height, Bitmap.Config.ARGB_8888);
        setPixels(out,pixels);

        log("Embed finished");

        return out;
    }

    /* wrapper used by Activities */

    public static void embed(Bitmap bmp,
                             byte[] payload,
                             String filename,
                             String password,
                             FileOutputStream out) throws Exception {

        Bitmap encoded = embed(bmp,payload,filename,password);

        encoded.compress(Bitmap.CompressFormat.PNG,100,out);

        out.flush();
        out.close();
    }

    /* =========================================================
       EXTRACT
       ========================================================= */

    public static ExtractedData extract(Bitmap bmp,String password) throws Exception {

        log("Extract started");

        int[] pixels = getPixels(bmp);

        byte[] prefix = revealSequential(pixels,HEADER_PREFIX);

        if(!checkMagic(prefix))
            throw new RuntimeException("Not a steg image");

        Header header = parseHeader(prefix,pixels);

        int headerPixels = pixelsForBytes(header.totalHeaderSize);

        byte[] encrypted = extractRandom(
                pixels,
                header.payloadSize,
                headerPixels,
                password
        );

        if(!Arrays.equals(header.hash,sha256(encrypted)))
            throw new RuntimeException("Integrity failure");

        byte[] decrypted = decrypt(encrypted,password,header.salt,header.iv);

        byte[] payload = decompress(decrypted);

        log("Extract finished");

        return new ExtractedData(header.filename,payload);
    }

    /* =========================================================
       CAPACITY FOR UI
       ========================================================= */

    public static int getMaxPayloadSize(Bitmap bmp){

        int w=bmp.getWidth();
        int h=bmp.getHeight();

        return capacityBytes(w,h) - HEADER_PREFIX;
    }

    /* =========================================================
       HEADER BUILD
       ========================================================= */

    private static byte[] buildHeader(String filename,
                                      int payloadLen,
                                      byte[] hash,
                                      byte[] salt,
                                      byte[] iv){

        byte[] nameBytes = filename.getBytes(StandardCharsets.UTF_8);

        ByteBuffer buf = ByteBuffer.allocate(
                HEADER_PREFIX + nameBytes.length);

        buf.put(MAGIC);
        buf.put(VERSION);
        buf.put((byte)0);

        buf.putShort((short)nameBytes.length);
        buf.putInt(payloadLen);

        buf.put(hash);
        buf.put(salt);
        buf.put(iv);

        buf.put(nameBytes);

        return buf.array();
    }

    /* =========================================================
       HEADER PARSE
       ========================================================= */

    private static Header parseHeader(byte[] prefix,int[] pixels) throws Exception{

        ByteBuffer pb = ByteBuffer.wrap(prefix);

        pb.get(new byte[4]);
        pb.get();
        pb.get();

        int nameLen = pb.getShort();
        int payloadSize = pb.getInt();

        byte[] hash = new byte[HASH_SIZE];
        pb.get(hash);

        byte[] salt = new byte[SALT_SIZE];
        pb.get(salt);

        byte[] iv = new byte[IV_SIZE];
        pb.get(iv);

        int fullHeaderSize = HEADER_PREFIX + nameLen;

        byte[] header = revealSequential(pixels,fullHeaderSize);

        ByteBuffer hb = ByteBuffer.wrap(header);

        hb.position(HEADER_PREFIX);

        byte[] nameBytes = new byte[nameLen];
        hb.get(nameBytes);

        String filename = new String(nameBytes,StandardCharsets.UTF_8);

        return new Header(filename,payloadSize,hash,fullHeaderSize,salt,iv);
    }

    /* =========================================================
       EMBED SEQUENTIAL
       ========================================================= */

    private static void embedSequential(int[] pixels,byte[] data){

        int bitIndex=0;

        for(int i=0;i<pixels.length;i++){

            int r=(pixels[i]>>16)&0xff;
            int g=(pixels[i]>>8)&0xff;
            int b=pixels[i]&0xff;

            if(bitIndex<data.length*8) r=setLSB(r,getBit(data,bitIndex++));
            if(bitIndex<data.length*8) g=setLSB(g,getBit(data,bitIndex++));
            if(bitIndex<data.length*8) b=setLSB(b,getBit(data,bitIndex++));

            pixels[i]=(0xff<<24)|(r<<16)|(g<<8)|b;

            if(bitIndex>=data.length*8) break;
        }
    }

    /* =========================================================
       RANDOM EMBED
       ========================================================= */

    private static void embedRandom(int[] pixels,byte[] data,int skip,String password) throws Exception{

        int[] idx = buildIndex(pixels.length,skip,password);

        int bitIndex=0;

        for(int p:idx){

            int r=(pixels[p]>>16)&0xff;
            int g=(pixels[p]>>8)&0xff;
            int b=pixels[p]&0xff;

            if(bitIndex<data.length*8) r=setLSB(r,getBit(data,bitIndex++));
            if(bitIndex<data.length*8) g=setLSB(g,getBit(data,bitIndex++));
            if(bitIndex<data.length*8) b=setLSB(b,getBit(data,bitIndex++));

            pixels[p]=(0xff<<24)|(r<<16)|(g<<8)|b;

            if(bitIndex>=data.length*8) break;
        }
    }

    /* =========================================================
       REVEAL
       ========================================================= */

    private static byte[] revealSequential(int[] pixels,int bytes){

        byte[] out=new byte[bytes];

        int bitIndex=0;

        for(int p:pixels){

            setBit(out,bitIndex++,(p>>16)&1);
            if(bitIndex>=bytes*8) break;

            setBit(out,bitIndex++,(p>>8)&1);
            if(bitIndex>=bytes*8) break;

            setBit(out,bitIndex++,(p)&1);
            if(bitIndex>=bytes*8) break;
        }

        return out;
    }

    private static byte[] extractRandom(int[] pixels,int bytes,int skip,String password) throws Exception{

        byte[] out=new byte[bytes];

        int[] idx = buildIndex(pixels.length,skip,password);

        int bitIndex=0;

        for(int p:idx){

            setBit(out,bitIndex++,(pixels[p]>>16)&1);
            if(bitIndex>=bytes*8) break;

            setBit(out,bitIndex++,(pixels[p]>>8)&1);
            if(bitIndex>=bytes*8) break;

            setBit(out,bitIndex++,(pixels[p])&1);
            if(bitIndex>=bytes*8) break;
        }

        return out;
    }

    /* =========================================================
       PIXELS
       ========================================================= */

    private static int[] getPixels(Bitmap bmp){

        int w=bmp.getWidth();
        int h=bmp.getHeight();

        int[] pixels=new int[w*h];

        bmp.getPixels(pixels,0,w,0,0,w,h);

        return pixels;
    }

    private static void setPixels(Bitmap bmp,int[] pixels){

        int w=bmp.getWidth();
        int h=bmp.getHeight();

        bmp.setPixels(pixels,0,w,0,0,w,h);
    }

    /* =========================================================
       INDEX
       ========================================================= */

    private static int[] buildIndex(int total,int skip,String password) throws Exception{

        int[] arr=new int[total-skip];

        for(int i=0;i<arr.length;i++)
            arr[i]=skip+i;

        shuffle(arr,password);

        return arr;
    }

    private static void shuffle(int[] arr,String password) throws Exception{

        long seed = ByteBuffer.wrap(sha256(password.getBytes())).getLong();

        Random r=new Random(seed);

        for(int i=arr.length-1;i>0;i--){

            int j=r.nextInt(i+1);

            int tmp=arr[i];
            arr[i]=arr[j];
            arr[j]=tmp;
        }
    }

    /* =========================================================
       BIT OPS
       ========================================================= */

    private static int setLSB(int v,int bit){ return (v&0xfe)|bit; }

    private static int getBit(byte[] d,int i){

        int bi=i/8;
        int off=7-(i%8);

        return (d[bi]>>off)&1;
    }

    private static void setBit(byte[] d,int i,int bit){

        int bi=i/8;
        int off=7-(i%8);

        if(bit==1) d[bi]|=(1<<off);
    }

    private static int pixelsForBytes(int bytes){

        int bits=bytes*8;

        return (bits+2)/3;
    }

    /* =========================================================
       HASH
       ========================================================= */

    private static byte[] sha256(byte[] d) throws Exception{

        MessageDigest md = MessageDigest.getInstance("SHA-256");

        return md.digest(d);
    }

    /* =========================================================
       COMPRESSION
       ========================================================= */

    private static byte[] compress(byte[] data) throws Exception{

        Deflater def=new Deflater();

        def.setInput(data);
        def.finish();

        byte[] buf=new byte[data.length+200];

        int len=def.deflate(buf);

        return Arrays.copyOf(buf,len);
    }

    private static byte[] decompress(byte[] data) throws Exception{

        Inflater inf=new Inflater();

        inf.setInput(data);

        byte[] buf=new byte[data.length*4+200];

        int len=inf.inflate(buf);

        return Arrays.copyOf(buf,len);
    }

    /* =========================================================
       ENCRYPTION AES-256
       ========================================================= */

    private static byte[] encrypt(byte[] data,String password,byte[] salt,byte[] iv) throws Exception{

        SecretKey key = deriveKey(password,salt);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        GCMParameterSpec spec = new GCMParameterSpec(128,iv);

        cipher.init(Cipher.ENCRYPT_MODE,key,spec);

        return cipher.doFinal(data);
    }

    private static byte[] decrypt(byte[] data,String password,byte[] salt,byte[] iv) throws Exception{

        SecretKey key = deriveKey(password,salt);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        GCMParameterSpec spec = new GCMParameterSpec(128,iv);

        cipher.init(Cipher.DECRYPT_MODE,key,spec);

        return cipher.doFinal(data);
    }

    private static SecretKey deriveKey(String password,byte[] salt) throws Exception{

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

        KeySpec spec = new PBEKeySpec(password.toCharArray(),salt,PBKDF2_ITER,AES_KEY_BITS);

        SecretKey tmp = factory.generateSecret(spec);

        return new SecretKeySpec(tmp.getEncoded(),"AES");
    }

    /* =========================================================
       CAPACITY
       ========================================================= */

    private static int capacityBytes(int w,int h){
        return (w*h*3)/8;
    }

    /* =========================================================
       UTIL
       ========================================================= */

    private static boolean checkMagic(byte[] p){

        for(int i=0;i<4;i++)
            if(p[i]!=MAGIC[i])
                return false;

        return true;
    }

    private static byte[] randomBytes(int n){

        byte[] b=new byte[n];

        new SecureRandom().nextBytes(b);

        return b;
    }

    /* =========================================================
       HEADER CLASS
       ========================================================= */

    private static class Header{

        String filename;
        int payloadSize;
        byte[] hash;
        int totalHeaderSize;
        byte[] salt;
        byte[] iv;

        Header(String f,int p,byte[] h,int s,byte[] salt,byte[] iv){

            filename=f;
            payloadSize=p;
            hash=h;
            totalHeaderSize=s;
            this.salt=salt;
            this.iv=iv;
        }
    }

    /* =========================================================
       RESULT CLASS
       ========================================================= */

    public static class ExtractedData{

        public final String filename;
        public final byte[] data;

        public ExtractedData(String f,byte[] d){

            filename=f;
            data=d;
        }
    }

    private static void log(String m){
        Log.d(TAG,m);
    }
}