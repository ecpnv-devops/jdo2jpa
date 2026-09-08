package com.ecpnv.openrewrite.jdo2jpa;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.SourceSpec;
import org.openrewrite.test.SourceSpecs;

import static org.openrewrite.java.Assertions.java;

class DefaultCompositeStreamOrderingTest extends BaseRewriteTest {

    @Test
    void preservesExistingStreamEncounterOrder() {
        rewriteRun(spec -> spec.parser(PARSER)
                        .recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.Persistent"),
                programmaticAnnotation(),
                //language=java
                java(
                        """
                                package a;

                                import java.util.ArrayList;
                                import java.util.Comparator;
                                import java.util.HashSet;
                                import java.util.LinkedHashSet;
                                import java.util.List;
                                import java.util.Set;
                                import java.util.SortedSet;
                                import java.util.TreeSet;
                                import java.util.stream.Stream;

                                import org.apache.isis.applib.annotation.Programmatic;

                                class SomeClass {
                                    private final List<String> orderedList = new ArrayList<>();
                                    private final Set<String> insertionOrderedSet = new LinkedHashSet<>();
                                    private final Set<String> ordinarySet = new HashSet<>();
                                    private final SortedSet<String> naturallyOrderedSet = new TreeSet<>();
                                    private final SortedSet<String> comparatorOrderedSet =
                                            new TreeSet<>(Comparator.reverseOrder());

                                    private Stream<String> helperOrdered() {
                                        return ordinarySet.stream().sorted(Comparator.reverseOrder());
                                    }

                                    @Programmatic
                                    Stream<String> delegatedStream() {
                                        return helperOrdered();
                                    }

                                    @Programmatic
                                    Stream<String> orderedListStream() {
                                        return orderedList.stream();
                                    }

                                    @Programmatic
                                    Stream<String> insertionOrderedSetStream() {
                                        return insertionOrderedSet.stream();
                                    }

                                    @Programmatic
                                    Stream<String> ordinarySetStream() {
                                        return ordinarySet.stream();
                                    }

                                    @Programmatic
                                    Stream<String> naturallyOrderedSetStream() {
                                        return naturallyOrderedSet.stream();
                                    }

                                    @Programmatic
                                    Stream<String> comparatorOrderedSetStream() {
                                        return comparatorOrderedSet.stream();
                                    }

                                    @Programmatic
                                    Stream<String> flattenedStream() {
                                        return orderedList.stream().flatMap(Stream::of);
                                    }

                                    @Programmatic
                                    Stream<String> visiblyComparatorSortedStream() {
                                        return ordinarySet.stream().sorted(Comparator.reverseOrder());
                                    }

                                    @Programmatic
                                    Stream<String> explicitlyUnorderedStream() {
                                        return orderedList.stream().unordered();
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void sortedSetFieldMigrationRetainsItsTreeSetOrderingWithoutStreamCompensation() {
        rewriteRun(spec -> spec.parser(PARSER)
                        .recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.Persistent"),
                programmaticAnnotation(),
                //language=java
                java(
                        """
                                package a;

                                import java.util.Comparator;
                                import java.util.SortedSet;
                                import java.util.TreeSet;
                                import java.util.stream.Stream;

                                import javax.persistence.OneToMany;

                                import org.apache.isis.applib.annotation.Programmatic;

                                class SomeClass {
                                    @OneToMany
                                    private SortedSet<String> items = new TreeSet<>(Comparator.reverseOrder());

                                    @Programmatic
                                    Stream<String> streamItems() {
                                        return items.stream();
                                    }
                                }
                                """,
                        """
                                package a;

                                import java.util.Comparator;
                                import java.util.SortedSet;
                                import java.util.TreeSet;
                                import java.util.stream.Stream;

                                import javax.persistence.OneToMany;

                                import org.apache.isis.applib.annotation.Programmatic;

                                class SomeClass {
                                    @OneToMany
                                    private TreeSet<String> items = new TreeSet<>(Comparator.reverseOrder());

                                    @Programmatic
                                    Stream<String> streamItems() {
                                        return items.stream();
                                    }
                                }
                                """
                )
        );
    }

    private static SourceSpecs programmaticAnnotation() {
        return java(
                """
                        package org.apache.isis.applib.annotation;

                        import java.lang.annotation.ElementType;
                        import java.lang.annotation.Retention;
                        import java.lang.annotation.RetentionPolicy;
                        import java.lang.annotation.Target;

                        @Target(ElementType.METHOD)
                        @Retention(RetentionPolicy.RUNTIME)
                        public @interface Programmatic {
                        }
                        """,
                SourceSpec::skip
        );
    }
}
