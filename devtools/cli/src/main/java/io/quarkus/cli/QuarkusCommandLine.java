package io.quarkus.cli;

import javax.inject.Inject;

import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import picocli.CommandLine;

@QuarkusMain
@CommandLine.Command(name = "quarkus", aliases = {
        "qs" }, subcommandsRepeatable = true, mixinStandardHelpOptions = true, subcommands = { Build.class,
                Clean.class, Create.class, Add.class, Remove.class, Dev.class, Extensions.class })
public class QuarkusCommandLine implements Runnable, QuarkusApplication {
    @Override
    public void run() {
        CommandLine.usage(this, System.out);

    }

    @Inject
    CommandLine.IFactory factory;

    @Override
    public int run(String... args) throws Exception {
        return new CommandLine(this, factory).execute(args);
    }
}
