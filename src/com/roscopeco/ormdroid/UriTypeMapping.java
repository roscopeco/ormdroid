package com.roscopeco.ormdroid;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

/**
 * User: wweems
 * Date: 12/18/12
 */
/*
	Converts Uri to sqlite nvarchar type, and vice versa
 */
public class UriTypeMapping implements TypeMapping {
	private Class<?> mJavaType;
	private String mSqlType;

	public UriTypeMapping(Class<?> type, String sqlType) {
		mJavaType = type;
		mSqlType = sqlType;
	}

	public Class<?> javaType() {
		return mJavaType;
	}

	public String sqlType(Class<?> concreteType) {
		return mSqlType;
	}

	public String encodeValue(SQLiteDatabase db, Object value) {
		return  value.toString();
	}

	public Object decodeValue(SQLiteDatabase db, Class<?> expectedType, Cursor c, int columnIndex) {
		String urlValue = c.getString(columnIndex);
		return (urlValue != null && !urlValue.equals("")) ? Uri.parse(c.getString(columnIndex)): null;
	}
}
