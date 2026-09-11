## Context

The composed `com.ecpnv.openrewrite.jdo2jpa.v2x.Persistent.relationships` recipe currently passes `CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH` as `defaultCascade` to both relationship-conversion recipes.
This makes graph-wide refresh and explicit-detach propagation universal consequences of migration rather than relationship-specific decisions.
JDO refresh does not imply equivalent traversal, and Causeway normally scopes managed entities to an interaction or persistence context rather than directly detaching selected roots.
A downstream Mallcomm test demonstrated that `REFRESH` on a bidirectional lazy collection can cause EclipseLink to materialize a large relationship graph during association resolution and exhaust a 4 GiB heap.
Removing only `REFRESH` from that collection made the test complete successfully in approximately 23 seconds while `DETACH` remained, establishing the refresh cascade as an active risk and leaving no demonstrated need for universal detach propagation.

## Goals / Non-Goals

**Goals:**

- Make the standard composed migration emit only `PERSIST` and `MERGE` as universal cascade defaults.
- Preserve dependency-derived `REMOVE` and orphan-removal behavior.
- Align tests, examples, and documentation with the safer default.
- Retain lower-level recipe configurability for consumers with deliberate relationship-specific cascade requirements.

**Non-Goals:**

- Do not prohibit callers from explicitly passing `CascadeType.REFRESH`, `CascadeType.DETACH`, or other legal cascade members through `defaultCascade`.
- Do not infer relationship-specific refresh or detach semantics from JDO metadata.
- Do not change fetch strategy, relationship cardinality, ownership, or orphan-removal rules.
- Do not rewrite already-generated JPA source independently of rerunning the migration recipe.

## Decisions

### Change the composed recipe configuration rather than filter cascade values in Java

The two `defaultCascade` entries in `datanucleus-jdo-to-jpa-eclipselink.yml` will become `CascadeType.PERSIST, CascadeType.MERGE`.
The Java recipes will continue to emit their configured `defaultCascade` unchanged.
This keeps the lower-level recipes general-purpose and makes the behavioral policy explicit at the composition layer where the unsafe universal default originates.

Filtering `REFRESH` and `DETACH` inside the Java recipes was rejected because it would silently override explicit caller intent and contradict the existing configurable API.

### Preserve dependency-derived removal independently

Dependent relationships will continue to prepend `CascadeType.REMOVE`, with `orphanRemoval = true` where supported, according to `dependent-collection-lifecycle`.
This change concerns only the non-`REMOVE` members supplied by the standard composition.

Removing all cascade values was rejected because `PERSIST` and `MERGE` support aggregate creation and reattachment workflows and no evidence currently shows that those defaults cause the graph-loading failure.

### Update standard-composition expectations broadly while retaining explicit-configuration coverage

Expected outputs produced through the standard recipe will omit `REFRESH` and `DETACH` for generated many-to-one, one-to-one, and one-to-many mappings.
Focused lower-level tests will continue to prove that explicitly configured cascade members are preserved verbatim, including `REFRESH` and `DETACH` where useful to establish compatibility.

This separation prevents the test suite from accidentally turning an old composition default into a permanent lower-level API requirement.

A dedicated standard-composition fixture will run for two cycles and require only the first cycle to change source.
This pins idempotence specifically for the reduced defaults rather than relying on unrelated rerun coverage elsewhere in the suite.

### Isolate the generated-source change with same-input A/B regeneration

The acceptance comparison will use pinned clean Estatio `prod` commit `4dd98637d572240ff99cb8c998590074c27c839d` as the source for both outputs.
A pre-change jdo2jpa artifact and the candidate artifact will be independently selectable through distinct versions or isolated local Maven repositories.
Each artifact will run in its own detached worktree with the same Estatio rewrite commands.

The A/B semantic delta must consist only of removing universally supplied `CascadeType.REFRESH` and `CascadeType.DETACH` members plus directly consequent annotation formatting.
The comparison must prove that `PERSIST`, `MERGE`, dependency-derived `REMOVE`, `orphanRemoval`, fetch strategy, cardinality, ownership, and unrelated annotations remain unchanged.
It will inventory affected mappings by relationship shape and inspect the representative Mallcomm collection that motivated the change.

Candidate output will also be compared separately with recorded Estatio JPA commit `907c723889d3306352c2e50cfbbae3df6406d949` as informational reconciliation evidence.
Differences caused by consumer-source drift or previously approved stream-order, dependency, reference-fetch, and LOB changes must be classified separately and must not be treated as this change's acceptance delta.
Generated-source comparison proves metadata structure only and does not prove runtime refresh or detach behavior.

### Treat downstream regeneration as a visible migration

Release notes and project documentation will state that rerunning the standard recipe removes universal refresh and detach propagation from newly generated annotations.
They will record the production motivation, the same-input A/B inventory, the candidate artifact version, and the final repository verification result.
Downstream projects requiring either behavior must add it intentionally through recipe configuration or post-migration customization.
Estatio must rerun representative workflows that deliberately refresh or detach graph roots after adoption; those application-level checks are a consumer handoff rather than claims made by recipe tests.

## Risks / Trade-offs

- [A downstream workflow implicitly relies on cascaded refresh] → Require that workflow to declare `REFRESH` deliberately and add application-level coverage for it.
- [A downstream caller explicitly detaches roots and expects graph-wide detach] → Preserve support through explicit `defaultCascade` configuration and document the default change.
- [Large expected-output churn obscures regressions] → Separate standard-composition tests from explicit-configuration tests and use same-input pre-change/candidate regeneration to review changes by relationship shape.
- [Same-coordinate snapshot replacement contaminates A/B evidence] → Use distinct artifact versions or isolated local Maven repositories and retain the exact artifact and consumer commit identifiers.
- [Removing `DETACH` has no currently observable effect] → Prefer absence over speculative universal semantics, while retaining an explicit opt-in path and requiring consumer-owned checks for workflows that deliberately detach roots.
- [Generated applications retain old annotations until rewritten] → Document that adoption requires rerunning the recipe and reviewing the generated diff.
