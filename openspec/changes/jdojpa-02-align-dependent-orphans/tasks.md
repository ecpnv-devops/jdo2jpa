## 1. Characterize Current Derivation

- [ ] 1.1 Add fixtures pinning today's behaviour for {dependent, non-dependent} × {mandatory inverse, optional inverse} on a `@OneToMany`, so the change shows as an intentional diff.
- [ ] 1.2 Add a fixture for a collection carrying `@Persistent(mappedBy = …)` **and** `@Element(dependent = "true")`, recording that `@Element` is currently never inspected because `getPersistentAnnotation(...).orElse(elemAnno...)` short-circuits.
- [ ] 1.3 Add a fixture for a single-valued `@Persistent(mappedBy = …, dependent = "true")`, recording that the generated `@OneToOne` currently receives neither `CascadeType.REMOVE` nor `orphanRemoval`.
- [ ] 1.4 Add a fixture for an explicit `"false"`, confirming it already routes to the non-dependent branch.
- [ ] 1.5 Confirm `Accumulator.varMandatoryInverseField` has no consumer other than `mustEnableOrphanRemoval`.

## 2. Resolve Dependency by Cardinality

- [ ] 2.1 Add a resolver that takes the field declaration and returns the dependency flag, reading `@Element(dependent)` then `@Persistent(dependentElement)` for a collection, and `@Persistent(dependent)` then `@Persistent(dependentElement)` for a single-valued field.
- [ ] 2.2 Have the resolver consult both annotations independently, rather than the single `persistentAnno` produced by `getPersistentAnnotation(...).orElse(elemAnno...)`.
- [ ] 2.3 Ignore `@Persistent(dependent)` on a collection; it is JDO's field-value flag, not the element flag.
- [ ] 2.4 Apply the documented precedence when both sources are present and disagree, first-in-order wins.
- [ ] 2.5 Leave `persistentAnno` itself unchanged for its other uses (`mappedBy`, `table`, column naming); this task adds a resolver, it does not re-plumb annotation lookup generally.

## 3. Derive Orphan Removal From Dependency

- [ ] 3.1 Emit `orphanRemoval = true` from the resolved flag, in the same branch that emits `CascadeType.REMOVE`, preserving the template builder's existing comma and attribute ordering.
- [ ] 3.2 Apply it to the `@OneToOne` branch as well as `@OneToMany` — do **not** carry over the `hasCollection(...)` guard, which existed only because the mandatory-inverse lookup needed an element type.
- [ ] 3.3 Retain `hasCollection(...)` where it is genuinely about cardinality: selecting the annotation name and resolving the element type.
- [ ] 3.4 Delete `mustEnableOrphanRemoval` and its call site.
- [ ] 3.5 Delete `Accumulator.varMandatoryInverseField` and the two scanner branches that populate it.
- [ ] 3.6 Remove the now-inaccurate `// mappedBy points to a mandatory inverse reference…` comment.

## 4. Cover the Metadata Cross-Product

- [ ] 4.1 Update the 1.1 fixtures to corrected expectations: dependency alone determines both attributes; inverse optionality determines neither.
- [ ] 4.2 Assert the mixed `@Persistent` + `@Element(dependent = "true")` collection now yields both attributes.
- [ ] 4.3 Assert `@Element(dependent = "true")` with relationship context but no `@Persistent` yields both attributes.
- [ ] 4.4 Assert a contradictory `@Element(dependent = "false")` / `@Persistent(dependentElement = "true")` pair resolves to non-dependent.
- [ ] 4.5 Assert `@Persistent(dependent = "true")` on a collection is ignored.
- [ ] 4.6 Assert the dependent `@OneToOne` from 1.3 now yields both attributes, and that `dependentElement` on a single-valued field is honoured as a fallback.
- [ ] 4.7 Assert `@Element(dependent = "true")` with no `mappedBy`, `table` or `@Join` produces no relationship annotation at all.
- [ ] 4.8 Assert a `defaultCascade` containing `CascadeType.REMOVE` on a non-dependent relationship yields `REMOVE` without `orphanRemoval`.
- [ ] 4.9 Assert a configured `defaultCascade` is otherwise emitted unchanged in both branches.
- [ ] 4.10 Assert idempotence by running the recipe twice over already-generated source.

## 5. Confirm Many-To-One Scope

- [ ] 5.1 Add a fixture asserting a dependent `@ManyToOne` receives `CascadeType.REMOVE` and no orphan-removal attribute.
- [ ] 5.2 Assert no mutator, callback or listener is generated for a dependent `@ManyToOne`.
- [ ] 5.3 Document on `ReplacePersistentWithManyToOneAnnotation` that the omission is deliberate and specific to `@ManyToOne`.

## 6. Verify Behaviour

- [ ] 6.1 Run the focused collection, one-to-one and reference tests.
- [ ] 6.2 Run the complete jdo2jpa suite and resolve any regressions.
- [ ] 6.3 Build and install the snapshot bundle locally (`mvn install`), note the resulting version.
- [ ] 6.4 In a detached Estatio worktree at the current `prod` HEAD, point `jdo2jpa.version` at that snapshot and run `./run-rewrite.sh`; record the Estatio commit SHA used.
- [ ] 6.5 Diff the regenerated tree against the existing `jpa` branch tree at the corresponding rewrite commit.
- [ ] 6.6 Confirm the diff is confined to `orphanRemoval` on `@OneToMany`, with no other annotation moved, added or reordered.
- [ ] 6.7 Confirm the five under-deleting collections gain `orphanRemoval = true`: `Project.children`, `Charge.children`, `BreakOptionPeriod.straightliningSchedules`, `BreakOptionPeriod.rentAccrualSchedules`, `SiteTurnoverForMonth.monthlyTurnovers`.
- [ ] 6.8 Confirm the two over-deleting collections lose it: `InvoiceAbstract.items`, `ReminderLetterBatch.letters`.
- [ ] 6.9 Confirm no generated `@OneToOne` changed, and no mapping changed through the `@Element` spelling — Estatio uses neither, so any such diff indicates an unintended widening.
- [ ] 6.10 Inventory any other mapping whose `orphanRemoval` changed, and record it for the release note.

## 7. Release

- [ ] 7.1 Draft release notes for the GitHub Release body created by the "Releasing" procedure in `README.md`; this repository has no CHANGELOG, and `gh release create --notes` is the destination.
- [ ] 7.2 Separate three causes in those notes so a consumer can attribute a regenerated diff: orphan-removal derivation, `@Element(dependent)` recognition, and dependent `@OneToOne` support.
- [ ] 7.3 State how a consumer finds affected mappings in its own source: dependent relationships with an optional inverse now delete on disassociation; non-dependent relationships with a mandatory inverse no longer do.
- [ ] 7.4 Hand the `@ManyToOne` and cross-ORM test work to Estatio. This is a **handoff, not an acceptance criterion** — do not block this change on a consumer-repository change existing.
