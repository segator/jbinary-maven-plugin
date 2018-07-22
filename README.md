# jbinary-maven-plugin
JBinary Maven plugin, do you want to generate a binary with non dependencies of any java application using maven? this is your tool

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.segator/jbinary-maven-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.segator/jbinary-maven-plugin) [![CircleCI branch](https://img.shields.io/circleci/project/github/segator/jbinary-maven-plugin/master.svg)](https://circleci.com/gh/segator)

```xml
<plugin>
    <groupId>com.github.segator</groupId>
    <artifactId>jbinary-maven-plugin</artifactId>
    <version>1.0.0</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>jbinary</goal>
            </goals>
            <configuration>
                <useMavenRepositoryJavaDownload>true</useMavenRepositoryJavaDownload>
                <!-- if you have your own repository, expected groupid com.oracle.java, artifact jre|jdk by default is downloaded from here https://artifacts.alfresco.com/nexus -->
                <JBinaryURLWindows>https://github.com/segator/jbinary/releases/download/%s/windows_amd64_jbinary_%s.exe</JBinaryURLWindows>
                <JBinaryURLLinux>https://github.com/segator/jbinary/releases/download/%s/linux_amd64_jbinary_%s</JBinaryURLLinux> 
                <jreVersion>1.8.0_131</jreVersion> <!-- Java version will be embeded to generated executable -->
            </configuration>
        </execution>
    </executions>
</plugin>
```
