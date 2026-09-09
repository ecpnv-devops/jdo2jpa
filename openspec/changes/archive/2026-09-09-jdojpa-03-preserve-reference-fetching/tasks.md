## 1. Characterize the Current Stage Interaction

- [x] 1.1 Add a focused characterization fixture that pins the current broad `v2x.optional` removal of `fetch = FetchType.LAZY` from every matching `@ManyToOne`, including cases without a datastore-identity or `EntityAbstract` constraint, while preserving eager fetch; task 3.6 will update this same fixture to the corrected expectation.
- [x] 1.2 Add or identify composed-pipeline fixtures that distinguish `v2x.Persistent` output from the output after optional migrations.
- [x] 1.3 Record the current inferred owning one-to-one cases where no `@Persistent` metadata causes `fetch` to be omitted.
- [x] 1.4 Pin current clean Estatio `prod` commit `4dd98637d572240ff99cb8c998590074c27c839d` as the validation input and record JPA commit `907c723889d3306352c2e50cfbbae3df6406d949` as the informational reconciliation reference.
- [x] 1.5 Confirm that the finding-3 frozen baseline carried by the pinned `prod` tree contains 453 implicit generated to-one mappings and documents structural policy rather than runtime-loading evidence.

Characterization evidence: `ReplacePersistentWithManyToOneAnnotationTest` exercises `v2x.Persistent`, `ReplacePersistentWithManyToOneAnnotationIsolatedTest` isolates relationship conversion, and `ReferenceFetchCompositionTest` records the subsequent optional-stage result.
The current inferred owning one-to-one output omits fetch only when the owning field has no `@Persistent`; explicit `defaultFetchGroup = "true"` resolves eager, while false or an omitted attribute on an existing `@Persistent` resolves lazy.

## 2. Make Fetch Resolution Explicit

- [x] 2.1 Refactor `ReplacePersistentWithManyToOneAnnotation` so every generated relationship resolves one explicit `FetchType` from relationship shape and JDO metadata.
- [x] 2.2 Preserve `defaultFetchGroup = "true"` as eager and explicit `false` or omitted default-fetch-group metadata as lazy.
- [x] 2.3 Preserve explicit lazy fetch for ordinary reference fields without `@Persistent` metadata.
- [x] 2.4 Emit explicit eager fetch for an inferred owning one-to-one without `@Persistent` metadata instead of omitting the fetch attribute.
- [x] 2.5 Preserve existing cascade, dependency, optionality, join-column, annotation-ordering, and import behaviour while changing fetch resolution.
- [x] 2.6 Remove the `v2x.optional` stage that conditionally strips lazy fetch from `@ManyToOne` mappings, leaving unrelated optional migrations unchanged.

## 3. Cover the Fetch Truth Table

- [x] 3.1 Add isolated many-to-one fixtures for default-fetch-group true, false, omitted, and no-`@Persistent` inputs.
- [x] 3.2 Add isolated inferred owning one-to-one fixtures for default-fetch-group true, false, omitted, and no-`@Persistent` inputs.
- [x] 3.3 Add a fixture containing named `@FetchGroup` metadata and prove that named membership without `defaultFetchGroup = "true"` does not make the global JPA mapping eager.
- [x] 3.4 Update inverse one-to-one composite fixtures to assert explicit fetch consistent with their JDO source metadata.
- [x] 3.5 Add interaction fixtures combining fetch selection with dependent and non-dependent cascades, nullable and non-nullable columns, join-column names, and unrelated leading annotations.
- [x] 3.6 Update the task-1.1 characterization fixture to prove across the same identity and inheritance variants that the corrected optional composite preserves both explicit lazy and explicit eager mappings unchanged.
- [x] 3.7 Add a consumer-order composition fixture proving that relationship conversion followed by optional migrations retains the resolved fetch value.
- [x] 3.8 Add rerun coverage proving that generated JPA receives no duplicate or changed fetch attribute and no unused fetch import.
- [x] 3.9 Rerun the finding-2 dependency and multiple-inverse-relationship fixtures to guard relationship lifecycle and cardinality behaviour.

## 4. Verify the Recipe Repository

- [x] 4.1 Run the focused many-to-one, one-to-many, one-to-one, and optional-composite test classes.
- [x] 4.2 Run the complete jdo2jpa test suite and resolve any unrelated output changes introduced by the fetch correction.
- [x] 4.3 Build and install candidate snapshot `1.2.2-SNAPSHOT` locally and record its version for consumer validation.
- [x] 4.4 Inspect the recipe descriptor and generated recipe metadata to confirm the removed optional stage is absent from the published bundle.

## 5. Validate Detached Estatio Regenerations

- [x] 5.1 Build distinct pre-change and candidate jdo2jpa artifacts that can be selected independently by the Estatio rewrite.
- [x] 5.2 Regenerate two clean detached outputs from the same matched `prod` input commit, using the pre-change artifact for one and the candidate artifact for the other, without committing consumer files.
- [x] 5.3 Compare the pre-change and candidate outputs and require the A/B delta to contain only intended fetch attributes and directly consequent import or formatting changes.
- [x] 5.4 Separately compare candidate output with recorded Estatio JPA reconciliation commit `907c723889d3306352c2e50cfbbae3df6406d949` and explicitly account for already approved stream-order and orphan-removal output plus consumer-source drift.
- [x] 5.5 Inventory candidate `@ManyToOne` and `@OneToOne` annotations and confirm that no generated production to-one mapping omits `fetch`.
- [x] 5.6 Keep the finding-3 frozen baseline during library validation, confirm that every jdo2jpa-generated production to-one mapping now declares fetch and that no new architecture violation is introduced, and record the remaining hand-written `BackgroundCommandsOrchestration.parentCommand` mapping for Estatio to fix before deliberately removing the baseline after adoption.
- [x] 5.7 Check representative default-fetch-group true, false, omitted, named-fetch-group-only, bare-reference, inferred owning one-to-one, dependent-reference, and inverse one-to-one mappings in the candidate output or focused recipe fixtures when Estatio has no representative source declaration.
- [x] 5.8 Record the Estatio recipe-version upgrade, architecture-baseline removal, static-weaving, graph-loading, and query-count work as a consumer handoff rather than modifying Estatio from this repository.

## 6. Prepare Consumer-Facing Release Evidence

- [x] 6.1 Update recipe documentation to state the fetch truth table, the inferred owning one-to-one exception, and the fact that JPA lazy to-one loading remains a provider hint.
- [x] 6.2 Prepare release notes identifying the broad optional-stage scope, the implicit-eager to explicit-lazy generated-source change as breaking, and the separate structural and runtime Estatio validation responsibilities.
- [x] 6.3 Run the repository's release verification checks and record the candidate artifact version and test summary for the approved publication workflow.

## Verification Notes

The current clean Estatio `prod` commit `4dd98637d572240ff99cb8c998590074c27c839d` was pinned as the same-input validation source.
Estatio JPA commit `907c723889d3306352c2e50cfbbae3df6406d949` was recorded as an informational reconciliation reference only.
The pinned prod tree carries the finding-3 frozen baseline with 453 reviewed structural violations.
Pre-change commit `00c1d326cb5017633d5c9f1345848335bb84e6ab` and the candidate were installed sequentially as `1.2.2-SNAPSHOT` and regenerated successfully in separate detached worktrees.
The same-input A/B comparison changed 274 Java files and no non-Java files.
It made 447 generated `@ManyToOne` mappings explicitly lazy and 7 inferred owning `@OneToOne` mappings explicitly eager, with no unrelated relationship-attribute changes.
The candidate inventory contains 462 `@ManyToOne` mappings and 16 `@OneToOne` mappings; every jdo2jpa-generated production mapping declares fetch.
Three implicit annotations remain outside that production-generation contract: an architecture-rule test fixture, a consumer-specific generated test mapping, and the hand-written `BackgroundCommandsOrchestration.parentCommand` JPA mapping.
The hand-written production mapping already appears in the frozen baseline, so static reconciliation found no new architecture violation.
A targeted Estatio architecture-test execution was attempted but the detached generated tree did not compile because of unrelated missing QueryDSL output and pre-existing duplicate `@EntityListeners` test fixtures; no Estatio file was changed to bypass those failures.
Estatio contains no named-fetch-group-only to-one example and no single-reference explicit-false example, so those truth-table rows are covered by focused recipe fixtures.
Representative Estatio output confirmed explicit eager dependent `CodaTransactionFetchOrchestration.codaTransaction`, explicit lazy inverse `ProjectBudget.previous`, ordinary lazy references, and inferred owning eager one-to-one mappings.
Reconciliation with the recorded JPA tree separately identified the expected fetch changes, 85 fewer synthetic `.sorted()` calls, approved dependent collection changes, and broader consumer-source drift.
Draft consumer release notes and the Estatio handoff are recorded in `release-notes.md`.
The repository release check `mvn clean verify` passed with 204 tests, no failures, and 2 skipped tests for candidate `1.2.2-SNAPSHOT`.
