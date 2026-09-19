## 1. Recipe change

- [x] 1.1 In `ReplacePersistentWithManyToOneAnnotation.java`, change the `eagerFetch` fallback (currently `.orElse(owningSideOfBidirectionalOneToOne && sourceAnnotationIfAny.isEmpty())`) to `.orElse(false)`, so a bare inferred owning one-to-one falls through to the same `LAZY` default as any other unannotated to-one reference.
- [x] 1.2 Update the comment above the fallback to remove the reference to the EclipseLink-deletion-workaround exception, and note that explicit `defaultFetchGroup` metadata still takes precedence.
- [x] 1.3 Check whether `owningSideOfBidirectionalOneToOne` is still read anywhere else in the file for a purpose other than this fallback (e.g. choosing `@OneToOne` vs `@ManyToOne`); if its only remaining use is annotation-kind selection, leave it — only the fetch fallback changes. (Confirmed: only other use, at line 224, selects `@OneToOne` vs `@ManyToOne` — left unchanged.)

## 2. Test updates

- [x] 2.1 Rename/rewrite `ReplacePersistentWithManyToOneAnnotationIsolatedTest#inferredOwningOneToOneGetsExplicitEagerFetch` (around line 145) to assert `fetch = FetchType.LAZY` for a bare inferred owning one-to-one, and rename it to reflect the new expectation (e.g. `inferredOwningOneToOneGetsExplicitLazyFetch`).
- [x] 2.2 Confirm `inferredOwningOneToOneHonoursExplicitDefaultFetchGroup` (around line 374) still passes unchanged — it covers the explicit-`defaultFetchGroup` cases, which this change doesn't touch.
- [x] 2.3 Check `ReplacePersistentWithOneToManyAnnotationTest.java:681` (the `@OneToOne(fetch = FetchType.EAGER, ...)` assertion on the inverse side) — confirm it's driven by an explicit `defaultFetchGroup = "true"` in that test's fixture and is unaffected by this change; update only if it turns out to depend on the fallback being removed. (It did depend on the fallback — the `Person.someEntity` field there has only `@Column`, no `@Persistent` — updated expectation to `LAZY`.)
- [x] 2.4 Search the full test suite for any other fixture relying on the old EAGER default for a bare inferred owning one-to-one and update expectations to `LAZY`. (Searched all `FetchType.EAGER` occurrences across the test suite; every remaining one is driven by an explicit `defaultFetchGroup = "true"` — no other fixtures affected.)

## 3. Composed-recipe verification

- [x] 3.1 Re-run the full recipe test suite (unit + any composed/integration recipe tests, e.g. `v2x.optional`, `v2x.Persistent` composites) to confirm the fetch value change propagates correctly and isn't overwritten by a later composed stage. (`mvn test`: 260 run, 0 failures, 0 errors, 2 pre-existing skips — BUILD SUCCESS.)
- [x] 3.2 If a golden-file / snapshot test fixture exists covering a self-referencing one-to-one (previous/next style), regenerate and review its diff. (No golden-file/snapshot fixtures exist in this repo for this pattern — N/A.)

## 4. Documentation

- [x] 4.1 Update any recipe README/docs section describing the EclipseLink deletion workaround or the old EAGER default for inferred owning one-to-ones, if such documentation exists outside the code comment already covered in 1.2. (README only documents the scalar `@Basic` fetch rule; no section describes relationship-level fetch or the EclipseLink workaround — nothing to update.)
- [x] 4.2 Note in the release notes / CHANGELOG (if the repo maintains one) that this is a **BREAKING** change for consumers regenerating JPA source, with guidance to re-run JDO-vs-JPA regression coverage for entity removal/deletion paths involving a one-to-one owner. (Repo has no CHANGELOG file — release notes are authored manually at tag/release time per README's release process. Flagging here for whoever cuts the next release: call out this BREAKING change and the regression-coverage recommendation in that release's notes.)
