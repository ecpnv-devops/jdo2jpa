package com.ecpnv.openrewrite.jdo2jpa;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;

import static org.openrewrite.java.Assertions.java;

class ChangeTypeForClassTest extends BaseRewriteTest {
    @DocumentExample
    @Test
    void happyPath() {
        rewriteRun(spec -> spec.parser(PARSER).recipes(new ChangeTypeForClass(
                        "a.SomeInterface",
                        "java.util.SortedSet",
                        "java.util.Set",
                        false)),
                //language=java
                java(
                        """
                                package a;
                                
                                import java.util.SortedSet;
                                
                                public interface SomeInterface {
                                    SortedSet<String> getSomeSet();
                                }
                                """,
                        """
                                package a;
                                
                                import java.util.Set;
                                
                                public interface SomeInterface {
                                    Set<String> getSomeSet();
                                }
                                """
                )
        );
    }

    /**
     * When multiple comma-separated type pairs are configured, every pair must be applied. A regression
     * where the loop re-visited the original class declaration each iteration silently dropped all but the
     * last pair.
     */
    @Test
    void multipleTypePairs() {
        rewriteRun(spec -> spec.parser(PARSER).recipes(new ChangeTypeForClass(
                        "a.SomeInterface",
                        "java.util.SortedSet,java.util.List",
                        "java.util.Set,java.util.Collection",
                        false)),
                //language=java
                java(
                        """
                                package a;

                                import java.util.List;
                                import java.util.SortedSet;

                                public interface SomeInterface {
                                    SortedSet<String> getSomeSet();
                                    List<String> getSomeList();
                                }
                                """,
                        """
                                package a;

                                import java.util.Collection;
                                import java.util.Set;

                                public interface SomeInterface {
                                    Set<String> getSomeSet();
                                    Collection<String> getSomeList();
                                }
                                """
                )
        );
    }
}