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

import java.util.List;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.roscopeco.ormdroid.Entity.EntityMapping;

/**
 * <p>Represents and assists with building a database query that 
 * will load an object (or graph). Mostly this class will be
 * used indirectly via the {@link Entity#query} method.</p>
 * 
 * <p>Example usage:</p>
 * 
 * <ul>
 *  <li>MyModel m = {@link Entity#query Entity.query}(MyModel).{@link #whereId whereId}().{@link #eq eq}(1).{@link #execute execute}()</li>
 *  <li>MyModel m = {@link Entity#query Entity.query}(MyModel).{@link #where where}("name").{@link #eq eq}("Joe").{@link #execute execute}()</li>
 *  <li>List<MyModel> l = {@link Entity#query Entity.query}(MyModel).{@link #where where}("city").{@link #eq eq}("London").{@link #executeMulti() executeMulti}()</li>
 *  <li>List<MyModel> l = {@link Entity#query Entity.query}(MyModel).{@link #executeMulti executeMulti}()</li>
 * </ul>
 */
public class Query<T extends Entity> {
  private static final String TAG = "Query";
  
  
  // TODO maybe some validation, e.g. EQUALS called before WHERE etc...?
  private final StringBuilder mSb = new StringBuilder().append("SELECT * FROM ");
  private final Class<T> mClass;
  private final EntityMapping mEntityMapping; 

  public Query(Class<T> clz) {    
    mEntityMapping = Entity.getEntityMapping(mClass = clz);
    mSb.append(mEntityMapping.mTableName).append(" ");
  }
  
  public Query<T> whereId(Object key) {
    mSb.append("WHERE ").append(mEntityMapping.mPrimaryKeyColumnName);
    return this;
  }

  public Query<T> where(String column) {
    mSb.append("WHERE ").append(column);
    return this;
  }
  
  public Query<T> eq(Object value) {
    mSb.append("=").append(TypeMapper.encodeValue(null, value));
    return this;
  }
  
  public Query<T> ne(Object value) {
    mSb.append("!=").append(TypeMapper.encodeValue(null, value));
    return this;
  }
  
  public Query<T> gt(Object value) {
    mSb.append(">").append(TypeMapper.encodeValue(null, value));
    return this;
  }
  
  public Query<T> lt(Object value) {
    mSb.append("<").append(TypeMapper.encodeValue(null, value));
    return this;
  }
  
  public Query<T> is(String operator, Object value) {
    mSb.append(operator).append(TypeMapper.encodeValue(null, value));
    return this;
  }
  
  public Query<T> and() {
    mSb.append(" AND ");
    return this;
  }
  
  public Query<T> or() {
    mSb.append(" OR ");
    return this;
  }
 
  public String toString() {
    return mSb.toString();
  }
  
  /** 
   * Execute the query on the default database, returning only a single result.
   * If the query would return multiple results, only the first will be returned by this method. 
   */
  public T execute() {
    SQLiteDatabase db = ORMDroidApplication.getDefaultDatabase();
    try {
      return execute(db);
    } finally {
      db.close();
    }
  }
  
  /** 
   * Execute the query on the specified database, returning only a single result.
   * If the query would return multiple results, only the first will be returned by this method. 
   */
  public T execute(SQLiteDatabase db) {
    EntityMapping map = Entity.getEntityMappingEnsureSchema(db, mClass);
    String sql = mSb.toString() + " LIMIT 1";
    Log.v(TAG, sql);
    Cursor c = db.rawQuery(mSb.toString(), null);
    if (c.moveToFirst()) {
      return map.<T>load(db, c);
    } else {
      return null;
    }
  }
  
  /**
   * Execute the query on the default database, returning all results.
   */
  public List<T> executeMulti() {
    SQLiteDatabase db = ORMDroidApplication.getDefaultDatabase();
    try {
      return executeMulti(db);
    } finally {
      db.close();
    }
  }
  
  /**
   * Execute the query on the specified database, returning all results.
   */
  public List<T> executeMulti(SQLiteDatabase db) {
    String sql = mSb.toString();
    Log.v(TAG, sql);
    return Entity.getEntityMappingEnsureSchema(db, mClass).loadAll(db, db.rawQuery(mSb.toString(), null));
  }
}
