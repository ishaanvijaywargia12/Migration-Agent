package io.migrationagent.eval;

/**
 * One entry from {@code benchmark/manifest.yaml}'s {@code repos:} list.
 * Only the fields the eval harness actually needs to run something are
 * parsed here — the YAML's {@code verified_*} sections are
 * human-readable run history, not programmatically consumed.
 */
public record BenchmarkRepo(String name, String repoUrl, String commitSha) {
}
