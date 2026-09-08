## 1. Characterize the Current Stage Interaction

- [ ] 1.1 Add a focused regression fixture proving that `v2x.optional` currently removes `fetch = FetchType.LAZY` from a generated `@ManyToOne` while leaving eager fetch unchanged.
- [ ] 1.2 Add or identify composed-pipeline fixtures that distinguish `v2x.Persistent` output from the output after optional migrations.
- [ ] 1.3 Record the current inferred owning one-to-one cases where no `@Persistent` metadata causes `fetch` to be omitted.
- [ ] 1.4 Confirm that the Estatio finding-8 baseline contains 453 implicit generated to-one mappings at the recorded consumer commit.

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
- [ ] 3.3 Update inverse one-to-one composite fixtures to assert explicit fetch consistent with their JDO source metadata.
- [ ] 3.4 Add interaction fixtures combining fetch selection with dependent and non-dependent cascades, nullable and non-nullable columns, join-column names, and unrelated leading annotations.
- [ ] 3.5 Add optional-composite fixtures proving that both explicit lazy and explicit eager mappings survive unchanged.
- [ ] 3.6 Add a consumer-order composition fixture proving that relationship conversion followed by optional migrations retains the resolved fetch value.
- [ ] 3.7 Add rerun coverage proving that generated JPA receives no duplicate or changed fetch attribute and no unused fetch import.
- [ ] 3.8 Rerun the finding-2 dependency and multiple-inverse-relationship fixtures to guard relationship lifecycle and cardinality behaviour.

## 4. Verify the Recipe Repository

- [ ] 4.1 Run the focused many-to-one, one-to-many, one-to-one, and optional-composite test classes.
- [ ] 4.2 Run the complete jdo2jpa test suite and resolve any unrelated output changes introduced by the fetch correction.
- [ ] 4.3 Build and install the candidate snapshot locally and record its version for consumer validation.
- [ ] 4.4 Inspect the recipe descriptor and generated recipe metadata to confirm the removed optional stage is absent from the published bundle.

## 5. Validate a Detached Estatio Regeneration

- [ ] 5.1 Create a detached Estatio worktree at a recorded `prod` commit, point `jdo2jpa.version` at the local snapshot, and run the supported rewrite command without committing consumer files.
- [ ] 5.2 Inventory generated `@ManyToOne` and `@OneToOne` annotations and confirm that no generated to-one mapping omits `fetch`.
- [ ] 5.3 Deliberately remove the finding-3 frozen-baseline artifacts in the detached worktree, without enabling baseline creation, update, or refreeze, and confirm that exactly the 453 reviewed implicit-fetch violations disappear with no replacement violations.
- [ ] 5.4 Classify the generated diff into ordinary many-to-one lazy additions, inferred owning one-to-one eager additions, and explicit JDO eager or lazy preservation, rejecting unrelated mapping changes.
- [ ] 5.5 Check representative default-fetch-group true, false, omitted, bare-reference, inferred owning one-to-one, dependent-reference, and inverse one-to-one mappings in the generated output.
- [ ] 5.6 Record the Estatio recipe-version upgrade, architecture-baseline removal, static-weaving, graph-loading, and query-count work as a consumer handoff rather than modifying Estatio from this repository.

## 6. Prepare Consumer-Facing Release Evidence

- [ ] 6.1 Update recipe documentation to state the fetch truth table, the inferred owning one-to-one exception, and the fact that JPA lazy to-one loading remains a provider hint.
- [ ] 6.2 Prepare release notes identifying the implicit-eager to explicit-lazy generated-source change as breaking and describing the Estatio validation evidence.
- [ ] 6.3 Run the repository's release verification checks and record the candidate artifact version and test summary for the approved publication workflow.
