package com.eguzkitza.bingen.lib.crypto;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import android.annotation.SuppressLint;
import android.util.Base64;
import android.util.Log;

/**
 * DiffieHellmanExchangeKey
 * 
 * Class to implement Diffie-Hellman exchange key protocol
 * See: https://en.wikipedia.org/wiki/Diffie%E2%80%93Hellman_key_exchange#Description
 * 
 * We don't use native java helpers, like DHPublicKeySpec, etc
 * because it requires a minimum of 512 bits 
 *
 */
@SuppressLint({ "NewApi", "InlinedApi" })
public class DiffieHellmanExchangeKey {
	//public static final String P = "1429742953378664421791917940395330037446739685059";
	public static final String P = "534503469283761741181754911265573624179983689603";
	public static final String G = "2";
	public static final int BIT_LENGTH = 159; // 160 bits = 20 bytes, maximum allowed in 
												// BLE single transaction. 1 byte for two's compliment 
	public static final String ALGORITHM = "HmacSHA256";
	public static final int KEY_BIT_LENGTH = 128;

	private static final String TAG = "DHExchangeKey";
	
	private BigInteger p; // prime number base of multiplicative group
	private BigInteger g; // primitive root modulo p 
	private int dhBitLength; // size of integers used in DH key exchange
	private String algorithm; // Algorithm name used for the final hash function
	private int keyBitLength; // size of final key
	
	public DiffieHellmanExchangeKey(String p, String g, int dhBitLength,
			String algorithm, int keyBitLength) 
	{
		this.p = new BigInteger(p);
		this.g = new BigInteger(g);
		this.dhBitLength = dhBitLength;
		this.algorithm = algorithm;
		this.keyBitLength = keyBitLength;
	}
	
	/**
	 * generatePrivateKey
	 * 
	 * generates a random exponent (private key) and
	 * calculates g^exp mod p, the value to be sent to the other party
	 * 
	 * @return array containing private key in the first position and 
	 * public value in the second one, both in String format
	 */
	public DhPrivateKey generatePrivateKey() {
		SecureRandom random = new SecureRandom();
		BigInteger exponent = new BigInteger(dhBitLength, random);
		BigInteger sharedPreKey = g.modPow(exponent, p);
				
		DhPrivateKey keyPair = new DhPrivateKey(exponent, sharedPreKey);
		
		return keyPair;
	}
	
	/**
	 * calculateSharedKey
	 * 
	 * Calculates the shared key resulting from the DH key agreement. 
	 * Then applies a key derivation hash function like in here:
	 * https://tools.ietf.org/html/rfc5869
	 * 
	 * @param base String containing public value sent by the other party 
	 * @param exponent String containing own exponent (secret key)
	 * @param salt1 We'll use sharedPreKey from client
	 * @param salt2 We'll use sharedPreKey from server
	 * 
	 * @return String containing final shared key
	 */
	public String calculateSharedKey(String base, String exponent, 
			String salt1, String salt2) 
	{
		BigInteger b = new BigInteger(base);
		BigInteger e = new BigInteger(exponent);
		//Log.i(TAG, "Base: " + base);
		//Log.i(TAG, "Exponent: " + exponent);
		// key
		BigInteger commonKey = b.modPow(e, p);
		byte[] secret = commonKey.toByteArray();
		//Log.e(TAG, "Key: " + commonKey.toString()); // Remember to comment it out!!
		// salt
		byte[] salt1Byte = Base64.decode(salt1, Base64.DEFAULT);
		byte[] salt2Byte = Base64.decode(salt2, Base64.DEFAULT);
		byte[] salt = new byte[salt1Byte.length + salt2Byte.length];
		System.arraycopy(salt1Byte, 0, salt, 0, salt1Byte.length);
		System.arraycopy(salt2Byte, 0, salt, salt1Byte.length, salt2Byte.length);
		//Log.i(TAG, "Salt: " + new String(salt));
		
		String sharedKey = keyDerivation(secret, salt);
		
		return sharedKey;
	}
	
    /**
     * getSharedKey
     * 
     * Method to derive a key suitable for encryption with AES from DH key agreement result.
     * 
     * It uses algorithm (e.g. "HMAC_SHA256") 
     * and truncates it to outputLength (e.g. 128) bits for later use in AES 128 encryption
     * 
     * @param key Initial key from DH
     * @param data Salt
     * @return String final key to be used
     */
    private String keyDerivation(byte[] key, byte[] data) 
    {
    	String output = null;
		try {
			Mac hmac = Mac.getInstance(algorithm);
	        final SecretKeySpec secretKey = new SecretKeySpec(key, algorithm);
	        hmac.init(secretKey);
	        final byte[] macData = hmac.doFinal(data);
			Log.i(TAG, "macData size: " + macData.length);
	        byte[] truncatedResult = Arrays.copyOf(macData, keyBitLength / 8);
			Log.i(TAG, "truncatedResult size: " + truncatedResult.length);
	        output = Base64.encodeToString(truncatedResult, Base64.DEFAULT);
			Log.i(TAG, "output size: " + output.length());
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			e.printStackTrace();
		}
        
        return output;
    }
    
}
