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

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;

/**
 * <p>Provides static-initialization for the ORMDroid framework.
 * The {@link #initialize(Context)} method must be called with
 * a valid {@link Context} prior to using any framework methods
 * that reference the default database.</p>
 * 
 * <p>Note that this class extends {@link android.app.Application},
 * allowing you to set it as the Application class in your manifest
 * to have this initialization handled automatically.</p>
 */
public class ORMDroidApplication extends Application {
  @SuppressWarnings("unused")
  private static final String VISIBILITY_PRIVATE = "PRIVATE";
  private static final String VISIBILITY_WORLD_READABLE = "WORLD_READABLE";
  private static final String VISIBILITY_WORLD_WRITEABLE = "WORLD_WRITEABLE";
	
  private static ORMDroidApplication singleton;  
  private Context mContext;
  private String mDBName;
  private int mDBVisibility = Context.MODE_PRIVATE;

  private static void initInstance(ORMDroidApplication app, Context ctx) {
    app.mContext = ctx.getApplicationContext();
		if (app.getBaseContext() == null) {
			app.attachBaseContext(app.mContext);
		}
  }
  
  public static boolean isInitialized() {
    return (singleton != null);
  }
  
  /**
   * <p>Intialize the ORMDroid framework. This <strong>must</strong> be called before
   * using any of the methods that use the default database.</p>
   * 
   * <p>If your application doesn't use the default database (e.g. you pass in your
   * own {@link SQLiteDatabase} handle to the {@link Query#execute(SQLiteDatabase)} and
   * {@link Entity#save(SQLiteDatabase)} methods) the you don't <i>technically</i>
   * need to call this, but it doesn't hurt.</p>
   * 
   * <p>This method may be called multiple times - subsequent calls are simply 
   * ignored.</p>
   * 
   * @param ctx A {@link Context} within the application to initialize.
   */
  public static void initialize(Context ctx) {
    if (!isInitialized()) {
      initInstance(singleton = new ORMDroidApplication(), ctx);
    }
  }

  /**
   * Obtain the singleton instance of this class.
   * 
   * @return the singleton instance.
   */
  public static ORMDroidApplication getSingleton() {
    if (!isInitialized()) {
      Log.e("ORMDroidApplication", "ORMDroid is not initialized");
      throw new ORMDroidException("ORMDroid is not initialized - You must call ORMDroidApplication.initialize");
    }

    return singleton;
  }
  
  /**
   * Obtain the default database used by the framework. This
   * is a convenience that calls {@link #getDatabase()} on the
   * singleton instance.
   * 
   * @return the default database.
   */
  public static SQLiteDatabase getDefaultDatabase() {
    return getSingleton().getDatabase();    
  }
  
  @Override
  public void onCreate() {
    if (singleton != null) {
      throw new IllegalStateException("ORMDroidApplication already initialized!");
    }
    singleton = this;
    initInstance(this, getApplicationContext());
  }
  
  private void initDatabaseConfig() {
    try {
      ApplicationInfo ai = mContext.getPackageManager().getApplicationInfo(mContext.getPackageName(), PackageManager.GET_META_DATA);
      mDBName = ai.metaData.get("ormdroid.database.name").toString();
    } catch (Exception e) {
      throw new ORMDroidException("ORMDroid database configuration not found; Did you set properties in your app manifest?", e);
    }
    try {
      ApplicationInfo ai = this.mContext.getPackageManager().getApplicationInfo(this.mContext.getPackageName(), PackageManager.GET_META_DATA);
      String metaVisibility = ai.metaData.get("ormdroid.database.visibility").toString();
      if (ORMDroidApplication.VISIBILITY_WORLD_WRITEABLE.equals(metaVisibility)) {
        this.mDBVisibility = Context.MODE_WORLD_WRITEABLE;
      }
      else if (ORMDroidApplication.VISIBILITY_WORLD_READABLE.equals(metaVisibility)) {
        this.mDBVisibility = Context.MODE_WORLD_READABLE;
      }
      else {
        this.mDBVisibility = Context.MODE_PRIVATE;
      }
    }
    catch (Exception e) {}
  }
  
  /**
   * Get the database name used by the framework in this application.
   * 
   * @return The database name.
   */
  public String getDatabaseName() {
    if (mDBName == null) {
      initDatabaseConfig();
    }
    return mDBName;
  }
  
  /**
   * Get the database used by the framework in this application.
   * 
   * @return The database.
   */
  public SQLiteDatabase getDatabase() {
  	try {
      return SQLiteDatabase.openDatabase(mContext.getDatabasePath(getDatabaseName()).getPath(), null, SQLiteDatabase.OPEN_READWRITE);
  	} catch (SQLiteException e) {
  		// Couldn't open the database. It may never have existed, or it may have been
  		// deleted while the app was running. If this is the case, entity mappings may still
  		// be hanging around from that run, with their mSchemaCreated flag set to true. Since
  		// this information is now stale, let's flush it (See issue #17).
  		Entity.flushSchemaCreationCache();
  		
  		return this.openOrCreateDatabase(this.getDatabaseName(), BuildConfig.DEBUG ? Context.MODE_WORLD_READABLE : this.mDBVisibility, null);
  	}
  }  
}
