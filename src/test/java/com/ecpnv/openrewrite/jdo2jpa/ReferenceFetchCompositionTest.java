package com.ecpnv.openrewrite.jdo2jpa;

import org.junit.jupiter.api.Test;

import static org.openrewrite.java.Assertions.java;

class ReferenceFetchCompositionTest extends BaseRewriteTest {

    @Test
    void optionalCompositePreservesExplicitFetchBroadly() {
        rewriteRun(spec -> spec.parser(PARSER)
                        .recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.optional"),
                //language=java
                java(
                        """
                                import javax.persistence.Entity;
                                import javax.persistence.FetchType;
                                import javax.persistence.ManyToOne;

                                class EntityAbstract {}
                                @Entity
                                class Related {}
                                @Entity
                                class PlainEntity {
                                    @ManyToOne(fetch = FetchType.LAZY)
                                    private Related lazy;
                                    @ManyToOne(fetch = FetchType.EAGER)
                                    private Related eager;
                                }
                                @Entity
                                class EntityWithBase extends EntityAbstract {
                                    @ManyToOne(fetch = FetchType.LAZY)
                                    private Related lazy;
                                    @ManyToOne(fetch = FetchType.EAGER)
                                    private Related eager;
                                }
                                """
                )
        );
    }

    @Test
    void consumerOrderCompositionRetainsResolvedFetchOnRerun() {
        rewriteRun(spec -> spec.parser(PARSER)
                        .recipeFromResources(
                                "com.ecpnv.openrewrite.jdo2jpa.v2x.Persistent",
                                "com.ecpnv.openrewrite.jdo2jpa.v2x.optional")
                        .cycles(2)
                        .expectedCyclesThatMakeChanges(1),
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Persistent;
                                import javax.persistence.Entity;

                                @Entity
                                class Related {}
                                @Entity
                                class Owner {
                                    @Persistent(defaultFetchGroup = "false")
                                    private Related lazy;
                                    @Persistent(defaultFetchGroup = "true")
                                    private Related eager;
                                }
                                """,
                        """
                                import javax.persistence.CascadeType;
                                import javax.persistence.Entity;
                                import javax.persistence.FetchType;
                                import javax.persistence.ManyToOne;

                                @Entity
                                class Related {}
                                @Entity
                                class Owner {
                                    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH})
                                    private Related lazy;
                                    @ManyToOne(fetch = FetchType.EAGER, cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH})
                                    private Related eager;
                                }
                                """
                )
        );
    }
}
