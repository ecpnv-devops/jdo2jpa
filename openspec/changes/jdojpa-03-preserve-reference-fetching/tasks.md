## 1. Characterize the Current Stage Interaction

- [ ] 1.1 Add a focused characterization fixture that pins the current broad `v2x.optional` removal of `fetch = FetchType.LAZY` from every matching `@ManyToOne`, including cases without a datastore-identity or `EntityAbstract` constraint, while preserving eager fetch; task 3.6 will update this same fixture to the corrected expectation.
- [ ] 1.2 Add or identify composed-pipeline fixtures that distinguish `v2x.Persistent` output from the output after optional migrations.
- [ ] 1.3 Record the current inferred owning one-to-one cases where no `@Persistent` metadata causes `fetch` to be omitted.
- [ ] 1.4 Record the Estatio `prod` input, JPA rewrite-baseline, and finding-3 frozen-baseline commits as a matched validation set.
- [ ] 1.5 Confirm that the matched finding-3 frozen baseline contains 453 implicit generated to-one mappings and documents structural policy rather than runtime-loading evidence.

## 2. Make Fetch Resolution Explicit

- [ ] 2.1 Refactor `ReplacePersistentWithManyToOneAnnotation` so every generated relationship resolves one explicit `FetchType` from relationship shape and JDO metadata.
- [ ] 2.2 Preserve `defaultFetchGroup = "true"` as eager and explicit `false` or omitted default-fetch-group metadata as lazy.
- [ ] 2.3 Preserve explicit lazy fetch for ordinary reference fields without `@Persistent` metadata.
- [ ] 2.4 Emit explicit eager fetch for an inferred owning one-to-one without `@Persistent` metadata instead of omitting the fetch attribute.
- [ ] 2.5 Preserve existing cascade, dependency, optionality, join-column, annotation-ordering, and import behaviour while changing fetch resolution.
- [ ] 2.6 Remove the `v2x.optional` stage that conditionally strips lazy fetch from `@ManyToOne` mappings, leaving unrelated optional migrations unchanged.

## 3. Cover the Fetch Truth Table

- [ ] 3.1 Add isolated many-to-one fixtures for default-fetch-group true, false, omitted, and no-`@Persistent` inputs.
- [ ] 3.2 Add isolated inferred owning one-to-one fixtures for default-fetch-group true, false, omitted, and no-`@Persistent` inputs.
- [ ] 3.3 Add a fixture containing named `@FetchGroup` metadata and prove that named membership without `defaultFetchGroup = "true"` does not make the global JPA mapping eager.
- [ ] 3.4 Update inverse one-to-one composite fixtures to assert explicit fetch consistent with their JDO source metadata.
- [ ] 3.5 Add interaction fixtures combining fetch selection with dependent and non-dependent cascades, nullable and non-nullable columns, join-column names, and unrelated leading annotations.
- [ ] 3.6 Update the task-1.1 characterization fixture to prove across the same identity and inheritance variants that the corrected optional composite preserves both explicit lazy and explicit eager mappings unchanged.
- [ ] 3.7 Add a consumer-order composition fixture proving that relationship conversion followed by optional migrations retains the resolved fetch value.
- [ ] 3.8 Add rerun coverage proving that generated JPA receives no duplicate or changed fetch attribute and no unused fetch import.
- [ ] 3.9 Rerun the finding-2 dependency and multiple-inverse-relationship fixtures to guard relationship lifecycle and cardinality behaviour.

## 4. Verify the Recipe Repository

- [ ] 4.1 Run the focused many-to-one, one-to-many, one-to-one, and optional-composite test classes.
- [ ] 4.2 Run the complete jdo2jpa test suite and resolve any unrelated output changes introduced by the fetch correction.
- [ ] 4.3 Build and install the candidate snapshot locally and record its version for consumer validation.
- [ ] 4.4 Inspect the recipe descriptor and generated recipe metadata to confirm the removed optional stage is absent from the published bundle.

## 5. Validate Detached Estatio Regenerations

- [ ] 5.1 Build distinct pre-change and candidate jdo2jpa artifacts that can be selected independently by the Estatio rewrite.
- [ ] 5.2 Regenerate two clean detached outputs from the same matched `prod` input commit, using the pre-change artifact for one and the candidate artifact for the other, without committing consumer files.
- [ ] 5.3 Compare the pre-change and candidate outputs and require the A/B delta to contain only intended fetch attributes and directly consequent import or formatting changes.
- [ ] 5.4 Separately compare candidate output with the matched Estatio JPA rewrite-baseline commit and explicitly account for already approved stream-order and orphan-removal output.
- [ ] 5.5 Inventory candidate `@ManyToOne` and `@OneToOne` annotations and confirm that no generated to-one mapping omits `fetch`.
- [ ] 5.6 Deliberately remove the finding-3 frozen-baseline artifacts in the detached candidate worktree, without enabling baseline creation, update, or refreeze, and confirm that exactly the 453 reviewed structural violations disappear with no replacement violations.
- [ ] 5.7 Check representative default-fetch-group true, false, omitted, named-fetch-group-only, bare-reference, inferred owning one-to-one, dependent-reference, and inverse one-to-one mappings in the candidate output.
- [ ] 5.8 Record the Estatio recipe-version upgrade, architecture-baseline removal, static-weaving, graph-loading, and query-count work as a consumer handoff rather than modifying Estatio from this repository.

## 6. Prepare Consumer-Facing Release Evidence

- [ ] 6.1 Update recipe documentation to state the fetch truth table, the inferred owning one-to-one exception, and the fact that JPA lazy to-one loading remains a provider hint.
- [ ] 6.2 Prepare release notes identifying the broad optional-stage scope, the implicit-eager to explicit-lazy generated-source change as breaking, and the separate structural and runtime Estatio validation responsibilities.
- [ ] 6.3 Run the repository's release verification checks and record the candidate artifact version and test summary for the approved publication workflow.
