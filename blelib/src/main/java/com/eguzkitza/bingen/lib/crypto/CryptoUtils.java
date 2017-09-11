package com.eguzkitza.bingen.lib.crypto;

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import android.annotation.SuppressLint;
import android.util.Base64;
import android.util.Log;


@SuppressLint("NewApi")
public class CryptoUtils {

	private static final String ENCRYPTION_TYPE_RSA = "RSA";
	private static final String ENCRYPTION_TYPE_AES = "AES";

	private final static String TAG = "CryptoUtils";

    /**
     * decrypt
     * 
     * Method that chooses proper algorithm and tries to decrypt ciphered text locally
     *   
     * @param encryptionType EncryptionType
     * @param cipherBytes byte[]
     * @param privateKey String
     * @param publicKey String
     * @return String Unencrypted text
     */
    public static String decrypt(EncryptionType encryptionType, byte[] cipherBytes, 
    		String privateKey, String publicKey) 
    {
		//Log.i(TAG, "decrypt cipherText: " + new String(cipherBytes));
		Log.i(TAG, "decrypt cipherText: " + bytesToHex(cipherBytes));
		Log.i(TAG, "decrypt encryptionType: " + encryptionType.toString());
		//Log.i(TAG, "decrypt publicKey: " + publicKey);
		
		byte[] clear = null;
    	if (ENCRYPTION_TYPE_RSA.equals(encryptionType.getType())) {
    		clear = decryptRSA(encryptionType, cipherBytes, privateKey);
    	} else if (ENCRYPTION_TYPE_AES.equals(encryptionType.getType())) {
			clear = decryptAES(encryptionType, cipherBytes, privateKey, publicKey);
    	}
    	if (clear == null) {
    		return "";
    	}

    	return new String(clear);
		//return Base64.encodeToString(clear, Base64.DEFAULT);
    }
    public static String decrypt(EncryptionType encryptionType, String cipherText, 
    		String privateKey, String publicKey) 
    {
		byte[] cipherBytes = Base64.decode(cipherText, Base64.DEFAULT);
    	return decrypt(encryptionType, cipherBytes, privateKey, publicKey);
    }
    
    /**
     * decryptRSA
     * 
     * @param encryptionType EncryptionType
     * @param cipherBytes byte[]
     * @param stringKey String
     * @return String Unencrypted text
     */
    private static byte[] decryptRSA(EncryptionType encryptionType, byte[] cipherBytes, String stringKey) {
    	// https://rtyley.github.io/spongycastle/
    	//Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
    	
    	byte[] plainText = null;
        try {
        	
        	// http://stackoverflow.com/questions/7216969/getting-rsa-private-key-from-pem-base64-encoded-private-key-file#7221381
    	    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    	    PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(Base64.decode(stringKey, Base64.DEFAULT));
    	    //X509EncodedKeySpec keySpec = new X509EncodedKeySpec(Base64.decode(stringKey, Base64.DEFAULT));
    	    PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
    	    
            //Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding", "BC");
            Cipher cipher = Cipher.getInstance("RSA/" + encryptionType.getMode() + "/" + encryptionType.getPadding(), "BC");

            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            //cipher.update(cipherBytes);
            
            int cipherBytesLength = cipherBytes.length;
            Log.i(TAG, "cipherBytesLength: " + cipherBytesLength);
            
            plainText = cipher.doFinal(cipherBytes);

        } catch (GeneralSecurityException e) {
        	Log.e(TAG, "GeneralSecurityException: " + e.getMessage());
            throw new RuntimeException(e);
        }
        
        return plainText;
    }
    /**
     * decryptAES128
     * 
     * @param encryptionType EncryptionType
     * @param cipherBytes byte[]
     * @param stringKey String
     * @param stringIv String
     * @return String Unencrypted text
     */
    private static byte[] decryptAES(EncryptionType encryptionType, byte[] cipherBytes,
    		String stringKey, String stringIv) 
    {
    	
    	// https://rtyley.github.io/spongycastle/
    	//Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
    	
    	byte[] plainText = null;
        try {
    	    SecretKeySpec keySpec = new SecretKeySpec(Base64.decode(stringKey, Base64.DEFAULT), "AES");
            IvParameterSpec ivParameterSpec = new IvParameterSpec(Base64.decode(stringIv, Base64.DEFAULT));

            Cipher cipher = Cipher.getInstance("AES/" + encryptionType.getMode() + "/" +
            		encryptionType.getPadding(), "BC");
            if (encryptionType.getMode().equals("ECB") || stringIv == null || "".equals(stringIv)) {
                cipher.init(Cipher.DECRYPT_MODE, keySpec);
            } else {
                cipher.init(Cipher.DECRYPT_MODE, keySpec, ivParameterSpec);
            }
            
            plainText = cipher.doFinal(cipherBytes);

        } catch (GeneralSecurityException e) {
        	Log.e(TAG, "GeneralSecurityException: " + e.getMessage());
        	e.printStackTrace();
		}
        
        return plainText;
    }
    
    // we are going to use Android 5 for the reader (as it needs BLE Peripheral), 
    // so it shouldn't be a problem
    @SuppressLint("TrulyRandom")
	public static String getRandomKey(String algorithm, int keySize) {
    	KeyGenerator keyGenerator;
		try {
			keyGenerator = KeyGenerator.getInstance(algorithm);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return null;
		}
    	keyGenerator.init(keySize);
    	SecretKey secretKey = keyGenerator.generateKey();
    	
    	return secretKey.toString();
    }

    /**
     * getSharedKey
     * 
     * Method to get a shared secret key between user device and reader
     * from initial nonces.
     * It uses algorithm(e.g. "HMAC_SHA256") 
     * and truncates it to outputLength (e.g. 128) bits for later use in AES 128 encryption
     * 
     * @param key
     * @param data
     * @param algorithm
     * @param outputLength length in bits to truncate output to
     * @return String shared key
     */
    public static String getSharedKey(String key, String data, 
    		String algorithm, int outputLength) 
    {
    	if (outputLength > 256) {
    		outputLength = 256;
    	}
    	String output = null;
		try {
			Mac hmac = Mac.getInstance(algorithm);
	        final SecretKeySpec secretKey = new SecretKeySpec(Base64.decode(key, Base64.DEFAULT), algorithm);
	        hmac.init(secretKey);
	        final byte[] macData = hmac.doFinal(Base64.decode(data, Base64.DEFAULT));
	        byte[] truncatedResult = Arrays.copyOf(macData, outputLength);
	        output = Base64.encodeToString(truncatedResult, Base64.DEFAULT);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			e.printStackTrace();
		}
        
        return output;
    }

	public static String bytesToHex(byte[] bytes) {
		final char[] hexArray = "0123456789ABCDEF".toCharArray();
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

    /**
     * verifyHMAC
     *
     * @param encryptionType EncryptionType
     * @param data String Data to be verified
     * @param stringKey String
     * @param hmac String Original HMAC digest
     * @return boolean If verification succeeded
     */
	public static boolean verifyHMAC(EncryptionType encryptionType, String data,
									 String stringKey, String hmac)
	{
        boolean result = false;
        try {
            result = verifyHMAC(
                    encryptionType,
					Base64.decode(data, Base64.DEFAULT), //data.getBytes("UTF-8"),
                    stringKey,
                    Base64.decode(hmac, Base64.DEFAULT));
//		} catch (UnsupportedEncodingException e) {
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }
    /**
     * verifyHMAC
     * 
     * @param encryptionType EncryptionType
     * @param data byte[] Data to be verified
     * @param stringKey String
     * @param hmac byte[] Original HMAC digest
     * @return boolean If verification succeeded
     */
    public static boolean verifyHMAC(EncryptionType encryptionType, byte[] data, 
    		String stringKey, byte[] hmac) 
    {
    	boolean result = false;
    	byte[] digest = null;
        try {
    	    SecretKeySpec keySpec = new SecretKeySpec(Base64.decode(stringKey, Base64.DEFAULT), 
    	    		encryptionType.getType());
    	    //SecretKeySpec keySpec = new SecretKeySpec(stringKey.getBytes(), "AES");
    	    
            Mac mac = Mac.getInstance(encryptionType.getType() + encryptionType.getAlgorithm());
            mac.init(keySpec);
            
            digest = mac.doFinal(data);
            if (Arrays.equals(hmac, digest)) {
            	result = true;
				Log.d(TAG, "VerifyHMAC success!");
            } else {
				Log.e(TAG, "VerifyHMAC failure!");
            }
            
        } catch (GeneralSecurityException e) {
        	Log.e(TAG, "GeneralSecurityException: " + e.getMessage());
        	e.printStackTrace();
		}
        
        return result;
    }
    
    
    public static class EncryptionType {
        // RSA default params
        private static final int RSA_BITS = 2048;
        private static final String RSA_MODE = "ECB";
        private static final String RSA_PADDING = "PKCS1Padding";
        // AES params
        private static final int AES_BITS = 128;
        private static final String AES_MODE = "CBC";
        private static final String AES_PADDING = "ZeroBytePadding";
        
    	private String type;
		private String mode = "";
    	private String padding = "";
    	private int bits = -1;
    	private String algorithm = "";
    	
    	public EncryptionType (String encryptionType) {
    		String[] encyptionTypeArray = encryptionType.split("\\/", -1);
    		this.type = encyptionTypeArray[0];
    		this.setMode(encyptionTypeArray);
    		this.setPadding(encyptionTypeArray);
    		this.setBits(encyptionTypeArray);
    		this.setAlgorithm(encyptionTypeArray);
    	}
    	
    	public String toString() {
    		return this.type + "/" + this.mode + "/" + this.padding + 
    				"/" + this.bits + "/" + this.algorithm;
    	}
    	public String toStringShort() {
    		return this.type + "/" + this.mode + "/" + this.padding;
    	}
    	
    	public String getType() {
    		return this.type;
    	}
    	// mode
    	public String getMode() {
    		return this.mode;
    	}
    	private void setMode(String[] encyptionTypeArray) {
    		if (encyptionTypeArray.length > 1 && encyptionTypeArray[1] != null) {
        		this.mode = encyptionTypeArray[1];
    		} else {
    			if (ENCRYPTION_TYPE_RSA.equals(this.type)) {
    				this.mode = RSA_MODE;
    			} else if (ENCRYPTION_TYPE_AES.equals(this.type)) {
    				this.mode = AES_MODE;
    			}
    		}
    		
    	}
    	// padding
		public String getPadding() {
			return padding;
		}
    	private void setPadding(String[] encyptionTypeArray) {
    		if (encyptionTypeArray.length > 2 && encyptionTypeArray[2] != null) {
        		this.padding = encyptionTypeArray[2];
    		} else {
    			if (ENCRYPTION_TYPE_RSA.equals(this.type)) {
    				this.padding = RSA_PADDING;
    			} else if (ENCRYPTION_TYPE_AES.equals(this.type)) {
    				this.padding = AES_PADDING;
    			}
    		}
    	}
    	// bits
    	public int getBits() {
			return bits;
		}
    	private void setBits(String[] encyptionTypeArray) {
    		if (encyptionTypeArray.length > 3 && encyptionTypeArray[3] != null) {
        		this.bits = Integer.parseInt(encyptionTypeArray[3]);
    		} else {
    			if (ENCRYPTION_TYPE_RSA.equals(this.type)) {
    				this.bits = RSA_BITS;
    			} else if (ENCRYPTION_TYPE_AES.equals(this.type)) {
    				this.bits = AES_BITS;
    			}
    		}
    	}
    	// algorithm
		public String getAlgorithm() {
			return algorithm;
		}
    	private void setAlgorithm(String[] encyptionTypeArray) {
    		if (encyptionTypeArray.length > 4 && encyptionTypeArray[4] != null) {
        		this.algorithm = encyptionTypeArray[4];
    		}
    	}
    }
}
