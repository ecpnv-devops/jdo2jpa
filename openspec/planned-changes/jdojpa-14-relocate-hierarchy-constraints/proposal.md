## Why

In JPA only the root entity of a `SINGLE_TABLE` hierarchy owns the physical table mapping, so a `@Table` declared on a subclass is ignored — its `indexes` and `uniqueConstraints` have no effect on metadata or generated DDL.

JDO has no such restriction. A class mapped `InheritanceStrategy.SUPERCLASS_TABLE` declares its indexes locally, and DataNucleus applies them to the shared table. The migration translates those declarations onto the subclass's own `@Table`, where JPA silently drops them.

The output therefore *looks* faithful and isn't. In the Estatio consumer this loses **19 index declarations across 7 subclasses** in three hierarchies.

## What Changes

- Detect index and unique-constraint declarations that the migration would place on a class that becomes a subclass in a JPA `SINGLE_TABLE` hierarchy.
- Relocate them onto the effective root entity's `@Table`, merging with whatever the root already declares.
- Deduplicate declarations that are equivalent after relocation, and fail with an actionable diagnostic where two declarations share a name but differ in definition.
- Leave `JOINED` and `TABLE_PER_CLASS` hierarchies untouched — a subclass owns its own table there, so its `@Table` is honoured.
- Recognise a hierarchy that is `SINGLE_TABLE` **by omission** as well as by explicit annotation.
- Add recipe fixtures for single-level, multi-level, multiple-subclass, duplicate, conflicting, and non-`SINGLE_TABLE` hierarchies.

## Capabilities

### New Capabilities

- `hierarchy-constraint-placement`: Defines where index and unique-constraint declarations must be emitted for each JPA inheritance strategy, so that generated annotations describe the schema that actually results.

### Modified Capabilities

None.

## Impact

Generated source changes only in the placement of `indexes` and `uniqueConstraints` within `@Table` annotations. No mapping, column, relationship or lifecycle metadata changes.

Measured against Estatio: 19 index declarations move from 7 subclasses onto 3 roots.

| Root | Strategy | Subclasses losing a local `@Table` | Indexes relocated |
|---|---|---|---|
| `CommunicationChannel` | explicit `SINGLE_TABLE` | `EmailAddress`, `PostalAddress`, `ElectronicInvoiceAddress`, `PhoneOrFaxNumber` | 4 |
| `InvoiceAbstract` | **implicit** (no `@Inheritance`) | `IncomingInvoice`, `InvoiceForLease` | 12 |
| `InvoiceItem` | explicit `SINGLE_TABLE` | `InvoiceItemForLease` | 3 |

No unique constraints are affected in that consumer — every relocated declaration is an index — but the recipe must handle both, since the placement rule is identical and other consumers may differ.

Because Flyway is authoritative for the schema in that consumer, this corrects the metadata rather than the database. It matters where annotation-driven DDL generation or schema validation is used.

## Out of scope

- **Verifying the constraints are correct.** This change relocates what the migration already produces; it does not reconcile those declarations against the actual database.
- **Consumer-side schema tests.** Metadata and DDL assertions belong in the consuming application, which owns the Flyway definitions.
