## Why

`ReplacePersistentWithManyToOneAnnotation` derives explicit JPA fetch metadata from JDO reference intent, but the optional composite later removes `FetchType.LAZY` from generated `@ManyToOne` mappings.
The composed migration therefore falls back to JPA's eager default, while inferred owning `@OneToOne` mappings can also omit fetch metadata; Estatio's new architecture gate records 453 implicit to-one mappings, exposing changed graph loading, query volume, cycle risk, and refresh behaviour.

## What Changes

- **BREAKING**: Preserve JDO's lazy default by retaining explicit `FetchType.LAZY` on generated `@ManyToOne` mappings instead of allowing the optional composite to convert them to implicit eager mappings.
- Preserve explicit `defaultFetchGroup = "true"` as `FetchType.EAGER` and explicit `false` as `FetchType.LAZY`.
- Generate explicit `FetchType.LAZY` for ordinary references with omitted `defaultFetchGroup`, including references without `@Persistent` metadata.
- Generate explicit `FetchType.EAGER` for an inferred owning `@OneToOne` that deliberately cannot use the ordinary lazy default under EclipseLink, rather than expressing the exception by omitting `fetch`.
- Preserve explicit fetch metadata through the base, persistent, optional, and consumer-composed recipe paths.
- Add isolated and composite recipe tests covering eager opt-in, lazy false/default/omitted cases, inferred owning one-to-one handling, annotation ordering, imports, and interaction with cascade and join-column generation.
- Validate a locally installed recipe against a detached Estatio worktree and inventory all generated to-one mappings that remain implicit.
- Publish release notes describing the generated-source and runtime-loading impact for consumers.

## Capabilities

### New Capabilities

- `reference-fetch-preservation`: Defines how JDO default-fetch-group intent and the documented EclipseLink one-to-one exception become explicit JPA to-one fetch metadata across composed recipes.

### Modified Capabilities

None.

## Impact

- Primary implementation areas are `ReplacePersistentWithManyToOneAnnotation`, `datanucleus-jdo-to-jpa-eclipselink.yml`, and their isolated and composite tests.
- Generated `@ManyToOne` mappings that currently become implicitly eager will become explicitly lazy unless JDO opted them into the default fetch group.
- Inferred owning `@OneToOne` mappings that require eager loading remain eager, but their intent becomes explicit.
- No runtime ORM branch or `OrmUtil.isJdo()` conditional is introduced.
- Estatio owns EclipseLink query-count and graph-loading tests and the deliberate removal of its finding-3 architecture baseline after consuming the released recipe; those consumer changes are a handoff rather than release inputs for this repository.
- Named JDO fetch plans are outside this static mapping change and remain query-level consumer concerns.
