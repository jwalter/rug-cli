package com.atomist.rug.cli.command.shell;

import java.net.URI;

import org.apache.commons.cli.CommandLine;

import com.atomist.rug.cli.Log;
import com.atomist.rug.cli.command.AbstractCommand;
import com.atomist.rug.cli.command.CommandUtils;
import com.atomist.rug.cli.command.ServiceLoadingCommandInfoRegistry;
import com.atomist.rug.cli.command.utils.CommandHelpFormatter;
import com.atomist.rug.resolver.ArtifactDescriptor;

public class HelpCommand extends AbstractCommand {

    private static final Log log = new Log(HelpCommand.class);

    @Override
    protected void run(URI[] uri, ArtifactDescriptor artifact, CommandLine commandLine) {
        log.info(new CommandHelpFormatter().printHelp(new ServiceLoadingCommandInfoRegistry(),
                CommandUtils.options()));
    }
}
