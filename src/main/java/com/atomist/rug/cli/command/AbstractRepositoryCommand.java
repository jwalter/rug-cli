package com.atomist.rug.cli.command;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.io.FileUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;

import com.atomist.project.archive.Rugs;
import com.atomist.rug.cli.Constants;
import com.atomist.rug.cli.command.annotation.Command;
import com.atomist.rug.cli.command.annotation.Option;
import com.atomist.rug.cli.output.ProgressReportingOperationRunner;
import com.atomist.rug.cli.output.Style;
import com.atomist.rug.cli.settings.SettingsReader;
import com.atomist.rug.cli.tree.ArtifactSourceTreeCreator;
import com.atomist.rug.cli.tree.LogVisitor;
import com.atomist.rug.cli.utils.CommandLineOptions;
import com.atomist.rug.resolver.ArtifactDescriptor;
import com.atomist.rug.resolver.ArtifactDescriptorFactory;
import com.atomist.rug.resolver.deployer.AbstractMavenBasedDeployer;
import com.atomist.rug.resolver.deployer.DefaultDeployerEventListener;
import com.atomist.rug.resolver.deployer.Deployer;
import com.atomist.rug.resolver.git.RepositoryDetails;
import com.atomist.rug.resolver.git.RepositoryDetailsReader;
import com.atomist.rug.resolver.manifest.Manifest;
import com.atomist.rug.resolver.project.GitInfo;
import com.atomist.rug.resolver.project.SimpleGitInfo;
import com.atomist.source.ArtifactSource;
import com.atomist.source.FileArtifact;

public abstract class AbstractRepositoryCommand extends AbstractAnnotationBasedCommand {

    @Command
    public void run(Rugs operationsAndHandlers, ArtifactDescriptor artifact, ArtifactSource source,
            @Option("archive-group") String archiveGroup,
            @Option("archive-artifact") String archiveArtifact,
            @Option("archive-version") String archiveVersion, CommandLine commandLine)
            throws IOException {

        artifact = ArtifactDescriptorFactory.copyFrom(artifact, archiveGroup, archiveArtifact,
                archiveVersion);

        String zipFileName = artifact.artifact() + "-" + artifact.version() + "."
                + artifact.extension().toString().toLowerCase();
        File projectRoot = CommandUtils.getRequiredWorkingDirectory();
        File archive = new File(projectRoot,
                Constants.ATOMIST_ROOT + File.separator + "target" + File.separator + zipFileName);

        prepareTargetDirectory(archive);

        Deployer deployer = new RepositoryCommandMavenDeployer(commandLine, projectRoot);
        deployer.deploy(operationsAndHandlers, source, artifact, projectRoot);
    }

    protected abstract void doWithRepositorySession(RepositorySystem system,
            RepositorySystemSession session, ArtifactSource source, Manifest manifest, Artifact zip,
            Artifact pom, Artifact metadata, CommandLine commandLine);

    protected void printTree(ArtifactSource source) {
        if (CommandLineOptions.hasOption("V")) {
            log.newline();
            log.info(Style.cyan(Constants.DIVIDER) + " " + Style.bold("Contents"));
            ArtifactSourceTreeCreator.visitTree(source, new LogVisitor(log));
        }
    }

    private void prepareTargetDirectory(File zipFile) {
        FileUtils.deleteQuietly(zipFile.getParentFile());
        if (!zipFile.getParentFile().exists()) {
            zipFile.getParentFile().mkdirs();
        }
    }

    private class ReportingDeployerEventListener extends DefaultDeployerEventListener {

        @Override
        public void metadataFileGenerated(FileArtifact file) {
            log.info("  Created %s", file.path());

        }
    }

    private class RepositoryCommandMavenDeployer extends AbstractMavenBasedDeployer {

        private CommandLine commandLine;
        private File projectRoot;

        public RepositoryCommandMavenDeployer(CommandLine commandLine, File projectRoot) {
            super(SettingsReader.read().getLocalRepository().path());
            this.commandLine = commandLine;
            this.projectRoot = projectRoot;
            registerEventListener(new ReportingDeployerEventListener());
        }

        @Override
        protected void doWithRepositorySession(RepositorySystem system,
                RepositorySystemSession session, ArtifactSource source, Manifest manifest,
                Artifact zip, Artifact pom, Artifact metadata) {
            AbstractRepositoryCommand.this.doWithRepositorySession(system, session, source,
                    manifest, zip, pom, metadata, commandLine);
        }

        @Override
        protected GitInfo getGitInfo() {
            try {
                Optional<RepositoryDetails> repositoryDetails = RepositoryDetailsReader
                        .read(projectRoot);
                if (repositoryDetails.isPresent()) {
                    return new SimpleGitInfo(repositoryDetails.get().repo(),
                            repositoryDetails.get().branch(), repositoryDetails.get().sha());
                }
            }
            catch (IOException e) {

            }
            return null;
        }

        @Override
        protected ArtifactSource generateMetadata(Rugs operationsAndHandlers,
                ArtifactDescriptor artifact, ArtifactSource source, Manifest manifest) {
            return new ProgressReportingOperationRunner<ArtifactSource>(
                    "Generating archive metadata").run(indicator -> {
                        return super.generateMetadata(operationsAndHandlers, artifact, source,
                                manifest);
                    });
        }
    }
}
