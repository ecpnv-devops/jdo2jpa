## Why

The Estatio F04 investigation found that superclass insertion can leave `EntityAbstract` attributed as `JavaType.Unknown`, causing the subsequent Causeway listener recipe to silently skip concrete entities such as `TurnoverRollupRun` and `TurnoverRollupRunOrchItem`.
A whitespace-sensitive match of the JDO `identityType` argument makes this failure depend on source formatting, and existing standalone listener tests do not cover the combined migration sequence.

## What Changes

- Preserve or recover superclass type information when inserting an entity superclass, rather than leaving later recipes to infer inheritance from an unknown type.
- Make Causeway listener insertion depend on actual effective listener inheritance, not the current proxy of whether the immediate superclass is abstract.
- Treat any explicit entity-level listener declaration, including a custom or empty declaration, as an override that suppresses automatic Causeway listener insertion.
- Avoid duplicate inherited listeners within a source set and across modules when attributed dependencies contain the already-migrated parent; predicting future changes in another module or using stale parent artifacts is unsupported.
- Report genuinely unresolved hierarchy decisions through a typed execution-context error and reject the run's partial output rather than silently treating unknown types as listener-bearing parents.
- Match an explicitly declared datastore identity structurally, independent of whitespace, comments, qualification, or static imports, while preserving existing regex-based recipe configurations for other callers.
- Preserve the public three-argument constructor and existing YAML properties while adding optional `annotationAttributeName` and `annotationAttributeValue` options.
- Gate implementation on an attribution spike, then add combined-recipe regressions, module-boundary fixtures, compatibility tests, and repeat-run tests based on the two turnover-class shapes.
- Require pinned same-input Estatio A/B regeneration, affected-class inventory, affected-module compilation, final recipe build evidence, and verification of intended listener presence without redundant annotation-defined registrations before acceptance.
- Runtime lifecycle execution and callback-count verification are explicitly out of scope.

## Capabilities

### New Capabilities

- `causeway-entity-listener-preservation`: Concrete migrated entities receive the configured Causeway listener only when they have neither an explicit entity-level listener declaration nor an effective inherited registration, including after superclass insertion in the same recipe chain.
- `datastore-superclass-insertion`: Explicit datastore identity detection and superclass insertion are formatting-independent and preserve usable inheritance metadata for subsequent recipes.

### Modified Capabilities

None.

## Impact

- Recipe definitions in `src/main/resources/META-INF/rewrite/datanucleus-jdo-to-jpa-eclipselink.yml`.
- `ExtendWithClassForAnnotationConditionally`, the Causeway-specific use of `AddAnnotationConditionally`, and relevant type-resolution/template utilities.
- Recipe tests for superclass insertion, conditional annotations, and Causeway listener insertion.
- Downstream generated entities may gain previously missing lifecycle integration and superclass declarations that formatting previously prevented.
- No new runtime application dependency is intended.
- Generated-source changes require explicit breaking-change release notes even though the recipe constructor and existing YAML configurations remain compatible.

## Acceptance Boundary

Regenerate both baseline and candidate from Estatio prod `4dd98637d572240ff99cb8c998590074c27c839d` with otherwise identical inputs and isolated dependency repositories.
Use JPA `907c723889d3306352c2e50cfbbae3df6406d949` only to reconcile historical output, not as an A/B input or correctness oracle.
Record exact baseline/candidate recipe coordinates, unique artifact versions, source commits, artifact checksums, environment, commands, logs, and outcomes.
Require an explained inventory of all changed classes, only intended superclass/listener changes and necessary imports, compilation of directly affected modules where the baseline supports it, and a successful final `mvn clean verify` for the candidate recipe artifact.
Candidate-specific build failures block library acceptance, while unrelated failures reproduced by the baseline must be recorded without bypassing checks or being reported as passes and remain a non-blocking consumer handoff.
Consumer handoff must include F04/Mallcomm listener-presence evidence for the inspected candidate artifact.
Runtime callback verification is not an acceptance or rollout gate imposed by this change, and the maintained BackgroundCommandsOrchestration fix remains separate.

## Non-goals

- Changing the maintained Estatio `BackgroundCommandsOrchestration` JPA source, which is excluded from all three Estatio rewrite passes and requires a separate downstream fix.
- Changing Estatio's custom listener implementation, audit semantics, persistence configuration, or other findings in the independent review.
- Enabling listener insertion indiscriminately on all subclasses or changing the global meaning of `allowInherited` for unrelated annotation recipes.
- Inferring additional JDO default identity policies beyond the existing explicit datastore-identity migration rule.
