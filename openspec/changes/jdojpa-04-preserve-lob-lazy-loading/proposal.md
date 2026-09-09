## Why

The column recipe recognizes JDO `jdbcType = "CLOB"` as `@Lob` but removes `jdbcType = "BLOB"` without preserving equivalent metadata, while the persistent recipe removes scalar `defaultFetchGroup = "false"` without emitting JPA lazy-basic metadata.
The current Estatio migration therefore loses `@Lob` from four binary fields and makes the two excluded `DocumentAbstract` payload attributes implicitly eager, creating portability and payload-loading risks that the existing generated mappings do not express.

## What Changes

- **BREAKING**: Translate `defaultFetchGroup = "false"` on remaining non-relationship persistent attributes to `@Basic(fetch = FetchType.LAZY)` before removing the JDO attribute.
- Preserve JPA's eager basic default for scalar attributes with `defaultFetchGroup = "true"` or no explicit exclusion.
- Translate both JDO `jdbcType = "BLOB"` and `jdbcType = "CLOB"` column metadata to `@Lob` before removing JDO-only column attributes.
- Support field-access and property-access mappings without adding duplicate annotations or changing unrelated `@Basic` attributes.
- Exclude relationships and collections from basic-field translation so findings 2 and 3 remain authoritative for association lifecycle and fetch metadata.
- Preserve `@Column`, `@Convert`, domain annotations, annotation ordering, and required imports through the persistent, column, and consumer-composed recipe paths.
- Add isolated and composite recipe fixtures covering binary and character LOBs, lazy scalar attributes, eager/default attributes, existing `@Basic` metadata, converters, field/property access, and reruns.
- A/B regenerate the same pinned Estatio `prod` input with pre-change and candidate jdo2jpa artifacts, requiring the recipe-only delta to contain only intended `@Lob`, lazy-basic, import, and directly consequent formatting changes.
- Separately reconcile candidate output with the recorded Estatio JPA tree while accounting for already approved stream-order, orphan-removal, and reference-fetch changes plus consumer-source drift.
- Publish a consumer handoff for EclipseLink static-weaving, deferred-payload SQL, attribute-state, detached-access, and payload round-trip validation.

## Capabilities

### New Capabilities

- `lob-and-lazy-basic-preservation`: Defines how JDO BLOB/CLOB typing and default-fetch-group exclusion become explicit JPA LOB and lazy-basic metadata across composed recipes.

### Modified Capabilities

None.

## Impact

- Primary implementation areas are the persistent and column recipe composition in `datanucleus-jdo-to-jpa-eclipselink.yml`, a focused scalar-metadata recipe if required for safe type and import handling, and their isolated and composite tests.
- On the current Estatio source inventory, four `byte[]` BLOB mappings gain `@Lob`, while `DocumentAbstract.blobBytes` and `DocumentAbstract.clobChars` gain `@Basic(fetch = FetchType.LAZY)`.
- Entity Java types, column names, and the Flyway-managed SQL Server schema remain unchanged.
- Flyway remains authoritative; generated DDL is diagnostic, and Estatio must verify that EclipseLink's corrected metadata remains compatible with the existing `image`/binary and `varchar(max)`/character columns.
- Existing application-level Joda and other attribute converters remain application concerns and SHALL be preserved rather than replaced by this change.
- The recipe guarantees generated metadata only because JPA basic lazy loading is a provider hint.
- Estatio owns runtime proof under static weaving, including initial SQL projection, first-access fetch behavior, detached access, and null, empty, and large payload round trips.
- No `OrmUtil.isJdo()` branch or ORM-specific conditional is introduced into `DocumentAbstract`.
