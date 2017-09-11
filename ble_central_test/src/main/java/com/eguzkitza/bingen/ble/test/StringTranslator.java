package com.eguzkitza.bingen.ble.test;

import android.content.Context;

import com.eguzkitza.bingen.ble.test.BLuEHelper.StringInterface;

/**
 * StringTranslator
 * 
 * Class used to wrap StringUtils.getString and make BLuEHelper independent of it.
 * Implements BLuEHelper.StringInterface
 *
 */
public class StringTranslator implements StringInterface {

	Context mContext;
	
	public StringTranslator(Context context) {
		this.mContext = context;
	}
	@Override
	public String getString(int id) {
		return mContext.getString(id);
	}

}
