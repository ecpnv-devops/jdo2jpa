package com.ecpnv.openrewrite.jdo2jpa;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;

import static org.openrewrite.java.Assertions.java;

class PreserveScalarDefaultFetchGroupTest extends BaseRewriteTest {

    @Nested
    class Isolated extends BaseRewriteTest {
        @Override
        public void defaults(RecipeSpec spec) {
            spec.parser(PARSER).recipe(new PreserveScalarDefaultFetchGroup());
        }

        @Test
        void addsLazyBasicToExcludedFieldsAndProperties() {
            rewriteRun(
                    //language=java
                    java(
                            """
                                    import javax.jdo.annotations.Persistent;

                                    class Payload {
                                        @Persistent(defaultFetchGroup = "false")
                                        private String text;

                                        @Persistent(defaultFetchGroup = "false")
                                        public byte[] getBytes() {
                                            return null;
                                        }
                                    }
                                    """,
                            """
                                    import javax.jdo.annotations.Persistent;
                                    import javax.persistence.Basic;
                                    import javax.persistence.FetchType;

                                    class Payload {
                                        @Basic(fetch = FetchType.LAZY)
                                        @Persistent(defaultFetchGroup = "false")
                                        private String text;

                                        @Basic(fetch = FetchType.LAZY)
                                        @Persistent(defaultFetchGroup = "false")
                                        public byte[] getBytes() {
                                            return null;
                                        }
                                    }
                                    """));
        }

        @Test
        void updatesExistingBasicAndPreservesOtherAttributes() {
            rewriteRun(
                    //language=java
                    java(
                            """
                                    import javax.jdo.annotations.Persistent;
                                    import javax.persistence.Basic;
                                    import javax.persistence.FetchType;

                                    class Payload {
                                        @Basic(optional = false, fetch = FetchType.EAGER)
                                        @Persistent(defaultFetchGroup = "false")
                                        private String text;

                                        @Basic(fetch = FetchType.LAZY)
                                        @Persistent(defaultFetchGroup = "false")
                                        private String alreadyLazy;
                                    }
                                    """,
                            """
                                    import javax.jdo.annotations.Persistent;
                                    import javax.persistence.Basic;
                                    import javax.persistence.FetchType;

                                    class Payload {
                                        @Basic(optional = false, fetch = FetchType.LAZY)
                                        @Persistent(defaultFetchGroup = "false")
                                        private String text;

                                        @Basic(fetch = FetchType.LAZY)
                                        @Persistent(defaultFetchGroup = "false")
                                        private String alreadyLazy;
                                    }
                                    """));
        }

        @Test
        void leavesNonExcludedScalarsUnchanged() {
            rewriteRun(
                    //language=java
                    java(
                            """
                                    import javax.jdo.annotations.Persistent;

                                    class Payload {
                                        @Persistent(defaultFetchGroup = "true")
                                        private String eager;

                                        @Persistent
                                        private String implicit;

                                        private String unannotated;
                                    }
                                    """));
        }

        @Test
        void skipsRelationshipsCollectionsAndMaps() {
            rewriteRun(
                    //language=java
                    java(
                            """
                                    import java.util.List;
                                    import java.util.Map;
                                    import javax.jdo.annotations.Persistent;
                                    import javax.persistence.Entity;
                                    import javax.persistence.ManyToOne;

                                    @Entity
                                    class Person {}

                                    class Payload {
                                        @Persistent(defaultFetchGroup = "false")
                                        private Person entityReference;

                                        @ManyToOne
                                        @Persistent(defaultFetchGroup = "false")
                                        private Person convertedReference;

                                        @Persistent(defaultFetchGroup = "false")
                                        private List<String> values;

                                        @Persistent(defaultFetchGroup = "false")
                                        private Map<String, String> valuesByName;
                                    }
                                    """));
        }
    }

    @Nested
    class PersistentComposite extends BaseRewriteTest {
        @Override
        public void defaults(RecipeSpec spec) {
            spec.parser(PARSER).recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.Persistent");
        }

        @Test
        void convertsRelationshipsThenScalarAndThenCleansUpInOneCycle() {
            rewriteRun(
                    //language=java
                    java(
                            """
                                    import java.util.List;
                                    import javax.jdo.annotations.Persistent;
                                    import javax.persistence.Entity;

                                    @Entity
                                    class Person {}

                                    @Entity
                                    class Payload {
                                        @Persistent(defaultFetchGroup = "false")
                                        private String text;

                                        @Persistent(defaultFetchGroup = "false")
                                        private Person owner;

                                        @Persistent(mappedBy = "owner", defaultFetchGroup = "false")
                                        private List<Person> people;
                                    }
                                    """,
                            """
                                    import java.util.List;
                                    import javax.persistence.*;

                                    @Entity
                                    class Person {}

                                    @Entity
                                    class Payload {
                                        @Basic(fetch = FetchType.LAZY)
                                        private String text;

                                        @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH})
                                        private Person owner;

                                        @OneToMany(mappedBy = "owner", cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH}, fetch = FetchType.LAZY)
                                        private List<Person> people;
                                    }
                                    """));
        }
    }
}
