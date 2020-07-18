package io.quarkus.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

import io.quarkus.cli.core.ExitCodes;
import io.quarkus.cli.core.ExtensionFormat;
import io.quarkus.devtools.commands.ListExtensions;
import io.quarkus.devtools.commands.data.QuarkusCommandException;
import io.quarkus.devtools.project.QuarkusProject;
import io.quarkus.platform.tools.config.QuarkusPlatformConfig;
import picocli.CommandLine;

@CommandLine.Command(name = "extensions", aliases = "exts", mixinStandardHelpOptions = true, description = "Display a list of available Quarkus extensions")
public class Extensions implements Callable<Integer> {
    @CommandLine.Option(names = { "-a",
            "--all" }, defaultValue = "false", description = "Display all or just the installable extensions.")
    private boolean all = false;

    @CommandLine.Option(names = { "-f", "--format" }, defaultValue = "name", description = "Select the output format among:\n"
            +
            "'name' - display the name only\n" +
            "'concise' - (display name and description\n" +
            "'full' - (concise format and version related columns.\n")
    private ExtensionFormat format;

    @CommandLine.Option(names = { "-s",
            "--search" }, defaultValue = "*", description = "Search filter on extension list. The format is based on Java Pattern.")
    private String searchPattern;

    @Override
    public Integer call() throws Exception {
        Path projectDirectory = Paths.get(System.getProperty("user.dir"));
        try {

            new ListExtensions(QuarkusProject.resolveExistingProject(projectDirectory,
                    QuarkusPlatformConfig.getGlobalDefault().getPlatformDescriptor()))
                            .all(all)
                            .format(format.name())
                            .search(searchPattern)
                            .info(false)
                            .execute();
        } catch (QuarkusCommandException e) {
            return ExitCodes.FAILURE;
        } catch (IllegalStateException e) {
            System.out.println("You must 'create' a project before list of available extensions can be provided");
            return ExitCodes.FAILURE;
        }
        return ExitCodes.SUCCESS;
    }

}
