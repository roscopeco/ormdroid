package com.roscopeco.ormdroid;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;

public class ListTypeMapping implements TypeMapping {
  private static final String TAG = "ListTypeMapping";

  public Class<?> javaType() {
    return List.class;
  }

  // TODO make sure concreteType is not List, it should be the List's element, or change the argument to List<Class<?>>...
  public String sqlType(Class<?> concreteType) {
    // TODO basic typed lists not supported yet
    if(Number.class.isAssignableFrom(concreteType)) {
      return "BLOB"; // Raw packed numbers
    }
    else if(String.class.isAssignableFrom(concreteType)) {
      return "BLOB"; // Packed null-terminated strings
    }

    // Should never be reached, as Entity Lists must be inversed
    return "NONE";
  }

  public String encodeValue(SQLiteDatabase db, Object value) {
    Log.d(TAG, "encoding... " + value);

    @SuppressWarnings("unchecked")
    List<Entity> model = (List<Entity>)value;

    for(Entity entity : model) {
      if (entity.isTransient()) {
        if (db == null) {
          throw new IllegalArgumentException("Transient object doesn't make sense here");
        } else {
          TypeMapper.encodeValue(db, entity.save(db));
        }
      } else {
        TypeMapper.encodeValue(db, entity.getPrimaryKeyValue());
      }
    }

    return "<list>";
  }

  // TODO columnIndex == primaryKey for inverse fields... (+ many more dots...)
  public Object decodeValue(SQLiteDatabase db, Field field, Cursor c, int columnIndex, ArrayList<Entity> precursors) {
    Log.d(TAG, "decoding... " + field);

    Class<?> type = field.getType();

    if (List.class.isAssignableFrom(type)) {

      ParameterizedType parameterizedType = (ParameterizedType) field.getGenericType();
      @SuppressWarnings("unchecked")
      Class<? extends Entity> expEntityType = (Class<? extends Entity>) parameterizedType.getActualTypeArguments()[0];
      // TODO parameterizedType.getActualTypeArguments().length should check to always == 1

      // TODO could use Query here? Maybe Query could have a primaryKey() method to select by prikey?
      Entity.EntityMapping map = Entity.getEntityMappingEnsureSchema(db, expEntityType);

      List list;

      try {
        list = (List) type.newInstance();
      }
      catch (IllegalAccessException e) {
        Log.e(TAG, "IllegalAccessExpression thrown");
        e.printStackTrace();
        return null;
      }
      catch (InstantiationException e) {
        Log.e(TAG, "InstantiationException thrown");
        e.printStackTrace();
        return null;
      }

      String inverseColumnName = field.getAnnotation(Column.class).inverse();

      // TODO non-inverse Lists (i.e. basic types) are not yet implemented
      if("".equals(inverseColumnName))
        return list;

      Log.d(TAG, "map.mTableName: " + map.mTableName);
      String sql = "SELECT * FROM " + map.mTableName + " WHERE " + inverseColumnName + "=" + columnIndex;
      Log.v(TAG, sql);
      Cursor valc = db.rawQuery(sql, null);

      if (valc.moveToFirst()) {
        do {
          list.add(map.load(db, valc, precursors));
        } while(valc.moveToNext());
      }

      return list;

    } else {
      throw new IllegalArgumentException("ListTypeMapping can only be used with List subclasses");
    }
  }
}