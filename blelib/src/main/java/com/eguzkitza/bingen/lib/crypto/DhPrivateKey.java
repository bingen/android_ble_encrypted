package com.eguzkitza.bingen.lib.crypto;

import java.math.BigInteger;

public class DhPrivateKey {
	private DhBigInteger exponent;
	private DhBigInteger sharedPreKey;
	
	public DhPrivateKey(BigInteger exponent, BigInteger sharedPreKey) {
		this.setExponent(new DhBigInteger(exponent));
		this.setSharedPreKey(new DhBigInteger(sharedPreKey));
	}

	public DhBigInteger getExponent() {
		return exponent;
	}

	public void setExponent(DhBigInteger exponent) {
		this.exponent = exponent;
	}

	public DhBigInteger getSharedPreKey() {
		return sharedPreKey;
	}

	public void setSharedPreKey(DhBigInteger sharedPreKey) {
		this.sharedPreKey = sharedPreKey;
	}

}
