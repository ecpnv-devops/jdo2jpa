package com.ecpnv.openrewrite.jdo2jpa;

import org.junit.jupiter.api.Test;

import static org.openrewrite.java.Assertions.java;

class RelationshipCascadeDefaultsTest extends BaseRewriteTest {

    @Test
    void standardCompositionUsesReducedDefaultsAndIsIdempotent() {
        rewriteRun(spec -> spec.parser(PARSER)
                        .recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.Persistent.relationships")
                        .cycles(2)
                        .expectedCyclesThatMakeChanges(1),
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Persistent;
                                import javax.persistence.Entity;

                                @Entity
                                class Child {}

                                @Entity
                                class Parent {
                                    @Persistent
                                    private Child child;

                                    @Persistent(mappedBy = "parent", dependentElement = "true")
                                    private List<Child> children;
                                }
                                """,
                        """
                                import java.util.List;
                                import javax.persistence.*;

                                @Entity
                                class Child {}

                                @Entity
                                class Parent {
                                    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
                                    private Child child;

                                    @OneToMany(mappedBy = "parent", cascade = {CascadeType.REMOVE, CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = true, fetch = FetchType.LAZY)
                                    private List<Child> children;
                                }
                                """));
    }

    @Test
    void lowerLevelManyToOnePreservesExplicitRefresh() {
        rewriteRun(spec -> spec.parser(PARSER)
                        .recipe(new ReplacePersistentWithManyToOneAnnotation("CascadeType.REFRESH")),
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Persistent;
                                import javax.persistence.Entity;

                                @Entity
                                class Child {}

                                @Entity
                                class Parent {
                                    @Persistent
                                    private Child child;
                                }
                                """,
                        """
                                import javax.persistence.CascadeType;
                                import javax.persistence.Entity;
                                import javax.persistence.FetchType;
                                import javax.persistence.ManyToOne;

                                @Entity
                                class Child {}

                                @Entity
                                class Parent {
                                    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.REFRESH})
                                    private Child child;
                                }
                                """));
    }

    @Test
    void lowerLevelOneToManyPreservesExplicitDetachAndOrder() {
        rewriteRun(spec -> spec.parser(PARSER)
                        .recipe(new ReplacePersistentWithOneToManyAnnotation(
                                "CascadeType.DETACH, CascadeType.REFRESH")),
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Persistent;
                                import javax.persistence.Entity;

                                @Entity
                                class Child {}

                                @Entity
                                class Parent {
                                    @Persistent(mappedBy = "parent")
                                    private List<Child> children;
                                }
                                """,
                        """
                                import java.util.List;
                                import javax.persistence.CascadeType;
                                import javax.persistence.Entity;
                                import javax.persistence.FetchType;
                                import javax.persistence.OneToMany;

                                @Entity
                                class Child {}

                                @Entity
                                class Parent {
                                    @OneToMany(mappedBy = "parent", cascade = {CascadeType.DETACH, CascadeType.REFRESH}, fetch = FetchType.LAZY)
                                    private List<Child> children;
                                }
                                """));
    }
}
