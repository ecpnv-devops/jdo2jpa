## Context

`reference-fetch-preservation` (archived, `openspec/specs/reference-fetch-preservation/spec.md`)
defines how `ReplacePersistentWithManyToOneAnnotation` resolves `FetchType` for every
generated to-one JPA mapping. Its "Inferred owning one-to-one provider exception"
requirement carves out one deliberate departure from the JDO-default-fetch-group truth
table: a bare reference (no `@Persistent` at all) that turns out to be the owning side
of a bidirectional one-to-one gets `FetchType.EAGER` instead of the `LAZY` every other
unannotated reference gets. The code comment attributes this to "an existing EclipseLink
deletion workaround," but no reproducing test, ticket, or design note documents what
that workaround actually guards against.

A downstream consumer (Estatio) audited every genuine bidirectional one-to-one in their
JDO codebase and found exactly 7 — all the same self-referencing `previous`/`next`
history-chain shape — and none of them declare `defaultFetchGroup` on either side, i.e.
JDO's own semantics say lazy. The consumer hand-patched all 7 generated fields to
`LAZY` in their JPA output and has been running that way without incident. That patch
sits downstream of code generation, so it is silently undone the next time the recipe
runs.

## Goals / Non-Goals

**Goals:**
- Make the recipe's `@OneToOne` fetch decision for a bare inferred owning reference
  follow the same JDO-default-fetch-group truth table as every other to-one reference,
  removing the special-cased EAGER fallback.
- Keep the fix at the recipe level so it survives regeneration, replacing the need for
  any downstream hand-patch.
- Preserve all other fetch-resolution behavior in `reference-fetch-preservation`
  untouched (explicit `defaultFetchGroup` still wins in every case).

**Non-Goals:**
- Re-deriving or re-validating whatever EclipseLink cascade-delete behavior originally
  motivated the EAGER fallback — that's a downstream consumer verification step (see
  proposal Impact / Migration Plan below), not something this recipe change can prove
  on its own since it has no EclipseLink runtime to test against.
- Changing fetch resolution for the *inverse* (`mappedBy`) side of a one-to-one, which
  already defaults to `LAZY` in `ReplacePersistentWithOneToManyAnnotation` and is
  unaffected by this change.
- Changing any `@ManyToOne` fetch resolution — that path already correctly follows
  `defaultFetchGroup` and isn't part of this fallback.

## Decisions

**Remove the fallback rather than gate it behind a recipe option.** Considered making
the EAGER-on-bare-inferred-one-to-one behavior opt-in via a recipe parameter, so
existing consumers could keep the old behavior without a code change. Rejected: the
audit found zero cases in a real, large codebase where JDO actually intended eager
loading here, and the fallback's own stated justification (undocumented EclipseLink
workaround) has already been safely bypassed downstream via a hand-patch. Adding a
flag preserves a default that's wrong for everyone rather than fixing it once.

**Treat this as a `MODIFIED Requirement`, not a new capability.** The existing
`reference-fetch-preservation` spec already owns to-one fetch resolution end-to-end;
this change narrows one of its requirements rather than introducing new behavior
surface.

## Risks / Trade-offs

- [Risk] The EclipseLink deletion workaround the fallback encoded may still be real,
  just untested/undocumented, and could resurface as a runtime failure (e.g. an
  orphaned row or failed cascade) once these references become lazy → Mitigation:
  flag this explicitly in the proposal's Impact section; recommend consumers run their
  JDO-vs-JPA regression/DB-diff suite specifically covering *removal* of entities that
  own a one-to-one before adopting. The consumer that inspired this change already has
  an existing hand-patch running in production-like regression without incident, which
  is corroborating (not conclusive) evidence.
- [Trade-off] Any consumer currently relying on the EAGER default (knowingly or not)
  gets a behavior change on next regeneration with no opt-out. Mitigation: called out
  as **BREAKING** in the proposal; the fix is one line to revert locally
  (re-add `FetchType.EAGER` on the specific field) if a consumer hits a real problem.

## Migration Plan

- No data migration — this only changes generated Java source (fetch strategy on
  `@OneToOne` mappings), not persisted schema or data.
- Consumers adopt by bumping the recipe version and regenerating; any existing
  downstream hand-patches matching the old EAGER-to-LAZY correction become no-ops and
  can be deleted.
- Rollback: pin to the prior recipe version, or reapply `FetchType.EAGER` by hand on
  the affected fields, same as before this change existed.

## Open Questions

- Is there a way to positively confirm (rather than infer from absence-of-incident)
  that the EclipseLink deletion workaround is obsolete? Out of scope for this recipe
  repo since it has no EclipseLink integration test harness — left to consuming
  projects' own regression suites.
