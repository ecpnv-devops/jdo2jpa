package com.ecpnv.openrewrite.jdo2jpa;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;

import static org.openrewrite.java.Assertions.java;

class DependentRelationshipLifecycleTest extends BaseRewriteTest {

    private static final String DEFAULT_CASCADE =
            "CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(PARSER).recipe(new ReplacePersistentWithOneToManyAnnotation(DEFAULT_CASCADE));
    }

    @Test
    void dependentCollectionWithMandatoryInverseGetsRemoveAndOrphanRemoval() {
        rewriteRun(
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Column;
                                import javax.jdo.annotations.Persistent;
                                import javax.persistence.Entity;

                                @Entity
                                class Child {
                                    @Column(allowsNull = "false")
                                    private Parent parent;
                                }
                                @Entity
                                class Parent {
                                    @Persistent(mappedBy = "parent", dependentElement = "true")
                                    private List<Child> children;
                                }
                                """,
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Column;
                                import javax.persistence.CascadeType;
                                import javax.persistence.Entity;
                                import javax.persistence.FetchType;
                                import javax.persistence.OneToMany;

                                @Entity
                                class Child {
                                    @Column(allowsNull = "false")
                                    private Parent parent;
                                }
                                @Entity
                                class Parent {
                                    @OneToMany(mappedBy = "parent", cascade = {CascadeType.REMOVE, CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH}, orphanRemoval = true, fetch = FetchType.LAZY)
                                    private List<Child> children;
                                }
                                """
                )
        );
    }

    @Test
    void resolvesCollectionDependencyAcrossPersistentAndElementAnnotations() {
        rewriteRun(
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Element;
                                import javax.jdo.annotations.Persistent;
                                import javax.persistence.Entity;

                                @Entity
                                class Child {}
                                @Entity
                                class Parent {
                                    @Persistent(mappedBy = "parent")
                                    @Element(dependent = "true")
                                    private List<Child> children;
                                }
                                """,
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Element;
                                import javax.persistence.*;

                                @Entity
                                class Child {}
                                @Entity
                                class Parent {
                                    @OneToMany(mappedBy = "parent", cascade = {CascadeType.REMOVE, CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH}, orphanRemoval = true, fetch = FetchType.LAZY)
                                    @Element(dependent = "true")
                                    @JoinColumn()
                                    private List<Child> children;
                                }
                                """
                )
        );
    }

    @Test
    void elementDependencyTakesPrecedenceAndPersistentDependentIsIgnoredForCollections() {
        rewriteRun(
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Element;
                                import javax.jdo.annotations.Persistent;
                                import javax.persistence.Entity;

                                @Entity
                                class Child {}
                                @Entity
                                class Parent {
                                    @Persistent(mappedBy = "parent", dependentElement = "true")
                                    @Element(dependent = "false")
                                    private List<Child> contradictory;

                                    @Persistent(mappedBy = "parent", dependent = "true")
                                    private List<Child> singularFlagOnCollection;
                                }
                                """,
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Element;
                                import javax.persistence.*;

                                @Entity
                                class Child {}
                                @Entity
                                class Parent {
                                    @OneToMany(mappedBy = "parent", cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH}, fetch = FetchType.LAZY)
                                    @Element(dependent = "false")
                                    @JoinColumn()
                                    private List<Child> contradictory;

                                    @OneToMany(mappedBy = "parent", cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH}, fetch = FetchType.LAZY)
                                    private List<Child> singularFlagOnCollection;
                                }
                                """
                )
        );
    }

    @Test
    void elementOnlyDependencyRequiresRelationshipContext() {
        rewriteRun(
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Element;
                                import javax.persistence.Entity;

                                @Entity
                                class Child {}
                                @Entity
                                class Parent {
                                    @Element(dependent = "true", table = "PARENT_CHILD", column = "CHILD_ID")
                                    private List<Child> relatedChildren;

                                    @Element(dependent = "true")
                                    private List<Child> childrenWithoutRelationshipContext;
                                }
                                """,
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Element;
                                import javax.persistence.*;

                                @Entity
                                class Child {}
                                @Entity
                                class Parent {
                                    @JoinTable(name = "PARENT_CHILD",
                                            inverseJoinColumns = {@javax.persistence.JoinColumn(name = "CHILD_ID")})
                                    @OneToMany(cascade = {CascadeType.REMOVE, CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH}, orphanRemoval = true, fetch = FetchType.LAZY)
                                    private List<Child> relatedChildren;

                                    @Element(dependent = "true")
                                    private List<Child> childrenWithoutRelationshipContext;
                                }
                                """
                )
        );
    }

    @Test
    void generatedOneToOneUsesSingularDependencyWithDependentElementFallback() {
        rewriteRun(
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Persistent;
                                import javax.persistence.Entity;

                                @Entity
                                class Child {}
                                @Entity
                                class Parent {
                                    @Persistent(mappedBy = "owner", dependent = "true")
                                    private Child child;

                                    @Persistent(mappedBy = "owner", dependentElement = "true")
                                    private Child fallbackChild;
                                }
                                """,
                        """
                                import javax.persistence.CascadeType;
                                import javax.persistence.Entity;
                                import javax.persistence.FetchType;
                                import javax.persistence.OneToOne;

                                @Entity
                                class Child {}
                                @Entity
                                class Parent {
                                    @OneToOne(mappedBy = "owner", cascade = {CascadeType.REMOVE, CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH}, orphanRemoval = true, fetch = FetchType.LAZY)
                                    private Child child;

                                    @OneToOne(mappedBy = "owner", cascade = {CascadeType.REMOVE, CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH}, orphanRemoval = true, fetch = FetchType.LAZY)
                                    private Child fallbackChild;
                                }
                                """
                )
        );
    }

    @Test
    void defaultRemoveCascadeDoesNotImplyOrphanRemoval() {
        rewriteRun(spec -> spec.recipe(new ReplacePersistentWithOneToManyAnnotation(
                        "CascadeType.REMOVE, CascadeType.MERGE")),
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
                                    @OneToMany(mappedBy = "parent", cascade = {CascadeType.REMOVE, CascadeType.MERGE}, fetch = FetchType.LAZY)
                                    private List<Child> children;
                                }
                                """
                )
        );
    }

    @Test
    void dependencyDerivationIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
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
                                    @Persistent(mappedBy = "parent", dependentElement = "true")
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
                                    @OneToMany(mappedBy = "parent", cascade = {CascadeType.REMOVE, CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH}, orphanRemoval = true, fetch = FetchType.LAZY)
                                    private List<Child> children;
                                }
                                """
                )
        );
    }
}
