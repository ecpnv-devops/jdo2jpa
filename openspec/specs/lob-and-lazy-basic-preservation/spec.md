# lob-and-lazy-basic-preservation Specification

## Purpose
Define how explicit JDO BLOB/CLOB classification and scalar default-fetch-group exclusion become JPA LOB and lazy-basic metadata across composed migration recipes.

## Requirements

### Requirement: JDO BLOB and CLOB metadata becomes JPA Lob metadata
The recipe SHALL add `@Lob` to persistent field-access and property-access declarations whose JDO `@Column` explicitly declares `jdbcType = "BLOB"` or `jdbcType = "CLOB"`.
The recipe SHALL preserve an existing `@Lob` without duplication and SHALL make the decision before removing JDO-only column attributes.

#### Scenario: Binary field declares BLOB
- **WHEN** a persistent `byte[]` field has JDO `@Column(jdbcType = "BLOB")`
- **THEN** the generated JPA field declares `@Lob`

#### Scenario: Character property declares CLOB
- **WHEN** a persistent getter has JDO `@Column(jdbcType = "CLOB")`
- **THEN** the generated JPA property declares `@Lob`

#### Scenario: Lob annotation already exists
- **WHEN** a declaration with BLOB or CLOB metadata already has JPA `@Lob`
- **THEN** the generated declaration contains exactly one `@Lob`

#### Scenario: SQL type alone resembles a LOB
- **WHEN** a column declares `sqlType = "BLOB"` or a large length but does not declare JDO `jdbcType = "BLOB"` or `"CLOB"`
- **THEN** this capability does not infer `@Lob` from that information alone

### Requirement: Explicit scalar default-fetch-group exclusion becomes lazy basic metadata
The recipe SHALL translate `defaultFetchGroup = "false"` on a remaining non-relationship persistent field or property to `@Basic(fetch = FetchType.LAZY)` before removing the JDO attribute.
This requirement applies to LOB and non-LOB JPA basic attributes.

#### Scenario: Binary LOB is excluded from the default fetch group
- **WHEN** a BLOB field declares `@Persistent(defaultFetchGroup = "false")`
- **THEN** the generated field declares both `@Lob` and `@Basic(fetch = FetchType.LAZY)`

#### Scenario: Character LOB is excluded from the default fetch group
- **WHEN** a CLOB field or property declares `@Persistent(defaultFetchGroup = "false")`
- **THEN** the generated declaration declares both `@Lob` and `@Basic(fetch = FetchType.LAZY)`

#### Scenario: Ordinary scalar is excluded from the default fetch group
- **WHEN** a non-LOB basic attribute declares `@Persistent(defaultFetchGroup = "false")`
- **THEN** the generated declaration declares `@Basic(fetch = FetchType.LAZY)` without adding `@Lob`

#### Scenario: Existing Basic has another attribute
- **WHEN** an excluded scalar declaration already has `@Basic(optional = false)`
- **THEN** the generated `@Basic` retains `optional = false` and also declares `fetch = FetchType.LAZY`

#### Scenario: Existing Basic is already lazy
- **WHEN** an excluded scalar declaration already has `@Basic(fetch = FetchType.LAZY)`
- **THEN** the generated declaration contains one unchanged `@Basic` annotation and one fetch attribute

### Requirement: Non-excluded basic attributes retain eager-default behavior
The recipe SHALL NOT add lazy-basic metadata to scalar declarations with `defaultFetchGroup = "true"`, an omitted `defaultFetchGroup`, or no `@Persistent` annotation.
LOB classification SHALL NOT by itself imply lazy loading.

#### Scenario: Scalar explicitly joins the default fetch group
- **WHEN** a scalar declaration has `@Persistent(defaultFetchGroup = "true")`
- **THEN** the recipe does not add `@Basic(fetch = FetchType.LAZY)`

#### Scenario: Persistent scalar omits default-fetch-group metadata
- **WHEN** a scalar declaration has `@Persistent` without `defaultFetchGroup`
- **THEN** the recipe does not add `@Basic(fetch = FetchType.LAZY)`

#### Scenario: BLOB has no fetch-group exclusion
- **WHEN** a BLOB declaration does not explicitly set `defaultFetchGroup = "false"`
- **THEN** the generated declaration has `@Lob` without gaining lazy-basic metadata from its storage type

### Requirement: Association metadata is not translated as Basic
The recipe SHALL NOT add `@Basic` to single-valued relationships, collections, maps, or declarations carrying JPA relationship annotations.
Association fetch and lifecycle metadata SHALL remain controlled by the dedicated relationship recipes.

#### Scenario: Single relationship is excluded from the default fetch group
- **WHEN** a JDO entity reference declares `defaultFetchGroup = "false"`
- **THEN** the relationship recipe emits association fetch metadata and the scalar recipe does not add `@Basic`

#### Scenario: Collection is excluded from the default fetch group
- **WHEN** a JDO collection or map declares `defaultFetchGroup = "false"`
- **THEN** the generated relationship does not receive `@Basic`

#### Scenario: Relationship conversion cannot run
- **WHEN** a declaration still carries a JPA relationship annotation when scalar lazy-basic translation executes
- **THEN** the scalar recipe skips that declaration

### Requirement: LOB and lazy-basic metadata compose without collateral changes
The persistent composite SHALL enforce relationship conversion, scalar lazy-basic translation, and JDO persistent-metadata cleanup as explicit ordered stages using a mechanism robust to parameterless and configured recipe scheduling.
The persistent, column, top-level, and supported consumer compositions SHALL produce the same `@Lob` and lazy-basic outcome for the same source declaration.
The transformation SHALL preserve column names, nullability, converters, domain annotations, unrelated annotation attributes, and valid annotation placement.

#### Scenario: Actual persistent composite enforces stage order in one cycle
- **WHEN** one compilation unit contains an excluded scalar, an excluded to-one relationship, and an excluded collection and the actual `v2x.Persistent` composite executes for one change cycle
- **THEN** relationship conversion completes before scalar translation, only the scalar receives `@Basic(fetch = FetchType.LAZY)`, and cleanup removes all source `defaultFetchGroup` metadata afterward

#### Scenario: Lazy converted attribute has Column metadata
- **WHEN** an excluded scalar attribute also declares column name and nullability
- **THEN** the generated declaration retains the JPA column values and gains the resolved lazy-basic metadata

#### Scenario: Lazy attribute has a converter
- **WHEN** an excluded scalar attribute carries `@Convert`
- **THEN** the converter annotation remains unchanged alongside `@Basic(fetch = FetchType.LAZY)`

#### Scenario: Field and getter forms are composed
- **WHEN** equivalent JDO metadata is placed on a field in one source and a getter in another
- **THEN** each generated annotation is placed on the corresponding persistent declaration

#### Scenario: Generated source is processed again
- **WHEN** the applicable recipe sequence is rerun over generated JPA source
- **THEN** `@Lob`, `@Basic`, and fetch attributes are not removed, changed, or duplicated

#### Scenario: New imports are required
- **WHEN** translation introduces `Lob`, `Basic`, or `FetchType` into a compilation unit
- **THEN** the generated source contains the required imports and no fetch-related unused import

### Requirement: Domain and schema contracts remain unchanged
The transformation SHALL preserve entity Java attribute types and column names and SHALL NOT create or modify Flyway migrations.
Generated annotations SHALL remain compatible with consumer validation against the existing authoritative schema before adoption.

#### Scenario: Binary payload is transformed
- **WHEN** a `byte[]` BLOB attribute gains JPA LOB or lazy-basic metadata
- **THEN** its Java type and mapped column name remain unchanged

#### Scenario: Character payload is transformed
- **WHEN** a `String` CLOB attribute gains lazy-basic metadata
- **THEN** its Java type and mapped column name remain unchanged

#### Scenario: Recipe validation examines schema implications
- **WHEN** corrected LOB metadata changes provider diagnostic DDL or binding behavior
- **THEN** the recipe does not alter Flyway and the difference is recorded for consumer SQL Server compatibility validation
