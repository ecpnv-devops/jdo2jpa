package com.ecpnv.openrewrite.jdo2jpa;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.ShortenFullyQualifiedTypeReferences;

import static org.openrewrite.java.Assertions.java;

class AddEntityScanAnnotationConditionallyTest extends BaseRewriteTest {

    @Test
    void nestedConfigurationDoesNotAnnotateEnclosingTypes() {
        nestedConfiguration(false);
    }

    @Test
    void preservesNestedChangesWhenEnclosingConfigurationAlsoChanges() {
        nestedConfiguration(true);
    }

    private void nestedConfiguration(boolean outerComponentScan) {
        String outerAnnotation = outerComponentScan ? "@ComponentScan\n" : "";
        String outerResult = outerComponentScan ? "@ComponentScan\n@EntityScan({\"example\"})\n" : "";
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipes(new AddEntityScanAnnotationConditionally(),
                                new ShortenFullyQualifiedTypeReferences())
                        .cycles(3).expectedCyclesThatMakeChanges(1),
                java("""
                        package example;

                        import javax.persistence.Entity;
                        import org.springframework.context.annotation.ComponentScan;

                        @Entity
                        class SomeEntity {}

                        %sclass Outer {
                            static class Middle {
                                @ComponentScan
                                static class Configuration {}
                            }
                        }
                        """.formatted(outerAnnotation), """
                        package example;

                        import javax.persistence.Entity;

                        import org.springframework.boot.autoconfigure.domain.EntityScan;
                        import org.springframework.context.annotation.ComponentScan;

                        @Entity
                        class SomeEntity {}

                        %sclass Outer {
                            static class Middle {
                                @ComponentScan
                                @EntityScan({"example"})
                                static class Configuration {}
                            }
                        }
                        """.formatted(outerResult)));
    }

    @DocumentExample
    @Test
    void happyPath() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipes(new AddEntityScanAnnotationConditionally(),
                                new ShortenFullyQualifiedTypeReferences()),
                java("""
                                package com.ecpnv.openrewrite.jdo2jpa.dom;
                        
                                import javax.persistence.Entity;
                        
                                @Entity
                                public class SomeEntity {
                                }
                        """),
                java(
                        """
                                package com.ecpnv.openrewrite.jdo2jpa;
                                
                                import org.springframework.context.annotation.ComponentScan;
                                import org.springframework.context.annotation.Configuration;
                                
                                @Configuration
                                @ComponentScan
                                public class SomeConfiguration {
                                }
                                """
                ,
                        """
                                package com.ecpnv.openrewrite.jdo2jpa;
                                
                                import org.springframework.boot.autoconfigure.domain.EntityScan;
                                import org.springframework.context.annotation.ComponentScan;
                                import org.springframework.context.annotation.Configuration;
                                
                                @Configuration
                                @EntityScan({"com.ecpnv.openrewrite.jdo2jpa.dom"})
                                @ComponentScan
                                public class SomeConfiguration {
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void multipleSubDirs() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipes(new AddEntityScanAnnotationConditionally(),
                                new ShortenFullyQualifiedTypeReferences()),
                java("""
                                package com.ecpnv.openrewrite.jdo2jpa.entities;
                        
                                import javax.persistence.Entity;
                        
                                @Entity
                                public class SomeEntity {
                                }
                        """),
                java("""
                                package com.ecpnv.openrewrite.jdo2jpa.dom;
                        
                                import javax.persistence.Entity;
                        
                                @Entity
                                public class SomeOtherEntity {
                                }
                        """),
                java(
                        """
                                package com.ecpnv.openrewrite.jdo2jpa.config;
                                
                                import org.springframework.context.annotation.ComponentScan;
                                import org.springframework.context.annotation.Configuration;
                                
                                @Configuration
                                @ComponentScan
                                public class SomeConfiguration {
                                }
                                """
                ,
                        """
                                package com.ecpnv.openrewrite.jdo2jpa.config;
                                
                                import org.springframework.boot.autoconfigure.domain.EntityScan;
                                import org.springframework.context.annotation.ComponentScan;
                                import org.springframework.context.annotation.Configuration;
                                
                                @Configuration
                                @EntityScan({"com.ecpnv.openrewrite.jdo2jpa.dom", "com.ecpnv.openrewrite.jdo2jpa.entities"})
                                @ComponentScan
                                public class SomeConfiguration {
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void mergeWithExistingEntityScan() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipes(new AddEntityScanAnnotationConditionally(),
                                new ShortenFullyQualifiedTypeReferences()),
                java("""
                                package com.ecpnv.openrewrite.jdo2jpa.entities;
                        
                                import javax.persistence.Entity;
                        
                                @Entity
                                public class SomeEntity {
                                }
                        """),
                java("""
                                package com.ecpnv.openrewrite.jdo2jpa.dom;
                        
                                import javax.persistence.Entity;
                        
                                @Entity
                                public class SomeOtherEntity {
                                }
                        """),
                java(
                        """
                                package com.ecpnv.openrewrite.jdo2jpa.config;
                                
                                import org.springframework.boot.autoconfigure.domain.EntityScan;
                                import org.springframework.context.annotation.ComponentScan;
                                import org.springframework.context.annotation.Configuration;
                                
                                @Configuration
                                @EntityScan({"com.ecpnv.openrewrite.jdo2jpa.backup"})
                                @ComponentScan
                                public class SomeConfiguration {
                                }
                                """
                ,
                        """
                                package com.ecpnv.openrewrite.jdo2jpa.config;
                                
                                import org.springframework.boot.autoconfigure.domain.EntityScan;
                                import org.springframework.context.annotation.ComponentScan;
                                import org.springframework.context.annotation.Configuration;
                                
                                @Configuration
                                @EntityScan({
                                        "com.ecpnv.openrewrite.jdo2jpa.backup",
                                        "com.ecpnv.openrewrite.jdo2jpa.dom",
                                        "com.ecpnv.openrewrite.jdo2jpa.entities"})
                                @ComponentScan
                                public class SomeConfiguration {
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void unhappyPath() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipes(new AddEntityScanAnnotationConditionally(),
                                new ShortenFullyQualifiedTypeReferences()),
                java("""
                                package com.ecpnv.openrewrite.jdo2jpa.dom;
                        
                                import javax.persistence.Entity;
                        
                                @Entity
                                public class SomeEntity {
                                }
                        """),
                java(
                        """
                                package com.ecpnv.openrewrite.jdo2jpa;

                                import org.springframework.context.annotation.Configuration;

                                @Configuration
                                public class SomeConfiguration {
                                }
                                """
                )
        );
    }

    /**
     * A pre-existing bare {@code @EntityScan} (no arguments) alongside {@code @ComponentScan} must not crash the
     * recipe (previously {@code getArguments().getFirst()} threw). The recipe safely leaves the annotation as-is.
     */
    @Test
    void bareEntityScanDoesNotCrash() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipes(new AddEntityScanAnnotationConditionally()),
                java("""
                                package com.ecpnv.openrewrite.jdo2jpa.dom;

                                import javax.persistence.Entity;

                                @Entity
                                public class SomeEntity {
                                }
                        """),
                java(
                        """
                                package com.ecpnv.openrewrite.jdo2jpa.config;

                                import org.springframework.boot.autoconfigure.domain.EntityScan;
                                import org.springframework.context.annotation.ComponentScan;
                                import org.springframework.context.annotation.Configuration;

                                @Configuration
                                @EntityScan
                                @ComponentScan
                                public class SomeConfiguration {
                                }
                                """
                )
        );
    }
}