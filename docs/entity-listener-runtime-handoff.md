# Estatio listener runtime handoff

## Status and ownership

**Rollout acceptance is OPEN.**
No approved non-production runtime, test dataset, or callback observations have been supplied for this verification.
Compilation, recipe tests, and annotation inventories are not substitutes for runtime evidence.
The Estatio application maintainer owns executing and recording these checks, with a named tester still to be assigned.
This external verification does not block completion or archival of an otherwise accepted recipe change.
Library regeneration and comparison gates remain separate and must still be satisfied.

## Candidate and evidence

Candidate coordinates: `com.ecpnv.openrewrite:jdo2jpa:1.2.4-f04-candidate6`.
Source commit: `f91f8e780a20925436963b27492fe5b04969f943`.
JAR SHA-256: `8b8ab10efc148c63b8e0032ea371fd8f0cf852943f857b06d25633012f5a90ac`.
The artifact is installed in the isolated local comparison repository; it has not been published remotely by this workflow.
Refer to `entity-listener-acceptance.json` for pinned inputs, build logs, comparison status, and outstanding library gates.
Refer to `entity-listener-consumer-prerequisites.md` for the explicit consumer configuration and dependency corrections.

## Turnover callbacks

Verify both `TurnoverRollupRun` and `TurnoverRollupRunOrchItem` in `estatio-mallcomm/mallcommturnover`.
Use an approved disposable dataset and confirm the database, application profile, and provider are non-production before making changes.
The generated application must use the final `org.estatio.base.prod.integration.OrmEntityListener` identity.
Check effective XML mappings and default listeners as well as declared and inherited annotations.

For each entity, exercise each applicable persist, update, and remove operation through the application's normal transaction boundary.
Observe the relevant listener and its intended downstream effects using approved instrumentation or existing test hooks.
Record fixture identifiers, transaction boundaries, flush/commit points, observation method, expected callback count, actual callback count, and downstream result.
The expected count for each applicable callback is exactly one per intended lifecycle event, with no duplicate inherited registration.
Record unsupported operations explicitly rather than silently omitting them.
Do not report a passing annotation inspection or a single successful operation as proof of all lifecycle callbacks.

| Entity | Operation | Tester / dataset | Expected | Actual | Result |
| --- | --- | --- | --- | --- | --- |
| TurnoverRollupRun | Persist | Pending | 1 applicable callback | Not observed | Open |
| TurnoverRollupRun | Update | Pending | 1 applicable callback | Not observed | Open |
| TurnoverRollupRun | Remove | Pending | 1 applicable callback | Not observed | Open |
| TurnoverRollupRunOrchItem | Persist | Pending | 1 applicable callback | Not observed | Open |
| TurnoverRollupRunOrchItem | Update | Pending | 1 applicable callback | Not observed | Open |
| TurnoverRollupRunOrchItem | Remove | Pending | 1 applicable callback | Not observed | Open |

## Separate maintained-source repair

The Estatio application maintainer also owns a separate repair and verification task for `estatio-integration/orchestration/src/main/java-jpa/org/estatio/integration/orchestration/dom/BackgroundCommandsOrchestration.java`.
That maintained source is excluded from regeneration and must remain unchanged by this recipe comparison.
Do not infer that generated-turnover listener fixes repair its missing integration.
Inspect its complete effective hierarchy and mappings before adding a listener, then verify the relevant callbacks independently in the same approved non-production environment.
Record that repair's application commit and evidence separately from the recipe artifact.
