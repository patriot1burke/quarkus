package io.quarkus.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import io.quarkus.cli.core.ExitCodes;
import io.quarkus.devtools.commands.CreateProject;
import io.quarkus.devtools.project.BuildTool;
import io.quarkus.devtools.project.codegen.BasicProjectGenerator;
import io.quarkus.platform.tools.config.QuarkusPlatformConfig;
import picocli.CommandLine;

@CommandLine.Command(name = "create", mixinStandardHelpOptions = true, description = "create a new quarkus project in current directory")
public class Create implements Callable<Integer> {

    @CommandLine.Option(names = { "-g", "--groupId" }, paramLabel = "GROUP_ID", description = "groupId for project")
    String groupId = "org.acme";

    @CommandLine.Option(names = { "-a", "--artifactId" }, paramLabel = "ARTIFACT_ID", description = "groupId for project")
    String artifactId = "my-project";

    @CommandLine.Option(names = { "-pv", "--project-version" }, paramLabel = "VERSION", description = "version for project")
    String version = "1.0-SNAPSHOT";

    @CommandLine.Parameters(arity = "0..1", paramLabel = "EXTENSION", description = "extension to add to project")
    String[] extensions;

    @CommandLine.Option(names = { "-b",
            "--buildTool" }, defaultValue = "MAVEN", description = "Build tool of project (MAVEN | GRADLE)")
    BuildTool buildTool = BuildTool.MAVEN;

    @Override
    public Integer call() throws Exception {
        Path projectDirectory = Paths.get(System.getProperty("user.dir"));
        try {

            Files.createDirectories(projectDirectory);

            final Map<String, Object> context = new HashMap<>();
            boolean status = new CreateProject(projectDirectory,
                    QuarkusPlatformConfig.getGlobalDefault().getPlatformDescriptor())
                            .groupId(groupId)
                            .artifactId(artifactId)
                            .version(version)
                            .buildTool(buildTool)
                            .projectTemplate(BasicProjectGenerator.NAME)
                            .doCreateProject(context);

            if (status) {
                System.out.println("Project " + artifactId +
                        " created.");
            } else {
                System.out.println("Failed to create project");
            }
        } catch (IOException e) {
            System.out.println("Project creation failed, " + e.getMessage());
            System.out.println("Is the given path an empty folder?");
            return ExitCodes.FAILURE;
        }

        if (extensions != null && extensions.length > 0) {
            if (Add.addExtensions(projectDirectory, extensions) != ExitCodes.SUCCESS) {
                System.out
                        .println("Failure adding extensions to created project.  You must now add them via the 'add' command.");
                return ExitCodes.FAILURE;
            }
        }

        return ExitCodes.SUCCESS;
    }
}
