# jbinary-maven-plugin
JBinary Maven plugin, do you want to generate a binary with non dependencies of any java application using maven? this is your tool

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.segator/jbinary-maven-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.segator/jbinary-maven-plugin) [![CircleCI branch](https://img.shields.io/circleci/project/github/segator/jbinary-maven-plugin/master.svg)](https://circleci.com/gh/segator)

```xml
<plugin>
    <groupId>com.github.segator</groupId>
    <artifactId>jbinary-maven-plugin</artifactId>
    <version>1.0.8</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>jbinary</goal>
            </goals>
            <configuration>
                <jreVersion>1.8.0_131</jreVersion> <!-- Jre version to compile in the executable embeded file-->
                <jBinaryJavaDownload>http://mycustomrepository.com/{javaType}/{javaVersion}/{javaType}-{javaVersion}-{platform}{architecture}.tgz</jBinaryJavaDownload>
                <!--If you have your own jre, you can set the url, (the tar.gz file must jave a folder /java inside and jre content inside -->
                <useMavenRepositoryJavaDownload>true</useMavenRepositoryJavaDownload>
                <!-- if you have your own repository, expected groupid com.oracle.java, artifact jre|jdk by default is downloaded from here https://artifacts.alfresco.com/nexus
                This param is skiped if you use jBinaryJavaDownload -->
                <jBinaryURLWindows>https://github.com/segator/jbinary/releases/download/%s/windows_amd64_jbinary_%s.exe</jBinaryURLWindows>
                <jBinaryURLLinux>https://github.com/segator/jbinary/releases/download/%s/linux_amd64_jbinary_%s</jBinaryURLLinux> 
                <!--Custom JBinary Download URL, %s is replaced by the Jbinary Version -->
                <jreVersion>1.8.0_131</jreVersion> <!-- Java version will be embeded to generated executable -->
                <compressBinary>true</compressBinary> <!-- Compress = less binary size, uncompress faster boot-->
                <jvmArguments>-Xms512M;-Xmx1024M</jvmArguments> <!-- Static JVM Arguments you want to be used by generated binary -->
                <appArguments>--verbose</appArguments> <!-- Static Applicaiton arguments you want to be used by generated binary -->
                <!-- Next arguments only apply on windows builds, it defines executable metadata -->
                <winCompany>My Company<winCompany>
                <winCopyright> Copyright 2018</winCopyright>
                <winDescription>Application Description</winDescription>
                <!-- Default behaviour to run app, in gui mode no console is shown but you can't execute by console or capture stdout, (console|gui) default(console) -->
                <winExecutionBehaviour>console</winExecutionBehaviour>
                <!-- Arguments that will force console mode in case of default behaviour gui, default (-console;-terminal) -->
                <winExecutionEnableConsoleBehaviourArgs>-console</winExecutionEnableConsoleBehaviourArgs>
                <!-- Relative path to application icon -->
                <winIconPath>myapplication.ico</winIconPath>
                <!--Windows Invoker type  asInvoker|requireAdministrator default(asInvoker) -->
                <winInvoker>requireAdministrator</winInvoker>                
                <winProductName>My Product Name</winProductName>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## Prerequisites
You must have installed golang 1.10.x or supperior
set in the system vars $GOPATH $GOSRC $GOBIN
and add in your $PATH the $GOBIN path

## Create tiny binary files
You could try the new cool system of java modules in java9 to create a very tiny jre
To create a tiny java9 module you can use jlink(installed by default on a JDK installation)
```bash
jlink --module-path $JAVA_HOME/jmods --verbose --compress 2 --no-header-files --output  "$HOME/myjre/java" --add-modules java.base,java.rmi,java.xml,java.desktop,java.sql
tar -czvf my-custom-tiny-jre.tar.gz "$HOME/myjre/java"
```
After create the tar.gz upload to a web server or mvn repository and use in your mvn pom
**jBinaryJavaDownload** in case of web server or **useMavenRepositoryJavaDownload** in case of mvn repository

## Application slow to boot
Play disabling compression, could help or think about use java9 modules