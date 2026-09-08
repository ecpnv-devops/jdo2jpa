## Why

`AddSortedMethodToStreamMethods` is applied broadly by the default JDO-to-JPA composite and can append natural-order `.sorted()` after ordering established inside a helper, changing the element selected by terminal operations such as `findFirst()`.
The current migration does not identify a collection whose ordering is demonstrably lost, so collection shape alone is not a sufficient reason to invent natural ordering.

## What Changes

- Remove `AddSortedMethodToStreamMethods` from the default JDO-to-JPA composite.
- Preserve source encounter-order semantics for helper-returned streams, flattened pipelines, lists, insertion-ordered sets, sorted sets with custom comparators, ordinary sets, and pipelines containing `.unordered()`.
- Retain the standalone recipe for compatibility, but mark it deprecated and document that it is an explicit opt-in rather than a safe migration default.
- Require any future synthetic ordering to be coupled to a transformation that can demonstrate which source ordering was lost and how to reproduce that same ordering.
- Add composite-level regression tests proving that the default migration does not inject blanket natural sorting.

## Capabilities

### New Capabilities

- `stream-order-preservation`: Defines the evidence required before JDO-to-JPA migration may synthesize stream ordering and requires the default migration to preserve existing encounter-order semantics.

### Modified Capabilities

None.

## Impact

The default recipe registration in `datanucleus-jdo-to-jpa-eclipselink.yml` will stop invoking `AddSortedMethodToStreamMethods`.
The standalone recipe and its direct tests remain available for compatibility but will be deprecated.
Regenerated JPA output for consuming applications will lose previously injected `.sorted()` calls unless another explicit source or recipe supplies them.
Estatio will provide the cross-ORM business regression test separately; no runtime dependency or shared-code ORM conditional is introduced here.
