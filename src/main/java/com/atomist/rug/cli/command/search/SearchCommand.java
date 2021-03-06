package com.atomist.rug.cli.command.search;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import com.atomist.rug.cli.Constants;
import com.atomist.rug.cli.command.AbstractAnnotationBasedCommand;
import com.atomist.rug.cli.command.annotation.Argument;
import com.atomist.rug.cli.command.annotation.Command;
import com.atomist.rug.cli.command.annotation.Option;
import com.atomist.rug.cli.command.search.SearchOperations.Archive;
import com.atomist.rug.cli.command.search.SearchOperations.Operation;
import com.atomist.rug.cli.output.ProgressReportingOperationRunner;
import com.atomist.rug.cli.output.Style;
import com.atomist.rug.cli.settings.Settings;
import com.fasterxml.jackson.databind.ObjectMapper;

@SuppressWarnings("unused")
public class SearchCommand extends AbstractAnnotationBasedCommand {

    public static final List<String> CATALOG_URL = Arrays
            .asList("https://api.atomist.com/catalog/operation/search");
    public static final String CATALOG_URL_KEY = "catalog-service-urls";

    private ObjectMapper mapper = new ObjectMapper();

    @Command
    public void run(Settings settings, @Argument(index = 1) String search,
            @Option("tag") Properties tags, @Option("type") String type,
            @Option("operations") boolean showOps) {

        Map<String, List<Operation>> operations = new ProgressReportingOperationRunner<Map<String, List<Operation>>>(
                "Searching catalogs").run(indicator -> {
                    List<Operation> results = getCatalogServiceUrls(settings).stream().map(u -> {
                        indicator.report("  Searching " + u);
                        return new SearchOperations().collectResults(u, search, type, tags,
                                settings);
                    }).flatMap(List::stream).collect(Collectors.toList());

                    return results.stream().collect(Collectors.groupingBy(o -> o.archive().key()));
                });

        log.newline();
        log.info(
                Style.cyan(Constants.DIVIDER) + " "
                        + Style.bold(
                                "Remote Archives")
                        + (operations.keySet().size() > 0 ? " (" + operations.keySet().size() + " "
                                + com.atomist.rug.cli.utils.StringUtils.puralize("archive",
                                        operations.keySet())
                                + " found)" : ""));

        if (operations.isEmpty()) {
            log.info(Style.yellow("  No matching archives found"));
            log.newline();
        }
        else {
            operations.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey))
                    .forEach(a -> printArchive(a.getValue(), showOps));
            if (Constants.isShell()) {
                log.info("\nFor more information on specific archive version, run:\n"
                        + "  shell ARCHIVE -a VERSION\nfollowed by:\n  describe archive");
            }
            else {
                log.info("\nFor more information on specific archive version, run:\n"
                        + "  %sdescribe archive ARCHIVE -a VERSION", Constants.command());
            }
        }
    }

    private void printArchive(List<Operation> operations, boolean showOps) {
        Operation op = operations.get(0);
        Archive archive = op.archive();
        log.info("  %s %s(%s)", Style.yellow("%s:%s", archive.group(), archive.artifact()),
                (archive.scope() != null ? Style.gray("[" + archive.scope() + "]") + " " : ""),
                archive.version().value());
        if (showOps) {
            printOperations(operations, "generator", "Generators");
            printOperations(operations, "editor", "Editors");
            printOperations(operations, "executor", "Executors");
        }
    }

    private void printOperations(List<Operation> operations, String kind, String label) {
        Collection<Operation> ops = operations.stream().filter(o -> o.type().equals(kind))
                .sorted(Comparator.comparing(Operation::name)).collect(Collectors.toList());
        Operation last = ops.stream().reduce((a, b) -> b).orElse(null);
        if (!ops.isEmpty()) {
            log.info("    %s", Style.bold(label));
            ops.forEach(o -> {
                if (o.equals(last)) {
                    log.info("    %s%s", Constants.LAST_TREE_NODE, o.name());
                }
                else {
                    log.info("    %s%s", Constants.TREE_NODE, o.name());
                }
            });
        }
    }

    private List<String> getCatalogServiceUrls(Settings settings) {
        return settings.getConfigValue(CATALOG_URL_KEY, CATALOG_URL);
    }
}