> **DRAFT.** Parked from finding 9 in `JDO-JPA-EQUIVALENCE-REVIEW-PI-CONSOLIDATED.md`.
> Promote this draft to the sole active OpenSpec change before implementation.

## Why

Prod's `src/main/resources/META-INF/rewrite/post-jpa.yml` contains class-name and annotation-text-specific workarounds for broader transformation gaps.
These rules fail open when a class is renamed, an annotation expression changes, or a new subclass or relationship is introduced.
At least one current rule does not match its apparent target, with correctness supplied accidentally by a JPA default.

## What Changes

- Inventory each by-name post-JPA workaround and record the generic semantic condition it represents.
- Implement reusable recipes in `jdo2jpa` for inheritance propagation, incorrect `@Transient` removal, association inference, identifier naming, and hierarchy-constraint placement where practical.
- Add recipe fixtures that cover existing targets plus new classes with different names but equivalent structure.
- Remove each prod workaround only after the semantic recipe reproduces its intended output.
- Pair any unavoidable targeted workaround with a JPA architecture rule that fails when an equivalent new class is unhandled.
- Add a generation-level comparison that detects dormant or newly unmatched post-processing rules.

## Scope

This is a coordinated change across `jdo2jpa` and prod.
Generic transformation behaviour belongs in `jdo2jpa`, while prod retains only application-specific exceptions and enforcement rules.
The work must not be implemented as runtime ORM branching.

The primary edit site is this `jdo2jpa` repository.
The consuming changes belong in the Estatio `prod` worktree at `~/repos/gitlab/ecpnv.devops/apps/estatio/estatio/prod` and include the `jdo2jpa.version` bump and `src/main/resources/META-INF/rewrite/post-jpa.yml` cleanup.
Sequence the repositories so each prod workaround is deleted only when its released upstream replacement is consumed, otherwise the regenerated `jpa` branch loses the fix in between.

## Depends on

The "pair any unavoidable targeted workaround with a JPA architecture rule" step requires
architecture rules to exist. **Finding 8 has no proposal**, yet `estatio-base-archtestjpa` currently
declares no rules at all — so that enforcement mechanism is not available. Findings 1, 2 and 3 also
reference architecture or generation-level checks. A proposal for finding 8 should be raised and
land first; it is cheap (the rules largely mirror `archtestjdo`, and `javax.persistence` is already
on prod's main compile classpath), and CI already runs `mvn_archtest.sh` over the rewritten sources
before pushing to `jpa`, which makes it a gate on the rewrite itself.

## Validation

- Every existing workaround is classified as semantic, application-specific, obsolete, or intentionally retained.
- Semantic recipe tests pass for both existing and differently named fixtures.
- Regenerated JPA source retains all currently required annotations and removes obsolete post-processing entries.
- Architecture tests detect any retained exception that stops matching future source.

## Open Questions

- Which post-JPA rules encode genuinely application-specific intent that cannot be inferred from JDO metadata or Java structure?
- Should migration occur in one atomic regeneration or as a sequence of independently reviewable recipe replacements?
