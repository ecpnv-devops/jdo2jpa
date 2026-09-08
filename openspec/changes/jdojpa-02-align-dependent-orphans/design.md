## Context

`ReplacePersistentWithOneToManyAnnotation` is a `ScanningRecipe<Accumulator>`.
Despite its name it generates **two** mapping shapes. Conversion triggers only when a relationship context is present, and cardinality then selects the annotation:

```java
if (mappedBy.isPresent() || table.isPresent() || joinAnno.isPresent()) {   // else: no conversion at all
    if (hasCollection(multiVariable)) { template.append(ONE_TO_MANY); }
    else                              { template.append(ONE_TO_ONE); }
```

Two independent decisions then feed the template. Cascade, from dependency metadata:

```java
RewriteUtils.findArgumentAsBoolean(persistentAnno, Constants.Jdo.PERSISTENT_ARGUMENT_DEPENDENT_ELEMENT)
        .filter(isDependent -> isDependent)
        .ifPresentOrElse(isDependent -> { /* cascade = {CascadeType.REMOVE, <defaultCascade>} */ },
                         ()           -> { /* cascade = {<defaultCascade>} */ });
```

Orphan removal, from a different fact entirely:

```java
// mappedBy points to a mandatory inverse reference, so deleting children should remove orphans.
if (mustEnableOrphanRemoval(multiVariable, mappedBy)) { template.append("orphanRemoval = true"); }
```

`mustEnableOrphanRemoval` is guarded by `hasCollection(...)` and resolves `<elementType>#<mappedByField>` against `Accumulator.varMandatoryInverseField`, which the scanner populates for non-nullable inverse references.

The comment states the intended reasoning: a mandatory inverse foreign key means an orphan cannot legally exist, so removal should imply deletion. That is defensible as JPA modelling advice. It is not a translation of JDO, and this recipe's contract is translation.

Two consequences follow. A JDO-dependent relationship whose inverse field is *optional* gets `CascadeType.REMOVE` without `orphanRemoval`, so disassociation leaves an orphan row JDO would have deleted. A JDO-*non*-dependent relationship whose inverse field is mandatory gets `orphanRemoval` it never had, so disassociation deletes a row JDO would have kept.

### Dependency is spelled three ways, and cardinality decides which applies

JDO distinguishes the *field's* value from a collection's *elements*:

| Annotation & attribute | Applies to | Meaning |
|---|---|---|
| `@Persistent(dependent = "true")` | single-valued field | the referenced object is dependent |
| `@Persistent(dependentElement = "true")` | collection field | the collection's elements are dependent |
| `@Element(dependent = "true")` | collection field | same fact, declared on the element annotation |

The current code reads only `dependentElement`, from a single resolved annotation:

```java
J.Annotation persistentAnno = getPersistentAnnotation(multiVariable).orElse(elemAnno.orElse(null));
```

That has two defects. `@Element` is consulted only when `@Persistent` is *absent*, so a field carrying `@Persistent(mappedBy = …)` **and** `@Element(dependent = "true")` — the ordinary way to write a dependent join-table collection — never has its dependency seen. And on the `@OneToOne` branch the singular spelling `dependent` is not read at all, so a dependent single reference converted by this recipe gets neither cascade nor orphan removal.

`ReplacePersistentWithManyToOneAnnotation` reads `dependent` then falls back to `dependentElement`. That order is right for a single reference and wrong for a collection, so it must not simply be copied.

## Goals / Non-Goals

**Goals:**

- Translate JDO dependency metadata faithfully into the JPA annotations that jointly express it, for both shapes this recipe generates.
- Keep foreign-key optionality out of the lifecycle decision entirely.
- Recognise dependency however JDO spells it for the cardinality at hand, across both annotations.
- Cover the metadata cross-product with fixtures so neither divergence direction can regress silently.

**Non-Goals:**

- Change `ReplacePersistentWithManyToOneAnnotation`; `@ManyToOne` has no orphan-removal attribute to emit.
- Infer ownership from naming, cardinality, or nullability.
- Change cascade members other than `REMOVE` — the `REFRESH`/`DETACH` question is a separate change.
- Alter when conversion triggers, or which of `@OneToMany`/`@OneToOne` is chosen.
- Modify consuming application code or add ORM conditionals.

## Decisions

### Derive orphan removal from dependency metadata alone

`orphanRemoval = true` is emitted if and only if dependency is resolved true, from the same flag that emits `CascadeType.REMOVE`.
This makes the pair atomic and the mapping readable as a direct translation.

Keeping the mandatory-inverse rule as an *additional* trigger was rejected: it reintroduces the over-deletion direction, and the case it protects against — an orphan with a non-nullable FK — is a data-integrity concern the database already enforces. A consumer wanting orphan removal on a non-dependent relationship should say so in JDO.

### Apply it to generated `@OneToOne` as well as `@OneToMany`

JPA supports `orphanRemoval` on `@OneToOne`, and JDO `dependent = "true"` on a single-valued field means precisely that: delete the referent when the owner is deleted or the reference is replaced.
So the dependency-derived pair applies to both shapes this recipe generates, and `hasCollection(...)` is **not** an orphan-removal gate.

`hasCollection(...)` is retained where it is genuinely about cardinality — choosing the annotation name, and resolving the element type for `mappedBy`. It is removed from the orphan-removal decision along with `mustEnableOrphanRemoval` itself.

The alternative — retain the guard and declare `@OneToOne` out of scope — was rejected. It would leave a known, expressible divergence in place, and the guard's presence today is incidental: it exists because the *mandatory-inverse* inference needed an element type to look up, not because `@OneToOne` was considered and excluded.

This is the one decision with no measured consumer impact either way: Estatio declares no dependent single-valued `@Persistent`, so no generated `@OneToOne` changes.

### Resolve dependency by cardinality, not by a single annotation

Resolution moves to a helper that first determines cardinality, then reads the attributes that JDO defines for it, across **both** annotations rather than one pre-resolved annotation:

- **Collection:** `@Element(dependent)`, then `@Persistent(dependentElement)`.
  `@Element` wins because it is the element-level declaration of an element-level fact, and because it is the spelling that survives when both annotations are present.
  `@Persistent(dependent)` is **ignored** on a collection: JDO defines it as the field-value flag, and honouring it would silently reinterpret a singular declaration as an element one.
- **Single-valued:** `@Persistent(dependent)`, then `@Persistent(dependentElement)`.
  The fallback tolerates the common mis-spelling and matches `ReplacePersistentWithManyToOneAnnotation`, so a field carrying both behaves identically in both recipes.

Where both sources are present and disagree, the first in the list wins. This is a deliberate precedence rule rather than an error, because JDO itself does not forbid the combination and failing generation on it would be a harsher contract than the input language imposes.

### `defaultCascade` may contain `REMOVE`; only derived `REMOVE` implies orphan removal

The `defaultCascade` parameter is caller-supplied and may legitimately include `CascadeType.REMOVE`.
The invariant is therefore stated over *dependency-derived* `REMOVE`, not over the presence of `REMOVE` in the emitted set: a caller who puts `REMOVE` in `defaultCascade` gets it on every relationship and gets `orphanRemoval` on none.

Rejecting or stripping `REMOVE` from `defaultCascade` was considered and rejected as an unrelated contract change that would break callers who deliberately configure it.

### Treat explicit `false` as non-dependent

`findArgumentAsBoolean(...).filter(isDependent -> isDependent)` already routes an explicit `false` to the non-dependent branch, so today's behaviour is correct.
It will be asserted by fixture rather than changed, so a later refactor cannot quietly turn `false` into "absent, therefore default".

### Remove the inference plumbing rather than leave it dormant

`Accumulator.varMandatoryInverseField` has exactly one consumer, `mustEnableOrphanRemoval`. With that gone, the field and the two scanner branches populating it are unreachable.
They will be deleted so a future reader cannot mistake dormant plumbing for a supported feature; version control recovers them if a later change needs the fact.

## Risks / Trade-offs

- [Risk] Consumers gain deletions on dependent relationships whose inverse field is optional, where the generated mapping quietly retained orphans.
  → Mitigation: this restores JDO behaviour, so a consumer migrating from JDO returns to its original semantics rather than adopting new ones. Call it out in the release note; require consumers to exercise disassociation paths.
- [Risk] Consumers lose deletions on non-dependent relationships whose inverse field is mandatory, where application code may have come to rely on the inferred `orphanRemoval`.
  → Mitigation: in the one measured consumer both such collections already delete explicitly at their single removal path, so the annotation was redundant. Other consumers must verify; the release note should say how to find them.
- [Risk] Newly reading `@Element(dependent)` and the `@OneToOne` singular flag marks relationships dependent that previous releases treated as independent.
  → Mitigation: these are corrections, but behaviour-changing on first regeneration. Flag each separately in the release note so a consumer can attribute a diff to the right cause.
- [Risk] The collection precedence rule silently resolves a contradictory `@Element`/`@Persistent` pair.
  → Mitigation: documented in the spec with an explicit scenario, so the behaviour is chosen rather than emergent.
- [Trade-off] Deleting the accumulator plumbing makes a future mandatory-inverse feature slightly more work.
  → Accepted: dormant unreachable code is the larger cost.

## Migration Plan

Release the corrected bundle; consumers bump their pinned version and regenerate.
Regenerated mappings change only in `orphanRemoval`, plus `cascade` for relationships newly recognised as dependent, so the diff is reviewable by inspection.
Consumers then run disassociation, re-parenting and parent-deletion scenarios before adopting the regenerated source.
Rollback is pinning the prior version.

## Open Questions

- Should a contradictory `@Element(dependent = "false")` / `@Persistent(dependentElement = "true")` pair warn as well as resolve?
- Is there a JDO mapping where the parent-delete cascade and the disassociation-delete genuinely differ, which would make the atomic `REMOVE` + `orphanRemoval` pair wrong?
