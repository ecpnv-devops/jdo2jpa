# Estatio comparison prerequisites

These are explicit consumer/harness adjustments, not transformations performed by the recipe library.
They are applied identically to the disposable baseline and candidate inputs.
The input commit remains pinned; every checkout adjustment is recorded separately.
The maintained Estatio `ReadOnly` configuration correction is committed as `74709138e1`; the additional comparison-only dependency amendments below still require consumer adoption.

## Reproducible input patch

`entity-listener-evidence/consumer-input-adjustments.patch` captures the symmetric consumer POM corrections against clean Estatio input `4dd98637d572240ff99cb8c998590074c27c839d`.
Apply it only to that clean pinned input, not blindly to an already modified checkout.
The patch is checked against the pinned Git tree, and its bytes must match both comparison sides.
The final patch includes all recorded round6 amendments and was rechecked against both completed trees.

## Additional baseline-reproduced dependency gaps

| Consumer | Missing dependency / correction |
| --- | --- |
| `bankmandate`, `treso2incomingorchestrations`, `invoiceforlease`, `leasecontract`, `admindashboard` | Explicit PDF.js API in the JPA profile |
| `lease`, `fastnet` | Explicit FullCalendar API in the JPA profile; Fastnet needs it through the lease entity hierarchy |
| `invoiceforlease` | `org.springframework:spring-web:5.3.31` for the used `HttpHeaders.CONTENT_DISPOSITION` constant |
| `contracttolease-tests`, `fastnet-tests`, `asset-tests` | Explicit `p6spy-spring-boot-starter:1.7.1`, preserving the resolved JPA version while making installed fixture POMs consumable outside their producers' profiles |
| `estatio-webapp` | Literal `jar` packaging, matching the pinned v2 build and removing the profile-dependent recursive `${packaging}` expression from the published POM |

The extension API versions match the pinned Isis runtime version recorded in the PDF.js example below.
A parent module compiling successfully does not prove its published profile-dependent POM exposes every API needed by a consumer.
Each failure was retained on both sides, affected sources were restored before regeneration, and amendments were applied symmetrically.
The producer metadata corrections required rebuilding and reinstalling the affected producers before reprocessing `datawarehouse`, `webapp-tests`, or `report`.
No failed check was suppressed.

## Listener identity

Set this Maven project property before recipe loading:

```xml
<jdo2jpa.entityListenerClass>org.estatio.base.prod.integration.OrmEntityListener</jdo2jpa.entityListenerClass>
```

The old baseline ignores the new property and retains its existing listener-replacement pass.
Candidate5 directly uses the final listener identity, so inherited registrations remain recognisable on subsequent passes.
Round5 Maven logs confirm the property resolves to the intended FQN.

## EclipseLink read-only mappings

Activate `com.ecpnv.openrewrite.jdo2jpa.v2x.eclipselink` before `com.ecpnv.openrewrite.jdo2jpa.v2x` in the first rewrite profile.
The existing provider recipe converts DataNucleus `ReadOnly` to `org.eclipse.persistence.annotations.ReadOnly`; the general recipe does not select that provider-specific conversion automatically.
Retain `org.datanucleus:datanucleus-api-jdo:6.0.1` alongside `javax.jdo:jdo-api:3.2.1` in all three rewrite-only profiles so the old annotation remains attributable after parent artifacts become JPA.
Neither input dependency belongs in the JPA compilation profile.

Activating the provider recipe alone left the annotation unchanged in the baseline capex diagnostic.
With both activation and input attribution corrected, all three rewrite passes and clean JPA compilation passed, and the generated import was `org.eclipse.persistence.annotations.ReadOnly`.
Evidence: round5 `readonly-activation-diagnostic.json` and `readonly-input-api-diagnostic.json`.
These diagnostics do not replace a fresh full comparison with identical corrected inputs.

## Input attribution and clean builds

Retain `javax.jdo:jdo-api:3.2.1` explicitly in each rewrite profile while parent artifacts are progressively installed as JPA.
Do not add it to the JPA compilation profile merely to make unconverted annotations compile.
Clean each module before JPA installation so old JDO-generated QueryDSL sources do not shadow the installed JPA metamodel.
Preserve parent-first ordering and the error guard.

## Baseline PDF.js dependency failure

The pinned `BankMandate_pdf.java` imports PDF.js annotations and configuration classes.
The baseline JPA dependency graph did not contain their API artifact, and `bankmandate` compilation failed before Mallcomm could be validated.
The original Mallcomm preflight reactor includes `bankmandate`, so this is not an unrelated branch that can simply be skipped.

A diagnostic baseline clean installation passed after adding this declaration to `estatio-shared-kernel/bankmandate/pom.xml`:

```xml
<profile>
    <id>jpa</id>
    <dependencies>
        <dependency>
            <groupId>org.apache.isis.extensions</groupId>
            <artifactId>isis-extensions-pdfjs-applib</artifactId>
            <version>2.2.0.20260826-1810.maintenance-branch.a08bda89</version>
        </dependency>
    </dependencies>
</profile>
```

That version matches the resolved Isis applib in this pinned comparison; it is not a recommendation to mix framework versions in other builds.
The diagnostic ran the normal clean installation without introducing additional skip flags or changing Java source.
Round5 applies the declaration to both sides before processing that module.
The Estatio application maintainer owns adopting an appropriate permanent dependency declaration.

Evidence is recorded under `/tmp/f04-acceptance-401d6c4/round3/baseline-bankmandate-explicit-pdfjs.json` and the round5 `pdfjs-input-adjustment.json`.
A passing diagnostic or corrected dependency declaration does not replace the remaining complete regeneration, comparison, compilation, and runtime evidence.
