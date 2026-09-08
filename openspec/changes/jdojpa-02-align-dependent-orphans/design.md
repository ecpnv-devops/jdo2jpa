## Context

`ReplacePersistentWithOneToManyAnnotation` is a `ScanningRecipe<Accumulator>`.
Its visitor builds the `@OneToMany` template in `ReplacePersistentWithOneToManyAnnotationVisitor`, and two independent decisions currently feed it.

Cascade, at the `// Search for dependentElement` block:

```java
RewriteUtils.findArgumentAsBoolean(persistentAnno, Constants.Jdo.PERSISTENT_ARGUMENT_DEPENDENT_ELEMENT)
        .filter(isDependent -> isDependent)
        .ifPresentOrElse(isDependent -> { /* cascade = {CascadeType.REMOVE, <defaultCascade>} */ },
                         ()           -> { /* cascade = {<defaultCascade>} */ });
```

Orphan removal, immediately after, from a different fact entirely:

```java
// mappedBy points to a mandatory inverse reference, so deleting children should remove orphans.
if (mustEnableOrphanRemoval(multiVariable, mappedBy)) { template.append("orphanRemoval = true"); }
```

`mustEnableOrphanRemoval` resolves `<elementType>#<mappedByField>` against `Accumulator.varMandatoryInverseField`, which the scanner populates for fields whose inverse reference is non-nullable.

The comment states the intended reasoning: a mandatory inverse foreign key means an orphan cannot legally exist, so removal should imply deletion. That is defensible as JPA modelling advice. It is not a translation of JDO, and this recipe's contract is translation.

Two consequences follow. A JDO-dependent collection whose inverse field is *optional* gets `CascadeType.REMOVE` without `orphanRemoval`, so removing a child from the collection leaves an orphan row that JDO would have deleted. A JDO-*non*-dependent collection whose inverse field is mandatory gets `orphanRemoval` it never had, so removing a child deletes a row that JDO would have kept.

### Attribute-name asymmetry

`persistentAnno` falls back to the `@Element` annotation when no `@Persistent` is present:

```java
J.Annotation persistentAnno = getPersistentAnnotation(multiVariable).orElse(elemAnno.orElse(null));
```

But the cascade block reads only `PERSISTENT_ARGUMENT_DEPENDENT_ELEMENT` (`"dependentElement"`). JDO spells the element-level flag `dependent` on `@Element`, and `Constants.Jdo.PERSISTENT_ARGUMENT_DEPENDENT` already exists for it.
`ReplacePersistentWithManyToOneAnnotation` handles both, checking `dependent` first and falling back to `dependentElement`.
So a collection declared `@Element(dependent = "true")` is currently treated as non-dependent by the collection recipe. No Estatio collection uses that spelling today, so this is a latent library defect rather than an observed consumer failure — but it is the same defect class and is cheap to close alongside.

## Goals / Non-Goals

**Goals:**

- Translate JDO collection dependency metadata faithfully into the two JPA annotations that jointly express it.
- Keep foreign-key optionality out of the lifecycle decision entirely.
- Recognise dependency however JDO spells it on a collection.
- Cover the metadata cross-product with recipe fixtures so neither direction can regress silently.

**Non-Goals:**

- Express orphan semantics for singular references; JPA cannot, and this recipe should not pretend otherwise.
- Infer ownership from naming, cardinality, or nullability.
- Change cascade members other than `REMOVE` — the `REFRESH`/`DETACH` question is a separate change.
- Modify consuming application code or add ORM conditionals.

## Decisions

### Derive orphan removal from dependency metadata alone

`orphanRemoval = true` is emitted if and only if `CascadeType.REMOVE` is emitted, from the same resolved dependency flag.
This makes the pair atomic and makes the mapping readable as a direct translation of `dependentElement`.

The alternative — keeping the mandatory-inverse rule as an additional trigger — was rejected. It reintroduces the over-deletion direction, and the case it protects against (an orphan with a non-nullable FK) is a data-integrity concern the database already enforces. If a consumer genuinely wants orphan removal on a non-dependent collection, that is a modelling decision for the consumer to state in JDO, not one for the recipe to infer.

### Remove the inference plumbing rather than leave it dormant

`Accumulator.varMandatoryInverseField` has exactly one consumer, `mustEnableOrphanRemoval`. With that gone the field and the two scanner branches that populate it become unreachable.
They will be deleted rather than retained, so a future reader cannot mistake dormant plumbing for a supported feature. Recovering it from version control is trivial if a later change needs the mandatory-inverse fact for another purpose.

### Resolve dependency from both attribute spellings

Dependency resolution moves to a single helper that checks `dependent` then `dependentElement` on the resolved annotation, matching `ReplacePersistentWithManyToOneAnnotation`.
Checking `dependent` first preserves the precedence already established there, so a hypothetical field carrying both attributes behaves identically in both recipes.

### Treat explicit `false` as non-dependent

`findArgumentAsBoolean(...).filter(isDependent -> isDependent)` already routes an explicit `false` to the non-dependent branch, so the current code has the right behaviour. It will be asserted by fixture rather than changed, so a future refactor cannot quietly turn `false` into "absent, therefore default".

### Leave singular references to the consumer

`ReplacePersistentWithManyToOneAnnotation` already emits `CascadeType.REMOVE` for a dependent single reference. That is the whole of what JPA can express.
Emitting a `@PreUpdate` hook, a listener, or generated mutator logic to delete a displaced referent was considered and rejected: it would put behavioural code into a mapping translation, and the correct remedy depends on application semantics the recipe cannot see.
The recipe documents the limitation; consumers decide.

## Risks / Trade-offs

- [Risk] Consumers gain deletions on dependent collections whose inverse field is optional, where the previous generated mapping quietly retained orphans.
  → Mitigation: this restores JDO behaviour, so a consumer migrating from JDO is returning to its original semantics, not adopting new ones. Call it out in the release note and require consumers to exercise disassociation paths.
- [Risk] Consumers lose deletions on non-dependent collections whose inverse field is mandatory, where application code may have come to rely on the inferred `orphanRemoval`.
  → Mitigation: in the one measured consumer, both such collections already delete explicitly at their single removal path, so the inferred annotation was redundant. Other consumers must verify the same; the release note should say how to find them (`@Persistent(mappedBy=…)` without `dependentElement`, mandatory inverse).
- [Risk] Recognising `@Element(dependent = "true")` newly marks collections as dependent in consumers that use that spelling.
  → Mitigation: this is a correction, but it is behaviour-changing on first regeneration. Flag it separately in the release note from the orphan-removal change so consumers can attribute a diff to the right cause.
- [Trade-off] Deleting the accumulator plumbing makes a future mandatory-inverse feature slightly more work.
  → Accepted: dormant unreachable code is the larger cost.

## Migration Plan

Release the corrected recipe bundle and have consumers bump their pinned version and regenerate.
Regenerated mappings change only in the `orphanRemoval` attribute of `@OneToMany` (plus cascade for any `@Element(dependent)` collections), so the diff is reviewable by inspection.
Consumers should then run their disassociation, re-parenting and parent-deletion scenarios before adopting the regenerated source.
Rollback is pinning the prior recipe version.

## Open Questions

- Should the release note enumerate the affected mappings per consumer, or is a detection recipe (report-only) more useful for consumers to run against their own source?
- Is there a JDO mapping where the parent-delete cascade and the disassociation-delete genuinely differ, which would make the atomic `REMOVE` + `orphanRemoval` pair wrong?
