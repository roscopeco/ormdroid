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
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

/**
 * <p>Base class for persistent entities. The only hard requirements
 * for model classes are that they subclass this class, and that
 * they provide a (currently integral) primary key (see below).</p>
 * 
 * <p><code>Entity</code> is the primary class in ORMDroid, and is
 * where most interaction with the API will take place. A model class
 * will subclass this class, and will inherit its save() and delete()
 * methods from it. The simplest possible model class would be:</p>
 * 
 * <p><pre><code>
 * public class Person extends Entity {
 *   public int id;
 * }
 * </pre></code></p>
 * 
 * <p>This is obviously useless, as it holds no data other than the
 * (required) primary key. In order to actually store other data,
 * you would add further public fields. Entities may also define 
 * any methods you wish - these are never called by the framework</p>
 * 
 * <h3>Table & column names</h3>
 * 
 * <p>By default, the framework creates table names based on the 
 * fully-qualified name of the entity class. You can change this
 * behaviour by applying the {@link Table} annotation to the class,
 * e.g:</p>
 * 
 * <p><pre><code>
 * {@literal @}Table(name = "people")
 * public class Person extends Entity {
 *   // ...
 * }
 * </pre></code></p>
 * 
 * <p>Similarly, any column can be explicitly named using the
 * {@link Column} annotation:</p>
 * 
 * <p><pre><code>
 *   {@literal @}Column(name = "person_name")
 *   public String name;
 * </pre></code></p>
 *  
 * <h3>Primary key field</h3>
 * 
 * <p>In the example above, the framework automatically selects the 
 * primary key field based on it's name. Currently, the framework
 * will use the first field it finds named 'id' or '_id', and will
 * map these fields to database column '_id' in any case, unless
 * the field is explicitly named (see above).</p>
 * 
 * <p>It is possible to explicitly select the primary key column
 * using the {@link Column} annotation:
 * 
 * <p><pre><code>
 *   {@literal @}Column(primaryKey = true)
 *   public int myPrimaryKey;
 * </pre></code></p>
 * 
 * <p>It should be noted that, although you can use any data type
 * for primary key fields, parts of the framework currently expect
 * primary keys to be integers, and will behave in an indeterminite
 * manner if other types are used. This limitation primarily affects
 * the {@link Query} class, and the {@link #equals(Object)} and 
 * {@link #hashCode()} implementations defined in this class, and
 * will be removed in a future version.</p>
 * 
 * <h3>Private fields</h3>
 * 
 * By default, private fields are ignored when mapping model classes.
 * It is possible to force them to be mapped, however, using the
 * {@link Column} annotation
 * 
 * <p><pre><code>
 *   {@literal @}Column(forceMap = true)
 *   private String myPrivateField;
 * </pre></code></p>
 * 
 * <h3>Relationships</h3>
 * 
 * <p>The framework currently provides built-in support for 
 * one-to-one relationships - simply adding a field of an 
 * <code>Entity</code>-subclass type will cause that field to
 * be persisted when the containing object is persisted.</p>
 * 
 * <p>Many relationships are not currently natively supported,
 * but can easily be implemented using helper methods on your
 * model class. For example (taken from the ORMSample app):</p>
 * 
 * <p><pre><code>
 * public List<Person> people() {
 *   return query(Person.class).where("department").eq(id).executeMulti();
 * }
 * </pre></code></p>
 * 
 * <p>More support for such relationships (including entity type mappings
 * for {@link java.util.List} and {@link java.util.Map}) will be added in a 
 * future version.</p>
 * 
 * <p>Using the
 * <code>{@literal @}{@link Column}(inverse = "otherFieldName")</code>
 * annotation will prevent the field from being persisted when its
 * model is stored.</p>
 * 
 * <h3>Model lifecycle</h3>
 * 
 * <p>The typical lifecycle of a model is shown below:</p>
 * 
 * <p><pre><code>
 * MyModel m = new MyModel();
 *   // ... or ... 
 * MyModel m = Entity.query(MyModel).whereId().eq(1).execute();
 * 
 *   // ... do some work ...
 *   
 * MyModel.save();
 *   // ... or ...
 * MyModel.delete();
 * </pre></code></p>
 * 
 * <h3>{@link #equals} and {@link #hashCode}</h3>
 * 
 * <p>The default implementation of equals and hashCode provided
 * by this class define equality in terms of primary key, and 
 * utilise reflective field access. You may of course override 
 * these if you wish to change their behaviour, or for performance
 * reasons (e.g. to directly compare primary key fields rather than
 * using reflection).</p>
 */
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
      private ArrayList<Field> mInverseFields = new ArrayList<Field>();
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

      // Use a raw Class var to spin through the hierarchy, to avoid
      // type safety warnings which we know are not an issue.
      @SuppressWarnings("rawtypes")
      Class cClz = clz; 
      
      ArrayList<String> seenFields = new ArrayList<String>();
      
      // Loop through the class hierarchy, picking up the fields we'll be 
      // mapping as we go. 
      while (!"com.roscopeco.ormdroid.Entity".equals(cClz.getName())) {
        for (Field f : cClz.getDeclaredFields()) {
          f.setAccessible(true);
        
          // Blithely ignore this field if we've already seen one with same name -
          // Java field hiding allows this to happen and if it does, without this
          // we'd be adding the same column name twice.
          //
          // We might as well also ignore it here if it's inverse, since we'll
          // never want to access it via the mapping.
          //
          // Also, ignore statics/finals (bug #4)
          //
          // We ignore private fields, *unless* they're annotated with
          // the force attribute.
          Column colAnn = f.getAnnotation(Column.class);
          boolean inverse = colAnn != null && !colAnn.inverse().equals("");
          boolean force = colAnn != null && colAnn.forceMap();
  
          int modifiers = f.getModifiers();
          if (!Modifier.isStatic(modifiers) &&
              !Modifier.isFinal(modifiers) &&
              (!Modifier.isPrivate(modifiers) || force) &&
              !seenFields.contains(f.getName())) {

            seenFields.add(f.getName());

            if(!inverse) {

              // Check we can map this type - if not, let's fail fast.
              // This will save us weird exceptions somewhere down the line...
              if (TypeMapper.getMapping(f.getType()) == null) {
                throw new TypeMappingException("Model " +
                                               cClz.getName() +
                                               " has unmappable field: " + f);
              }

              // TODO basic type Lists ought to be okay here, but they are not implemented yet
              if(List.class.isAssignableFrom(f.getType())) {
                  throw new TypeMappingException("Model " +
                          cClz.getName() +
                          " field must be declared inverse: " + f);
              }

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

              mapping.mColumnNames.add(name);
              mapping.mFields.add(f);
            }
            else {
              mapping.mInverseFields.add(f);
            }
          }
        }
        
        cClz = cClz.getSuperclass();
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
      b.append("CREATE TABLE IF NOT EXISTS ").append(mTableName).append(" (");

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
          b.append(names.get(i));
          
          if (i < len-1) {
            b.append(",");
          }

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
          
          b.append(val == null ? "null" : processValue(db, val));

          if (i < len-1) {
            b.append(",");
          }
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
    
    /* issue #6 */
    private String stripTrailingComma(String string) {
      // check for last comma
      if (string.endsWith(",")) {
        return string.substring(0, string.length() - 1);
      }
      return string;
    }

    int insert(SQLiteDatabase db, Entity o) {
      String sql = "INSERT INTO " + mTableName + " ("
          + stripTrailingComma(getColNames()) + ") VALUES ("
          + stripTrailingComma(getFieldValues(db, o)) + ")";

      Log.v(getClass().getSimpleName(), sql);

      db.execSQL(sql);

      Cursor c = db.rawQuery("select last_insert_rowid();", null);
      try {
	    if (c.moveToFirst()) {
	      Integer i = c.getInt(0);
          setPrimaryKeyValue(o, i);
	      return i;
	    } else {
	      throw new ORMDroidException("Failed to get last inserted id after INSERT");
	    }
      } finally {
      	c.close();
      }
    }

    void update(SQLiteDatabase db, Entity o) {
      // stripTrailingComma: issue #9
      String sql = "UPDATE " + mTableName + " SET " + stripTrailingComma(getSetFields(db, o))
          + " WHERE " + mPrimaryKeyColumnName + "=" + getPrimaryKeyValue(o);

      Log.v(getClass().getSimpleName(), sql);

      db.execSQL(sql);
    }

    /*
     * Doesn't move the cursor - expects it to be positioned appropriately.
     */
    <T extends Entity> T load(SQLiteDatabase db, Cursor c) {
        return load(db, c, null);
    }

    <T extends Entity> T load(SQLiteDatabase db, Cursor c, ArrayList<Entity> precursors) {
      try {
        // TODO we should be checking here that we've got data before
        // instantiating...
        @SuppressWarnings("unchecked")
        T model = (T) mMappedClass.newInstance();
        model.mTransient = false;

        ArrayList<String> colNames = mColumnNames;
        ArrayList<Field> fields = mFields;
        int len = colNames.size();

        int pkColIndex = c.getColumnIndex(mPrimaryKeyColumnName);

        {
          Field f = mPrimaryKey;
          Class<?> ftype = f.getType();

          if (pkColIndex == -1) {
            Log.e("Internal<ModelMapping>", "Got -1 column index for `"+mPrimaryKeyColumnName+"' - Database schema may not match entity");
            throw new ORMDroidException("Got -1 column index for `"+mPrimaryKeyColumnName+"' - Database schema may not match entity");
          } else {
            Object o = TypeMapper.getMapping(ftype).decodeValue(db, f, c, pkColIndex, precursors);
            f.set(model, o);
          }
        }

        if(precursors != null) {
          int index = precursors.indexOf(model);

          Log.d("ListTypeMapping", "found = " + index + " for " + precursors.size() + " precursor(s)");

          if(index > -1) {
            Entity precursor = precursors.get(index);

            return (T) precursor;
          }
        }
        else {
          precursors = new ArrayList<Entity>();
        }

        precursors.add(model);

        for (int i = 0; i < len; i++) {
          Field f = fields.get(i);
          Class<?> ftype = f.getType();
          int colIndex = c.getColumnIndex(colNames.get(i));

          if(colIndex == pkColIndex)
            continue;

          if (colIndex == -1) {
            Log.e("Internal<ModelMapping>", "Got -1 column index for `"+colNames.get(i)+"' - Database schema may not match entity");
            throw new ORMDroidException("Got -1 column index for `"+colNames.get(i)+"' - Database schema may not match entity");
          } else {
            Object o = TypeMapper.getMapping(ftype).decodeValue(db, f, c, colIndex, precursors);
            f.set(model, o);
          }
        }

        // Inverse fields are not loaded from database
        // The Primary Key is supplied in place of the column index

        len = mInverseFields.size();
          fields = mInverseFields;

        for(int i = 0; i < len; i++) {
          Field f = fields.get(i);
          Class<?> ftype = f.getType();

          Object o = TypeMapper.getMapping(ftype).decodeValue(db, f, c, (Integer) model.getPrimaryKeyValue(), precursors);
          f.set(model, o);
        }

        return model;
      } catch (InstantiationException e) {
        throw new ORMDroidException(
            "Failed to instantiate model class - does it have a public null constructor?",
            e);
      } catch (IllegalAccessException e) {
        throw new ORMDroidException(
            "Access denied. Is your model's constructor non-public?",
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
      
      /* issue #6 */
      c.close();      

      return list;
    }
    
    void delete(SQLiteDatabase db, Entity o) {
      String sql = "DELETE FROM " + mTableName + " WHERE " + 
                   mPrimaryKeyColumnName + "=" + getPrimaryKeyValue(o);

      Log.v(getClass().getSimpleName(), sql);

      db.execSQL(sql);
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
  
  /*
   * Flushes the schema creation cache, ensuring that all existing mapping's
   * schemas will be recreated if they do not exist when they're next accessed.
   * 
   * See issue #17
   */
  static void flushSchemaCreationCache() {
  	for (Class<? extends Entity> clz : entityMappings.keySet()) {
      entityMappings.get(clz).mSchemaCreated = false;
  	}
  }

  static EntityMapping getEntityMappingEnsureSchema(SQLiteDatabase db,
      Class<? extends Entity> clz) {
    EntityMapping map = getEntityMapping(clz);
    if (!map.mSchemaCreated) {
      map.createSchema(db);
    }
    return map;
  }

  /**
   * <p>Create a new {@link Query} that will query against the
   * table mapped to the specified class.</p>
   * 
   * <p>See the {@link Query} documentation for examples of
   * usage.</p>
   * 
   * @param clz The class to query.
   * @return A new <code>Query</code>.
   */
  public static <T extends Entity> Query<T> query(Class<T> clz) {
    return new Query<T>(clz);
  }
  
  /**
   * Load the next entity from the given cursor using the default database.
   * 
   * If the cursor does not reference the default database, then you should
   * instead use {@link #load(Class, SQLiteDatabase, Cursor)} to allow the
   * database from which linked entities will be loaded.
   * 
   * @param clz The class of Entity the cursor holds.
   * @param c The cursor.
   * @return the loaded entity.
   */
  public static <T extends Entity> T load(Class<T> clz, Cursor c) {
  	return load(clz, ORMDroidApplication.getDefaultDatabase(), c);
  }
  
  /**
   * Load the next entity from the given cursor using the specified database.
   * 
   * @param clz The class of Entity the cursor holds.
   * @param db The database from which to load any linked entities.
   * @param c The cursor.
   * @return the loaded entity.
   */
  public static <T extends Entity> T load(Class<T> clz, SQLiteDatabase db, Cursor c) {
  	// Go ahead and assume schema already exists, since we have a cursor...
  	return getEntityMapping(clz).<T>load(db, c);
  }

  /**
   * Load all entities from the given cursor using the default database.
   * 
   * If the cursor does not reference the default database, then you should
   * instead use {@link #load(Class, SQLiteDatabase, Cursor)} to allow the
   * database from which linked entities will be loaded.
   * 
   * @param clz The class of Entity the cursor holds.
   * @param c The cursor.
   * @return the loaded entities, in a List.
   */
  public static <T extends Entity> List<T> loadAll(Class<T> clz, Cursor c) {
  	return loadAll(clz, ORMDroidApplication.getDefaultDatabase(), c);
  }
  
  /**
   * Load all entities from the given cursor using the specified database.
   * 
   * @param clz The class of Entity the cursor holds.
   * @param db The database from which to load any linked entities.
   * @param c The cursor.
   * @return the loaded entities, in a List.
   */
  public static <T extends Entity> List<T> loadAll(Class<T> clz, SQLiteDatabase db, Cursor c) {
  	// Go ahead and assume schema already exists, since we have a cursor...
  	return getEntityMapping(clz).<T>loadAll(db, c);
  }

  boolean mTransient;
  private EntityMapping mMappingCache;

  protected Entity() {
    mTransient = true;
  }

  /**
   * <p>Determine whether this instance is backed by the database.</p>
   * 
   * <p><strong>Note</strong> that a <code>false</code> result
   * from this method does <strong>not</strong> indicate that
   * the data in the database is up to date with respect to the
   * object's fields.</p>
   * 
   * @return <code>false</code> if this object is stored in the database.
   */
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

  /**
   * <p>Get the value of the primary key field for this object.</p>
   * 
   * <p>Note that this currently uses reflection.</p>
   * 
   * @return The primary key value.
   */
  public Object getPrimaryKeyValue() {
    return getEntityMapping().getPrimaryKeyValue(this);
  }

  /**
   * Insert or update this object using the specified database
   * connection.
   * 
   * @param db The database connection to use.
   * @return The primary key of the inserted item (if object was transient), or -1 if an update was performed.
   */
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

  /**
   * Insert or update this object using the default database
   * connection.
   * 
   * @return The primary key of the inserted item (if object was transient), or -1 if an update was performed.
   */
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
  
  /**
   * Delete this object using the specified database connection.
   * 
   * @param db The database connection to use.
   */
  public void delete(SQLiteDatabase db) {
    EntityMapping mapping = getEntityMappingEnsureSchema(db);

    if (!mTransient) {
      mapping.delete(db, this);
    }
  }
  
  /**
   * Delete this object using the default database connection.
   */
  public void delete() {
    if (!mTransient) {
      SQLiteDatabase db = ORMDroidApplication.getDefaultDatabase();
      db.beginTransaction();
  
      try {
        delete(db);
        db.setTransactionSuccessful();
      } finally {
        db.endTransaction();
      }
  
      db.close();
    }
  }
  
  /**
   * Defines equality in terms of primary key values. 
   */
  @Override
  public boolean equals(Object other) {
    // TODO indirectly using reflection here (via getPrimaryKeyValue).
    return other != null && 
           other.getClass().equals(getClass()) && 
           ((Entity) other).getPrimaryKeyValue().equals(getPrimaryKeyValue());    
  }

  /**
   * Defines the hash code in terms of the primary key value. 
   */
  @Override
  public int hashCode() {
    // TODO this uses reflection. Also, could act wierd if non-int primary keys... 
    return 31 * getClass().hashCode() + getPrimaryKeyValue().hashCode();
  }
}
