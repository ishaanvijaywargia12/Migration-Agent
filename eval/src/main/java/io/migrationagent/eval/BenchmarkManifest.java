package io.migrationagent.eval;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Loads {@code benchmark/manifest.yaml}. Deliberately tolerant of unknown
 * fields — the YAML's {@code verified_baseline}/{@code verified_rewrite}/
 * {@code verified_migrate}/{@code notes}/etc. sections are human-readable
 * run history maintained by hand, not something this loader needs to model
 * just to find a repo's URL and commit.
 */
public record BenchmarkManifest(List<BenchmarkRepo> repos) {

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static BenchmarkManifest load(Path yamlFile) throws IOException {
        return MAPPER.readValue(yamlFile.toFile(), BenchmarkManifest.class);
    }

    public BenchmarkRepo get(String repoName) {
        return repos.stream()
                .filter(r -> r.name().equals(repoName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No repo named '" + repoName + "' in the manifest"));
    }
}
