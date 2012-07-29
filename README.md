# Groovy [Remember The Milk](http://www.rememberthemilk.com)! [![Build Status](https://buildhive.cloudbees.com/job/eriwen/job/groovyrtm/badge/icon)](https://buildhive.cloudbees.com/job/eriwen/job/groovyrtm/)

GroovyRTM allows you to easily take advantage of the Remember The Milk REST API
using any language on the JVM. In short, you can now write apps for Remember The
Milk without having to worry about all the HTTP transaction stuff, error
handling, etc. As its name implies, it's written in Groovy, which made it much
easier to write and test.

## Getting Started:

You need 2 things to create a app using GroovyRTM:

* An API key at [http://www.rememberthemilk.com/services/api/keys.rtm](http://www.rememberthemilk.com/services/api/keys.rtm) (You'll need an RTM account to test)
* The _groovyrtm.jar_ file, available on Maven Central or as a [download from GitHub](https://github.com/eriwen/groovyrtm/downloads).

```groovy Gradle/Grails
compile 'com.eriwen:groovyrtm:2.1.2'
```

```xml Maven
<dependency>
    <groupId>com.eriwen</groupId>
    <artifactId>groovyrtm</artifactId>
    <version>2.1.2</version>
</dependency>
```

## Simple Example Java Application:

```java
import org.eriwen.rtm.*;
class MyGroovyRtmTest {
    public static void main(String[] args) {
        GroovyRtm groovyrtm = new GroovyRtm("<api key>","<shared secret>");
        groovyrtm.testEcho();
    }
}
```

## Authentication Example:

```java
package groovyrtmauth;
import java.awt.Desktop;
import java.io.IOException;
import java.net.*;
import org.eriwen.rtm.*;
public class MyGroovyRtmTest {
   public static void main(String[] args) {
       GroovyRtm rtm = new GroovyRtm("myapikey", "mysharedsecret");
       try {
           if (!rtm.isAuthenticated()) {
               // Start login process by getting the authorization URL
               try {
                   String authUrl = rtm.getAuthUrl();
                   if (Desktop.isDesktopSupported()) {
                       // Open the auth web page
                       Desktop desktop = Desktop.getDesktop();
                       desktop.browse(new URI(authUrl));
                       System.out.println("I've just opened a web page so you can authorize Groovy RTM");
                   } else {
                       System.out.println("Please open " + authUrl + " in a web browser to authorize Groovy RTM");
                   }
                   System.out.println("Press any key when done...");
                   //TODO: Put your application logic or button here so users can click a button when done
                   System.in.read();
                   // GroovyRTM automatically stores the auth token
                   String authToken = rtm.authGetToken();
                   if (authToken != null) {
                       // We're golden!
                       System.out.println("Groovy RTM successfully authorized!!");
                   }
               } catch (URISyntaxException ue) {
                   ue.printStackTrace();
               } catch (IOException ioe) {
                   ioe.printStackTrace();
               }
           } else {
               System.out.println("Groovy RTM already authorized!");
           }
       } catch (GroovyRtmException rse) {
           rse.printStackTrace();
       }
   }
}
```

## Quick-start Project:

Download a NetBeans project setup with the latest GroovyRTM JAR and optimal project structure [here](http://cloud.github.com/downloads/emwendelin/groovyrtm/GroovyRtmAuth.zip). Project also includes RTM authorization code to help you start even faster!

## Contributing

When submitting patches, please include comments when code is not obvious and include thorough tests.

Statistics, note the avg. complexity:
[[http://static.eriwen.com/images/groovyrtm_stats.png]]

Clover code coverage:
[[http://static.eriwen.com/images/groovyrtm_coverage.png]]
