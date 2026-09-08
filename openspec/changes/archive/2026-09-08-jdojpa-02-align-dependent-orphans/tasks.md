## 1. Characterize Current Derivation

- [x] 1.1 Add fixtures pinning today's behaviour for {dependent, non-dependent} × {mandatory inverse, optional inverse} on a `@OneToMany`, so the change shows as an intentional diff.
- [x] 1.2 Add a fixture for a collection carrying `@Persistent(mappedBy = …)` **and** `@Element(dependent = "true")`, recording that `@Element` is currently never inspected because `getPersistentAnnotation(...).orElse(elemAnno...)` short-circuits.
- [x] 1.3 Add a fixture for a single-valued `@Persistent(mappedBy = …, dependent = "true")`, recording that the generated `@OneToOne` currently receives neither `CascadeType.REMOVE` nor `orphanRemoval`.
- [x] 1.4 Add a fixture for an explicit `"false"`, confirming it already routes to the non-dependent branch.
- [x] 1.5 Confirm `Accumulator.varMandatoryInverseField` has no consumer other than `mustEnableOrphanRemoval`.

## 2. Resolve Dependency by Cardinality

- [x] 2.1 Add a resolver that takes the field declaration and returns the dependency flag, reading `@Element(dependent)` then `@Persistent(dependentElement)` for a collection, and `@Persistent(dependent)` then `@Persistent(dependentElement)` for a single-valued field.
- [x] 2.2 Have the resolver consult both annotations independently, rather than the single `persistentAnno` produced by `getPersistentAnnotation(...).orElse(elemAnno...)`.
- [x] 2.3 Ignore `@Persistent(dependent)` on a collection; it is JDO's field-value flag, not the element flag.
- [x] 2.4 Apply the documented precedence when both sources are present and disagree, first-in-order wins.
- [x] 2.5 Leave `persistentAnno` itself unchanged for its other uses (`mappedBy`, `table`, column naming); this task adds a resolver, it does not re-plumb annotation lookup generally.

## 3. Derive Orphan Removal From Dependency

- [x] 3.1 Emit `orphanRemoval = true` from the resolved flag, in the same branch that emits `CascadeType.REMOVE`, preserving the template builder's existing comma and attribute ordering.
- [x] 3.2 Apply it to the `@OneToOne` branch as well as `@OneToMany` — do **not** carry over the `hasCollection(...)` guard, which existed only because the mandatory-inverse lookup needed an element type.
- [x] 3.3 Retain `hasCollection(...)` where it is genuinely about cardinality: selecting the annotation name and resolving the element type.
- [x] 3.4 Delete `mustEnableOrphanRemoval` and its call site.
- [x] 3.5 Delete `Accumulator.varMandatoryInverseField` and the two scanner branches that populate it.
- [x] 3.6 Remove the now-inaccurate `// mappedBy points to a mandatory inverse reference…` comment.

## 4. Cover the Metadata Cross-Product

- [x] 4.1 Update the 1.1 fixtures to corrected expectations: dependency alone determines both attributes; inverse optionality determines neither.
- [x] 4.2 Assert the mixed `@Persistent` + `@Element(dependent = "true")` collection now yields both attributes.
- [x] 4.3 Assert `@Element(dependent = "true")` with relationship context but no `@Persistent` yields both attributes.
- [x] 4.4 Assert a contradictory `@Element(dependent = "false")` / `@Persistent(dependentElement = "true")` pair resolves to non-dependent.
- [x] 4.5 Assert `@Persistent(dependent = "true")` on a collection is ignored.
- [x] 4.6 Assert the dependent `@OneToOne` from 1.3 now yields both attributes, and that `dependentElement` on a single-valued field is honoured as a fallback.
- [x] 4.7 Assert `@Element(dependent = "true")` with no `mappedBy`, `table` or `@Join` produces no relationship annotation at all.
- [x] 4.8 Assert a `defaultCascade` containing `CascadeType.REMOVE` on a non-dependent relationship yields `REMOVE` without `orphanRemoval`.
- [x] 4.9 Assert a configured `defaultCascade` is otherwise emitted unchanged in both branches.
- [x] 4.10 Assert idempotence by running the recipe twice over already-generated source.

## 5. Confirm Many-To-One Scope

- [x] 5.1 Add a fixture asserting a dependent `@ManyToOne` receives `CascadeType.REMOVE` and no orphan-removal attribute.
- [x] 5.2 Assert no mutator, callback or listener is generated for a dependent `@ManyToOne`.
- [x] 5.3 Document on `ReplacePersistentWithManyToOneAnnotation` that the omission is deliberate and specific to `@ManyToOne`.

## 6. Verify Behaviour

- [x] 6.1 Run the focused collection, one-to-one and reference tests.
- [x] 6.2 Run the complete jdo2jpa suite and resolve any regressions.
- [x] 6.3 Build and install the snapshot bundle locally (`mvn install`), note the resulting version.
- [x] 6.4 In a detached Estatio worktree at the current `prod` HEAD, point `jdo2jpa.version` at that snapshot and run `./run-rewrite.sh`; record the Estatio commit SHA used.
- [x] 6.5 Diff the regenerated tree against the existing `jpa` branch tree at the corresponding rewrite commit.
- [x] 6.6 Confirm the diff is confined to `orphanRemoval` on `@OneToMany`, with no other annotation moved, added or reordered.
- [x] 6.7 Confirm the five under-deleting collections gain `orphanRemoval = true`: `Project.children`, `Charge.children`, `BreakOptionPeriod.straightliningSchedules`, `BreakOptionPeriod.rentAccrualSchedules`, `SiteTurnoverForMonth.monthlyTurnovers`.
- [x] 6.8 Confirm the two over-deleting collections lose it: `InvoiceAbstract.items`, `ReminderLetterBatch.letters`.
- [x] 6.9 Confirm no generated `@OneToOne` changed, and no mapping changed through the `@Element` spelling — Estatio uses neither, so any such diff indicates an unintended widening.
- [x] 6.10 Inventory any other mapping whose `orphanRemoval` changed, and record it for the release note.

## 7. Release

- [x] 7.1 Draft release notes for the GitHub Release body created by the "Releasing" procedure in `README.md`; this repository has no CHANGELOG, and `gh release create --notes` is the destination.
- [x] 7.2 Separate three causes in those notes so a consumer can attribute a regenerated diff: orphan-removal derivation, `@Element(dependent)` recognition, and dependent `@OneToOne` support.
- [x] 7.3 State how a consumer finds affected mappings in its own source: dependent relationships with an optional inverse now delete on disassociation; non-dependent relationships with a mandatory inverse no longer do.
- [x] 7.4 Hand the `@ManyToOne` and cross-ORM test work to Estatio. This is a **handoff, not an acceptance criterion** — do not block this change on a consumer-repository change existing.

## Verification Notes

Focused collection, one-to-one, and many-to-one coverage passed with 21 tests and no failures.
The complete jdo2jpa suite passed with 198 tests, no failures, and 2 skipped tests.
Version `1.2.2-SNAPSHOT` was built and installed in the local Maven repository.
A detached Estatio worktree at `prod` commit `e596c864fcdbd091143dc3bc3d04520f1b76520e` was regenerated successfully with `./run-rewrite.sh`.
The mapping comparison baseline was Estatio JPA rewrite commit `0c70434386f0b90c59a46db3c72afcf49c05ff5a`, generated from source commit `12553d6d3f2fb093ac5cb11d3b07561c86c272d2`.
Both trees contained 99 `@OneToMany` mappings and 14 `@OneToOne` mappings.
Exactly seven `@OneToMany` mappings changed, and each normalized annotation differed only by `orphanRemoval = true`.
The five expected dependent collections gained orphan removal, and `InvoiceAbstract.items` plus `ReminderLetterBatch.letters` lost it.
No other `@OneToMany` annotation changed, no `@OneToOne` annotation changed, and Estatio contained no `@Element(dependent = ...)` mapping.
Draft GitHub release notes and the non-blocking Estatio handoff are recorded in `release-notes.md`.
