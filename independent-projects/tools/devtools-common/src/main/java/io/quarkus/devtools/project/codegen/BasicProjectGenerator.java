package io.quarkus.devtools.project.codegen;

import static java.lang.String.format;

import io.quarkus.devtools.commands.data.QuarkusCommandInvocation;
import io.quarkus.devtools.project.BuildTool;
import io.quarkus.devtools.project.codegen.writer.FileProjectWriter;
import io.quarkus.devtools.project.codegen.writer.ProjectWriter;
import io.quarkus.maven.utilities.MojoUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map.Entry;

public class BasicProjectGenerator implements ProjectGenerator {

    public static final String NAME = "basic";

    public BasicProjectGenerator() {
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void generate(QuarkusCommandInvocation invocation) throws IOException {
        final FileProjectWriter writer = new FileProjectWriter(invocation.getQuarkusProject().getProjectDirPath().toFile());
        writer.init();
        generate(writer, invocation);
    }

    void generate(ProjectWriter writer, QuarkusCommandInvocation invocation) throws IOException {
        final BasicRestProject project = new BasicRestProject(writer, invocation);

        project.initProject();

        project.createDockerFiles();
        project.createDockerIgnore();
        project.createApplicationConfig();

        project.createGitIgnore();
    }

    private class BasicRestProject {
        private QuarkusCommandInvocation invocation;
        private ProjectWriter writer;
        private String srcMainPath;
        private String testMainPath;
        private String nativeTestMainPath;
        private SourceType type;

        private BasicRestProject(final ProjectWriter writer, QuarkusCommandInvocation invocation) {
            this.writer = writer;
            this.invocation = invocation;
            this.type = invocation.getValue(SOURCE_TYPE, SourceType.JAVA);
        }

        @SuppressWarnings("unchecked")
        private <T> T get(final String key, final T defaultValue) {
            return invocation.getValue(key, defaultValue);
        }

        private boolean initProject() throws IOException {
            boolean newProject = initBuildTool();

            srcMainPath = writer.mkdirs(type.getSrcDir());
            testMainPath = writer.mkdirs(type.getTestSrcDir());
            // for gradle we want to place the native tests in under 'src/native-test/java'
            // since gradle's idiomatic way of running integration tests is to create a new source set
            if (getBuildTool() == BuildTool.GRADLE) {
                nativeTestMainPath = writer.mkdirs(type.getTestSrcDir().replace("test", "native-test"));
            } else {
                nativeTestMainPath = testMainPath;
            }

            return newProject;
        }

        private boolean initBuildTool() throws IOException {
            BuildTool buildTool = getBuildTool();
            if (!invocation.hasValue(ADDITIONAL_GITIGNORE_ENTRIES)) {
                invocation.setValue(ADDITIONAL_GITIGNORE_ENTRIES, buildTool.getGitIgnoreEntries());
            }
            if (!invocation.hasValue(BUILD_DIRECTORY)) {
                invocation.setValue(BUILD_DIRECTORY, buildTool.getBuildDirectory());
            }
            if (!invocation.hasValue(MAVEN_REPOSITORIES)) {
                invocation.setValue(MAVEN_REPOSITORIES, "");
            }
            if (!invocation.hasValue(MAVEN_PLUGIN_REPOSITORIES)) {
                invocation.setValue(MAVEN_PLUGIN_REPOSITORIES, "");
            }

            boolean newProject = !writer.exists(buildTool.getDependenciesFile());
            if (newProject) {
                for (String buildFile : buildTool.getBuildFiles()) {
                    generate(type.getBuildFileResourceTemplate(getName(), buildFile), invocation, buildFile, buildFile);
                }
            } else {
                String[] gav;
                if (BuildTool.MAVEN.equals(buildTool)) {
                    ByteArrayInputStream buildFileInputStream = new ByteArrayInputStream(
                            writer.getContent(buildTool.getDependenciesFile()));
                    gav = MojoUtils.readGavFromPom(buildFileInputStream);
                } else {
                    gav = new String[3];
                    for (String buildFile : buildTool.getBuildFiles()) {
                        if (writer.exists(buildFile)) {
                            try (ByteArrayInputStream buildFileInputStream = new ByteArrayInputStream(
                                    writer.getContent(buildFile))) {
                                gav = MojoUtils.readGavFromSettingsGradle(buildFileInputStream, gav);
                            }
                        }
                    }
                }
                if (gav[0] != null) {
                    invocation.setValue(PROJECT_GROUP_ID, gav[0]);
                }
                if (gav[1] != null) {
                    invocation.setValue(PROJECT_ARTIFACT_ID, gav[1]);
                }
            }
            return newProject;
        }

        private BuildTool getBuildTool() {
            return invocation.getQuarkusProject().getBuildTool();
        }

        private void generate(final String templateName, QuarkusCommandInvocation invocation, final String outputFilePath,
                final String resourceType)
                throws IOException {
            if (!writer.exists(outputFilePath)) {
                String template = invocation.getPlatformDescriptor().getTemplate(templateName);
                if (template == null) {
                    throw new IOException("Template resource is missing: " + templateName);
                }
                for (Entry<String, Object> e : invocation.getValues().entrySet()) {
                    if (e.getValue() instanceof String) { // Exclude null values (classname and path can be null)
                        template = template.replace(format("${%s}", e.getKey()), (String) e.getValue());
                    }
                }

                // do some nasty replacements for Java target if we want to generate Java 11 projects
                if ("11".equals(invocation.getValue(JAVA_TARGET))) {
                    if (BuildTool.GRADLE.equals(invocation.getQuarkusProject().getBuildTool())) {
                        template = template.replace("JavaVersion.VERSION_1_8", "JavaVersion.VERSION_11");
                    } else {
                        template = template.replace("<maven.compiler.source>1.8</maven.compiler.source>",
                                "<maven.compiler.source>11</maven.compiler.source>");
                        template = template.replace("<maven.compiler.target>1.8</maven.compiler.target>",
                                "<maven.compiler.target>11</maven.compiler.target>");
                        // Kotlin
                        template = template.replace("<jvmTarget>1.8</jvmTarget>", "<jvmTarget>11</jvmTarget>");
                        // Scala
                        // For now, we keep Java 8 as a target for Scala as we don't want to upgrade to 2.13
                        // template = template.replace("<arg>-target:jvm-1.8</arg>", "<arg>-target:jvm-11</arg>");
                    }
                }

                writer.write(outputFilePath, template);
            }
        }

        private void createDockerFiles() throws IOException {
            String dockerRoot = "src/main/docker";
            String dockerRootDir = writer.mkdirs(dockerRoot);
            generate("templates/dockerfile-native.ftl", invocation, dockerRootDir + "/Dockerfile.native",
                    "native docker file");
            generate("templates/dockerfile-jvm.ftl", invocation, dockerRootDir + "/Dockerfile.jvm", "jvm docker file");
            generate("templates/dockerfile-fast-jar.ftl", invocation, dockerRootDir + "/Dockerfile.fast-jar",
                    "jvm docker file");
        }

        private void createDockerIgnore() throws IOException {
            String docker = writer.mkdirs("") + ".dockerignore";
            generate("templates/dockerignore.ftl", invocation, docker, "docker ignore");
        }

        private void createGitIgnore() throws IOException {
            String gitignore = writer.mkdirs("") + ".gitignore";
            generate("templates/gitignore.ftl", invocation, gitignore, "git ignore");
        }

        private void createApplicationConfig() throws IOException {
            String meta = "src/main/resources";
            String file = writer.mkdirs(meta) + "/application.properties";
            if (!writer.exists(file)) {
                writer.write(file, "# Configuration file" + System.lineSeparator() + "# key = value");
            }
        }
    }
}
