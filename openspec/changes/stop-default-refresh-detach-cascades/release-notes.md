# Release Notes: Safer Relationship Cascade Defaults

## Breaking generated-source change

The standard `v2x.Persistent.relationships` composition now supplies only `CascadeType.PERSIST` and `CascadeType.MERGE` to generated relationships.
Rerunning the standard recipe therefore removes universally supplied `CascadeType.REFRESH` and `CascadeType.DETACH` from generated `@ManyToOne`, `@OneToOne`, and `@OneToMany` mappings.
This is a breaking generated-source change for consumers that implicitly relied on refresh or detach propagation.

Dependency metadata still adds `CascadeType.REMOVE` and `orphanRemoval = true` where required.
Fetch strategy, cardinality, ownership, columns, and unrelated annotations are unchanged by this change.
Lower-level relationship recipes retain the configurable `defaultCascade` option and emit caller-defined members in caller-defined order, so consumers can deliberately opt into `REFRESH` or `DETACH`.

A production-scale Mallcomm workflow motivated the change after cascaded refresh of a bidirectional lazy collection caused EclipseLink to materialize a large relationship graph and exhaust a 4 GiB heap.
Removing refresh propagation allowed the workflow to complete in approximately 23 seconds.
Generated metadata validation does not establish provider runtime behavior, so application-level refresh and detach checks remain consumer responsibilities.

## Repository validation

A focused 54-test run covers reduced many-to-one, inferred and mapped one-to-one, one-to-many, dependent lifecycle, fetch, inheritance, join-column, explicit configuration, descriptor, composition, and two-cycle behavior, with no failures or skips.
Explicit lower-level tests retain `REFRESH` and `DETACH` and prove caller-defined cascade ordering.
The published descriptor contains exactly two standard `defaultCascade: 'CascadeType.PERSIST, CascadeType.MERGE'` entries and contains neither `REFRESH` nor `DETACH` in the relationship wrapper.

The complete repository release verification passed under JDK 21.0.10 with 221 tests, no failures, and 2 skipped tests.
The command was `mvn clean verify`.
Candidate artifact `1.2.2-cascade-candidate-SNAPSHOT` contains the validated standard defaults.

## Reproducible Estatio evidence

The validation input is clean Estatio `prod` commit `4dd98637d572240ff99cb8c998590074c27c839d`.
The pre-change recipe is jdo2jpa commit `fab2bf971b8d15b4b54356e6461c4ab8849e8648`, installed as `1.2.2-cascade-pre-SNAPSHOT`.
The candidate implementation is jdo2jpa commit `5807a0777a19d7b0df362b3f4ab36d251a0dc34d`, installed from its validated implementation tree as `1.2.2-cascade-candidate-SNAPSHOT`.
Estatio JPA commit `907c723889d3306352c2e50cfbbae3df6406d949` is an informational reconciliation reference only.

Both artifacts were applied independently in detached worktrees with:

[source,shell]
----
./run-rewrite.sh -Djdo2jpa.version=1.2.2-cascade-pre-SNAPSHOT
./run-rewrite.sh -Djdo2jpa.version=1.2.2-cascade-candidate-SNAPSHOT
----

Both regeneration commands completed successfully.
The same-input A/B comparison changes 304 Java files and no non-Java files.
The complete path inventory is retained in `estatio-ab-inventory.txt`.

The changed mappings comprise 458 `@ManyToOne`, 14 `@OneToOne`, and 97 `@OneToMany` annotations, for 569 relationships in total.
All 569 lose `CascadeType.REFRESH` and `CascadeType.DETACH` while retaining `CascadeType.PERSIST` and `CascadeType.MERGE`.
The 94 dependency-derived `CascadeType.REMOVE` members and 93 `orphanRemoval = true` attributes remain unchanged.
After removing the two intended cascade members and whitespace from output A, every changed Java file is byte-equivalent to output B.
This proves that fetch strategy, cardinality, ownership, column metadata, and unrelated annotations are unchanged in the acceptance delta.

The representative `MonthlyTurnover.dailyTurnovers` Mallcomm collection remains lazy, mapped by `monthlyTurnover`, dependent with `REMOVE`, and orphan-removing; only universal `REFRESH` and `DETACH` are removed.
Other inspected Mallcomm bidirectional lazy collections show the same constrained delta.

The separate candidate-to-JPA reconciliation contains broad consumer drift and is not an acceptance comparison.
It includes consumer commits after the pinned source plus the previously approved stream-order, dependency, reference-fetch, and LOB metadata changes.
On common Java paths, the candidate contains the expected reduced cascades and newer explicit fetch, dependency, LOB, and lazy-basic metadata, while stream-order counts and other consumer edits also differ independently.

A direct compile attempt for affected Mallcomm modules used `mvnd -pl :estatio-mallcomm-mallcommproxy,:estatio-mallcomm-turnover -am -DskipTests install`.
Compilation stopped in the unrelated upstream `estatio-base-settings` module because generated QueryDSL type `QApplicationSettingForEstatio` was unavailable.
The blocker was recorded rather than bypassed, while both full rewrite regenerations themselves completed successfully.

## Estatio adoption handoff

Estatio should upgrade the recipe version, regenerate, and review the generated cascade diff.
It must rerun the representative Mallcomm workflow with EclipseLink and verify that the lazy collection no longer triggers graph-wide refresh loading or heap exhaustion.
It must also identify workflows that intentionally refresh or detach graph roots and test their required propagation after adoption.
Any relationship that genuinely requires `REFRESH` or `DETACH` should declare that behavior intentionally through lower-level recipe configuration or reviewed post-migration customization.
Recipe tests and generated annotations prove metadata structure only and do not substitute for these runtime checks.
