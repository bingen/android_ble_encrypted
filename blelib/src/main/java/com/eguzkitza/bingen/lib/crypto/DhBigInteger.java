package com.eguzkitza.bingen.lib.crypto;

import java.math.BigInteger;

import android.annotation.SuppressLint;
import android.util.Base64;

@SuppressLint("NewApi")
public class DhBigInteger {
	private String value;
	private byte[] valueBytes;
	private String valueBase64;
	
	public DhBigInteger(BigInteger value) {
		this.setValue(value.toString());
		this.setValueBytes(value.toByteArray());
		this.setValueBase64(Base64.encodeToString(value.toByteArray(), Base64.DEFAULT));
	}

	public DhBigInteger(byte[] value) {
		this.setValue(new BigInteger(value).toString());
		this.setValueBytes(value);
		this.setValueBase64(Base64.encodeToString(value, Base64.DEFAULT));
	}
	
	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public byte[] getValueBytes() {
		return valueBytes;
	}

	public void setValueBytes(byte[] valueBytes) {
		this.valueBytes = valueBytes;
	}

	public String getValueBase64() {
		return valueBase64;
	}

	public void setValueBase64(String valueBase64) {
		this.valueBase64 = valueBase64;
	}
	
}
