package io.migrationagent.cli;

import picocli.CommandLine;

@CommandLine.Command(
        name = "migration-agent",
        mixinStandardHelpOptions = true,
        version = "migration-agent 0.1.0",
        subcommands = {BaselineCommand.class, RewriteCommand.class, MigrateCommand.class, EvaluateCommand.class},
        description = "Verified Spring Boot 2.7 to 3.x migration agent"
)
public final class MigrationAgentCli {

    public static void main(String[] args) {
        CommandLine commandLine = new CommandLine(new MigrationAgentCli())
                .setCaseInsensitiveEnumValuesAllowed(true);
        int exitCode = commandLine.execute(args);
        System.exit(exitCode);
    }
}
