package io.quarkus.cli;

import java.util.concurrent.Callable;

import io.quarkus.cli.core.ExecuteUtil;
import picocli.CommandLine;

@CommandLine.Command(name = "dev", mixinStandardHelpOptions = true, description = "Execute project in live coding dev mode")
public class Dev implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        return ExecuteUtil.executeBuildsystem(null, "quarkus:dev", "quarkusDev");
    }
}
