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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.artifact.versioning.OverConstrainedVersionException;
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

    //Jbinary Configuration
    @Parameter(property = "jreVersion", defaultValue = "1.8.0_131")
    private String jreVersion;
    @Parameter(property = "jBinaryVersion", defaultValue = "0.0.6")
    private String jBinaryVersion;
    
    @Parameter(property = "compressBinary")
    private Boolean compressBinary=true;
    
    @Parameter(property = "JBinaryJavaDownload")
    private String jBinaryJavaDownload;
    
    @Parameter(property = "JBinaryURLWindows", defaultValue = "https://github.com/segator/jbinary/releases/download/%s/windows_amd64_jbinary_%s.exe")
    private String JBinaryURLWindows;

    @Parameter(property = "JBinaryURLLinux", defaultValue = "https://github.com/segator/jbinary/releases/download/%s/linux_amd64_jbinary_%s")
    private String JBinaryURLLinux;

    @Parameter(property = "useMavenRepositoryJavaDownload")
    private boolean useMavenRepositoryJavaDownload = false;

    @Parameter(property = "jvmArguments")
    private String jvmArguments;

    @Parameter(property = "appArguments")
    private String appArguments;

    @Parameter(property = "winCompany")
    private String winCompany;

    @Parameter(property = "winCopyright")
    private String winCopyright;

    @Parameter(property = "winDescription")
    private String winDescription;

    @Parameter(property = "winExecutionBehaviour")
    private String winExecutionBehaviour;

    @Parameter(property = "winExecutionEnableConsoleBehaviourArgs")
    private String winExecutionEnableConsoleBehaviourArgs;

    @Parameter(property = "winIconPath")
    private String winIconPath;

    @Parameter(property = "winInvoker")
    private String winInvoker;

    @Parameter(property = "winProductName")
    private String winProductName;

    @Parameter(defaultValue = "${project.build.finalName}", readonly = true)
    private String finalName;

    @Parameter(defaultValue = "${project.version}", readonly = true)
    private String projectVersion;

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
        } catch (Exception ex) {
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

    private File generateExecutable(File jBinaryExecutable, String platform) throws IOException, InterruptedException, OverConstrainedVersionException {
        getLog().info("building executable file with embeded JRE version" + jreVersion);
        List<Repository> repositories = project.getRepositories();
        boolean executed = false;
        for (Repository repository : repositories) {
            StringBuilder jBinaryCommand = new StringBuilder(jBinaryExecutable.getAbsolutePath())
                    .append(getParameterIfNotNul("-platform", platform))
                    .append(!compressBinary?"-no-compress":"")
                    .append(getParameterIfNotNul("-output-name", finalName))
                    .append(getParameterIfNotNul("-jar", project.getArtifact().getFile().getAbsolutePath()))
                    .append(getParameterIfNotNul("-build", outputDirectory.getAbsolutePath()))
                    .append(getParameterIfNotNul("-java-download-link", jBinaryJavaDownload))
                    .append(getParameterIfNotNul("-java-server-url", useMavenRepositoryJavaDownload ? repository.getUrl() : null))
                    .append(getParameterIfNotNul("-app-arguments", getAppArguments()))
                    .append(getParameterIfNotNul("-jvm-arguments", getJvmArguments()));
            if (platform.equals("windows")) {
                jBinaryCommand.append(getParameterIfNotNul("-win-company", getWinCompany()))
                        .append(getParameterIfNotNul("-win-copyright", getWinCopyright()))
                        .append(getParameterIfNotNul("-win-description", getWinDescription()))
                        .append(getParameterIfNotNul("-win-execution-behaviour", getWinExecutionBehaviour()))
                        .append(getParameterIfNotNul("-win-execution-enable-console-behaviour-args", getWinExecutionEnableConsoleBehaviourArgs()))
                        .append(getParameterIfNotNul("-win-icon-path", getWinIconPath() != null ? new File(project.getBasedir(), getWinIconPath()).getAbsolutePath() : null))
                        .append(getParameterIfNotNul("-win-invoker", getWinInvoker()))
                        .append(getParameterIfNotNul("-win-product-name", getWinProductName()))
                        .append(getParameterIfNotNul("-win-product-version", projectVersion))
                        .append(getParameterIfNotNul("-win-version-major", project.getArtifact().getSelectedVersion().getMajorVersion()))
                        .append(getParameterIfNotNul("-win-version-minor", project.getArtifact().getSelectedVersion().getMinorVersion()))
                        .append(getParameterIfNotNul("-win-version-patch", project.getArtifact().getSelectedVersion().getIncrementalVersion()))
                        .append(getParameterIfNotNul("-win-version-build", project.getArtifact().getSelectedVersion().getBuildNumber())).toString();
            }
            //project.getArtifact().getSelectedVersion().parseVersion(jreVersion);
            CommandLine cmd = CommandLine.parse(jBinaryCommand.toString());
            getLog().info(String.format("Execute:%s", jBinaryCommand.toString()));
            ExecuteWatchdog wd = new ExecuteWatchdog(ExecuteWatchdog.INFINITE_TIMEOUT);
            Executor exec = new DefaultExecutor();
            PumpStreamHandler psh = new PumpStreamHandler(System.out);
            exec.setStreamHandler(psh);
            exec.setWatchdog(wd);
            try {
                exec.execute(cmd);
            } catch (ExecuteException ex) {
                //JRE URL not found
                if (ex.getExitValue() != 4) {
                    throw ex;
                } else {
                    continue;
                }
            }
            executed = true;
            break;
        }
        if (!executed) {
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

    public boolean isUseMavenRepositoryJavaDownload() {
        return useMavenRepositoryJavaDownload;
    }

    public void setUseMavenRepositoryJavaDownload(boolean useMavenRepositoryJavaDownload) {
        this.useMavenRepositoryJavaDownload = useMavenRepositoryJavaDownload;
    }

    public String getJvmArguments() {
        return jvmArguments;
    }

    public void setJvmArguments(String jvmArguments) {
        this.jvmArguments = jvmArguments;
    }

    public String getAppArguments() {
        return appArguments;
    }

    public void setAppArguments(String appArguments) {
        this.appArguments = appArguments;
    }

    public String getWinCompany() {
        return winCompany;
    }

    public void setWinCompany(String winCompany) {
        this.winCompany = winCompany;
    }

    public String getWinCopyright() {
        return winCopyright;
    }

    public void setWinCopyright(String winCopyright) {
        this.winCopyright = winCopyright;
    }

    public String getWinDescription() {
        return winDescription;
    }

    public void setWinDescription(String winDescription) {
        this.winDescription = winDescription;
    }

    public String getWinExecutionBehaviour() {
        return winExecutionBehaviour;
    }

    public void setWinExecutionBehaviour(String winExecutionBehaviour) {
        this.winExecutionBehaviour = winExecutionBehaviour;
    }

    public String getWinExecutionEnableConsoleBehaviourArgs() {
        return winExecutionEnableConsoleBehaviourArgs;
    }

    public void setWinExecutionEnableConsoleBehaviourArgs(String winExecutionEnableConsoleBehaviourArgs) {
        this.winExecutionEnableConsoleBehaviourArgs = winExecutionEnableConsoleBehaviourArgs;
    }

    public String getWinIconPath() {
        return winIconPath;
    }

    public void setWinIconPath(String winIconPath) {
        this.winIconPath = winIconPath;
    }

    public String getWinInvoker() {
        return winInvoker;
    }

    public void setWinInvoker(String winInvoker) {
        this.winInvoker = winInvoker;
    }

    public String getWinProductName() {
        return winProductName;
    }

    public void setWinProductName(String winProductName) {
        this.winProductName = winProductName;
    }

    public String getProjectVersion() {
        return projectVersion;
    }

    public void setProjectVersion(String projectVersion) {
        this.projectVersion = projectVersion;
    }

    private String getParameterIfNotNul(String argumentCommand, Object field) {
        if (field != null && !field.toString().isEmpty()) {
            return String.format(" %s \"%s\" ", argumentCommand, field);
        }
        return "";
    }

}
