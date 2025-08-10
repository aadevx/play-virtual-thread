# play-virtual-thread

This play framework was developed from www.playframework.com version 1.5.3 with customizations using virtual thread.
[See more details in the documentation](documentation/manual/home.textile) or [http:/localhost:9000/@documentation](http:/localhost:9000/@documentation)

## requirement
1. java 21 or later
2. python v3.1 or later
3. Postgresql 10 or later
4. Mysql 5.17.x or later
5. MariaDB 10.x or later


## Changelog
1. using [Netty 4.2](https://netty.io/)
2. not using [JPA / Hibernate](https://hibernate.org/)
3. using [Sql2o](https://www.sql2o.org/) for query like ORM in database [Postgresql](https://www.postgresql.org/) & [MySQL](https://www.mysql.com/)

```java
 @Table
 public class Berita extends BaseTable { 
    @Id 
    public Long id;

}  
```

3. Changes in the methods of the class **play.db.jdbc.BaseTable**

<table>
<tr>
    <td>Previous</td>
    <td>now</td>    
</tr>
<tr>
    <td>public static int deleteAll()</td>
    <td>public static void deleteAll()</td>    
</tr>
<tr>
    <td>public static int delete(String sql, Object... params)</td>
    <td>public static void delete(String sql, Object... params)</td>    
</tr>
<tr>
    <td>public static int delete(QueryBuilder builder)</td>
    <td>public static void delete(QueryBuilder builder)</td>    
</tr>
<tr>
    <td>public static int delete(String sql)</td>
    <td>public static void delete(String sql)</td>    
</tr>
<tr>
    <td>public int save()</td>
    <td>public void save()</td>    
</tr>
</table>

4. Changes in **play.libs.WS**

<table>
<tr>
    <td>Previous</td>
    <td>now</td>    
</tr>
<tr>
    <td>play.libs.WS.WSRequest</td>
    <td>play.libs.ws.WSRequest</td>    
</tr>
<tr>
    <td>play.libs.WS.HttpResponse</td>
    <td>play.libs.ws.HttpResponse</td>    
</tr>
<tr>
    <td>HttpResponse Response = WS.url(...).get();</td>
    <td>HttpResponse Response = WS.url(...).getResponse();</td>    
</tr>
<tr>
    <td>HttpResponse Response = WS.url(...).post();</td>
    <td>HttpResponse Response = WS.url(...).postResponse();</td>    
</tr>
<tr>
    <td>HttpResponse Response = WS.url(...).put();</td>
    <td>HttpResponse Response = WS.url(...).putResponse();</td>    
</tr>
</table>

*support [okhttp](https://square.github.io/okhttp/), using WS.okUrl()* 

5. field access changes in controller class
<table>
<tr>
    <td>Previous</td>
    <td>now</td>    
</tr>
<tr>
    <td>request</td>
    <td>request()</td>    
</tr>
<tr>
    <td>response</td>
    <td>response()</td>    
</tr>
<tr>
    <td>session</td>
    <td>session()</td>    
</tr>
<tr>
    <td>flash</td>
    <td>flash()</td>    
</tr>
<tr>
    <td>params</td>
    <td>params()</td>    
</tr>
<tr>
    <td>renderArgs</td>
    <td>renderArgs()</td>    
</tr>
<tr>
    <td>validation</td>
    <td>validation()</td>    
</tr>
<tr>
    <td>inbound</td>
    <td>inbound()</td>    
</tr>
<tr>
    <td>outbound</td>
    <td>outbound()</td>    
</tr>
</table>

6. Using [log4j2](https://logging.apache.org/log4j/2.x/) as the base application logger

create file *conf/log4j2.xml* & copy config script below
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration>
  <Appenders>
    <!-- Console Appender -->
    <Console name="Console" target="SYSTEM_OUT">
      <PatternLayout pattern="%-5p ~ %m%n" />
    </Console>
    <!-- Rolling File Appender -->
    <RollingFile name="RollingFile">
      <FileName>logs/application.log</FileName>
      <FilePattern>logs/application-%d{yyyy-MM-dd-hh-mm}.log</FilePattern>
      <PatternLayout>
        <Pattern>%d{ddMMyy HH:mm:ss} %-5p ~ %m%n</Pattern>
      </PatternLayout>
        <Policies>
            <SizeBasedTriggeringPolicy size="10 MB"/>
        </Policies>
    </RollingFile>

  </Appenders>
  <Loggers>
    <Logger name="play" level="info" additivity="false">
<!--      <AppenderRef ref="RollingFile" />-->
      <AppenderRef ref="Console" />
    </Logger>
    <Root level="info">
<!--      <AppenderRef ref="RollingFile" />-->
      <AppenderRef ref="Console" />
    </Root>
  </Loggers>
</Configuration>
```
7. Using a router via annotations

route definition can be done in the controller directly without going through conf/routes, if you use this feature, make sure the route settings in conf/routes are deleted because it is feared that the route definition is double.
usage as follows:
java code 

```java

@Any("/publik/{action}")
public class PublikCtr extends Controller {

    @Get("/")
    public static void index(){
       
    }

    @Post("/")
    public static void submit(){
       
    }
}
```
8. Supports the use of template engines [JTE](https://jte.gg/)

jte template is used as an alternative to the default play template because it has several advantages including easy to adopt, simple, secure and faster than the default playframework template, the default jte template folder is at **app/jte**

```html
@param String name
Hello ${name}!
The current timestamp is ${System.currentTimeMillis()}.
```

```java
public class Application extends Controller {
    
    @Get("/")
	public static void index() {
		String name = "LKPP";
		renderTemplate("index.jte", name);
	}

}
```

9. Supports non static controller

Generally, action methods in the controller use **static void**, but it is also possible that actions or methods in the controller can be defined non-statically provided that the class must use or extends from the controller **play.mvc.GenericController**
 
 ```java
public class Application extends GenericController {
    
    @Get("/")
    public Result index() {
        String name = "LKPP";
        return renderTemplate("index.html", name);
    }

}
```
10. Using **Redirect()** in the controller action method
 
accessing an action method inside another action method is not recommended, it is better to use **redirect()**
```java 
 public class Application extends Controller {

 @Get("/")
 public static void index() {
     
     render();
 }

 @Get("/home")
 public static void home() {
  
     redirect(Applicatio.class, "index");
 }

}
```

an example of using guice module can be seen in **sample-and-tests/nonstatic-app**. 

## Deployment 
1. compile 

```bash
play clean
play deps
play precompile
```

2. copy and run **build.xml** below through **IDE eclipse / intellij / netbeans**.   *After running the build.xml above, the ready deploy application is in the ${target.dir}* folder.

```xml
<project default="build-compile">
	<property environment="env" />
	<property name="play.home" value="/Users/development/Lkpp/GITLAB/play"/>
	<property file="conf/application.conf" />
	<property name="app.path" value="." />
	<property name="target.dir" value="target"/>
	<property name="filesExclude" value="**/*.svn modules/* logs/** eclipse/** test/** data/** .settings/** test-result/**
		tmp/** /.classpath/** /.project/** **/build.xml **/command.* *.log *.txt **/*.yml  application.bat app/** src/** **/*.sql
		public/images/imgng/mm* /testrunner/** /console/** **/.idea .build/**  **/*.java **/*.py*   documentation/** README.md *.yml route" />

	<!--untuk module, mungkin beda  -->
	<property name="filesExcludeModule" value="${filesExclude}" />
	<property name="application.target.dir" value="${target.dir}/${application.name}" />
	<echo message="Target: ${target.dir}"/>

	<target name="packaging" depends="templateDist">
		<copy todir="${application.target.dir}/framework/lib">
            <fileset dir="${play.home}/framework/lib" includes="*.jar" />
            <fileset dir="./lib" includes="*.jar" />
        </copy>
        <copy file="${play.home}/framework/play-lkpp.jar" tofile="${application.target.dir}/framework/play-lkpp.jar" />
        <move file="play-templates.jar" tofile="${application.target.dir}/framework/lib/play-templates.jar" />
        <copy todir="${application.target.dir}" includeemptydirs="false">
            <fileset dir="${app.path}" includes="conf/** public/**" excludes="log4j.properties" />
        </copy>
    </target>

	<target name="templateDist" description="generate the distribution" depends="modules-package">
     <delete file="${app.path}/play-templates.jar" />
     <jar destfile="${app.path}/play-templates.jar">
      <fileset dir="${app.path}" includes="conf/** app/views/**"/>
      <fileset dir="${app.path}/precompiled/templates"/>
      <fileset dir="${app.path}/precompiled/java" />
      <fileset dir="${application.target.dir}" includes="modules/**"  excludes="**/public/** *.yml"/>
     </jar>
		<delete file="${app.path}/play-templates.jar" />
        <jar destfile="${app.path}/play-templates.jar">
            <fileset dir="${app.path}" includes="conf/** app/views/**"/>
            <fileset dir="${app.path}/precompiled/java" />
            <fileset dir="${application.target.dir}" includes="modules/**"  excludes="**/public/** *.yml"/>
        </jar>
        <delete dir="${application.target.dir}/modules/jcommon-core-1.5/app" />
        <delete dir="${application.target.dir}/modules/jcommon-mail-1.5/app" />
	</target>

	 <target name="modules-package" description="packaging conf modules">
        <delete dir="${application.target.dir}" />
        <loadfile property="module1" srcfile="modules/jcommon-core-1.5" />
        <copy todir="${application.target.dir}/modules/jcommon-core-1.5" includeemptydirs="false">
            <fileset dir="${module1}" includes="conf/** public/** app/views/**" excludes="**/*.yml" />
        </copy>
        <loadfile property="module2" srcfile="modules/jcommon-mail-1.5" />
        <copy todir="${application.target.dir}/modules/jcommon-mail-1.5" includeemptydirs="false">
            <fileset dir="${module2}" includes="conf/** public/** app/views/**" excludes="**/*.yml"/>
        </copy>
    </target>

	<target name="clean">
		<delete dir="${app.path}/tmp" />
	</target>

</project>
```

3. copy the shell script **starter.sh** below, place it in the root folder of the prod application

```bash

#!/bin/bash
################################################
## Yang perlu diset oleh ITO saat instalasi: JAVA_HOME, APP_HOME
################################################
JAVA_HOME=/usr/local/src/graalvm
APP_HOME=/home/appserv/application
RETVAL=0
start() {
    echo "Starting application ... ${APP_HOME}"
	cd $APP_HOME
	#Delete server.pid if no process exists
	if [ -f $APP_HOME/server.pid ]; then
       if ps -p `cat $APP_HOME/server.pid` > /dev/null; then   
               echo "$PID is running"
              else
		  rm $APP_HOME/server.pid
		  echo "Remove server.pid because no process exists";
       fi
	fi

        if [ -d $APP_HOME/tmp ]; then
                rm -rf $APP_HOME/tmp
        fi
	 if [ ! -f $APP_HOME/conf/application.conf ]; then
                echo "ERROR conf/application.conf is missing"
		exit -2
        fi

	if [ ! -d $APP_HOME/logs ]; then
		mkdir $APP_HOME/logs 
	fi
    echo "Using JAVA_HOME: ${JAVA_HOME}"
    CLASSPATH="${APP_HOME}/framework/lib/*:${APP_HOME}/lib/*:${APP_HOME}/conf"
    ARGUMENTS="-javaagent:${APP_HOME}/framework/play-lkpp.jar -Dwritepid=true -Dapplication.path=${APP_HOME} -Dprecompiled=true -server -Xshare:off -Dfile.encoding=utf-8 $MIGRATION"
    exec ${JAVA_HOME}/bin/java -cp ${CLASSPATH} ${ARGUMENTS} play.server.Server > ${APP_HOME}/logs/system.out &
    echo "application 4 started"
}

stop() {
    echo "Stop application 4 ... ${APP_HOME}"
    cd $APP_HOME
    if [ -d $APP_HOME/tmp ]; then
        rm -rf $APP_HOME/tmp
    fi
	if [ -f $APP_HOME/server.pid ]; then
	   kill -9 `cat $APP_HOME/server.pid`
		rm $APP_HOME/server.pid
	fi
    echo "application 4 stopped"
}

case "$1" in
        start)
                start
                ;;
        stop)
                stop
                ;;
        restart)
                stop
                start
       			 ;;
    *)
        echo "Usage: starter {start|stop|restart}"
        RETVAL=2
esac

exit $RETVAL
```

4. copy folder **target/{application.name}** into the production server
5. seting **conf/log4j2.xml** for production

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration>
  <Appenders>
    <!-- Rolling File Appender -->
    <RollingFile name="RollingFile">
      <FileName>logs/application.log</FileName>
      <FilePattern>logs/application-%d{yyyy-MM-dd-hh-mm}.log</FilePattern>
      <PatternLayout>
        <Pattern>%d{ddMMyy HH:mm:ss} %-5p ~ %m%n</Pattern>
      </PatternLayout>
        <Policies>
            <SizeBasedTriggeringPolicy size="10 MB"/>
        </Policies>
    </RollingFile>

  </Appenders>
  <Loggers>
    <Logger name="play" level="info" additivity="false">
      <AppenderRef ref="RollingFile" />
    </Logger>
    <Root level="info">
      <AppenderRef ref="RollingFile" />
    </Root>
  </Loggers>
</Configuration>
```

6. Run the application via the shell script **starter.sh**

<pre>
./starter.sh start
    OR
./starter.sh restart
</pre>

7. To stop the application execute the command below

<pre>
./starter.sh stop
</pre>

*Don't forget to set application.mode=prod in application.conf*

