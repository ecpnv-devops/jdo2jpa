## Why

`ReplacePersistentWithOneToManyAnnotation` derives `CascadeType.REMOVE` from JDO `dependentElement`, but derives `orphanRemoval` from an unrelated signal: whether the inverse (`mappedBy`) field is mandatory.
Foreign-key optionality and dependency ownership are different facts, so the generated mapping diverges from JDO in both directions — collections JDO deleted are retained, and collections JDO retained are deleted.

JDO `dependentElement = "true"` deletes a child both when the parent is deleted *and* when the child leaves the collection.
JPA needs two annotations to express that: `CascadeType.REMOVE` for the first and `orphanRemoval = true` for the second.
Emitting only the first silently drops half the contract.

In the Estatio consumer this affects seven collection mappings, five of which expose `removeFrom…` methods, so the difference is executable domain behaviour rather than metadata drift.

## What Changes

- Derive `orphanRemoval = true` from the same JDO dependency metadata that already drives `CascadeType.REMOVE`.
- Stop inferring orphan removal from a mandatory inverse foreign key, and remove the now-unused accumulator plumbing that supports that inference.
- Detect dependency on collections declared with `@Element(dependent = "true")` as well as `@Persistent(dependentElement = "true")`, mirroring the dual-attribute lookup `ReplacePersistentWithManyToOneAnnotation` already performs.
- Treat an explicit `dependentElement = "false"` as non-dependent, emitting neither `CascadeType.REMOVE` nor `orphanRemoval`.
- Add recipe fixtures covering the full cross-product of dependency metadata and inverse-field optionality.

## Capabilities

### New Capabilities

- `dependent-collection-lifecycle`: Defines how JDO collection dependency metadata is translated into JPA cascade and orphan-removal semantics, and what must not be inferred from unrelated mapping facts.

### Modified Capabilities

None.

## Impact

Generated `@OneToMany` mappings change for two populations of collection.
Dependent collections whose inverse field is optional gain `orphanRemoval = true`; non-dependent collections whose inverse field is mandatory lose it.
Both are behavioural changes in consuming applications, not metadata-only edits, so consumers must regenerate and exercise their disassociation paths.

No database schema changes; no runtime dependency; no ORM conditional is introduced.

## Out of scope

Two parts of the originating review finding are **not** addressed here, because they are not expressible in the recipe:

- **Singular dependent references.** JPA has no `orphanRemoval` for `@ManyToOne`, so "delete the displaced referent on reassignment" has no declarative equivalent to emit. `ReplacePersistentWithManyToOneAnnotation` already emits `CascadeType.REMOVE` for a dependent single reference, which is as much as the mapping can express. The remaining semantics belong to the consuming application.
- **Consumer regression tests.** Cross-ORM disassociation, re-parenting and parent-deletion tests over the seven Estatio collections belong in that repository, which has both ORMs available. This change supplies recipe fixtures only.

Both are currently **untracked**: Estatio's `openspec/planned-changes/` was emptied when these
proposals moved here, so no change owns the consumer-side work. A change should be raised in Estatio
covering the singular reference (`CodaTransactionFetchOrchestration.codaTransaction`, whose field has
been shown to be assigned only in its two constructors, so an architecture rule asserting no
reassignment path may be sufficient) and the cross-ORM tests for the seven affected collections.
