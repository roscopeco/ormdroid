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
import android.util.Log;

public class ORMDroidApplication extends Application {
  private static ORMDroidApplication singleton;  
  private Context mContext;
  private String mDBName;

  private static void initInstance(ORMDroidApplication app, Context ctx) {
    app.attachBaseContext(app.mContext = ctx.getApplicationContext());
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
  
  public static ORMDroidApplication getSingleton() {
    if (!isInitialized()) {
      Log.e("ORMDroidApplication", "ORMDroid is not initialized");
      throw new ORMDroidException("ORMDroid is not initialized - You must call ORMDroidApplication.initialize");
    }

    return singleton;
  }
  
  public static SQLiteDatabase getDefaultDatabase() {
    return getSingleton().getDatabase();    
  }
  
  @Override
  public void onCreate() {
    if (singleton != null) {
      throw new IllegalStateException("ORMDroidApplication already initialized!");
    }
    singleton = this;
    mContext = getApplicationContext();
    //initInstance(this, getApplicationContext());
  }
  
  private void initDatabaseConfig() {
    try {
      ApplicationInfo ai = mContext.getPackageManager().getApplicationInfo(mContext.getPackageName(), PackageManager.GET_META_DATA);
      mDBName = ai.metaData.get("ormdroid.database.name").toString();
    } catch (Exception e) {
      throw new ORMDroidException("ORMDroid database configuration not found; Did you set properties in your app manifest?", e);
    }
  }
  
  public String getDatabaseName() {
    if (mDBName == null) {
      initDatabaseConfig();
    }
    return mDBName;
  }
  
  public SQLiteDatabase getDatabase() {
    return openOrCreateDatabase(getDatabaseName(), 0, null);
  }
}
