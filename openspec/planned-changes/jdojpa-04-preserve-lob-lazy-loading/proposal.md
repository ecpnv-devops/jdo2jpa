> **DRAFT.** Parked from finding 4 in `JDO-JPA-EQUIVALENCE-REVIEW-PI-CONSOLIDATED.md`.
> Promote this draft to the sole active OpenSpec change before implementation.

## Why

The rewrite preserves JDO CLOB metadata as `@Lob` but does not consistently do the same for BLOB metadata.
It also drops `defaultFetchGroup = "false"` for scalar payload fields, so loading document metadata can eagerly fetch large binary and character content.
EclipseLink static weaving is enabled, making lazy basic-field loading a viable target rather than a purely declarative annotation change.

## What Changes

- Map JDO `jdbcType = "BLOB"` fields to `@Lob` as well as preserving CLOB mappings.
- Map scalar and LOB fields with `defaultFetchGroup = "false"` to `@Basic(fetch = FetchType.LAZY)`.
- Preserve explicit JDO fetch-group intent when column and converter recipes are composed.
- Add recipe tests for binary payloads, character payloads, ordinary eager fields, and combinations with converters.
- Add an EclipseLink integration test that loads document metadata without fetching payload attributes and fetches them on first access.

## Scope

The metadata translation belongs in `jdo2jpa`.
Prod owns the weaving configuration and integration tests that verify the generated annotations have the intended runtime effect.
No ORM conditional belongs in `DocumentAbstract`.

## Validation

- Generated BLOB and CLOB mappings both contain `@Lob`.
- Fields excluded from the JDO default fetch group contain explicit lazy basic mappings.
- SQL or EclipseLink attribute-state assertions demonstrate deferred payload loading.
- Payload round trips remain correct for null, empty, and large values.

## Open Questions

- Does every deployment execute the same static-weaving phase used by the integration test?
- Are any non-LOB scalar fields excluded from the default fetch group and therefore part of the same generic recipe requirement?
