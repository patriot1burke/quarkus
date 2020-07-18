package io.quarkus.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import io.quarkus.cli.core.ExitCodes;
import io.quarkus.dependencies.Extension;
import io.quarkus.devtools.commands.RemoveExtensions;
import io.quarkus.devtools.commands.data.QuarkusCommandOutcome;
import io.quarkus.devtools.project.QuarkusProject;
import io.quarkus.platform.descriptor.QuarkusPlatformDescriptor;
import io.quarkus.platform.tools.config.QuarkusPlatformConfig;
import picocli.CommandLine;

@CommandLine.Command(name = "remove", aliases = "rm", mixinStandardHelpOptions = true)
public class Remove implements Callable<Integer> {

    @CommandLine.Parameters(arity = "1", paramLabel = "EXTENSION", description = "extension to remove")
    String extension;

    @Override
    public Integer call() {
        final QuarkusPlatformDescriptor platformDescr = QuarkusPlatformConfig.getGlobalDefault().getPlatformDescriptor();
        try {
            Path projectDirectory = Paths.get(System.getProperty("user.dir"));
            RemoveExtensions project = new RemoveExtensions(QuarkusProject.resolveExistingProject(projectDirectory,
                    platformDescr)).extensions(Collections.singleton(extension));
            QuarkusCommandOutcome result = project.execute();
            return result.isSuccess() ? ExitCodes.SUCCESS : ExitCodes.FAILURE;
        } catch (Exception e) {
            System.out.println("Unable to remove extension matching:" + e.getMessage());
            return ExitCodes.FAILURE;
        }
    }

    private boolean findExtension(String name, List<Extension> extensions) {
        for (Extension ext : extensions) {
            if (ext.getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

}
