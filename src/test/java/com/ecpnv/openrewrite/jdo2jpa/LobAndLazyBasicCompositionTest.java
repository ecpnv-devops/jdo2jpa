package com.ecpnv.openrewrite.jdo2jpa;

import org.junit.jupiter.api.Test;

import static org.openrewrite.java.Assertions.java;

class LobAndLazyBasicCompositionTest extends BaseRewriteTest {

    private static final String BEFORE = """
            import javax.jdo.annotations.Column;
            import javax.jdo.annotations.Persistent;
            import javax.persistence.Convert;
            import javax.persistence.Entity;

            @Entity
            class Payload {
                @Persistent(defaultFetchGroup = "false")
                @Column(name = "payload_bytes", allowsNull = "true", jdbcType = "BLOB", sqlType = "BLOB")
                @Convert
                @DomainMarker
                private byte[] bytes;
            }

            @interface DomainMarker {}
            """;

    private static final String AFTER = """
            import javax.persistence.*;

            @Entity
            class Payload {
                @Basic(fetch = FetchType.LAZY)
                @Column(name = "payload_bytes")
                @Convert
                @DomainMarker
                @Lob
                private byte[] bytes;
            }

            @interface DomainMarker {}
            """;

    @Test
    void supportedConsumerOrderPreservesMetadataOnRerun() {
        rewriteRun(spec -> spec.parser(PARSER)
                        .recipeFromResources(
                                "com.ecpnv.openrewrite.jdo2jpa.v2x.Persistent",
                                "com.ecpnv.openrewrite.jdo2jpa.v2x.Column")
                        .cycles(2)
                        .expectedCyclesThatMakeChanges(1),
                //language=java
                java(BEFORE, AFTER));
    }

    @Test
    void lobStorageMetadataDoesNotMakeDefaultOrEagerScalarsLazy() {
        rewriteRun(spec -> spec.parser(PARSER)
                        .recipeFromResources(
                                "com.ecpnv.openrewrite.jdo2jpa.v2x.Persistent",
                                "com.ecpnv.openrewrite.jdo2jpa.v2x.Column"),
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Column;
                                import javax.jdo.annotations.Persistent;
                                import javax.persistence.Entity;

                                @Entity
                                class Payload {
                                    @Persistent(defaultFetchGroup = "true")
                                    @Column(name = "eager_bytes", jdbcType = "BLOB")
                                    private byte[] eagerBytes;

                                    @Persistent
                                    @Column(name = "implicit_text", jdbcType = "CLOB")
                                    private String implicitText;

                                    @Column(name = "unannotated_bytes", jdbcType = "BLOB")
                                    private byte[] unannotatedBytes;
                                }
                                """,
                        """
                                import javax.persistence.Column;
                                import javax.persistence.Entity;
                                import javax.persistence.Lob;

                                @Entity
                                class Payload {
                                    @Column(name = "eager_bytes")
                                    @Lob
                                    private byte[] eagerBytes;

                                    @Column(name = "implicit_text")
                                    @Lob
                                    private String implicitText;

                                    @Column(name = "unannotated_bytes")
                                    @Lob
                                    private byte[] unannotatedBytes;
                                }
                                """));
    }

    @Test
    void topLevelCompositeProducesTheSameMetadata() {
        rewriteRun(spec -> spec.parser(PARSER)
                        .recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x")
                        .cycles(2)
                        .expectedCyclesThatMakeChanges(1),
                //language=java
                java(BEFORE, AFTER));
    }
}
