package io.quarkus.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

import io.quarkus.cli.core.ExitCodes;
import io.quarkus.devtools.commands.AddExtensions;
import io.quarkus.devtools.commands.data.QuarkusCommandException;
import io.quarkus.devtools.commands.data.QuarkusCommandOutcome;
import io.quarkus.devtools.project.QuarkusProject;
import io.quarkus.platform.tools.config.QuarkusPlatformConfig;
import picocli.CommandLine;

@CommandLine.Command(name = "add", mixinStandardHelpOptions = true)
public class Add implements Callable<Integer> {

    @CommandLine.Parameters(arity = "1", paramLabel = "EXTENSION", description = "extension to add to project")
    String[] extensions;

    @Override
    public Integer call() throws Exception {
        Path projectDirectory = Paths.get(System.getProperty("user.dir"));
        return addExtensions(projectDirectory, extensions);
    }

    public static Integer addExtensions(Path projectDirectory, String[] extensions) {
        try {
            QuarkusProject quarkusProject = QuarkusProject.resolveExistingProject(projectDirectory,
                    QuarkusPlatformConfig.getGlobalDefault().getPlatformDescriptor());

            AddExtensions project = new AddExtensions(quarkusProject);
            Set<String> exts = new HashSet<>();
            for (String ext : extensions)
                exts.add(ext);
            project.extensions(exts);

            QuarkusCommandOutcome result = project.execute();
            return result.isSuccess() ? ExitCodes.SUCCESS : ExitCodes.FAILURE;
        } catch (QuarkusCommandException e) {
            System.out.println("Unable to add extension" + (extensions.length > 1 ? "s" : "") + ": " + e.getMessage());
        } catch (IllegalStateException e) {
            System.out.println("No build file found. Will not attempt to add any extensions.");
        }

        return ExitCodes.SUCCESS;
    }
}
