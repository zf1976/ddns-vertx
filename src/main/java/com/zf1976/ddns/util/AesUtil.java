package com.zf1976.ddns.util;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

/**
 * @author ant
 * Create by Ant on 2021/7/31 3:12 PM
 */
@SuppressWarnings("all")
public class AesUtil {
    private static final String KEY_ALGORITHM = "AES";

    public AesUtil() {
    }

    public static byte[] decryptByCBC(byte[] content, byte[] key, byte[] initVector) {
        return decryptByCBC(content, key, initVector, "AES/CBC/PKCS5Padding");
    }

    public static byte[] decryptByCBC(byte[] content, byte[] key, byte[] initVector, String padding) {
        try {
            return doFinal(content, key, initVector, 2, padding);
        } catch (GeneralSecurityException var5) {
            var5.printStackTrace();
            throw new RuntimeException("AES CBC decrypt error");
        }
    }

    public static byte[] encryptByCBC(byte[] content, byte[] key, byte[] initVector) {
        return encryptByCBC(content, key, initVector, "AES/CBC/PKCS5Padding");
    }

    public static byte[] encryptByCBC(byte[] content, byte[] key, byte[] initVector, String padding) {
        try {
            return doFinal(content, key, initVector, 1, padding);
        } catch (GeneralSecurityException var5) {
            var5.printStackTrace();
            throw new RuntimeException("AES CBC encrypt error");
        }
    }

    public static byte[] decryptByECB(byte[] content, byte[] key) {
        return decryptByECB(content, key, "AES/ECB/PKCS5Padding");
    }

    public static byte[] decryptByECB(byte[] content, byte[] key, String padding) {
        try {
            return doFinalECB(content, key, 2, padding);
        } catch (GeneralSecurityException var4) {
            var4.printStackTrace();
            throw new RuntimeException("Aes decrypt error");
        }
    }

    public static byte[] encryptByECB(byte[] content, byte[] key) {
        return encryptByECB(content, key, "AES/ECB/PKCS5Padding");
    }

    public static byte[] encryptByECB(byte[] content, byte[] key, String padding) {
        try {
            return doFinalECB(content, key, 1, padding);
        } catch (GeneralSecurityException var4) {
            var4.printStackTrace();
            throw new RuntimeException("Aes encrypt error");
        }
    }

    public static String decodeByCBC(String content, String key, String initVector) {
        return decodeByCBC(content, key, initVector, "AES/CBC/PKCS5Padding");
    }

    public static String decodeByCBC(String content, String key, String initVector, String padding) {
        checkParamsOfCBC(content, key, initVector);
        byte[] decryptFrom = HexUtil.hexStr2ByteArr(content);
        byte[] decryptResult = decryptByCBC(decryptFrom, key.getBytes(), initVector.getBytes(), padding);
        return new String(decryptResult);
    }

    public static String decodeByCBCBase64(String content, String key, String initVector, String padding) {
        checkParamsOfCBC(content, key, initVector);
        byte[] decryptFrom = Base64Util.decryptBASE64(content);
        byte[] decryptResult = decryptByCBC(decryptFrom, key.getBytes(), initVector.getBytes(), padding);
        return new String(decryptResult);
    }

    public static String encodeByCBC(String content, String key, String initVector) {
        return encodeByCBC(content, key, initVector, "AES/CBC/PKCS5Padding");
    }

    public static String encodeByCBC(String content, String key, String initVector, String padding) {
        return encodeByCBC(content, key, initVector, padding, false);
    }

    public static String encodeByCBCBase64(String content, String key, String initVector, String padding) {
        return encodeByCBC(content, key, initVector, padding, true);
    }

    public static String encodeByECB(String content, String key) {
        return encodeByECB(content, key, "AES/ECB/PKCS5Padding");
    }

    public static String encodeByECB(String content, String key, String padding) {
        checkContentAndKey(content, key);

        try {
            byte[] encrypted = encryptByECB(content.getBytes("utf-8"), key.getBytes("utf-8"), padding);
            return Base64Util.encryptToString(encrypted);
        } catch (UnsupportedEncodingException var4) {
            var4.printStackTrace();
            return null;
        }
    }

    public static String decodeByECB(String content, String key) {
        checkContentAndKey(content, key);

        try {
            byte[] result = decryptByECB(Base64Util.decryptBASE64(content), key.getBytes("utf-8"));
            return new String(result, "utf-8");
        } catch (UnsupportedEncodingException var3) {
            var3.printStackTrace();
            return null;
        }
    }

    private static byte[] doFinalECB(byte[] content,
                                     byte[] key,
                                     int mode,
                                     String padding) throws GeneralSecurityException {
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance(padding);
        cipher.init(mode, keySpec);
        return cipher.doFinal(content);
    }

    private static byte[] doFinal(byte[] content,
                                  byte[] key,
                                  byte[] initVector,
                                  int mode,
                                  String padding) throws GeneralSecurityException {
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec iv = new IvParameterSpec(initVector);
        Cipher cipher = Cipher.getInstance(padding);
        cipher.init(mode, keySpec, iv);
        return cipher.doFinal(content);
    }

    private static void checkParamsOfCBC(String content, String key, String initVector) {
        checkContentAndKey(content, key);
        if (StringUtil.isEmpty(initVector)) {
            throw new NullPointerException("The init Vector can't be null or empty.");
        }
    }

    private static String encodeByCBC(String content, String key, String initVector, String padding, boolean base64) {
        checkParamsOfCBC(content, key, initVector);
        byte[] encryptResult = null;

        try {
            encryptResult = encryptByCBC(content.getBytes("utf-8"), key.getBytes(), initVector.getBytes(), padding);
        } catch (UnsupportedEncodingException var7) {
            var7.printStackTrace();
        }

        return base64 ? new String(Base64Util.encryptBASE64(encryptResult)) : HexUtil.byteArr2HexStr(encryptResult);
    }

    private static void checkContentAndKey(String content, String key) {
        if (StringUtil.isEmpty(content)) {
            throw new NullPointerException("The string to be encrypted cannot be null.");
        } else if (StringUtil.isEmpty(key)) {
            throw new NullPointerException("The key can't be null or empty.");
        } else if (key.length() != 16) {
            throw new RuntimeException("The length of key must be 16 while use AES CBC mode.");
        }
    }

    private static SecretKeySpec getSecretKey(String key) {
        return new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
    }

    public static AesUtil.AesKey generateKey() throws NoSuchAlgorithmException {
        String str = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        final var key = new StringBuffer();
        for (int i = 0; i < 16; i++) {
            int number = random.nextInt(62);
            key.append(str.charAt(number));
        }

        final var iv = new StringBuffer();
        for (int i = 0; i < 16; i++) {
            int number = random.nextInt(62);
            iv.append(str.charAt(number));
        }
        return new AesKey(key.toString(), iv.toString());
    }

    public static class AesKey {

        private String key;

        private String iv;

        public AesKey() {

        }

        public AesKey(String key, String iv) {
            this.key = key;
            this.iv = iv;
        }

        public String getKey() {
            return key;
        }

        public String getIv() {
            return iv;
        }

        @Override
        public String toString() {
            return "AesKey{" +
                    "key='" + key + '\'' +
                    ", iv='" + iv + '\'' +
                    '}';
        }
    }

}