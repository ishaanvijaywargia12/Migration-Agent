# Verified Spring Boot 2.7 → 3.x Migration Agent

A CLI tool that migrates a Spring Boot 2.7.x Maven project to Spring Boot 3.x
and **proves** the result instead of just claiming it: every run baselines
the original repo in a Docker sandbox, applies OpenRewrite's official Boot 3
recipe, rebuilds and retests on JDK 17, and — if the recipe alone didn't
leave the repo fully green — runs a hand-written LLM agent loop (no
LangChain, no agent framework) against whatever still fails. Nothing is
reported as "migrated" unless it actually compiles on JDK 17 and the
project's own original tests actually still pass.

Full architecture, threat model, and design rationale: [docs/DESIGN.md](docs/DESIGN.md).
Build commands and phase-by-phase status: [CLAUDE.md](CLAUDE.md).

## Why this exists

Spring Boot 3 requires JDK 17+ and replaced the entire `javax.*` namespace
with `jakarta.*` — a mechanical-but-widespread rename plus a long tail of
genuinely non-mechanical breakage (Hibernate 5→6 semantics, deprecated
Spring Security config, third-party libraries like SpringFox that never
shipped a Boot-3-compatible release at all). OpenRewrite's recipe handles
the mechanical part well. This project exists to answer the harder
question: **how far can a small, cheap, free-tier LLM agent get on the part
OpenRewrite can't touch, and how do you verify its output is trustworthy
rather than just plausible-looking?**

## How it works

1. **Baseline** — clone the target repo at a pinned commit, build and test
   it in an isolated Docker container on its original JDK. This is the
   "before" measurement everything else is compared against.
2. **OpenRewrite recipe** — apply the pinned, verified
   `org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_5` recipe
   (deterministic, no LLM involved), then rebuild/retest on JDK 17.
3. **Deterministic triage** — before ever calling a model, apply a small
   set of harness-coded, unambiguous fixes (e.g. remaining
   `javax.persistence` → `jakarta.persistence` imports the recipe missed).
4. **Agent loop** — if failures remain, a hand-written loop gives an LLM a
   fixed toolset (`read_file`, `search_code`, `propose_patch`, `run_build`,
   `run_tests`, `get_dependency_tree`, `lookup_migration_note`) and lets it
   iterate. Every proposed patch passes through a **guardrail engine**
   before it's ever applied — diff-pattern checks that reject test deletion,
   test weakening, silent exception swallowing, and version downgrades,
   enforced in harness code, never by asking the model nicely.
5. **Model cascade** — tries a cheap/fast free-tier model first and
   escalates to the next tier only when an iteration makes verified zero
   progress (failure count didn't drop). One-directional: never falls back
   once escalated.
6. **Post-success verification** — before reporting success, the final
   state is compared against the *original* baseline: test count must not
   have dropped, skipped count must not have grown.

Every model call, tool call, patch, and build result is written to
`trace.jsonl` — nothing about what the agent did is a black box.

## Build & run

```
./mvnw compile
./mvnw test                   # 117 fast unit tests, no Docker/network needed
```

```
./mvnw -pl cli -am package -DskipTests
java -jar cli/target/migration-agent.jar migrate \
  --repo-url https://github.com/spring-projects/spring-petclinic.git \
  --commit 276880edef4c3d1029865d19d6d28e982b9d4d01
```

Needs Docker running, and a real API key in `.env` for at least one free-tier
provider (Groq, OpenRouter, Gemini) if the agent loop is going to be
exercised — see `.env.example`. **Bring your own key**: `migrate` and
`evaluate` both take `--env-file` / `--providers-config` so you can run this
against your own keys and provider config without touching the committed
defaults.

Full command reference (including `baseline`, `rewrite`, `evaluate`) is in
[CLAUDE.md](CLAUDE.md).

## Real results (not estimated)

Every number below comes from an actual Docker build and, where noted, real
free-tier API calls — recorded in [runs/eval-results.md](runs/eval-results.md)
and [benchmark/manifest.yaml](benchmark/manifest.yaml). Nothing here is
projected or rounded up.

### spring-petclinic (Boot 2.7.3 → 3.5.x)

| Stage | Result |
|---|---|
| Baseline (JDK 8) | Compiled, 41/41 tests passing |
| OpenRewrite recipe | Exit 0, 22 files changed |
| After recipe (JDK 17) | Compiled, **41/41 tests still passing — zero regression** |
| Agent loop needed? | No — recipe alone fully resolved it |

A complete, deterministic-only Boot 2.7.3 → 3.5.x migration with no LLM
involvement at all, and no test regression.

### eladmin (Boot 2.7.18 → 3.5.x, multi-module reactor)

| Stage | Result |
|---|---|
| Baseline (JDK 8) | Compiled, 0 tests run (repo hardcodes Surefire `skip=true` in its own pom — a known limitation, not a tool bug) |
| OpenRewrite recipe | Exit 0, 166 files changed |
| After recipe (JDK 17) | **Did not compile** — 6 failures, all the same root cause: SpringFox (Swagger 2) is incompatible with Spring Framework 6 and was never updated for Boot 3 |
| Agent loop (`HYBRID`, real Groq + OpenRouter calls) | Escalated tier 0 → tier 1 exactly as designed, but exhausted its per-run token budget (200K) on exploration (57 tool calls) before ever proposing a single patch. Recorded honestly as `unresolved`, not retuned until it looked better |

eladmin is the harder benchmark on purpose — it's a genuinely hard migration
(SpringFox has no mechanical fix) rather than a tool limitation, and it's
the only repo so far with a complete 5-mode ablation run:

| Ablation | Resolved | Model calls | Tokens (in/out) | Final status |
|---|---|---|---|---|
| `OPENREWRITE_ONLY` | false | 0 | 0 / 0 | n/a |
| `HYBRID` | false | 16 | 19,709 / 1,306 | unresolved |
| `LLM_ONLY` (no recipe at all) | false | 8 | 17,754 / 2,347 | unresolved |
| `HYBRID_NO_TRIAGE` | false | 7 | 13,021 / 961 | unresolved |
| `HYBRID_NO_CASCADE` (single tier, no escalation) | false | 60 | 94,735 / 8,571 | budget_exhausted |

The ablation isn't there to make the project look good — it's there to
isolate which component (recipe, triage, cascade) is actually doing the
work. On this repo, none of the LLM-involving modes converged on a fix;
that's a real, recorded result about free-tier small-model limits on a
genuinely hard migration, not a bug to hide.

### What's not done yet

- `README` numbers above only use eladmin's full 5-mode ablation matrix —
  spring-petclinic currently only has `OPENREWRITE_ONLY` and `HYBRID`
  recorded; `LLM_ONLY`, `HYBRID_NO_TRIAGE`, and `HYBRID_NO_CASCADE` haven't
  been run for it yet (each costs real Docker time and, for the LLM modes,
  real free-tier API spend).
- Only two benchmark repos so far.
- JaCoCo coverage comparison in `PostSuccessVerifier` is intentionally not
  implemented (no report parser exists for it) rather than faked.

## Project status

Phases 0–6 are implemented and unit-tested (117 tests, zero Docker/network
needed for `./mvnw test`). See [CLAUDE.md](CLAUDE.md) for the detailed,
phase-by-phase history — including every real bug a live run caught and how
it was fixed, not just the final working state.
