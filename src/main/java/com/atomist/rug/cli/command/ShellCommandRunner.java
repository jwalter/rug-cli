package com.atomist.rug.cli.command;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.lang3.SystemUtils;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.LineReaderImpl;
import org.springframework.boot.loader.tools.RunProcess;

import com.atomist.rug.cli.Constants;
import com.atomist.rug.cli.ReloadException;
import com.atomist.rug.cli.command.shell.ArchiveNameCompleter;
import com.atomist.rug.cli.command.shell.CommandInfoCompleter;
import com.atomist.rug.cli.command.shell.FileAndDirectoryNameCompleter;
import com.atomist.rug.cli.command.shell.OperationCompleter;
import com.atomist.rug.cli.command.shell.ShellUtils;
import com.atomist.rug.cli.command.shell.ShortcutCompleter;
import com.atomist.rug.cli.command.shortcuts.Shortcut;
import com.atomist.rug.cli.command.shortcuts.ShortcutRegistry;
import com.atomist.rug.cli.output.Style;
import com.atomist.rug.cli.utils.ArtifactDescriptorUtils;
import com.atomist.rug.cli.utils.StringUtils;
import com.atomist.rug.resolver.ArtifactDescriptor;
import com.atomist.rug.resolver.LocalArtifactDescriptor;

public class ShellCommandRunner extends ReflectiveCommandRunner {

    private CommandInfoRegistry commandRegistry;
    private ShortcutRegistry shortcutRegistry;
    private LineReader reader;

    public ShellCommandRunner(CommandInfoRegistry registry) {
        super(registry);
        this.commandRegistry = registry;
    }

    private void clear() {
        ((LineReaderImpl) reader).clearScreen();
    }

    private void echo(String cmd) {
        log.info(cmd.substring(5));
    }

    private void exit(ArtifactDescriptor artifact, List<ArtifactDescriptor> dependencies) {
        // Exit the current shell
        invokeCommand("exit", new String[] { "exit" }, artifact, dependencies);
        throw new EndOfFileException();
    }

    private String expandHistory(String line) {
        if (reader != null) {
            try {
                return reader.getExpander().expandHistory(reader.getHistory(), line);
            }
            catch (IllegalArgumentException e) {
                log.error(e.getMessage());
            }
        }
        return line;
    }

    private void handleInput(ArtifactDescriptor artifact, List<ArtifactDescriptor> dependencies,
            String line) {
        // Remove confusing whitespace from beginning and end
        line = line.trim();

        // Expand history
        line = expandHistory(line);

        // Remove superfluous rug command
        if (line.startsWith("rug")) {
            line = line.substring(3).trim();
        }

        if (Constants.isReload()) {
            Constants.setReload(false);
            // Only in local mode we ever get to the point of being required to reload the shell
            // session
            if (line.startsWith("shell") || line.startsWith("load") || line.startsWith("repl")) {
                reload(line, artifact, dependencies);
            }
            else {
                reload("shell -l && " + line, artifact, dependencies);
            }
        }

        invokePotentialShortcutCommand(artifact, dependencies, line);
    }

    private void invokeChainedCommands(ArtifactDescriptor artifact,
            List<ArtifactDescriptor> dependencies, String line) {
        // Split commands by && and call them one after the other
        String[] cmds = line.trim().split("&&");
        for (int i = 0; i < cmds.length; i++) {
            String cmd = cmds[i].trim();

            if (cmd.startsWith("shell") || cmd.startsWith("load") || cmd.startsWith("repl")) {
                // For shell reload it is important that we collect all remaining commands
                if (i + 1 < cmds.length) {
                    for (int j = i + 1; j < cmds.length; j++) {
                        cmd = cmd + " && " + cmds[j];
                    }
                }
                reload(cmd, artifact, dependencies);
            }

            if ("exit".equals(cmd) || "quit".equals(cmd) || "q".equals(cmd)) {
                exit(artifact, dependencies);
            }
            else if ("/clear".equals(cmd) || "/cls".equals(cmd)) {
                clear();
            }
            else if (cmd.startsWith("/echo")) {
                echo(cmd);
            }
            else if (cmd.startsWith(Constants.SHELL_ESCAPE)) {
                sh(cmd);
            }
            else {
                String[] args = CommandUtils.splitCommandline(cmd);
                if (invokeCommand(args[0], args, artifact, dependencies) < 0) {
                    break;
                }
            }
        }
    }

    private void invokeCommandInLoop(ArtifactDescriptor artifact,
            List<ArtifactDescriptor> dependencies) {

        this.shortcutRegistry = new ShortcutRegistry();
        this.reader = lineReader();

        String line = null;
        try {
            while (true) {

                try {
                    line = reader.readLine(prompt(artifact));
                }
                catch (UserInterruptException e) {
                    // Ignore Ctrl-C
                    continue;
                }

                // Empty line
                if (line == null || line.length() == 0) {
                    continue;
                }

                // Now ready to handle the input line
                handleInput(artifact, dependencies, line);
            }
        }
        catch (EndOfFileException e) {
            // Handle Ctrl-D
            log.info("Goodbye!");
        }
        finally {
            // Jline creates some resources that need proper shutdown
            ShellUtils.shutdown(reader);
        }
    }

    private void invokePotentialShortcutCommand(ArtifactDescriptor artifact,
            List<ArtifactDescriptor> dependencies, String line) {
        String[] args = CommandUtils.splitCommandline(line);
        CommandLine commandLine = CommandUtils.parseInitialCommandline(args, commandRegistry);
        if (shortcutRegistry != null
                && shortcutRegistry.findShortcut(commandLine.getArgList().get(0)).isPresent()) {

            Shortcut shortcut = shortcutRegistry.findShortcut(commandLine.getArgList().get(0))
                    .get();
            invokeChainedCommands(artifact, dependencies, shortcut.toCommand(commandLine));

        }
        else {
            invokeChainedCommands(artifact, dependencies, line);
        }
    }

    private LineReader lineReader() {
        return ShellUtils.lineReader(ShellUtils.SHELL_HISTORY, new FileAndDirectoryNameCompleter(),
                new OperationCompleter(), new CommandInfoCompleter(commandRegistry),
                new ArchiveNameCompleter(), new ShortcutCompleter(shortcutRegistry));
    }

    private String prompt(ArtifactDescriptor artifact) {
        if (artifact != null && !(artifact.group().equals(Constants.GROUP)
                && artifact.artifact().equals(Constants.RUG_ARTIFACT))) {
            log.info(Style.gray(ArtifactDescriptorUtils.coordinates(artifact)));
        }
        return ShellUtils.DEFAULT_PROMPT;
    }

    private void reload(String line, ArtifactDescriptor artifact,
            List<ArtifactDescriptor> dependencies) {
        // Exit the current shell
        invokeCommand("exit", new String[] { "exit" }, artifact, dependencies);

        String[] args = CommandUtils.splitCommandline(line);
        CommandLine commandLine = CommandUtils.parseInitialCommandline(args, commandRegistry);
        // Only trigger reload if not help is what is requested
        if (!commandLine.hasOption("h") && !commandLine.hasOption("?")) {
            throw new ReloadException(args);
        }
    }

    private void sh(String line) {
        // Remove leading command
        line = line.substring(Constants.SHELL_ESCAPE.length());

        // Expand all env variables
        line = StringUtils.expandEnvironmentVarsAndHomeDir(line);

        // Split multiple commands into several commands that we run one ofter the other
        String[] cmds = line.split("&&");

        // Iterator all commands
        Arrays.stream(cmds).forEach(c -> {
            String[] args = CommandUtils.splitCommandline(c);

            RunProcess process = new RunProcess(SystemUtils.getUserDir(), args[0]);
            try {
                int rc = process.run(true, Arrays.copyOfRange(args, 1, args.length));
                // Change the working directory of this shell
                if (rc == 0 && "cd".equals(args[0])) {
                    if (args.length > 1) {
                        String path = args[1];
                        File p = new File(path);
                        if (p.exists()) {
                            System.setProperty("user.dir", p.getCanonicalPath());
                        }
                        else {
                            File workingDir = new File(SystemUtils.getUserDir(), path);
                            System.setProperty("user.dir", workingDir.getCanonicalPath());
                        }
                    }
                }
            }
            catch (IOException e) {
                log.error(e.getMessage());
            }
        });
    }

    @Override
    protected void artifactChanged(ArtifactDescriptor artifact, CommandInfo info,
            CommandLine commandLine) {
        if (info instanceof ArtifactDescriptorProvider) {
            ArtifactDescriptor newArtifact = null;
            try {
                newArtifact = ((ArtifactDescriptorProvider) info).artifactDescriptor(commandLine);

            }
            catch (CommandException e) {
                // This is ok here as it means that no artifact information was provided on the
                // commandline
                return;
            }
            // Verify that in a shell session we don't support fq operation or archive name
            if (Constants.isShell() && newArtifact != null
                    && !(newArtifact instanceof LocalArtifactDescriptor)) {
                // It is ok to load rug into the runtime; that just means we stay in current scope
                if (newArtifact.group().equals(Constants.GROUP)
                        && newArtifact.artifact().equals(Constants.RUG_ARTIFACT)) {
                    return;
                }
                // It is NOT ok to request a different archive without reloading
                if (!(artifact.group().equals(newArtifact.group())
                        && artifact.artifact().equals(newArtifact.artifact()))) {
                    throw new CommandException(String.format(
                            "Fully-qualified archive or Rug names are not allowed "
                                    + "within a shell session.\nTo load an archive into the shell, run:\n  shell %s:%s",
                            newArtifact.group(), newArtifact.artifact()), info.name());
                }
            }
        }
    }

    @Override
    protected void commandCompleted(int rc, String[] args, CommandInfo info,
            ArtifactDescriptor artifact, List<ArtifactDescriptor> dependencies) {
        // On finishing a single command from the command line, we want to start the shell if the
        // shell command was invoked
        if (rc == 0 && "shell".equals(info.name())) {
            // We might invoke this with some additional commands, primarily from reload but also
            // as part of shortcuts/aliasing
            String line = org.springframework.util.StringUtils.arrayToDelimitedString(args, " ");
            int ix = line.indexOf("&&");
            if (ix > 0 && line.length() > ix + 2) {
                invokePotentialShortcutCommand(artifact, dependencies, line.substring(ix + 2));
            }
            // Now start the loop
            invokeCommandInLoop(artifact, dependencies);
        }
    }
}
