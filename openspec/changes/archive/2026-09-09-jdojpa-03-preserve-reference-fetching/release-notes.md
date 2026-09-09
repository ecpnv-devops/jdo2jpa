# Draft GitHub Release Notes

## Preserve JDO reference fetch intent explicitly

Generated JPA to-one mappings now retain an explicit fetch strategy throughout the supported recipe composition.
Ordinary JDO references become `fetch = FetchType.LAZY` unless `defaultFetchGroup = "true"` explicitly opts them into eager loading.
Explicit `defaultFetchGroup = "false"` and an omitted `defaultFetchGroup` both remain lazy.
Named JDO fetch-group membership does not change the global JPA mapping.

This is a breaking generated-source change for consumers that previously ran `v2x.optional`.
That optional stage broadly removed `FetchType.LAZY` from every matching generated `@ManyToOne` in accepted entity compilation units, causing those mappings to fall back to JPA's eager default.
The stripping stage has been removed, so regenerated mappings now preserve the lazy strategy selected from JDO metadata.

An inferred owning `@OneToOne` without `@Persistent` metadata continues to use the existing EclipseLink deletion workaround, but now expresses it explicitly as `fetch = FetchType.EAGER` rather than relying on JPA's default.
Explicit JDO default-fetch-group metadata takes precedence over this fallback.

The recipe guarantees emitted mapping metadata only.
JPA to-one lazy loading remains a provider hint, and EclipseLink behavior depends on consumer configuration such as static weaving.

## Estatio evidence and handoff

The candidate is validated by regenerating pre-change and candidate output from the same pinned Estatio `prod` commit so the recipe-only delta can be isolated.
Estatio's finding-3 architecture baseline contains 453 implicit to-one mappings and supplies structural evidence only.
Provider-level graph-loading, query-count, cycle, refresh, and detached-access behavior remains an Estatio responsibility.

After release, Estatio should upgrade the jdo2jpa version, regenerate its JPA branch, declare an explicit fetch strategy for the hand-written `BackgroundCommandsOrchestration.parentCommand` mapping, remove the finding-3 frozen baseline deliberately, retain static weaving, and run representative EclipseLink graph-loading and query-count tests before accepting the generated change.
