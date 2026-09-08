## Context

`AddSortedMethodToStreamMethods` currently visits methods carrying the configured annotation, returning `Stream`, and consisting of a leading return statement whose expression is a method invocation.
It appends natural-order `.sorted()` unless an actual `sorted` invocation is already present in the visible invocation chain.

That structural guard handles direct chains such as `getItems().stream().sorted(comparator).map(...)`.
It cannot see ordering established inside a helper method.
Estatio's `streamCurrentOrPreviousTenantInvoiceAddresses(...)`, for example, delegates to a helper that materializes a comparator-sorted list and returns a new stream.
The outer invocation chain contains no visible `sorted` call, so the default migration appends natural sorting and overrides the helper's effective preference order before `findFirst()`.

The original recipe registration sits next to collection-type migration, but the current composite does not demonstrate an ordered-to-unordered conversion.
It converts a JPA `@OneToMany` field declared as `SortedSet` to `TreeSet` while leaving related methods unchanged, specifically preserving sorted collection behavior.
JDO `@Order` is translated to JPA `@OrderColumn`, which likewise expresses order rather than discarding it.
Ordinary `Set` iteration was not ordered by the JDO contract, while `List`, insertion-ordered sets, and sorted sets can already have meaningful encounter order.

## Goals / Non-Goals

**Goals:**

- Prevent the default migration from inventing natural ordering without evidence that migration discarded source ordering.
- Preserve explicit comparators and effective encounter order established by collection implementations, helper methods, and stream operations.
- Define a strong eligibility rule for any future ordering compensation.
- Keep existing users of the standalone recipe source-compatible during deprecation.
- Cover the default composite with representative ordering-regression fixtures.

**Non-Goals:**

- Infer ordering contracts by analyzing arbitrary helper implementations or call graphs.
- Treat every collection stream as naturally ordered.
- Redesign JPA collection mappings unrelated to an observed ordering loss.
- Modify consuming application business code.
- Introduce runtime dependencies or ORM conditionals.

## Decisions

### Require demonstrated ordering loss

The default migration may synthesize stream ordering only when the same migration can identify an ordered source, identify the transformation that loses that order, and reproduce the original ordering semantics.
A direct `Collection.stream()` call is not sufficient evidence because collection types carry different encounter-order contracts.

Any future compensation must be coupled to the order-losing transformation through explicit metadata, a marker, or a dedicated recipe that retains the source ordering information.
If the original comparator or order expression cannot be reproduced, the migration must leave the stream unchanged and report or document the ambiguity rather than substitute natural order.

### Remove blanket sorting from the default composite

`AddSortedMethodToStreamMethods` will be removed from `com.ecpnv.openrewrite.jdo2jpa.v2x.Persistent` in `datanucleus-jdo-to-jpa-eclipselink.yml`.
No current step in that composite establishes the evidence required for synthetic natural ordering.

This removes unsafe sorting from opaque helper returns and also avoids changing legitimate encounter order from other stream sources.
The alternative of narrowing the recipe to direct collection streams was rejected because it would still alter ordered lists, insertion-ordered sets, custom-comparator sorted sets, and explicitly unordered pipelines.

### Preserve explicit and effective source semantics

The default migration will not add `.sorted()` to helper-returned streams or to pipelines containing `flatMap` merely because their final type is `Stream`.
It will not add natural sorting to `List` or insertion-ordered `Set` streams because doing so changes their encounter order.
It will not add natural sorting to `SortedSet` streams because their comparator may differ from natural order.
It will not add natural sorting to ordinary `Set` streams because both the source and target contracts leave their encounter order unspecified.
It will not add natural sorting after `.unordered()` because that operation explicitly removes the ordering constraint.

### Retain and deprecate the standalone recipe

The standalone `AddSortedMethodToStreamMethods` class will remain available to avoid an immediate compatibility break for callers that invoke it explicitly.
It will be marked deprecated, and its description and documentation will state that it unconditionally introduces natural ordering for matching methods and is not safe as a general migration default.
Its existing direct behavior remains covered by unit tests during the deprecation period.

Deleting or silently changing the standalone recipe was rejected because explicit external consumers may rely on its named behavior.
Removal can be considered in a later major release.

### Test the composite boundary

Regression coverage will exercise the default composite or the smallest declarative recipe containing the former registration rather than testing only the standalone recipe.
Fixtures will cover an opaque helper with comparator-defined ordering, a `flatMap` pipeline, a custom-comparator `SortedSet`, an ordered `List`, an insertion-ordered set, an ordinary set, and `.unordered()`.
Each fixture will assert that no synthetic `.sorted()` is introduced.

A collection-conversion fixture will also document that the current `SortedSet` migration preserves an ordered implementation and therefore does not trigger compensation.

## Risks / Trade-offs

- [Risk] Consuming projects may have come to rely on accidentally injected natural ordering for otherwise unordered sets.
  → Mitigation: regenerate representative consumers, inventory removed calls, and require genuine business ordering to be explicit in source.
- [Risk] Removing generated sorting can expose nondeterministic behavior that the source JDO application already left unspecified.
  → Mitigation: treat this as a source-contract gap and add an explicit comparator where deterministic behavior is required.
- [Risk] External callers may continue using the deprecated standalone recipe unsafely.
  → Mitigation: provide a clear deprecation message and remove it from all bundled default composites.
- [Risk] A future collection transformation may genuinely lose ordering.
  → Mitigation: require that transformation to carry enough source metadata to reproduce the original order rather than reintroducing a blanket post-processing rule.

## Migration Plan

Remove the standalone recipe registration from the default composite and release the corrected recipe bundle.
Update consuming projects to that release and regenerate affected JPA sources.
Review removed `.sorted()` calls and make ordering explicit in source wherever the business contract genuinely requires it.
Run consuming application ordering tests, including Estatio's tenant invoice-address selection scenario.
Rollback consists of pinning the prior recipe version while explicit source-ordering requirements are added.

## Open Questions

- Which release should remove the deprecated standalone recipe if no explicit consumers remain?
