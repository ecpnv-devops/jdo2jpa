# Estatio listener verification scope

## Runtime verification descoped

The user has explicitly removed task 7.3's runtime persist/update/remove and callback-count verification requirement.
These checks were not performed and are not recorded as passed.
Their absence is not an acceptance or rollout gate imposed by this change.
No runtime environment, dataset, or tester is required for the descoped task.

## Required evidence: listener presence

Task 6.6 records candidate6's intended `org.estatio.base.prod.integration.OrmEntityListener` registration on both `TurnoverRollupRun` and `TurnoverRollupRunOrchItem`, without redundant annotation-defined inherited registrations.
The generated-output review, compiled metadata inventory, and successful JPA compilation supply the evidence for this requirement.
They do not establish observed runtime callback behaviour.

Candidate: `com.ecpnv.openrewrite:jdo2jpa:1.2.4-f04-candidate6`.
Source: `f91f8e780a20925436963b27492fe5b04969f943`.
JAR SHA-256: `8b8ab10efc148c63b8e0032ea371fd8f0cf852943f857b06d25633012f5a90ac`.
Evidence: `entity-listener-evidence/f04-target-results.json` and `entity-listener-evidence/affected-class-inventory.json`.
The artifact is locally installed, not remotely published by this workflow.
Acceptance of candidate6 does not extend to later cleanup commit `5f0acf2` or another unvalidated artifact.
The later cleanup code is reverted and deferred, restoring the accepted candidate6 source.
No artifact or consumer-validation gates remain for this change; see `entity-listener-acceptance.json`.

## Separate maintained-source repair

The Estatio application maintainer owns the separate repair of `estatio-integration/orchestration/src/main/java-jpa/org/estatio/integration/orchestration/dom/BackgroundCommandsOrchestration.java`.
That source is excluded from regeneration and remains unchanged in the comparison.
The generated-turnover listener fix does not repair it, and its downstream repair is not a recipe outcome.
