/*
Copyright 2018 Isaac Aymerich

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package com.github.segator.jbinary.maven.plugin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Repository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

/**
 *
 * @author isaac
 */
@Mojo(name = "jbinary")
public class JBinaryBuildMojo extends AbstractMojo {

    @Parameter(property = "jreVersion", defaultValue = "1.8.0_131")
    private String jreVersion;
    @Parameter(property = "jBinaryVersion", defaultValue = "0.0.5-ALPHA4")
    private String jBinaryVersion;
    @Parameter(property = "JBinaryURLWindows", defaultValue = "https://github.com/segator/jbinary/releases/download/%s/windows_amd64_jbinary_%s.exe")
    private String JBinaryURLWindows;

    @Parameter(property = "JBinaryURLLinux", defaultValue = "https://github.com/segator/jbinary/releases/download/%s/linux_amd64_jbinary_%s")
    private String JBinaryURLLinux;

    @Parameter(property = "useMavenRepositoryJavaDownload")
    private boolean useMavenRepositoryJavaDownload = false;

    @Parameter(defaultValue = "${project.build.finalName}", readonly = true)
    private String finalName;

    @Parameter(defaultValue = "${project.build.directory}", required = true)
    private File outputDirectory;

    /**
     * The {@link {MavenProject}.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * The {@link MavenSession}.
     */
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Component
    private MavenProjectHelper projectHelper;

    /**
     * The archive configuration to use. See
     * <a href="http://maven.apache.org/shared/maven-archiver/index.html">Maven
     * Archiver Reference</a>.
     */
    @Parameter
    private MavenArchiveConfiguration archive = new MavenArchiveConfiguration();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            File jBinaryExecutable = deployJBinary(SystemUtils.IS_OS_WINDOWS ? "windows" : "linux");
            String[] osList = new String[]{"windows", "linux"};
            for (String platform : osList) {
                File generatedExecutableArtifact = generateExecutable(jBinaryExecutable, platform);
                archiveFile(generatedExecutableArtifact, platform);
            }

        } catch (IOException | InterruptedException ex) {
            throw new MojoFailureException("unexpected error", ex);
        }
    }

    private void archiveFile(File file, String platform) {
        String extension = file.getName().substring(file.getName().lastIndexOf(".") + 1);
        projectHelper.attachArtifact(project, extension, platform, file);
    }

    private File deployJBinary(String platform) throws MalformedURLException, FileNotFoundException, IOException {
        String JBinaryURL = "";
        switch (platform) {
            case "windows":
                JBinaryURL = JBinaryURLWindows;
                break;
            case "linux":
                JBinaryURL = JBinaryURLLinux;
                break;
        }
        URL jbinaryURL = new URL(String.format(JBinaryURL, jBinaryVersion, jBinaryVersion));
        String JBinaryNameFile = FilenameUtils.getName(jbinaryURL.getPath());

        Path jbinaryPath = Paths.get(System.getProperty("java.io.tmpdir"), JBinaryNameFile);
        if (!jbinaryPath.toFile().exists()) {
            getLog().info("Download -->" + jbinaryURL);
            ReadableByteChannel rbc = Channels.newChannel(jbinaryURL.openStream());
            try (FileOutputStream fos = new FileOutputStream(jbinaryPath.toFile())) {
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                fos.flush();
            }
        }
        File jBinaryFile = jbinaryPath.toFile();
        jBinaryFile.setExecutable(true);
        return jBinaryFile;
    }

    private File generateExecutable(File jBinaryExecutable, String platform) throws IOException, InterruptedException {
        getLog().info("building executable file with embeded JRE version" + jreVersion);
        List<Repository> repositories = project.getRepositories();
        boolean executed = false;
        for (Repository repository : repositories) {
            String serverURLParam = useMavenRepositoryJavaDownload ? String.format("-java-server-url \"%s\"", repository.getUrl()) : "";
            String commandString = String.format("\"%s\" -platform \"%s\" -output-name \"%s\" -jar \"%s\" -build \"%s\" %s",
                    jBinaryExecutable.getAbsolutePath(),
                    platform,
                    finalName,
                    project.getArtifact().getFile().getAbsolutePath(),
                    outputDirectory.getAbsolutePath(),
                    serverURLParam);
            CommandLine cmd = CommandLine.parse(commandString);
            getLog().info(String.format("Execute:%s", commandString));
            ExecuteWatchdog wd = new ExecuteWatchdog(ExecuteWatchdog.INFINITE_TIMEOUT);
            Executor exec = new DefaultExecutor();
            PumpStreamHandler psh = new PumpStreamHandler(System.out);
            exec.setStreamHandler(psh);
            exec.setWatchdog(wd);
            try{
                exec.execute(cmd);
            }catch(ExecuteException ex){
                //JRE URL not found
                if(ex.getExitValue() != 4){
                    throw ex;
                }else{
                    continue;
                }
            }
            executed = true;
        }
        if(!executed){
             getLog().error("No Valid Repository found to download JRE");             
        }
        File resultBuildFile = Paths.get(outputDirectory.getAbsolutePath(), finalName + (platform.equals("windows") ? ".exe" : ".bin")).toFile();
        if (!resultBuildFile.exists()) {
            throw new IOException("Result File " + resultBuildFile.getAbsolutePath() + " hasn't been generated");
        }
        return resultBuildFile;
    }

    public String getJreVersion() {
        return jreVersion;
    }

    public void setJreVersion(String jreVersion) {
        this.jreVersion = jreVersion;
    }

    public String getJBinaryURLWindows() {
        return JBinaryURLWindows;
    }

    public void setJBinaryURLWindows(String JBinaryURLWindows) {
        this.JBinaryURLWindows = JBinaryURLWindows;
    }

    public String getJBinaryURLLinux() {
        return JBinaryURLLinux;
    }

    public void setJBinaryURLLinux(String JBinaryURLLinux) {
        this.JBinaryURLLinux = JBinaryURLLinux;
    }

    public String getFinalName() {
        return finalName;
    }

    public void setFinalName(String finalName) {
        this.finalName = finalName;
    }

    public File getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public MavenProject getProject() {
        return project;
    }

    public void setProject(MavenProject project) {
        this.project = project;
    }

    public MavenSession getSession() {
        return session;
    }

    public void setSession(MavenSession session) {
        this.session = session;
    }

    public MavenProjectHelper getProjectHelper() {
        return projectHelper;
    }

    public void setProjectHelper(MavenProjectHelper projectHelper) {
        this.projectHelper = projectHelper;
    }

    public MavenArchiveConfiguration getArchive() {
        return archive;
    }

    public void setArchive(MavenArchiveConfiguration archive) {
        this.archive = archive;
    }

    public String getjBinaryVersion() {
        return jBinaryVersion;
    }

    public void setjBinaryVersion(String jBinaryVersion) {
        this.jBinaryVersion = jBinaryVersion;
    }

}
