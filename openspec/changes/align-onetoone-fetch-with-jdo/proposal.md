## Why

`ReplacePersistentWithManyToOneAnnotation` still has an inferred-owning-one-to-one
fallback that declares `FetchType.EAGER` whenever a bare reference (no `@Persistent`
at all) turns out to be the owning side of a bidirectional one-to-one. It was added
as a workaround for an undocumented EclipseLink deletion behaviour, not because any
JDO source ever asked for eager loading there.

An audit of every genuine bidirectional one-to-one in a large consumer codebase
(Estatio) found exactly 7 pairs — all the same self-referencing `previous`/`next`
history-chain pattern (`TaxRate`, `IndexBase`, `LeaseTerm`, `TenantAdministrationRecord`,
`Agreement`, `ProjectBudget`, `ProjectForecast`) — and in every one of them JDO declares
no `defaultFetchGroup` on either side. Per JDO's own default-fetch-group semantics, a
reference field with no such declaration is lazy. So the recipe's EAGER fallback isn't
preserving JDO intent; it's manufacturing eagerness that never existed in the source.

The consumer already hand-patched all 7 generated fields back to `LAZY` directly in
their JPA output. That patch is not durable: the next time the recipe regenerates JPA
source from JDO, the fallback reintroduces `FetchType.EAGER` and silently reverts the
fix. The fix belongs in the recipe, not downstream.

## What Changes

- Remove the `owningSideOfBidirectionalOneToOne && sourceAnnotationIfAny.isEmpty()`
  EAGER fallback in `ReplacePersistentWithManyToOneAnnotation`. An inferred owning
  one-to-one with no `@Persistent` metadata falls through to the same `LAZY` default
  every other unannotated to-one reference already gets.
- **BREAKING**: any consumer relying on the old EAGER default for a bare inferred
  owning one-to-one will now get `LAZY` on regeneration. Consumers with existing
  hand-patches (like Estatio's) become no-ops and can be deleted.
- Explicit `defaultFetchGroup` metadata continues to take precedence in all cases —
  unaffected by this change.

## Capabilities

### Modified Capabilities
- `reference-fetch-preservation`: the "Inferred owning one-to-one provider exception
  is explicit" requirement currently mandates `FetchType.EAGER` for a bare inferred
  owning one-to-one as a documented EclipseLink-deletion exception. That exception is
  removed; a bare inferred owning one-to-one now follows the same JDO-default-fetch-
  group truth table as an ordinary reference (no `@Persistent` → `LAZY`).

## Impact

- Code: `src/main/java/com/ecpnv/openrewrite/jdo2jpa/ReplacePersistentWithManyToOneAnnotation.java`
  (the `.orElse(...)` fallback around the eager-fetch decision) and its recipe tests
  (`ReplacePersistentWithManyToOneAnnotationTest.java`) covering the bare
  inferred-owning-one-to-one case.
- Consumers: any project regenerating JPA source from JDO via this recipe will see
  every bare inferred owning one-to-one flip from `EAGER` to `LAZY` on next run.
  Consumers should audit call sites that read such a reference outside an active
  persistence context/transaction (detached DTOs, REST serializers, post-close
  background work) before regenerating, since those will now trigger lazy-loading
  where they previously got an already-loaded value for free.
- Validation risk: the original EclipseLink "deletion workaround" this fallback
  encoded was never documented with a reproducing test or ticket. The consumer's own
  hand-patch of the same 7 fields to `LAZY` has been running without incident, which
  is encouraging but not conclusive. After adopting this recipe change, consumers
  should re-run their JDO-vs-JPA regression suite (e.g. a DB-diff/comparison tool)
  specifically covering *removal/deletion* of entities that own a one-to-one, to
  confirm the EclipseLink behaviour the fallback guarded against does not resurface.
