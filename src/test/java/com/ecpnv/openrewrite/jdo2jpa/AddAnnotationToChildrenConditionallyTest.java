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

}