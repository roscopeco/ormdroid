/*
 * Copyright 2012 Ross Bamford
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package com.roscopeco.ormdroid;

import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

/**
 * <p>Simple {@link TypeMapping} that encodes it's values via the 
 * {@link java.lang.Object#toString()} method.</p>
 * 
 * <p>This is the default mapping, used when no custom mapping
 * is supplied for a given type. As a default, it will map to
 * the <code>VARCHAR</code> data type.</p>
 * 
 * @see TypeMapper#setDefaultMapping(TypeMapping)
 */
public class StringTypeMapping implements TypeMapping {
  private Class<?> mJavaType; 
  private String mSqlType;
  
  public StringTypeMapping(Class<?> type, String sqlType) {
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
    return DatabaseUtils.sqlEscapeString(value.toString());
  }

  public Object decodeValue(SQLiteDatabase db, Class<?> expectedType, Cursor c, int columnIndex) {
    return c.getString(columnIndex);
  }
}