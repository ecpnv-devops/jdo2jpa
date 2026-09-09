## 1. Characterize Current Metadata Loss

- [ ] 1.1 Add focused characterization fixtures proving that the column composite preserves CLOB as `@Lob` but currently removes BLOB metadata without adding `@Lob`.
- [ ] 1.2 Add a focused characterization fixture proving that scalar `@Persistent(defaultFetchGroup = "false")` is currently removed without producing `@Basic(fetch = FetchType.LAZY)`.
- [ ] 1.3 Confirm recipe ordering: association conversion precedes persistent-attribute cleanup, and LOB detection precedes JDO column-attribute removal.
- [ ] 1.4 Pin a clean Estatio `prod` commit and record its inventory of four BLOB declarations and two scalar default-fetch-group exclusions.
- [ ] 1.5 Record the corresponding Estatio JPA reconciliation commit and the Flyway column definitions for document and Fastnet binary and character payloads.

## 2. Preserve Lazy Basic Intent

- [ ] 2.1 Implement a focused recipe that recognizes remaining non-relationship field or property declarations with `defaultFetchGroup = "false"`.
- [ ] 2.2 Add `@Basic(fetch = FetchType.LAZY)` when `@Basic` is absent and manage the `Basic` and `FetchType` imports.
- [ ] 2.3 Update an existing `@Basic` to lazy while preserving unrelated attributes such as `optional` and avoiding duplicate fetch attributes.
- [ ] 2.4 Defensively skip single relationships, collections, maps, and declarations carrying JPA relationship annotations.
- [ ] 2.5 Leave scalar declarations with default-fetch-group true, an omitted attribute, or no `@Persistent` unchanged by lazy-basic translation.
- [ ] 2.6 Wire the recipe after association conversion and before generic `Persistent.defaultFetchGroup` removal in `v2x.Persistent`.

## 3. Preserve BLOB and CLOB Classification

- [ ] 3.1 Extend column conversion so exact JDO `jdbcType = "BLOB"` and `jdbcType = "CLOB"` both add JPA `@Lob` before `jdbcType` is removed.
- [ ] 3.2 Support valid field-access and property-access declarations without producing class-level `@Lob` metadata.
- [ ] 3.3 Preserve existing `@Lob` annotations without duplication and retain current CLOB output.
- [ ] 3.4 Do not infer `@Lob` from Java type, field name, `sqlType`, or column length without explicit JDO BLOB or CLOB `jdbcType` metadata.
- [ ] 3.5 Preserve Java types, column names, nullability, converters, domain annotations, and annotation ordering.

## 4. Cover Metadata Truth Tables and Composition

- [ ] 4.1 Add isolated BLOB and CLOB fixtures for `byte[]`, `String`, field access, property access, and existing `@Lob`.
- [ ] 4.2 Add negative LOB fixtures for `sqlType`-only, large-column, and ordinary binary or character declarations.
- [ ] 4.3 Add lazy-basic fixtures for BLOB, CLOB, and ordinary non-LOB scalar attributes with default-fetch-group false.
- [ ] 4.4 Add eager/default fixtures for default-fetch-group true, omitted default-fetch-group, no `@Persistent`, and BLOB or CLOB metadata without explicit exclusion.
- [ ] 4.5 Add existing-`@Basic` fixtures covering `optional = false`, existing lazy fetch, and conflicting eager fetch.
- [ ] 4.6 Add relationship and collection fixtures proving that scalar translation does not add `@Basic` and does not disturb finding-2 or finding-3 metadata.
- [ ] 4.7 Add interaction fixtures combining lazy-basic and LOB selection with `@Column`, `@Convert`, unrelated domain annotations, and nullable or named columns.
- [ ] 4.8 Add persistent-composite, column-composite, top-level, and consumer-order fixtures proving that cleanup stages retain the generated annotations.
- [ ] 4.9 Add rerun coverage proving that annotations, attributes, and imports are not duplicated, removed, or changed on a second cycle.

## 5. Verify the Recipe Repository

- [ ] 5.1 Run the focused scalar-fetch, column, relationship, and composition test classes under the repository's required JDK.
- [ ] 5.2 Run the complete jdo2jpa test suite and resolve any unrelated output changes introduced by the metadata correction.
- [ ] 5.3 Build and install the candidate snapshot locally and inspect generated recipe metadata for the new persistent and column composition order.
- [ ] 5.4 Run strict OpenSpec validation and record the recipe test count, skipped tests, JDK, and candidate artifact version.

## 6. Validate Detached Estatio Regenerations

- [ ] 6.1 Build independently selectable pre-change and candidate jdo2jpa artifacts using distinct versions or isolated local Maven repositories.
- [ ] 6.2 Regenerate two clean detached outputs from the same pinned Estatio `prod` input, using the pre-change artifact for one and the candidate artifact for the other without committing consumer files.
- [ ] 6.3 Retain the regeneration commands and a path-level inventory report so the A/B evidence is reproducible from the recorded commits.
- [ ] 6.4 Confirm that the A/B semantic delta consists of four BLOB `@Lob` additions and two scalar `@Basic(fetch = FetchType.LAZY)` additions, plus required imports and directly consequent formatting.
- [ ] 6.5 Inspect all source BLOB and CLOB declarations and scalar default-fetch-group exclusions to confirm that candidate output follows the metadata truth tables.
- [ ] 6.6 Separately compare candidate output with the recorded Estatio JPA tree and account for stream-order, orphan-removal, reference-fetch, and consumer-source differences.
- [ ] 6.7 Confirm that candidate Java payload types and column names are unchanged and record any diagnostic-DDL difference without modifying Flyway.
- [ ] 6.8 Compile the directly affected generated modules where supported, and record rather than bypass unrelated consumer build blockers.

## 7. Prepare Release and Consumer Handoff

- [ ] 7.1 Update recipe documentation with the independent LOB and lazy-basic truth tables, composition order, and provider-hint limitation.
- [ ] 7.2 Prepare release notes identifying the generated-source and possible detached-access change as breaking and summarizing reproducible A/B evidence.
- [ ] 7.3 Document the Estatio handoff to validate static weaving with both SQL projection and JPA/EclipseLink attribute-state evidence before and after first payload access.
- [ ] 7.4 Document null, empty, and large binary and character round trips against the unchanged Flyway-managed SQL Server schema as Estatio adoption criteria.
- [ ] 7.5 Document that first access may load a provider fetch group rather than requiring each lazy payload attribute to remain independently unloaded.
- [ ] 7.6 Record runtime graph loading, detached access, and any required explicit-fetch transaction boundary changes as Estatio responsibilities without introducing ORM branches.
- [ ] 7.7 Run the repository's release verification checks and record the candidate artifact version and test summary for the approved publication workflow.
