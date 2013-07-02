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

import android.database.sqlite.SQLiteDatabase;

/**
 * <p>Supports mappings between Java types and their SQL representations.</p>
 * 
 * <p>By default, ORMDroid provides a mapping for all primitive types
 * and Entity classes. All other types are mapped by the <em>default 
 * mapping</em> (if configured with {@link #setDefaultMapping(TypeMapping)}). 
 * By default, there is no default mapping - attempting to use a model class
 * with a field of an unmapped type will throw an exception.</p>
 * 
 * <p>Custom types may be mapped by registering an instance of 
 * {@link TypeMapping} via the {@link #mapType(TypeMapping) mapType} method.
 * The default mapping can also be overriden using the
 * {@link #setDefaultMapping(TypeMapping) setDefaultMapping} method.</p>
 * 
 * <p>Mappings are made based on assignability. When searching for a
 * mapping for a given type, this class will return the most recently 
 * added mapping with a type that is assignable from the type being
 * searched for.</p>
 * 
 * <p>Any custom mappings should be registered before any database operations
 * are performed. If mappings are changed when schemas already exist in
 * the database, errors are very likely to result.</p>
 */
public final class TypeMapper {
  private static final MappingList TYPEMAPS = new MappingList();
  private static TypeMapping mDefaultMapping = null;
  
  public static String sqlType(Class<?> type) {
    return getMapping(type).sqlType(type);
  }
  
  /**
   * Obtain the configured mapping the the specified Java type.
   * 
   * @param type the Java type. 
   * @return the configured mapping, or <code>null</code> if none.
   */
  public static TypeMapping getMapping(Class<?> type) {
    TypeMapping r = TYPEMAPS.findMapping(type);
    if (r != null) {
      return r;
    } else {
      return mDefaultMapping;
    }
  }
  
  /**
   * Shortcut to the {@link TypeMapping#encodeValue(SQLiteDatabase, Object)}
   * method for the given value.
   * 
   * @param db The {@link SQLiteDatabase} instance to use. 
   * @param value The value to encode.
   * @return The SQL representation of the value.
   */
  public static String encodeValue(SQLiteDatabase db, Object value) {
    return getMapping(value.getClass()).encodeValue(db, value);
  }
  
  /**
   * Add the specified mapping to the mapping list. New items
   * are added at the front of the list, allowing remapping
   * of already-mapped types.
   */
  public static void mapType(TypeMapping mapping) {
    TYPEMAPS.addMapping(mapping);
  }
  
  /**
   * Override the default mapping. This is used when no mapping
   * is configured for a given type.
   * 
   * @param mapping The {@link TypeMapping} to use by default.
   */
  public static void setDefaultMapping(TypeMapping mapping) {
    mDefaultMapping = mapping;
  }
  
  static {    
    // standard types.
    //    Added in reverse order of (perceived) popularity because new items
    //    are added at the front of the list...

    mapType(new NumericTypeMapping(Short.class, "SMALLINT"));
    mapType(new NumericTypeMapping(short.class, "SMALLINT"));
    mapType(new NumericTypeMapping(Byte.class, "TINYINT"));
    mapType(new NumericTypeMapping(byte.class, "TINYINT"));
    mapType(new NumericTypeMapping(Float.class, "FLOAT"));
    mapType(new NumericTypeMapping(float.class, "FLOAT"));
    mapType(new NumericTypeMapping(Double.class, "DOUBLE"));
    mapType(new NumericTypeMapping(double.class, "DOUBLE"));
    mapType(new NumericTypeMapping(Boolean.class, "TINYINT"));
    mapType(new NumericTypeMapping(boolean.class, "TINYINT"));
    mapType(new NumericTypeMapping(Long.class, "BIGINT"));
    mapType(new NumericTypeMapping(long.class, "BIGINT"));
    mapType(new DateTypeMapping());
    mapType(new EntityTypeMapping());
    mapType(new NumericTypeMapping(Integer.class, "INTEGER"));
    mapType(new NumericTypeMapping(int.class, "INTEGER"));

    //    String is mapped, even though it would be handled by the default, 
    //    so we don't have to traverse all the mappings before we decide
    //    on the default handler.
    mapType(new StringTypeMapping(String.class, "VARCHAR"));
    
  }
  
  private TypeMapper() throws InstantiationException { throw new InstantiationException(); }
}
