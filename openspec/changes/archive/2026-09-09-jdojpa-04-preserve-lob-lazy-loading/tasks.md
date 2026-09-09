## 1. Characterize Current Metadata Loss

- [x] 1.1 Add focused characterization fixtures proving that the column composite preserves CLOB as `@Lob` but currently removes BLOB metadata without adding `@Lob`.
- [x] 1.2 Add a focused characterization fixture proving that scalar `@Persistent(defaultFetchGroup = "false")` is currently removed without producing `@Basic(fetch = FetchType.LAZY)`.
- [x] 1.3 Characterize the repository's parameterless-versus-configured recipe scheduling and the existing declarative-wrapper convention; do not infer persistent-stage execution order from flat YAML position.
- [x] 1.4 Pin a clean Estatio `prod` commit and record its inventory of four BLOB declarations and two scalar default-fetch-group exclusions.
- [x] 1.5 Record the corresponding Estatio JPA reconciliation commit and the Flyway column definitions for document and Fastnet binary and character payloads.

## 2. Preserve Lazy Basic Intent

- [x] 2.1 Implement a focused recipe that recognizes remaining non-relationship field or property declarations with `defaultFetchGroup = "false"`.
- [x] 2.2 Add `@Basic(fetch = FetchType.LAZY)` when `@Basic` is absent and manage the `Basic` and `FetchType` imports.
- [x] 2.3 Update an existing `@Basic` to lazy while preserving unrelated attributes such as `optional` and avoiding duplicate fetch attributes.
- [x] 2.4 Defensively skip single relationships, collections, maps, and declarations carrying JPA relationship annotations.
- [x] 2.5 Leave scalar declarations with default-fetch-group true, an omitted attribute, or no `@Persistent` unchanged by lazy-basic translation.
- [x] 2.6 Define separate parameterless declarative wrappers for relationship conversion, scalar lazy-basic translation, and JDO persistent-metadata cleanup.
- [x] 2.7 Place the configured many-to-one and one-to-many recipes in the relationship wrapper, the focused scalar recipe in the scalar wrapper, and `defaultFetchGroup` plus empty-annotation removal in the cleanup wrapper.
- [x] 2.8 Wire the three wrappers into `v2x.Persistent` in relationship, scalar, cleanup order without relying on mixed parameterless/configured flat-list ordering.

## 3. Preserve BLOB and CLOB Classification

- [x] 3.1 Extend column conversion so exact JDO `jdbcType = "BLOB"` and `jdbcType = "CLOB"` both add JPA `@Lob` before `jdbcType` is removed.
- [x] 3.2 Support valid field-access and property-access declarations without producing class-level `@Lob` metadata.
- [x] 3.3 Preserve existing `@Lob` annotations without duplication and retain current CLOB output.
- [x] 3.4 Do not infer `@Lob` from Java type, field name, `sqlType`, or column length without explicit JDO BLOB or CLOB `jdbcType` metadata.
- [x] 3.5 Preserve Java types, column names, nullability, converters, domain annotations, and annotation ordering.

## 4. Cover Metadata Truth Tables and Composition

- [x] 4.1 Add isolated BLOB and CLOB fixtures for `byte[]`, `String`, field access, property access, and existing `@Lob`.
- [x] 4.2 Add negative LOB fixtures for `sqlType`-only, large-column, and ordinary binary or character declarations.
- [x] 4.3 Add lazy-basic fixtures for BLOB, CLOB, and ordinary non-LOB scalar attributes with default-fetch-group false.
- [x] 4.4 Add eager/default fixtures for default-fetch-group true, omitted default-fetch-group, no `@Persistent`, and BLOB or CLOB metadata without explicit exclusion.
- [x] 4.5 Add existing-`@Basic` fixtures covering `optional = false`, existing lazy fetch, and conflicting eager fetch.
- [x] 4.6 Add relationship and collection fixtures proving that scalar translation does not add `@Basic` and does not disturb finding-2 or finding-3 metadata.
- [x] 4.7 Add interaction fixtures combining lazy-basic and LOB selection with `@Column`, `@Convert`, unrelated domain annotations, and nullable or named columns.
- [x] 4.8 Add a descriptor-level test asserting the actual relationship, scalar, cleanup wrapper order in `v2x.Persistent`.
- [x] 4.9 Add a one-cycle actual-composite fixture containing scalar, to-one, and collection `defaultFetchGroup = "false"` declarations; prove that only the scalar gains `@Basic(fetch = FetchType.LAZY)`, relationships retain association metadata, and cleanup removes all source fetch-group attributes afterward.
- [x] 4.10 Add column-composite, top-level, and consumer-order fixtures proving that cleanup stages retain the generated annotations.
- [x] 4.11 Add rerun coverage proving that annotations, attributes, and imports are not duplicated, removed, or changed on a second cycle.

## 5. Verify the Recipe Repository

- [x] 5.1 Run the focused scalar-fetch, column, relationship, and composition test classes under the repository's required JDK.
- [x] 5.2 Run the complete jdo2jpa test suite and resolve any unrelated output changes introduced by the metadata correction.
- [x] 5.3 Build and install the candidate snapshot locally and inspect generated recipe metadata for the explicit persistent wrapper stages and column composition order.
- [x] 5.4 Run strict OpenSpec validation and record the recipe test count, skipped tests, JDK, and candidate artifact version.

## 6. Validate Detached Estatio Regenerations

- [x] 6.1 Build independently selectable pre-change and candidate jdo2jpa artifacts using distinct versions or isolated local Maven repositories.
- [x] 6.2 Regenerate two clean detached outputs from the same pinned Estatio `prod` input, using the pre-change artifact for one and the candidate artifact for the other without committing consumer files.
- [x] 6.3 Retain the regeneration commands and a path-level inventory report so the A/B evidence is reproducible from the recorded commits.
- [x] 6.4 Confirm that the A/B semantic delta consists of four BLOB `@Lob` additions and two scalar `@Basic(fetch = FetchType.LAZY)` additions, plus required imports and directly consequent formatting.
- [x] 6.5 Inspect all source BLOB and CLOB declarations and scalar default-fetch-group exclusions to confirm that candidate output follows the metadata truth tables.
- [x] 6.6 Separately compare candidate output with the recorded Estatio JPA tree and account for stream-order, orphan-removal, reference-fetch, and consumer-source differences.
- [x] 6.7 Confirm that candidate Java payload types and column names are unchanged and record any diagnostic-DDL difference without modifying Flyway.
- [x] 6.8 Compile the directly affected generated modules where supported, and record rather than bypass unrelated consumer build blockers.

## 7. Prepare Release and Consumer Handoff

- [x] 7.1 Update recipe documentation with the independent LOB and lazy-basic truth tables, composition order, and provider-hint limitation.
- [x] 7.2 Prepare release notes identifying the generated-source and possible detached-access change as breaking and summarizing reproducible A/B evidence.
- [x] 7.3 Document the Estatio handoff to validate static weaving with both SQL projection and JPA/EclipseLink attribute-state evidence before and after first payload access.
- [x] 7.4 Document null, empty, and large binary and character round trips against the unchanged Flyway-managed SQL Server schema as Estatio adoption criteria.
- [x] 7.5 Document that first access may load a provider fetch group rather than requiring each lazy payload attribute to remain independently unloaded.
- [x] 7.6 Record runtime graph loading, detached access, and any required explicit-fetch transaction boundary changes as Estatio responsibilities without introducing ORM branches.
- [x] 7.7 Run the repository's release verification checks and record the candidate artifact version and test summary for the approved publication workflow.

## Verification Notes

The pre-change characterization confirmed that `v2x.Column` preserved CLOB but erased BLOB classification and that persistent cleanup erased scalar fetch-group exclusion.
The repository descriptor documents parameterless-versus-configured scheduling, so `v2x.Persistent` now uses explicit relationship, scalar-fetch, and cleanup wrappers.
Estatio `prod` commit `4dd98637d572240ff99cb8c998590074c27c839d` is the pinned validation input.
That source contains four explicit BLOB declarations, two scalar default-fetch-group exclusions, and one separate collection exclusion.
Estatio JPA commit `907c723889d3306352c2e50cfbbae3df6406d949` is the informational reconciliation reference.
The authoritative Flyway migrations map the four binary columns to SQL Server `image` and `DocumentAbstract.clob_chars` to SQL Server `text`.

Focused scalar, column, descriptor, and composition verification passed 31 tests after the final eager/default fixture was added; relationship coverage also passed within the full suite.
The repository release verification `mvn clean verify` passed under JDK 21 with 217 tests, no failures, and 2 skipped tests.
The candidate implementation is commit `9865a92b29cb25518b917c6841d79be2a8cc2080` at normal version `1.2.2-SNAPSHOT`; independent Estatio validation used `1.2.2-lob-pre-SNAPSHOT` and `1.2.2-lob-candidate-SNAPSHOT`.
The published candidate descriptor contains the relationship, scalar-fetch, and cleanup wrappers and the combined BLOB/CLOB rule.

Both detached Estatio regenerations completed successfully from the same pinned input.
The A/B delta contains exactly three Java files and no non-Java files.
It adds four `@Lob` annotations and two `@Basic(fetch = FetchType.LAZY)` annotations, with only required imports and directly consequent annotation formatting besides those semantic additions.
All Java payload types and mapped column names are unchanged, and rewrite generation produced no DDL or Flyway difference.
The separate JPA reconciliation confirms that its three affected files lack the four new BLOB `@Lob` declarations and the two new lazy-basic declarations; broader differences remain attributable to approved stream-order, orphan-removal, reference-fetch changes and consumer-source drift.
Direct document-module compilation remains blocked by unrelated missing command-log classes, while direct Fastnet compilation remains blocked by unrelated missing QueryDSL `Q` classes.
No consumer source or Flyway migration was changed to bypass those blockers.
Detailed release and Estatio runtime handoff evidence is recorded in `release-notes.md`.
