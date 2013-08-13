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

import java.util.Date;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/*
 * Map java.util.Date to the database.
 * 
 * This implementation just stashes the number of seconds
 * since the epoch in a BIGINT.
 */
public class DateTypeMapping implements TypeMapping {
  private Class<?> mJavaType; 
  private String mSqlType;
  
  public DateTypeMapping() {
    mJavaType = Date.class;
    mSqlType = "BIGINT";      
  }

  public Class<?> javaType() {
    return mJavaType;
  }

  public String sqlType(Class<?> concreteType) {
    return mSqlType;
  }

  public String encodeValue(SQLiteDatabase db, Object value) {
    return "\"" + ((Date)value).getTime() + "\"";
  }

  public Object decodeValue(SQLiteDatabase db, Class<?> expectedType, Cursor c, int columnIndex) {
    return new Date(c.getLong(columnIndex));
  }
}