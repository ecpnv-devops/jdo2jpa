# causeway-entity-listener-preservation Specification

## Purpose
Preserve intended entity listener integration through composed migration, respecting explicit overrides, inherited registrations, module boundaries, and observable failure handling.
Acceptance covers verified listener presence in the one-shot consumer workflow, not runtime callback execution.

## Requirements
### Requirement: Refresh cycles preserve nested configuration scope

The composed migration SHALL add `EntityScan` only to types that themselves declare `ComponentScan`, not enclosing types that merely contain such a declaration.
Visiting an enclosing configuration SHALL preserve edits already made to its nested configurations.

#### Scenario: Nested configuration inside unannotated helper types

- **WHEN** only a nested configuration declares `ComponentScan`
- **THEN** only that configuration receives `EntityScan`
- **AND** its enclosing interface or helper classes remain unannotated
- **AND** subsequent cycles make no further configuration changes

#### Scenario: Enclosing and nested configurations both qualify

- **WHEN** an enclosing type and its nested configuration both declare `ComponentScan`
- **THEN** both receive `EntityScan` in the first changing cycle
- **AND** the enclosing edit does not discard the nested edit

### Requirement: Concrete migrated entities receive missing framework listener integration

The Causeway migration SHALL add its configured entity listener to a concrete entity that has neither an explicit entity-level listener declaration nor an effective inherited registration of that configured listener.
The result SHALL not depend on whether an abstract superclass existed before migration or was inserted earlier in the same recipe chain.

#### Scenario: Superclass inserted during the migration

- **WHEN** a concrete JDO datastore entity without a superclass is migrated through superclass insertion, entity conversion, and Causeway listener insertion
- **THEN** the result extends the configured entity superclass and declares the configured listener when that listener is not inherited
- **AND** the insertion does not silently fail because the earlier template initially produced incomplete superclass attribution

#### Scenario: Pre-existing abstract superclass without a listener

- **WHEN** a concrete entity already extends an abstract superclass that provides no configured listener
- **THEN** the entity receives the configured listener

#### Scenario: Concrete superclass without a listener

- **WHEN** a concrete entity extends a concrete superclass that provides no configured listener and is not itself scheduled to receive it
- **THEN** the entity receives the configured listener rather than being skipped merely because its parent is concrete

### Requirement: Effective inherited listeners are not duplicated

The Causeway migration SHALL inspect effective listener inheritance using fully qualified listener identities and SHALL avoid adding a redundant registration of the configured listener.
It SHALL account for listeners added to eligible ancestors within the same source set and for exclusions of superclass listeners.
Across separate module invocations, it SHALL evaluate attributed dependency types representing already-migrated parents rather than predicting changes in another module's source.
It SHALL not add a listener to an abstract class solely as part of this concrete-entity repair.

#### Scenario: Listener declared on an abstract ancestor

- **WHEN** a concrete entity inherits the configured listener from an abstract ancestor without excluding superclass listeners
- **THEN** the entity does not receive a redundant listener declaration
- **AND** the existing ancestor declaration is preserved

#### Scenario: Parent and child both migrate

- **WHEN** an eligible concrete parent without a listener and its concrete child are processed together
- **THEN** the parent receives the configured listener and the child inherits it without a redundant declaration
- **AND** reversing the source-file order produces the same declarations

#### Scenario: Superclass listeners explicitly excluded

- **WHEN** a concrete entity without its own listener declaration excludes superclass listeners
- **THEN** an ancestor's configured listener is not treated as effective on that entity
- **AND** the entity receives the configured listener

#### Scenario: Ancestor has only an unrelated listener

- **WHEN** a concrete entity has no own listener declaration and its ancestor supplies only a different listener
- **THEN** the concrete entity receives the configured framework listener without modifying the ancestor declaration

### Requirement: Cross-module inheritance uses migrated dependency artifacts

The consuming workflow SHALL regenerate and build parent modules before processing dependent modules and SHALL provide those migrated artifacts as attributed dependencies.
The recipe SHALL not claim to predict future cross-module additions or guarantee duplicate avoidance against stale/unmigrated parent binaries.
Acceptance evidence SHALL establish dependency provenance because a resolvable but stale binary is not reliably detectable by the recipe.

#### Scenario: Listener inherited from a separately migrated module

- **WHEN** a parent is migrated and compiled in one invocation and a child is processed separately with only that compiled parent available
- **THEN** the child inherits the configured listener without a redundant declaration
- **AND** the test does not provide parent source to the child invocation

#### Scenario: Dependency parent without configured listener

- **WHEN** the migrated dependency parent has no configured listener and the child has no explicit listener declaration
- **THEN** the child receives the configured listener

#### Scenario: Dependency listeners excluded

- **WHEN** the dependency parent declares the configured listener but the child excludes superclass listeners and has no own listener declaration
- **THEN** the child receives the configured listener

#### Scenario: Stale parent dependency provenance

- **WHEN** production-scale validation cannot establish that the child used its already-migrated parent artifact
- **THEN** cross-module acceptance remains blocked rather than treating source-set-only tests as whole-project proof

### Requirement: Explicit listener customizations and unrelated annotation recipes remain unchanged

The migration SHALL preserve any existing entity-level `EntityListeners` declaration verbatim and SHALL not replace or append to it automatically.
Any explicit declaration, including a custom-only or empty listener list, SHALL suppress automatic Causeway listener insertion.
The repair SHALL not globally change the semantics of `allowInherited` for unrelated uses of `AddAnnotationConditionally`.

#### Scenario: Entity already has a custom listener list

- **WHEN** an entity has an explicit listener declaration containing custom listeners, with or without the configured framework listener
- **THEN** the declaration and its listener ordering remain unchanged

#### Scenario: Entity explicitly declares an empty listener list

- **WHEN** an entity declares `@EntityListeners({})`
- **THEN** the empty declaration is preserved and the configured listener is not inserted automatically

#### Scenario: Unrelated conditional annotation recipe

- **WHEN** an existing caller uses `AddAnnotationConditionally` outside the Causeway listener migration
- **THEN** its configured inheritance behaviour remains unchanged by this repair

### Requirement: Unresolvable listener inheritance produces a diagnostic

When the effective listener decision requires hierarchy information that cannot be resolved, the recipe SHALL invoke the execution context error handler with an `UnresolvedEntityHierarchyException` containing the entity FQN, unresolved parent FQN/reference, recipe identity, and source/classpath remediation advice.
If the handler returns, the failing recipe SHALL leave the affected class and imports unchanged relative to its own input and SHALL NOT additionally throw; unaffected classes can continue.
If the handler throws, the recipe SHALL allow normal runner error propagation.
Earlier recipe edits and changes to other classes SHALL NOT be represented as automatically rolled back.
The acceptance/consumer runner SHALL collect these errors, exit nonzero, and reject the entire run's partial output.

#### Scenario: Parent cannot be resolved with a recording handler

- **WHEN** a concrete entity's relevant parent is unavailable and the execution context handler records errors without throwing
- **THEN** the handler receives the specified exception type and fields
- **AND** the failing listener recipe leaves the affected class and imports unchanged from its input without adding a listener or additionally throwing

#### Scenario: Earlier transformations already occurred

- **WHEN** an earlier recipe changed the class before listener hierarchy resolution fails
- **THEN** the listener recipe does not roll back those earlier changes
- **AND** the acceptance runner exits nonzero and rejects all partial generated output rather than publishing it

#### Scenario: Error handler throws

- **WHEN** the error handler throws on the unresolved-hierarchy error
- **THEN** failure propagates through normal runner handling and the acceptance runner reports a failed run

#### Scenario: Dependency hierarchy unavailable

- **WHEN** a separate child-module invocation lacks sufficient dependency metadata for its relevant parent hierarchy
- **THEN** the same typed error and output-rejection contract applies as for unresolved source-defined parents

### Requirement: Listener migration is idempotent and verified in composition

Successful listener migration SHALL produce no additional changes on a subsequent run of the focused superclass/listener regression fixtures.
This focused recipe contract SHALL NOT impose full-output repeat-run idempotence on the one-shot consumer CI workflow.
Combined-recipe tests SHALL retain normal type-attribution validation for successful transformations.

#### Scenario: Re-running a converted turnover-shaped fixture

- **WHEN** the superclass-and-listener migration is run again on its successful output for either turnover-shaped regression fixture
- **THEN** no additional annotations, superclass declarations, imports, or formatting changes are produced
- **AND** the hierarchy remains valid under the test framework's normal type checks

### Requirement: Production-scale listener acceptance is reproducible

Acceptance SHALL include baseline/candidate regeneration from the identical Estatio prod commit `4dd98637d572240ff99cb8c998590074c27c839d`, using isolated dependency repositories and identical non-recipe inputs and orchestration.
JPA `907c723889d3306352c2e50cfbbae3df6406d949` SHALL be used only for reconciliation, not as a regeneration input or correctness oracle.
Evidence SHALL identify exact distinct baseline/candidate artifact versions, coordinates, commits, checksums, environment, commands, logs, dependency provenance, and outcomes.
All generated differences SHALL be inventoried and justified as intended superclass/listener changes, declared-ComponentScan configuration-scope corrections, or necessary import cleanup/grouping before acceptance.
Directly affected modules and representative Mallcomm integration SHALL be compiled wherever the baseline supports it, and the exact candidate recipe revision SHALL pass an unskipped final `mvn clean verify`.
Candidate-specific compilation regressions SHALL block library acceptance.
Unrelated failures reproduced by the baseline SHALL be recorded without bypassing checks or being reported as passes, but SHALL remain non-blocking consumer handoffs rather than recipe metadata failures.

#### Scenario: Candidate regeneration and compilation

- **WHEN** the baseline and candidate are regenerated from the pinned prod input
- **THEN** the evidence inventories every changed class and explains its superclass/listener differences
- **AND** unexplained differences in any generated source, mapping, resource, or configuration block acceptance
- **AND** exact commands and results cover every directly affected module plus reactor dependencies and `estatio-mallcomm/mallcommturnover`
- **AND** maintained/excluded BackgroundCommandsOrchestration remains unchanged by regeneration

### Requirement: Consumer handoff verifies listener presence

Release guidance SHALL identify breaking generated-source changes, the exact candidate artifact, dependency-order requirements, failure/partial-output handling, and reproducible F04/Mallcomm inspection steps.
Acceptance for this change SHALL require the intended listener registration on both turnover entities without redundant annotation-defined inherited registrations.
Runtime persist/update/remove execution and callback-count verification are out of scope and SHALL NOT be acceptance or rollout gates imposed by this change.
Listener-presence evidence SHALL NOT be represented as observed runtime callback behaviour.
The maintained BackgroundCommandsOrchestration repair SHALL remain separately owned and SHALL NOT be claimed as a recipe outcome.

#### Scenario: Inspect generated F04 entities

- **WHEN** the generated and compiled candidate entities are inspected
- **THEN** both turnover entities have the intended listener registration without redundant annotation-defined inherited registrations
- **AND** the handoff identifies the inspected artifact and evidence

#### Scenario: Runtime checks are not performed

- **WHEN** no lifecycle callback execution or callback-count observation has been performed
- **THEN** the handoff records those checks as out of scope and not performed, rather than passed
- **AND** their absence does not block acceptance or archival of this change

### Requirement: Identified test sources inherit main-source plans

Within an identified project, test-source listener analysis MUST account for relevant main-source declarations and their planned listener additions.
Main-source analysis MUST NOT borrow test declarations, and project/version boundaries MUST remain isolated.

#### Scenario: Test entity extends a newly migrated main entity
- **WHEN** a test entity extends a main-source entity in the same identified project and the parent is scheduled to receive the configured listener
- **THEN** the test entity does not receive a redundant listener, regardless of file order

### Requirement: Standard listener configuration retains its default

The standard recipe MUST default to org.apache.isis.persistence.jpa.applib.integration.IsisEntityListener and MUST allow an explicit jdo2jpa.entityListenerClass recipe-environment property to select the consumer's final integration listener.
The override MUST NOT weaken explicit entity-level listener preservation or treat unrelated custom listeners as equivalent.

#### Scenario: Consumer configures its final integration listener
- **WHEN** the consumer selects its final listener FQN and reruns the recipes over reparsed generated main/test sources
- **THEN** inherited registrations are recognised and no redundant listener is introduced
