# jdojpa-14-relocate-hierarchy-constraints

Relocate single-table hierarchy constraints to the root mapping where JPA honours them.
This draft addresses finding 14 in the consolidated JDO/JPA equivalence review and must be promoted before implementation.

## WITHDRAWN — 2026-09-09

Promoted to an active change, then withdrawn before implementation. The design is unimplementable as
written, and the consumer that motivated it has resolved the problem another way.

**Why it cannot be implemented as designed.** The change relies on a scanning recipe that inspects a
subclass compilation unit and edits its hierarchy root. Estatio configures every rewrite profile with
`runPerSubmodule=true` (`pom.xml` 2209, 2264, 2318), so OpenRewrite runs with per-module scope: while
processing a child module the root exists only as dependency type metadata with no editable AST, and
while processing the root module the scanner cannot see downstream subclasses.

Two of the three affected hierarchies span modules, so **15 of the 19 relocations are out of reach**:

| Root | Root's module | Subclasses (module) | Indexes | Reachable? |
|---|---|---|---:|:-:|
| `CommunicationChannel` | `shared-kernel/communications` | `EmailAddress`, `PostalAddress`, `ElectronicInvoiceAddress`, `PhoneOrFaxNumber` (same) | 4 | yes |
| `InvoiceAbstract` | `shared-kernel/invoice` | `IncomingInvoice` (`incoming/capex`), `InvoiceForLease` (`outgoing/invoiceforlease`) | 12 | no |
| `InvoiceItem` | `shared-kernel/invoice` | `InvoiceItemForLease` (`outgoing/invoiceforlease`) | 3 | no |

Aggregated execution (`runPerSubmodule=false`) would make it implementable, but that is a substantial
change to a working pipeline over a 6,057-file reactor, to correct a finding rated Low (DDL drift) in
a consumer where Flyway is authoritative. The remedy costs more than the defect.

**How Estatio resolved it instead.** All 19 declarations are restated on the three roots in
`estatio-webapp/src/main/resources/META-INF/orm-jpa-overrides.xml`, which is loaded only under the
`orm_jpa` profile. Regression is prevented by an existing architecture gate — *"classes that are
annotated with `@Entity` and are non-owning single-table subtypes should not declare `@Table` indexes
or unique constraints"* — which currently carries those 7 subclasses as frozen violations.

**What would justify reviving this.** Any of:

- a consumer whose affected hierarchies are single-module, where the recipe fix works in full;
- a decision to adopt aggregated rewrite execution for other reasons, which would make this close to free;
- descoping to same-module hierarchies only, with a report-only diagnostic for cross-module cases —
  detection is feasible cross-module even though editing is not, since the root's strategy is readable
  from dependency type metadata.

`design.md`, `tasks.md` and `specs/` are retained. They contain analysis worth keeping if this is
revived: the phase-ordering constraint (relocation must run after `v2x.Discriminator`, not merely after
`v2x.Inheritance`, and `@DiscriminatorColumn` is not a root signal because subclasses carry it too);
the requirement that root resolution traverse but never select a `@MappedSuperclass` (`EntityAbstract`
sits above every hierarchy in Estatio, so "topmost persistent type" fails universally); constraint
identity semantics for unnamed constraints; and the same-commit A/B regeneration method needed for
consumer validation. The design's "implement as a scanning recipe" decision is the part that does not
hold.
