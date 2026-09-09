## Context

Constraints reach `@Table` through two declarative recipes. `v2x.Unique` unwraps `@Uniques`, rewrites `members` to `columnNames`, changes the type to `javax.persistence.UniqueConstraint`, then calls:

```yaml
- com.ecpnv.openrewrite.java.MoveAnnotationsToAttribute:
    sourceAnnotationType: javax.persistence.UniqueConstraint
    targetAnnotationType: javax.persistence.Table
    targetAttributeName: uniqueConstraints
```

`v2x.Index` does the same for `javax.persistence.Index` into `indexes`. `MoveAnnotationsToAttribute` operates on one class declaration at a time and has no notion of a type hierarchy, so it can only ever place a constraint on the class where it was declared.

### The ordering constraint

The `v2x` composite runs its phases in this order:

```
18  PersistenceCapable
19  Unique          ← constraints are placed on @Table here
20  Index           ← and here
21  Persistent
22  Column
23  Inheritance     ← JPA @Inheritance exists from here on
24  Discriminator   ← @DiscriminatorColumn is ADDED here, not before
```

At the point of relocation the class still carries JDO's `@Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)`; `javax.persistence.Inheritance` does not exist yet.

`@DiscriminatorColumn` is later than that again, and is not a root signal in any case. `v2x.Discriminator` adds it to **every** class matching `@.*(Discriminator|Inheritance)`, which includes subclasses carrying `@DiscriminatorValue`:

```yaml
- com.ecpnv.openrewrite.java.AddAnnotationConditionally:
    matchByRegularExpression: '@.*(Discriminator|Inheritance)(\n|$|\((.|\s|\n)*)'
    annotationType: javax.persistence.DiscriminatorColumn
```

It is stripped from subclasses only by `RemoveInheritedAnnotations` in `v2x.optional`, which runs under the separate `rewrite_post` Maven profile. So at every point within `v2x`, roots and subclasses alike carry `@DiscriminatorColumn`, and it distinguishes nothing.

Note also the warning already recorded in the YAML: *"recipes without parameters are always run before recipes with parameters, hence when one depends on ordering one should wrap the recipes with parameters in a declarative one"*. A new parameterless recipe added to a list containing parameterised ones will run **first**, not in listed position.

### Which hierarchies are affected

Only `SINGLE_TABLE`. Under `JOINED` and `TABLE_PER_CLASS` each subclass owns a table, so a subclass `@Table` is honoured and must be left alone.

A hierarchy can be `SINGLE_TABLE` two ways, and the Estatio consumer contains both:

- **explicitly** — the root carries `@Inheritance(strategy = InheritanceType.SINGLE_TABLE)`, as `CommunicationChannel` and `InvoiceItem` do;
- **implicitly** — the root carries no `@Inheritance` at all and relies on the JPA default, as `InvoiceAbstract` does.

An implementation that keys off `@Inheritance` alone silently misses the implicit case, which in that consumer is the largest of the three hierarchies (12 of the 19 declarations).

### Mapped superclasses are not tables

Every entity hierarchy in the Estatio consumer sits beneath `EntityAbstract`, which extends `EntityAbstractNoVersion`; both are `@MappedSuperclass`. Neither owns a table.

Resolution that walks to the "topmost persistent type" therefore lands on `EntityAbstractNoVersion` for **every** hierarchy in the codebase — not an edge case but total failure, and one that would be caught only by regenerating a consumer.

## Goals / Non-Goals

**Goals:**

- Emit constraints where JPA will honour them, so generated annotations describe the resulting schema.
- Preserve the schema intent expressed in the JDO source, including constraint names.
- Handle explicit and implicit `SINGLE_TABLE` roots identically.
- Fail loudly on a genuine conflict rather than silently choosing a winner.

**Non-Goals:**

- Change placement for `JOINED` or `TABLE_PER_CLASS` hierarchies.
- Alter constraint definitions — column lists, names, or uniqueness — beyond relocating them.
- Reconcile declared constraints against the real database.
- Generate DDL, or change whether DDL generation is used.

## Decisions

### Relocate after `v2x.Discriminator`, and do not depend on `@DiscriminatorColumn`

The relocation runs as a scanning recipe registered after phase 24, so that JPA `@Inheritance` is settled and the phase ordering is unambiguous. A scanning recipe is required because the decision needs knowledge of a *different* compilation unit — the root — than the one being edited.

Root detection does **not** consult `@DiscriminatorColumn`, for the reason above: within `v2x` it is present on subclasses too. Subtype presence is the signal instead.

Teaching `MoveAnnotationsToAttribute` to look up the hierarchy was rejected: it is a general-purpose recipe used for `@NamedNativeQuery` as well, and it would have to run before inheritance is translated, forcing it to interpret JDO strategy names.

### The relocation target is the highest `@Entity`, never a mapped superclass

Resolution walks `extends` upward, **traversing** `@MappedSuperclass` types but never **selecting** one. The target is the highest `@Entity` on the chain; the walk stops there rather than continuing into mapped superclasses above it.

For `Lease → Agreement → EntityAbstract → EntityAbstractNoVersion` the target is `Agreement`. For the reviewer's shape:

```
@MappedSuperclass Base
        ↑
@Entity Root          ← relocation target
        ↑
@Entity Child         ← declaring class
```

The strategy is whichever `@Inheritance` appears on the entity portion of that chain, defaulting to `SINGLE_TABLE` when none is present and the target has at least one subtype.

### Constraint identity is by explicit name; unnamed constraints are compared by definition

Both `@Index` and `@UniqueConstraint` default `name` to `""`, so two unrelated unnamed constraints would otherwise collide on the empty string.

- Two declarations **conflict** only if they share the same *non-blank* explicit name and differ in definition.
- Two declarations are **equivalent**, and deduplicate, if they have the same name — blank or otherwise — and the same normalized definition.
- Two unnamed declarations with different definitions **both survive**; they are unrelated constraints that happen to be anonymous.

Normalized definition is per annotation type, comparing attributes after applying defaults so that an omitted attribute and an explicitly-default one compare equal:

| Annotation | Compared |
|---|---|
| `@Index` | `columnList` (whitespace around separators normalized), `unique` (default `false`) |
| `@UniqueConstraint` | `columnNames` (as an ordered list) |

An `@Index` and a `@UniqueConstraint` are never compared with each other; they live in different attributes.

### Merge onto the root, preserving names

Relocated declarations are appended to the root's existing `indexes` / `uniqueConstraints`, preserving each declaration's `name` exactly. Names encode the schema contract and in the measured consumer are already unique across each hierarchy, so renaming would gratuitously diverge from Flyway.

### Emit deterministically

Relocated declarations are ordered by declaring class name, then by constraint name (unnamed last, in declaration order), so repeated runs produce byte-identical output and consumer diffs stay reviewable. Without this the emission order would follow scanner traversal order, which is not stable across platforms.

### Remove the subclass `@Table` only when it becomes empty

A subclass `@Table` may also carry `schema` or `name`. Those are ignored by JPA under `SINGLE_TABLE` too, but removing them is a larger change with no metadata benefit, and `name` in particular is a useful record of the JDO source. Strip only the relocated attributes; drop the annotation entirely only if nothing remains.

## Risks / Trade-offs

- [Risk] **Relocation activates constraints that JPA previously ignored.** A subclass declaration that disagreed with Flyway was inert before and becomes live afterwards, so annotation-driven DDL generation or schema validation can now surface a mismatch that was always latent in the source.
  → Mitigation: this is the point of the change — the metadata now says what JPA will do. Consumers must reconcile the relocated declarations against their Flyway definitions before adopting the release; that reconciliation is consumer-owned, and the release note must call for it explicitly rather than presenting the change as inert.
- [Risk] Relocation makes a previously inert conflict active, failing generation for a consumer whose source has always contained contradictory declarations.
  → Mitigation: intended; the diagnostic names both sides. Note it in the release notes as a possible first-regeneration failure.
- [Risk] Root resolution misidentifies the target in a hierarchy mixing `@MappedSuperclass` and `@Entity`.
  → Mitigation: this is the failure mode that would hit every Estatio hierarchy, so it is covered by fixtures *and* by the consumer regeneration check.
- [Trade-off] Running after phase 24 means the constraint annotations are written twice — once onto the subclass, then moved.
  → Accepted: the alternative is duplicating inheritance interpretation into the `Unique`/`Index` phases.

## Migration Plan

Release the corrected bundle; consumers bump their pinned version and regenerate. The diff is confined to `@Table` contents, so it is reviewable by inspection.

Consumers must then reconcile the relocated declarations against their authoritative schema definition — the declarations are now active where they previously were not. Consumers using annotation-driven DDL generation or schema validation should re-run it.

Rollback is pinning the prior version — the relocation changes no behaviour that application code depends on.

## Open Questions

- Should a subclass `@Table(name = …)` that disagrees with the root's table name be reported? It is ignored by JPA either way, but a disagreement suggests the source intended something the migration cannot express.
- Is failing generation the right response to a name conflict, or should it emit both and let schema validation catch it? Failing is proposed on the grounds that the generated source would otherwise be knowingly wrong.
