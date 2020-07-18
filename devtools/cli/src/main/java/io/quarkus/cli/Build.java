package io.quarkus.cli;

import java.util.concurrent.Callable;

import io.quarkus.cli.core.ExecuteUtil;
import picocli.CommandLine;

@CommandLine.Command(name = "build", mixinStandardHelpOptions = true, description = "build your quarkus project")
public class Build implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        return ExecuteUtil.executeBuildsystem(null, "package", "build");
    }
}
