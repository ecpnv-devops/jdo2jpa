## 1. Characterize Current Derivation

- [ ] 1.1 Add fixtures pinning today's behaviour for the four combinations of {dependent, non-dependent} × {mandatory inverse, optional inverse}, so the change shows as an intentional diff rather than an unexplained one.
- [ ] 1.2 Add a fixture for a collection declared `@Element(dependent = "true")` with no `@Persistent`, recording that it is currently treated as non-dependent.
- [ ] 1.3 Add a fixture for an explicit `dependentElement = "false"`, confirming it already routes to the non-dependent branch.
- [ ] 1.4 Confirm `Accumulator.varMandatoryInverseField` has no consumer other than `mustEnableOrphanRemoval`.

## 2. Derive Orphan Removal From Dependency

- [ ] 2.1 Extract dependency resolution in `ReplacePersistentWithOneToManyAnnotation` into a single helper that checks `Constants.Jdo.PERSISTENT_ARGUMENT_DEPENDENT` then `PERSISTENT_ARGUMENT_DEPENDENT_ELEMENT`, mirroring `ReplacePersistentWithManyToOneAnnotation`.
- [ ] 2.2 Emit `orphanRemoval = true` from that resolved flag, in the same branch that emits `CascadeType.REMOVE`, preserving the existing comma/attribute-ordering behaviour of the template builder.
- [ ] 2.3 Delete `mustEnableOrphanRemoval` and its call site.
- [ ] 2.4 Delete `Accumulator.varMandatoryInverseField` and the two scanner branches that populate it.
- [ ] 2.5 Remove the now-inaccurate `// mappedBy points to a mandatory inverse reference…` comment.

## 3. Cover the Metadata Cross-Product

- [ ] 3.1 Update the fixtures from 1.1 to the corrected expectations: dependency alone determines both attributes; inverse optionality determines neither.
- [ ] 3.2 Assert that `@Element(dependent = "true")` now yields `CascadeType.REMOVE` and `orphanRemoval = true`.
- [ ] 3.3 Assert precedence when both `dependent` and `dependentElement` are present.
- [ ] 3.4 Assert that a configured `defaultCascade` is emitted unchanged in both the dependent and non-dependent branches.
- [ ] 3.5 Assert idempotence by running the recipe twice over already-generated source.

## 4. Confirm Single-Reference Scope

- [ ] 4.1 Add a fixture asserting that a dependent `@ManyToOne` receives `CascadeType.REMOVE` and no orphan-removal attribute.
- [ ] 4.2 Assert that no mutator, callback or listener is generated for a dependent single reference.
- [ ] 4.3 Document the limitation on `ReplacePersistentWithManyToOneAnnotation` so the omission reads as deliberate.

## 5. Verify Behaviour

- [ ] 5.1 Run the focused collection and reference tests.
- [ ] 5.2 Run the complete jdo2jpa suite and resolve any regressions.
- [ ] 5.3 Regenerate a representative Estatio JPA tree and diff against the current generated output.
- [ ] 5.4 Confirm the diff is confined to `orphanRemoval` on `@OneToMany` (plus cascade for any `@Element(dependent)` collection), and that no other annotation moved.
- [ ] 5.5 Confirm the five under-deleting collections gain `orphanRemoval = true`: `Project.children`, `Charge.children`, `BreakOptionPeriod.straightliningSchedules`, `BreakOptionPeriod.rentAccrualSchedules`, `SiteTurnoverForMonth.monthlyTurnovers`.
- [ ] 5.6 Confirm the two over-deleting collections lose it: `InvoiceAbstract.items`, `ReminderLetterBatch.letters`.
- [ ] 5.7 Inventory any other consumer mapping whose `orphanRemoval` changes, and record it for the release note.

## 6. Release

- [ ] 6.1 Write a release note separating the orphan-removal derivation change from the `@Element(dependent)` recognition change, so consumers can attribute a regenerated diff to the right cause.
- [ ] 6.2 State how a consumer finds affected mappings in its own source: dependent collections with an optional inverse now delete on disassociation; non-dependent collections with a mandatory inverse no longer do.
- [ ] 6.3 Hand the singular-reference and cross-ORM test work back to the consuming application, raising a change there to own it (see Out of scope in the proposal).
