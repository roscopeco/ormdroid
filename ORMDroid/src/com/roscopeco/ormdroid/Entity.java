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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public abstract class Entity {
  static final class EntityMapping {
    private static final String TAG = "INTERNAL<EntityMapping>";
    private static final Pattern MATCH_DOTDOLLAR = Pattern.compile("[\\.\\$]");
    
    private Class<? extends Entity> mMappedClass;
    String mTableName;
    private Field mPrimaryKey;
    String mPrimaryKeyColumnName;
    private ArrayList<String> mColumnNames = new ArrayList<String>();
    private ArrayList<Field> mFields = new ArrayList<Field>();
    boolean mSchemaCreated = false;

    // Not concerned too much about reflective annotation access in this
    // method, since this only runs once per model class...
    static EntityMapping build(Class<? extends Entity> clz) {
      EntityMapping mapping = new EntityMapping();
      mapping.mMappedClass = clz;

      Table table = clz.getAnnotation(Table.class);
      if (table != null) {
        mapping.mTableName = table.name();
      } else {
        mapping.mTableName = MATCH_DOTDOLLAR.matcher(clz.getName()).replaceAll("");
      }

      ArrayList<String> seenFields = new ArrayList<String>();
      for (Field f : clz.getFields()) {
        // Blithely ignore this field if we've already seen one with same name -
        // Java field hiding allows this to happen and if it does, without this
        // we'd be adding the same column name twice.
        //
        // We might as well also ignore it here if it's inverse, since we'll
        // never want to access it via the mapping.
        Column colAnn = f.getAnnotation(Column.class);
        boolean inverse = colAnn != null && colAnn.inverse();

        if (!seenFields.contains(f.getName()) && !inverse) {
          Column col = f.getAnnotation(Column.class);
          String name;

          if (col != null) {
            // empty is default, means we should use field name...
            if ("".equals(name = col.name())) {
              name = f.getName();
            }

            if (col.primaryKey()) {
              mapping.mPrimaryKey = f;
              mapping.mPrimaryKeyColumnName = name;
            }
          } else {
            name = f.getName();
          }

          // Try to default primary key if we don't have one yet...
          if (mapping.mPrimaryKey == null) {
            if ("_id".equals(name) || "id".equals(name)) {
              mapping.mPrimaryKey = f;
              mapping.mPrimaryKeyColumnName = name;
            }
          }

          mapping.mFields.add(f);
          mapping.mColumnNames.add(name);
          seenFields.add(f.getName());
        }
      }

      if (mapping.mPrimaryKey == null) {
        // Error at this point - we must have a primary key!
        Log.e(TAG,
            "No primary key specified or determined for " + clz);
        throw new ORMDroidException(
            "No primary key was specified, and a default could not be determined for " + clz);
      }

      return mapping;
    }

    void createSchema(SQLiteDatabase db) {
      StringBuilder b = new StringBuilder();
      b.append("CREATE TABLE IF NOT EXISTS " + mTableName + " (");

      int len = mFields.size();
      for (int i = 0; i < len; i++) {
        String colName = mColumnNames.get(i);

        // Without this we'll add overriden fields twice...
        b.append(colName);
        b.append(" ");
        b.append(TypeMapper.sqlType(mFields.get(i).getType()));
        if (colName.equals(mPrimaryKeyColumnName)) {
          b.append(" PRIMARY KEY AUTOINCREMENT");
        }

        if (i < len - 1) {
          b.append(",");
        }
      }

      b.append(");");

      String sql = b.toString();
      Log.v(TAG, sql);
      db.execSQL(sql);
      mSchemaCreated = true;
    }

    private boolean isPrimaryKey(Field f) {
      return mPrimaryKey.equals(f);
    }

    Object getPrimaryKeyValue(Entity o) {
      try {
        return mPrimaryKey.get(o);
      } catch (IllegalAccessException e) {
        Log.e(TAG,
            "IllegalAccessException accessing primary key " + mPrimaryKey
                + "; Update failed");
        throw new ORMDroidException(
            "IllegalAccessException accessing primary key " + mPrimaryKey
                + "; Update failed");
      }
    }

    private void setPrimaryKeyValue(Entity o, Object value) {
      try {
        mPrimaryKey.set(o, value);
      } catch (IllegalAccessException e) {
        Log.e(TAG,
            "IllegalAccessException accessing primary key " + mPrimaryKey
                + "; Update failed");
        throw new ORMDroidException(
            "IllegalAccessException accessing primary key " + mPrimaryKey
                + "; Update failed");
      }
    }

    private String processValue(SQLiteDatabase db, Object value) {
      return TypeMapper.encodeValue(db, value);
    }

    private String getColNames() {
      StringBuilder b = new StringBuilder();
      ArrayList<String> names = mColumnNames;
      ArrayList<Field> fields = mFields;
      int len = names.size();

      for (int i = 0; i < len; i++) {
        Field f = fields.get(i);
        if (!isPrimaryKey(f)) {
          if (i > 0) {
            b.append(",");
          }
          b.append(names.get(i));
        }
      }

      return b.toString();
    }

    private String getFieldValues(SQLiteDatabase db, Entity receiver) {
      StringBuilder b = new StringBuilder();
      ArrayList<Field> fields = mFields;
      int len = fields.size();

      for (int i = 0; i < len; i++) {
        Field f = fields.get(i);
        if (!isPrimaryKey(f)) {
          Object val;
          try {
            val = f.get(receiver);
          } catch (IllegalAccessException e) {
            // Should never happen...
            Log.e(TAG,
                "IllegalAccessException accessing field "
                    + fields.get(i).getName() + "; Inserting NULL");
            val = null;
          }
          if (i > 0) {
            b.append(",");
          }

          b.append(val == null ? "null" : processValue(db, val));

        }
      }

      return b.toString();
    }

    private String getSetFields(SQLiteDatabase db, Object receiver) {
      StringBuilder b = new StringBuilder();
      ArrayList<String> names = mColumnNames;
      ArrayList<Field> fields = mFields;
      int len = names.size();

      for (int i = 0; i < len; i++) {
        Field f = fields.get(i);
        String name = names.get(i);

        // We don't want to set the primary key...
        if (name != mPrimaryKeyColumnName) {
          b.append(name);
          b.append("=");
          Object val;
          try {
            val = f.get(receiver);
          } catch (IllegalAccessException e) {
            Log.w(TAG,
                "IllegalAccessException accessing field "
                    + fields.get(i).getName() + "; Inserting NULL");
            val = null;
          }
          b.append(val == null ? "null" : processValue(db, val));
          if (i < (len - 1)) {
            b.append(",");
          }
        }
      }

      return b.toString();
    }

    int insert(SQLiteDatabase db, Entity o) {
      String sql = "INSERT INTO " + mTableName + " (" + getColNames()
          + ") VALUES (" + getFieldValues(db, o) + ")";

      Log.v(getClass().getSimpleName(), sql);

      db.execSQL(sql);

      Cursor c = db.rawQuery("select last_insert_rowid();", null);
      if (c.moveToFirst()) {
        Integer i = c.getInt(0);
        setPrimaryKeyValue(o, i);
        return i;
      } else {
        throw new ORMDroidException(
            "Failed to get last inserted id after INSERT");
      }
    }

    void update(SQLiteDatabase db, Entity o) {
      String sql = "UPDATE " + mTableName + " SET " + getSetFields(db, o)
          + " WHERE " + mPrimaryKeyColumnName + "=" + getPrimaryKeyValue(o);

      Log.v(getClass().getSimpleName(), sql);

      db.execSQL(sql);
    }

    /*
     * Doesn't move the cursor - expects it to be positioned appropriately.
     */
    <T extends Entity> T load(SQLiteDatabase db, Cursor c) {
      try {
        // TODO we should be checking here that we've got data before
        // instantiating...
        @SuppressWarnings("unchecked")
        T model = (T) mMappedClass.newInstance();
        model.mTransient = false;

        ArrayList<String> colNames = mColumnNames;
        ArrayList<Field> fields = mFields;
        int len = colNames.size();

        for (int i = 0; i < len; i++) {
          Field f = fields.get(i);
          Class<?> ftype = f.getType();
          int colIndex = c.getColumnIndex(colNames.get(i));
          
          if (colIndex == -1) {
            Log.e("Internal<ModelMapping>", "Got -1 column index for `"+colNames.get(i)+"' - Database schema may not match entity");
            throw new ORMDroidException("Got -1 column index for `"+colNames.get(i)+"' - Database schema may not match entity");
          } else {
            Object o = TypeMapper.getMapping(f.getType()).decodeValue(db, ftype,
                c, colIndex);
            f.set(model, o);
          }
        }

        return model;
      } catch (Exception e) {
        throw new ORMDroidException(
            "Failed to instantiate model class - does it have a public null constructor?",
            e);
      }

    }

    /*
     * Moves cursor to start, and runs through all records.
     */
    <T extends Entity> List<T> loadAll(SQLiteDatabase db, Cursor c) {
      ArrayList<T> list = new ArrayList<T>();

      if (c.moveToFirst()) {
        do {
          list.add(this.<T> load(db, c));
        } while (c.moveToNext());
      }

      return list;
    }

  } // end of EntityMapping

  private static final HashMap<Class<? extends Entity>, EntityMapping> entityMappings = new HashMap<Class<? extends Entity>, EntityMapping>();

  /*
   * Package private - used by Query as well as locally...
   */
  static EntityMapping getEntityMapping(Class<? extends Entity> clz) {
    EntityMapping mapping = entityMappings.get(clz);

    if (mapping == null) {
      // build map
      entityMappings.put(clz, mapping = EntityMapping.build(clz));
    }

    return mapping;
  }

  static EntityMapping getEntityMappingEnsureSchema(SQLiteDatabase db,
      Class<? extends Entity> clz) {
    EntityMapping map = getEntityMapping(clz);
    if (!map.mSchemaCreated) {
      map.createSchema(db);
    }
    return map;
  }

  public static <T extends Entity> Query<T> query(Class<T> clz) {
    return new Query<T>(clz);
  }

  private boolean mTransient;
  private EntityMapping mMappingCache;

  protected Entity() {
    mTransient = true;
  }

  public boolean isTransient() {
    return mTransient;
  }

  private EntityMapping getEntityMapping() {
    // This may be called multiple times on a single instance,
    // (e.g. during a save, looking for primary keys and whatnot)
    // so we cache it per instance, to save the hash cache lookup...
    if (mMappingCache != null) {
      return mMappingCache;
    } else {
      return mMappingCache = getEntityMapping(getClass());
    }
  }

  private EntityMapping getEntityMappingEnsureSchema(SQLiteDatabase db) {
    EntityMapping map = getEntityMapping();
    if (!map.mSchemaCreated) {
      map.createSchema(db);
    }
    return map;
  }

  public Object getPrimaryKeyValue() {
    return getEntityMapping().getPrimaryKeyValue(this);
  }

  public int save(SQLiteDatabase db) {
    EntityMapping mapping = getEntityMappingEnsureSchema(db);

    int result = -1;

    if (mTransient) {
      result = mapping.insert(db, this);
      mTransient = false;
    } else {
      mapping.update(db, this);
    }

    return result;
  }

  public int save() {
    SQLiteDatabase db = ORMDroidApplication.getDefaultDatabase();
    db.beginTransaction();

    int result = -1;

    try {
      result = save(db);
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    db.close();
    return result;
  }
}
