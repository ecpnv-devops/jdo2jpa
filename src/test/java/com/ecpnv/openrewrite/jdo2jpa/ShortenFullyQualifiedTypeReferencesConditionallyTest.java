package com.ecpnv.openrewrite.jdo2jpa;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;

import static org.openrewrite.java.Assertions.java;

import com.ecpnv.openrewrite.java.ShortenFullyQualifiedTypeReferencesConditionally;

class ShortenFullyQualifiedTypeReferencesConditionallyTest extends BaseRewriteTest {

    @DocumentExample
    @Test
    void shortenFullyQualifiedHappyPath() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new ShortenFullyQualifiedTypeReferencesConditionally("some.package")),
                java(
                        """
                                import java.util.List;
                                
                                public class SomeClass {
                                    private List<String> listOfStrings;
                                
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void shortenFullyQualifiedExcluded() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new ShortenFullyQualifiedTypeReferencesConditionally(
                                "java.util,some.package")),
                java(
                        """
                                public class SomeClass {
                                    private java.util.List<String> listOfStrings;
                                
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void shortenFullyQualifiedLombokExcluded() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new ShortenFullyQualifiedTypeReferencesConditionally(
                                "lombok,some.package")),
                java(
                        """
                                import lombok.Getter;
                                
                                public class SomeClass {
                                
                                    @Getter @lombok.ToString.Include
                                    private String someString;
                                
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void shortenFullyQualifiedLombok() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new ShortenFullyQualifiedTypeReferencesConditionally(
                                "some.package")),
                java(
                        """
                                import lombok.Getter;
                                
                                public class SomeClass {
                                
                                    @Getter @lombok.ToString.Include
                                    private String someString;
                                
                                }
                                """,
                        """
                                import lombok.Getter;
                                import lombok.ToString;
                                
                                public class SomeClass {
                                
                                    @Getter @ToString.Include
                                    private String someString;
                                
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void shortenFullyQualifiedLombokEdgeCase() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new ShortenFullyQualifiedTypeReferencesConditionally(
                                "lombok")),
                java(
                        """
                                import lombok.Getter;
                                import lombok.Setter;
                                import lombok.ToString;
                                
                                public class SomeClass {
                                
                                    @Setter @Getter @ToString.Include
                                    private String someString;
                                
                                    @Setter @Getter @lombok.ToString.Include
                                    private String someOtherString;
                                }
                                """
                )
        );
    }
}