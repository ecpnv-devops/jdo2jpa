## Context

The supported `v2x` composite runs `v2x.Persistent` before `v2x.Column`.
The persistent stage first converts relationship metadata, then removes `defaultFetchGroup` from every remaining `@Persistent` and removes empty JDO annotations.
That ordering lets relationship recipes consume association fetch intent, but scalar `defaultFetchGroup = "false"` currently disappears without a JPA equivalent.

JPA basic attributes default to eager loading.
`@Basic(fetch = FetchType.LAZY)` is the standard metadata hint for excluding a basic attribute from initial loading, while EclipseLink requires weaving for deferred basic-attribute fetching.
Estatio enables static weaving, but that configuration and its runtime effect are outside this recipe repository's metadata guarantee.

The column stage currently adds `@Lob` only when a JDO `@Column` contains `jdbcType = "CLOB"`.
It then removes `jdbcType` and `sqlType` before changing the annotation type to JPA `@Column`.
A `jdbcType = "BLOB"` marker is therefore erased without preserving the equivalent JPA LOB classification.

The pinned Estatio source inventory contains four `byte[]` fields with `jdbcType = "BLOB"`.
Only `DocumentAbstract.blobBytes` and `DocumentAbstract.clobChars` declare scalar `defaultFetchGroup = "false"`; the latter already receives `@Lob` through the CLOB rule, while the former loses both BLOB classification and lazy intent.
Other CLOB and BLOB fields remain eager unless their JDO metadata explicitly excludes them from the default fetch group.

Flyway defines the deployed SQL Server schema, including existing `image`/binary and `varchar(max)`/character payload columns.
This change must not alter entity Java types, column names, or Flyway migrations.
Generated DDL remains diagnostic, but corrected `@Lob` metadata can affect provider binding or diagnostic DDL and therefore requires consumer compatibility evidence.

## Goals / Non-Goals

**Goals:**

- Preserve JDO BLOB and CLOB classification as explicit JPA `@Lob` metadata.
- Preserve scalar `defaultFetchGroup = "false"` as explicit `@Basic(fetch = FetchType.LAZY)` metadata.
- Keep relationship fetch conversion under the existing association recipes.
- Preserve eager/default scalar behavior when JDO did not explicitly exclude the attribute.
- Support field and property access, existing `@Basic` attributes, converters, imports, annotation ordering, and reruns.
- Isolate generated-source changes with same-input pre-change/candidate regeneration.
- Define a precise Estatio handoff for static-weaving and SQL Server runtime validation.

**Non-Goals:**

- Guarantee that EclipseLink or another provider honours the JPA lazy-basic hint.
- Change Java payload types, converter implementations, table definitions, column names, or Flyway migrations.
- Make every BLOB or CLOB lazy merely because it is large.
- Translate relationship or collection fetch metadata to `@Basic`.
- Introduce entity graphs, fetch joins, repository changes, or `OrmUtil.isJdo()` branches.
- Commit Estatio integration tests or generated source from this repository.
- Treat generated DDL as authoritative schema evidence.

## Decisions

### Translate only explicit scalar exclusion to lazy basic metadata

A focused recipe will run after single-valued and collection relationship conversion but before the generic removal of `Persistent.defaultFetchGroup`.
It will process remaining persistent field or property declarations with `defaultFetchGroup = "false"` and add or update `@Basic(fetch = FetchType.LAZY)`.

The recipe will defensively skip declarations carrying JPA relationship annotations and declarations classified as collection, map, or entity references.
This preserves findings 2 and 3 as the owners of association lifecycle and fetch semantics even if a relationship recipe cannot transform malformed or incompletely attributed input.

When `@Basic` is absent, the recipe will add it and manage both `Basic` and `FetchType` imports.
When `@Basic` already exists, it will set `fetch` to lazy while retaining unrelated attributes such as `optional`.
When it is already lazy, the output will remain unchanged.
A converter or unrelated leading annotation will remain in place.

`defaultFetchGroup = "true"`, an omitted attribute, and a declaration without `@Persistent` will not cause `@Basic` to be added or changed.
For those scalar cases, JPA's eager basic default matches the source's lack of explicit exclusion.

An alternative is to express the transformation entirely with repeated generic YAML annotation rules.
That approach makes it difficult to distinguish non-relationship attributes, update an existing `@Basic` safely, and import `FetchType` without adding unused imports, so a purpose-built recipe is preferred.

### Preserve both binary and character LOB classification before column cleanup

The column composite will recognize exact JDO `jdbcType = "BLOB"` and `jdbcType = "CLOB"` values before removing `jdbcType`.
Both values will add `@Lob` to valid field-access and property-access declarations.
Existing `@Lob` annotations will not be duplicated.

The current CLOB behavior will remain unchanged apart from shared regression coverage.
The implementation will not infer `@Lob` solely from Java type, field name, `sqlType`, or column length because explicit JDO `jdbcType` is the authoritative source intent for this recipe.
Class-level `@Lob` is not part of the contract because JPA permits `@Lob` on persistent fields or properties, not entity classes.

### Keep LOB classification independent from fetch selection

`jdbcType = "BLOB"` or `"CLOB"` determines `@Lob` only.
`defaultFetchGroup = "false"` determines lazy-basic metadata only.
A LOB without explicit fetch-group exclusion remains eager, and a non-LOB scalar attribute with explicit exclusion becomes lazy.

This separation is required by the current Estatio inventory, where four BLOB fields need `@Lob` but only one of them requests lazy loading, while one lazy character payload already has CLOB metadata.
It also prevents a broad performance policy from being inferred from a storage-type declaration.

### Preserve recipe composition and idempotency

The scalar-fetch recipe must run after relationship conversion and before JDO persistent-attribute cleanup.
The LOB rule must run before JDO column-attribute cleanup.
The top-level `v2x`, the individual persistent and column composites, and the supported consumer sequence must agree on the final annotations.

Focused tests will exercise each recipe independently and in consumer order.
A second recipe cycle will prove that `@Basic`, `@Lob`, and their imports are neither removed nor duplicated.
Tests will cover adjacent `@Column`, `@Convert`, domain annotations, existing `@Basic(optional = false)`, nullable and named columns, and both field and getter declarations.

### Separate structural recipe evidence from runtime provider evidence

The implementation will build distinct pre-change and candidate artifacts and apply each to the same pinned Estatio `prod` input.
The A/B delta must be limited to expected `@Lob` and `@Basic(fetch = FetchType.LAZY)` additions, import changes, and directly consequent formatting.
For the current inventory, the expected semantic changes are four BLOB `@Lob` additions and two scalar lazy-basic additions.

A separate informational comparison will reconcile candidate output with the recorded Estatio JPA tree.
That comparison will explicitly account for stream-order, orphan-removal, reference-fetch, and consumer-source differences rather than attributing them to finding 4.
The pinned `prod` input, pre-change recipe commit, candidate recipe commit, and JPA reconciliation commit will be recorded.

Generated-source inventory is structural metadata evidence only.
It cannot prove deferred SQL, weaving, first-access behavior, detached access, or round-trip correctness.

### Make EclipseLink and SQL Server behavior an explicit Estatio handoff

After consuming the released recipe, Estatio must run with static weaving enabled and load representative binary and character documents through EclipseLink.
The runtime test should use SQL capture and provider/JPA attribute-state inspection to prove that an initial metadata load does not select the payload columns and that first payload access performs the deferred fetch.
The test need not require two lazy payload attributes to remain independently unloaded after one is accessed because provider fetch-group behavior may load them together.

Estatio must also prove null, empty, and large binary and character payload round trips against the Flyway-managed SQL Server schema.
That test verifies compatibility with existing column types without changing schema.
Detached-access expectations must be stated explicitly because a payload that was previously eager can now be unavailable after the persistence context closes.

These tests are consumer adoption criteria, not claims established by recipe unit tests or generated annotations.

## Risks / Trade-offs

- [Risk] EclipseLink may ignore lazy-basic metadata when static weaving is absent or ineffective.
  → Mitigation: describe lazy loading as a hint and require attribute-state plus SQL evidence in Estatio before adoption.
- [Risk] Existing code may read document payloads after detachment because implicit eager loading previously masked that dependency.
  → Mitigation: include detached-access scenarios in the consumer handoff and fix transaction boundaries or explicit fetching in Estatio rather than adding an ORM branch.
- [Risk] `@Lob` can alter EclipseLink binding or diagnostic DDL for existing SQL Server `image`, binary, text, or `varchar(max)` columns.
  → Mitigation: keep Flyway unchanged and require SQL Server round-trip and schema-compatibility evidence downstream.
- [Risk] A broad lazy rule could annotate relationships or all LOBs.
  → Mitigation: drive lazy selection only from explicit scalar `defaultFetchGroup = "false"`, run after association conversion, and add negative relationship and eager-LOB fixtures.
- [Risk] Field and property access can produce duplicate or misplaced annotations.
  → Mitigation: test both declaration forms, existing annotations, import cleanup, annotation ordering, and reruns.
- [Risk] Same-coordinate snapshot replacement can contaminate pre-change/candidate comparison.
  → Mitigation: use distinct artifact versions or isolated local Maven repositories and retain the commands and inventory report.
- [Risk] The recorded JPA tree includes earlier recipe changes and consumer drift.
  → Mitigation: use same-input A/B output as the acceptance comparison and treat JPA-tree reconciliation as separately classified information.

## Migration Plan

First add characterization fixtures for current CLOB preservation, BLOB metadata loss, and scalar default-fetch-group removal.
Then implement focused lazy-basic translation, extend LOB classification to BLOB, and update composite and rerun coverage.
Run the focused and complete jdo2jpa verification suites under the repository's required JDK.
Build independently selectable pre-change and candidate artifacts and perform the same-input Estatio A/B regeneration.
Prepare release notes describing the generated-source breaking change and the provider-runtime handoff.

Rollback consists of restoring the previous CLOB-only column rule and removing the scalar lazy-basic translation from the persistent composite.
Consumers can pin the previous jdo2jpa release if runtime validation exposes unresolved detached-access or SQL Server compatibility problems.

## Open Questions

None for recipe implementation.
The exact Estatio integration-test module and representative SQL Server fixture belong to the consumer handoff and do not change this repository's metadata contract.
