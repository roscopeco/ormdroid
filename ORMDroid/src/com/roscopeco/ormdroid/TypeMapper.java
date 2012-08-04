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
 * Top-level class in the <code>types</code> package. Most interaction
 * with the type mapping system will be done through the static methods
 * on this class.
 * 
 * @author rosco
 */
public final class TypeMapper {
  private static final MappingList TYPEMAPS = new MappingList();
  private static TypeMapping mDefaultMapping = new StringTypeMapping(Object.class, "VARCHAR");
  
  public static String sqlType(Class<?> type) {
    return getMapping(type).sqlType(type);
  }
  
  public static TypeMapping getMapping(Class<?> type) {
    TypeMapping r = TYPEMAPS.findMapping(type);
    if (r != null) {
      return r;
    } else {
      TypeMapping def = mDefaultMapping;
      if (def != null) {
        return def;
      } else {
        throw new TypeMappingException("No mapping found for type `" + type + "'");
      }
    }
  }
  
  public static String encodeValue(SQLiteDatabase db, Object value) {
    return getMapping(value.getClass()).encodeValue(db, value);
  }
  
  /**
   * Add the specified mapping to the mapping list.
   */
  public static void mapType(TypeMapping mapping) {
    TYPEMAPS.addMapping(mapping);
  }
  
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
