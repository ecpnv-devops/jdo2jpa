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
}