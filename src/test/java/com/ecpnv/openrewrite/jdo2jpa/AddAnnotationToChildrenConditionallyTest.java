package com.ecpnv.openrewrite.jdo2jpa;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.SourceSpec;

import static org.openrewrite.java.Assertions.java;

class AddAnnotationToChildrenConditionallyTest extends BaseRewriteTest {

    @DocumentExample
    @Test
    void happyPath() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipes(new AddAnnotationToChildrenConditionally(
                                "a.AbstractClass",
                                "lombok.NoArgsConstructor")),
                java("""
                                package a;
                        
                                import lombok.NoArgsConstructor;
                        
                                @NoArgsConstructor
                                public abstract class AbstractClass {
                                }
                        """, SourceSpec::skip),
                java(
                        """
                                package a;
                                
                                public class SomeClass extends AbstractClass {
                                }
                                """
                ,
                        """
                                package a;
                                
                                import lombok.NoArgsConstructor;
                                
                                @lombok.NoArgsConstructor
                                public class SomeClass extends AbstractClass {
                                }
                                """
                )
        );
    }

    /**
     * The parent may also be an interface. A class implementing the given interface must receive the
     * annotation; previously only the superclass chain was inspected, so interface parents were never matched.
     */
    /**
     * The recipe adds the annotation to children only: the class named by {@code fullClassName} must NOT be
     * annotated itself. Previously {@code checkIsExtended} returned true for the class equal to
     * {@code fullClassName}, so the parent got the annotation too.
     */
    @Test
    void annotatesChildrenButNotTheParentItself() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipes(new AddAnnotationToChildrenConditionally(
                                "a.AbstractClass",
                                "lombok.NoArgsConstructor")),
                java(
                        """
                                package a;

                                public abstract class AbstractClass {
                                }

                                class SomeClass extends AbstractClass {
                                }
                                """,
                        """
                                package a;

                                import lombok.NoArgsConstructor;

                                public abstract class AbstractClass {
                                }

                                @lombok.NoArgsConstructor
                                class SomeClass extends AbstractClass {
                                }
                                """
                )
        );
    }

}