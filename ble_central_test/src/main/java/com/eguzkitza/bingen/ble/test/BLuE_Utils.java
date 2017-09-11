package com.eguzkitza.bingen.ble.test;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Random;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import android.annotation.SuppressLint;
import android.util.Base64;
import android.util.Log;

@SuppressLint("NewApi")
public class BLuE_Utils {

	private static final String TAG = "BLuE_Utils";
	
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
	
	public static String stringFromByteArray(byte[] data){
		if (data != null && data.length > 0) {
            StringBuilder stringBuilder = new StringBuilder(data.length);
            for(byte byteChar : data){
                stringBuilder.append((char) byteChar);
            }
            return stringBuilder.toString();
		}
		return "";
	}

	public static String randomString(int len) {
		//private static final String AB = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
		final String AB = "0123456789";
		Random rnd = new Random();
		
		StringBuilder sb = new StringBuilder(len);
		for (int i = 0; i < len; i++)
			sb.append(AB.charAt(rnd.nextInt(AB.length())));
		return sb.toString();
	}
	
	public static int getRandomInt(int min, int max){
		Random random = new Random();
		return random.nextInt(max - min) + min;
	}

	public static String encryptToString(String clear, String encryptionType, String stringKey, String stringIv) {
		byte[] crypt = encrypt(clear, encryptionType, stringKey, stringIv);
		if (crypt == null) {
			return null;
		}
		return Base64.encodeToString(crypt, Base64.DEFAULT);
	}
	public static byte[] encrypt(String clear, String encryptionType, String stringKey, String stringIv) {
		//Log.i(TAG, "encrypt clear: " + new String(clear));
		//Log.i(TAG, "encrypt clear: " + bytesToHex(clear));
		//Log.i(TAG, "encrypt clear: " + clear);
		//Log.i(TAG, "encrypt clear decoded: " + new String(Base64.decode(clear, Base64.DEFAULT)));
		Log.i(TAG, "encrypt encryptionType: " + encryptionType);
		//Log.i(TAG, "encrypt IV: " + stringIv);
		byte[] crypt = null;
		
		Cipher cipher;
		try {
			cipher = Cipher.getInstance(encryptionType, "BC");
			SecretKeySpec keySpec = new SecretKeySpec(Base64.decode(stringKey, Base64.DEFAULT), "AES");
            if (stringIv == null || "".equals(stringIv)) {
                cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            } else {
                IvParameterSpec ivParameterSpec = new IvParameterSpec(Base64.decode(stringIv, Base64.DEFAULT));
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivParameterSpec);
            }
			//crypt = cipher.doFinal(Base64.decode(clear, Base64.DEFAULT));
			crypt = cipher.doFinal(clear.getBytes());
			
			return crypt;
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			e.printStackTrace();
		} catch (IllegalBlockSizeException e) {
			e.printStackTrace();
		} catch (BadPaddingException e) {
			e.printStackTrace();
		} catch (InvalidAlgorithmParameterException e) {
			e.printStackTrace();
		} catch (NoSuchProviderException e) {
			e.printStackTrace();
		}
		
		return crypt;
	}
}
