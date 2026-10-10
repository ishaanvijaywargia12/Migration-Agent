| Repo | Ablation | Baseline OK | Recipe Exit | Files Changed | Resolved | Final Tests (total/failed/errored) | Tests Preserved | Iterations | Model Calls | Tokens (in/out) | Wall Clock (s) | Rule Fixes | Final Status |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| eladmin | OPENREWRITE_ONLY | true (0 tests) | 0 | 166 | false | 0/0/0 | true | 0 | 0 | 0/0 | 171 | 0 | n/a |
| spring-petclinic | OPENREWRITE_ONLY | true (41 tests) | 0 | 22 | true | 41/0/0 | true | 0 | 0 | 0/0 | 789 | 0 | n/a |
| eladmin | HYBRID | true (0 tests) | 0 | 166 | false | 0/0/0 | true | 10 | 16 | 19709/1306 | 781 | 0 | unresolved |
| spring-petclinic | HYBRID | true (41 tests) | 0 | 23 | true | 41/0/0 | true | 0 | 0 | 0/0 | 826 | 0 | n/a |
| eladmin | LLM_ONLY | true (0 tests) | n/a | n/a | false | 0/0/0 | true | 10 | 8 | 17754/2347 | 667 | 0 | unresolved |
| eladmin | HYBRID_NO_TRIAGE | true (0 tests) | 0 | 166 | false | 0/0/0 | true | 10 | 7 | 13021/961 | 871 | 0 | unresolved |
| eladmin | HYBRID_NO_CASCADE | true (0 tests) | 0 | 166 | false | 0/0/0 | true | 10 | 60 | 94735/8571 | 1049 | 0 | budget_exhausted |
| spring-petclinic | LLM_ONLY | true (41 tests) | n/a | n/a | false | 41/0/0 | true | 0 | 0 | 0/0 | 35 | 0 | success |
