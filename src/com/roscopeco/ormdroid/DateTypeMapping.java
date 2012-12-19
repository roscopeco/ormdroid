package com.roscopeco.ormdroid;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.Date;

/**
 * User: Weston Weems
 * Date: 12/18/12
 */

/*
	Handles mapping from java.util.Date to sql and vice versa
 */
public class DateTypeMapping implements TypeMapping{
	private Class<?> mJavaType;
	private String mSqlType;

	public DateTypeMapping(Class<?> type, String sqlType) {
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
		return Long.toString(((Date) value).getTime());
	}

	public Object decodeValue(SQLiteDatabase db, Class<?> expectedType, Cursor c, int columnIndex) {
		return new Date(c.getLong(columnIndex));
	}
}
