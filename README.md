ORMDroid is a simple ORM persistence framework for your Android applications, providing an easy to use, almost-zero-config way to handle model persistence without ever having to deal with Android's built-in database interfaces.

ORMDroid is:

* *Small* - ~20k, target of no more than 30k.
* *Simple* - No excessive features or support for platforms other than Android.
* *Flexible* - Allowing you to configure what you need to, but at the same time...
* *Automatic* - ... sensible defaults for everything.

ORMDroid works with Android API 8 and up.

Getting ORMDroid
----------------

You can either download ORMDroid from [the download page](http://code.google.com/p/orm-droid/downloads/list), or check out of Git.

If downloading a packaged release, you'll need to unzip the file somewhere, and then import the project into your Eclipse. 

Getting started
---------------

To use ORMDroid, you need to set up ORMDroid as a required library in your android app. If you're using Eclipse, go to _project->properties->android_ and add ORMDroid as a required libray. Now, you just need to add a single XML tag to your `AndroidManifest.xml` as a child of the `Application` tag:  

```xml
<meta-data
  android:name="ormdroid.database.name"
  android:value="your_database_name" />
```

And initialize the framework somewhere (e.g. Application.onCreate, or even in your activity's onCreate since there's no penalty for calling initialize multiple times):

```java
ORMDroidApplication.initialize(someContext);
```

Then you create your model:

```java
public class Person extends Entity {
  public int id;
  public String name;
  public String telephone;
}
```

And work with it as you see fit!

```java
Person p = Entity.query(Person.class).where("id=1").execute();
p.telephone = "555-1234";
p.save();
```

There is also an object-oriented query API:

```java
import static com.roscopeco.ormdroid.Query.eql;

// ... later

Person person = Entity.query(Person.class).where(eql("id", id)).execute();
p.telephone = "555-1234";
p.save();
```      

That's it! If you want more customization over e.g. table names, column names, etc, take a look at the `Table` and `Column` annotations.

There is a more detailed version of these instructions in [this blog entry](http://roscopeco.wordpress.com/2012/08/05/ormdroid-on-google-code/)

*Update*: There is now a very simple sample app available for ORMDroid. You can get it from Git:

```
git clone https://github.com/roscopeco/ormdroid-example.git
```

For more information, check out [this blog entry](http://roscopeco.com/2012/08/23/ormdroid-bugfixes-sample-app-happiness/).
