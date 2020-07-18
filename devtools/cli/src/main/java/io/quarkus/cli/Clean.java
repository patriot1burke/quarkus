package io.quarkus.cli;

import java.util.concurrent.Callable;

import io.quarkus.cli.core.ExecuteUtil;
import picocli.CommandLine;

@CommandLine.Command(name = "clean", mixinStandardHelpOptions = true, description = "clean project")
public class Clean implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        return ExecuteUtil.executeBuildsystem(null, "clean", "clean");
    }

}
