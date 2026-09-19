## MODIFIED Requirements

### Requirement: Inferred owning one-to-one provider exception is explicit
An inferred owning `@OneToOne` without `@Persistent` metadata SHALL declare `FetchType.LAZY`, following the same JDO default-fetch-group truth table as an ordinary unannotated to-one reference. There is no EclipseLink-deletion-workaround exception for this case.
Explicit JDO default-fetch-group metadata SHALL still take precedence over that default.

#### Scenario: Bare reference becomes owning one-to-one
- **WHEN** a relationship without `@Persistent` is identified as the owning side of a bidirectional one-to-one
- **THEN** the generated `@OneToOne` declares `fetch = FetchType.LAZY`

#### Scenario: Owning one-to-one explicitly requests lazy fetch
- **WHEN** an owning one-to-one source relationship declares `defaultFetchGroup = "false"`
- **THEN** the generated `@OneToOne` declares `fetch = FetchType.LAZY`

#### Scenario: Owning one-to-one explicitly requests eager fetch
- **WHEN** an owning one-to-one source relationship declares `defaultFetchGroup = "true"`
- **THEN** the generated `@OneToOne` declares `fetch = FetchType.EAGER`

#### Scenario: Owning one-to-one has Persistent without fetch-group metadata
- **WHEN** an owning one-to-one source relationship has `@Persistent` but omits `defaultFetchGroup`
- **THEN** the generated `@OneToOne` declares `fetch = FetchType.LAZY`
