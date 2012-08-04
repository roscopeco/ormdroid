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

import com.roscopeco.ormdroid.Entity.EntityMapping;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Standard mapping for {@link Entity} subclasses. Automatically saves the
 * model if it's transient during encoding, when called from a 
 * {@link Entity#save()}. When called for query creation, will throw
 * an exception if the object is transient. Encodes value as an 
 * INTEGER with the value being the model's primary key.
 * 
 * @author rosco
 */
public class EntityTypeMapping implements TypeMapping {
  public Class<?> javaType() {
    return Entity.class;
  }

  public String sqlType(Class<?> concreteType) {    
    @SuppressWarnings("unchecked")
    EntityMapping map = Entity.getEntityMapping((Class<? extends Entity>)concreteType);
    
    // TODO ON_UPDATE, ON_DELETE etc.
    
    return "INTEGER REFERENCES " + map.mTableName + "(" + map.mPrimaryKeyColumnName + ")";
  }
  
  public String encodeValue(SQLiteDatabase db, Object value) {
    Entity model = (Entity)value;
    
    if (model.isTransient()) {
      if (db == null) {
        throw new IllegalArgumentException("Transient object doesn't make sense here");
      } else {
        return TypeMapper.encodeValue(db, model.save(db));
      }
    } else {    
      return TypeMapper.encodeValue(db, model.getPrimaryKeyValue());
    }
  }

  // TODO Fix this, it's a mess:
  //
  //            * It's hardcoded for int primary keys
  //            * It's inefficient
  //            * It's generally a mess...
  public Object decodeValue(SQLiteDatabase db, Class<?> expectedType, Cursor c, int columnIndex) {
    if (Entity.class.isAssignableFrom(expectedType)) {
      @SuppressWarnings("unchecked")
      Class<? extends Entity> expEntityType = (Class<? extends Entity>)expectedType;
      
      EntityMapping map = Entity.getEntityMappingEnsureSchema(db, expEntityType);
      Cursor valc = db.rawQuery("SELECT * FROM " + map.mTableName + " WHERE " + map.mPrimaryKeyColumnName + "=?", new String[] { ""+c.getInt(columnIndex) });
      if (valc.moveToFirst()) {
        return map.load(db, valc);
      } else {
        return null;
      }      
    } else {
      throw new IllegalArgumentException("EntityTypeMapping can only be used with Entity subclasses");
    }
  }
}
