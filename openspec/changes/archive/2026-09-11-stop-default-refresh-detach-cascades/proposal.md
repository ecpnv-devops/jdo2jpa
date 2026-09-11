## Why

The default JDO-to-JPA migration currently adds `CascadeType.REFRESH` and `CascadeType.DETACH` to essentially every generated relationship, even though JDO does not imply equivalent graph-wide operations and the consuming Causeway application does not directly detach entities.
A production-scale Mallcomm integration scenario demonstrated that the generated refresh cascade can turn resolution of a lazy association into recursive graph materialization and heap exhaustion, while removing `REFRESH` from the affected collection restores normal completion.

## What Changes

- Change the standard EclipseLink migration recipe defaults so generated relationships receive `CascadeType.PERSIST` and `CascadeType.MERGE`, but not `CascadeType.REFRESH` or `CascadeType.DETACH`.
- Continue adding dependency-derived `CascadeType.REMOVE` and `orphanRemoval = true` according to the existing dependent-relationship rules.
- Preserve the configurable `defaultCascade` recipe parameter so an explicit caller can still request `REFRESH` or `DETACH`; this change removes them from the supplied top-level defaults rather than banning supported JPA cascade values.
- Update recipe examples, generated-output assertions, composition tests, and explicit rerun coverage to establish the new defaults across many-to-one, one-to-one, and one-to-many transformations.
- Build independently selectable pre-change and candidate artifacts and regenerate the same pinned Estatio `prod` input with each, requiring the recipe-only A/B delta to remove only universal `REFRESH` and `DETACH` cascade members and directly consequent formatting.
- Separately reconcile candidate output with the recorded Estatio JPA tree so consumer drift and previously approved recipe changes are not attributed to this change.
- Document the behavioral change, production motivation, validation evidence, explicit opt-in path, and downstream refresh/detach testing responsibilities for projects that regenerate JPA sources.

## Capabilities

### New Capabilities

- `relationship-cascade-defaults`: Defines the cascade operations supplied by the standard JDO-to-JPA recipe and distinguishes universal migration defaults from explicitly configured cascade values and dependency-derived removal behavior.

### Modified Capabilities

- None.

## Impact

The primary change affects `META-INF/rewrite/datanucleus-jdo-to-jpa-eclipselink.yml` and tests whose expected generated annotations currently include `REFRESH` and `DETACH`.
Recipe classes retain their existing configurable cascade API, while examples and documentation will reflect the safer standard default.
Downstream applications must rerun the recipe and review the resulting broad annotation diff; explicit refresh or detach semantics, if genuinely required for a particular relationship, must be introduced deliberately rather than inherited universally.
Recipe validation guarantees generated metadata only and does not establish that every consumer workflow is safe without cascaded refresh or detach.
Estatio remains responsible for rerunning representative refresh and detach workflows after adopting the released recipe.
