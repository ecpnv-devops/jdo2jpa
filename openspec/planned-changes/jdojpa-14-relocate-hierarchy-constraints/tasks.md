## 1. Characterize Current Placement

- [ ] 1.1 Add fixtures pinning today's output for a subclass declaring an index in each of `SINGLE_TABLE` (explicit), `SINGLE_TABLE` (implicit, no `@Inheritance` on the root), `JOINED`, and `TABLE_PER_CLASS` hierarchies.
- [ ] 1.2 Add a fixture for a standalone entity with no hierarchy, recording that its constraints stay put.
- [ ] 1.3 Add a fixture where the root already declares its own constraints, so the merge is visible as a diff rather than a rewrite.
- [ ] 1.4 Add a fixture for `@MappedSuperclass Base` → `@Entity Root` → `@Entity Child`, recording where a `Child` constraint lands today.
- [ ] 1.5 Confirm `MoveAnnotationsToAttribute` is used for `@NamedNativeQuery` as well as `@Index`/`@UniqueConstraint`, so that any change to it would have wider blast radius than this change intends.

## 2. Resolve the Hierarchy

- [ ] 2.1 Add root resolution that walks `extends` upward, traversing `@MappedSuperclass` types but never selecting one, and returns the highest `@Entity` on the chain.
- [ ] 2.2 Determine the strategy from `@Inheritance` on the entity portion of that chain, defaulting to `SINGLE_TABLE` when absent and the target has at least one subtype.
- [ ] 2.3 Do **not** consult `@DiscriminatorColumn`: within `v2x` it is present on subclasses as well as roots, and is only stripped from subclasses later, in `v2x.optional` under the separate `rewrite_post` profile.
- [ ] 2.4 Return "no hierarchy" for a type with neither persistent supertype nor subtype, so standalone entities are untouched.
- [ ] 2.5 Cover multi-level chains, chains with a mapped superclass above the root, and chains with one between the subclass and the root.

## 3. Relocate

- [ ] 3.1 Implement relocation as a scanning recipe: the scan collects, per root, the constraints declared on its `SINGLE_TABLE` subclasses; the visitor edits both the root and the subclasses.
- [ ] 3.2 Register it **after** `com.ecpnv.openrewrite.jdo2jpa.v2x.Discriminator` (phase 24), not merely after `v2x.Inheritance` (phase 23).
- [ ] 3.3 Wrap it in a declarative recipe if it takes no parameters — parameterless recipes are hoisted ahead of parameterised ones within a `recipeList`, per the note in `datanucleus-jdo-to-jpa-eclipselink.yml`.
- [ ] 3.4 Add an ordering fixture that runs the **actual composite** for one cycle over a single-table hierarchy, so a future reordering of phases breaks a test rather than the output.
- [ ] 3.5 Merge relocated declarations with the root's existing `indexes` / `uniqueConstraints`, preserving names verbatim.
- [ ] 3.6 Emit ordered by declaring class name, then constraint name, with unnamed declarations last in declaration order.
- [ ] 3.7 Strip only the relocated attributes from the subclass `@Table`; remove the annotation only if nothing remains.
- [ ] 3.8 Implement per-type normalized definitions: `@Index` compares `columnList` (separator whitespace normalized) and `unique` defaulted to `false`; `@UniqueConstraint` compares `columnNames` as an ordered list.
- [ ] 3.9 Deduplicate declarations with the same name (blank or otherwise) and the same normalized definition.
- [ ] 3.10 Fail with a diagnostic naming both declaring classes and both definitions only when a **non-blank** name is shared and definitions differ.
- [ ] 3.11 Let two unnamed declarations with different definitions both survive.

## 4. Cover the Placement Matrix

- [ ] 4.1 Update the 1.1 fixtures to corrected expectations: relocated for both `SINGLE_TABLE` forms, unchanged for `JOINED` and `TABLE_PER_CLASS`.
- [ ] 4.2 Assert the 1.4 fixture now targets `Root`, and that `Base` is left unannotated.
- [ ] 4.3 Assert multiple subclasses of one root all contribute.
- [ ] 4.4 Assert a subclass declaring both an index and a unique constraint relocates each into the right attribute.
- [ ] 4.5 Assert equivalent named duplicates collapse to one.
- [ ] 4.6 Assert conflicting non-blank same-name declarations fail, and check the diagnostic names both sides.
- [ ] 4.7 Assert two unnamed declarations with different definitions both survive.
- [ ] 4.8 Assert two unnamed declarations with the same normalized definition collapse to one.
- [ ] 4.9 Assert an omitted `unique` and an explicit `unique = false` compare equal.
- [ ] 4.10 Assert same columns under different explicit names both survive.
- [ ] 4.11 Assert a subclass `@Table` retaining `schema`/`name` keeps them, and one left empty is removed.
- [ ] 4.12 Assert idempotence by running the recipe twice over already-relocated source.

## 5. Verify Against a Consumer

The acceptance evidence is an A/B comparison of two regenerations from the **same** consumer commit, differing only in the recipe bundle. Comparing a fresh regeneration against the existing `jpa` branch is not sufficient: that branch was generated from an older `prod` commit with an older bundle, so its diff conflates this change with consumer drift and with earlier recipe changes.

- [ ] 5.1 Run the focused placement tests.
- [ ] 5.2 Run the complete jdo2jpa suite and resolve any regressions.
- [ ] 5.3 Pin a clean Estatio `prod` commit for the comparison and record its SHA.
- [ ] 5.4 Build and install the **pre-change** bundle (the currently released version) and record its version.
- [ ] 5.5 Build and install the **candidate** bundle from this change and record its version.
- [ ] 5.6 In a detached worktree at the pinned commit, set `jdo2jpa.version` to the pre-change bundle, run `./run-rewrite.sh`, and keep the output as artifact **A**.
- [ ] 5.7 In a second detached worktree at the same pinned commit, set `jdo2jpa.version` to the candidate bundle, run `./run-rewrite.sh`, and keep the output as artifact **B**.
- [ ] 5.8 Diff **A** against **B**. This is the acceptance evidence: it isolates the recipe change from everything else.
- [ ] 5.9 Confirm the A/B diff is confined to `@Table` contents, with no mapping, column, relationship or lifecycle metadata touched.
- [ ] 5.10 Confirm 19 index declarations move from 7 subclasses onto 3 roots:
      `CommunicationChannel` ← `EmailAddress`, `PostalAddress`, `ElectronicInvoiceAddress`, `PhoneOrFaxNumber` (4);
      `InvoiceAbstract` ← `IncomingInvoice`, `InvoiceForLease` (12);
      `InvoiceItem` ← `InvoiceItemForLease` (3).
- [ ] 5.11 Confirm `InvoiceAbstract`'s own two indexes (`Invoice_invoiceNumber_IDX`, `Invoice_sendTo_IDX`) and its `name = "Invoice"` are preserved unchanged.
- [ ] 5.12 Confirm no constraint landed on `EntityAbstract` or `EntityAbstractNoVersion` — both are `@MappedSuperclass` and sit above every hierarchy in that consumer, so any constraint there means root resolution selected a mapped superclass.
- [ ] 5.13 Confirm no `JOINED` hierarchy changed — `Agreement`, `Party`, `FixedAsset`, `Classification`, `Permit`, `EventSourceLink`, `CommunicationChannelOwnerLink`, `Paperclip` — since a subclass `@Table` is legitimate there.
- [ ] 5.14 Confirm no constraint was renamed, dropped, or gained.
- [ ] 5.15 Separately, and for information only, compare **B** against the current `jpa` branch, to record what a consumer adopting this release will actually see. Differences here beyond the A/B set are consumer drift and earlier recipe changes, not this change.
- [ ] 5.16 Inventory anything else that moved, for the release note.

## 6. Release

- [ ] 6.1 Draft release notes for the GitHub Release body created by the "Releasing" procedure in `README.md`.
- [ ] 6.2 State that relocated declarations become **active** where JPA previously ignored them, and require consumers to reconcile them against their authoritative schema definition before adopting — this is not an inert metadata tidy-up.
- [ ] 6.3 Call out that consumers using annotation-driven DDL generation or schema validation should re-run it after regenerating.
- [ ] 6.4 Warn that a consumer whose source contains contradictory same-name declarations will now fail generation, and that the diagnostic names both sides.
