> **DRAFT.** Parked from finding 7 in `JDO-JPA-EQUIVALENCE-REVIEW-PI-CONSOLIDATED.md`.
> Promote this draft to the sole active OpenSpec change before implementation.

## Why

Generated relationship mappings apply a broad default cascade set that includes `REFRESH` and `DETACH`.
JDO reference refresh does not necessarily traverse the same graph, so refreshing one JPA owner can reload many related objects, discard pending changes, and issue additional queries.
Existing passing integration tests cover final outcomes but do not establish equivalent pending-change or traversal semantics.

## What Changes

- Review the cascade set where it is actually declared. There is no `Constants.DEFAULT_CASCADE`;
  `Constants.java` holds only `CASCADE_TYPE_FULL` (the FQN of `javax.persistence.CascadeType`). The
  cascade set is a declarative recipe *parameter*, passed identically twice in
  `datanucleus-jdo-to-jpa-eclipselink.yml:159` (to `ReplacePersistentWithManyToOneAnnotation`) and
  `:161` (to `ReplacePersistentWithOneToManyAnnotation`), both under `v2x.Persistent`.
- Decide whether the fix is a narrower YAML parameter value, or per-relationship derivation inside
  the two Java recipes — the latter needs the recipes to consult JDO metadata rather than emit a
  fixed string, which is a larger change than editing the parameter.
- Derive cascades from JDO ownership and lifecycle metadata instead of applying `REFRESH` and `DETACH` universally.
- Retain `PERSIST` and `MERGE` only where their broad use is justified by existing JDO behaviour.
- Require explicit opt-in for relationships that genuinely need refresh or detach propagation.
- Add recipe tests for each supported metadata combination.
- Add cross-ORM tests around production refresh call sites, including `InvoiceAbstract_remove` and mallcomm turnover scenarios.

## Scope

Cascade annotation generation belongs in `jdo2jpa`.
Prod supplies call-site integration tests and documents any relationships that require explicit refresh propagation.
Shared business methods should not branch on the active ORM.

## Validation

- Refreshing an owner has the same intended effect on related managed objects under both ORMs.
- Pending changes on unrelated or non-cascaded objects are not unexpectedly discarded.
- Query-count approval tests show the intended graph traversal.
- Existing persistence and deletion scenarios continue to pass with the narrowed cascade set.

## Open Questions

- Which current relationships deliberately depend on cascaded `REFRESH` or `DETACH`?
- Should the generic default cascade set be empty, limited to `PERSIST` and `MERGE`, or derived entirely per relationship?
