package com.ecpnv.openrewrite.jdo2jpa;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.ShortenFullyQualifiedTypeReferences;

import static org.openrewrite.java.Assertions.java;

class AddEntityScanAnnotationConditionallyTest extends BaseRewriteTest {

    @DocumentExample
    @Test
    void addEmptyEntityScanAnnotationConditionallyHappyPath() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipes(new AddEntityScanAnnotationConditionally(),
                                new ShortenFullyQualifiedTypeReferences()),
                java(
                        """
                                import org.springframework.context.annotation.ComponentScan;
                                import org.springframework.context.annotation.Configuration;
                                
                                @Configuration
                                @ComponentScan
                                public class SomeConfiguration {
                                }
                                """
                        ,
                        """
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

    @DocumentExample
    @Test
    void addEntityScanAnnotationConditionallyHappyPath() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipes(new AddEntityScanAnnotationConditionally(),
                                new ShortenFullyQualifiedTypeReferences()),
                java(
                        """
                                import org.springframework.context.annotation.ComponentScan;
                                import org.springframework.context.annotation.Configuration;
                                
                                @Configuration
                                @ComponentScan(basePackages = "com.ecpnv.openrewrite.jdo2jpa")
                                public class SomeConfiguration {
                                }
                                """
                        ,
                        """
                                import org.springframework.boot.autoconfigure.domain.EntityScan;
                                import org.springframework.context.annotation.ComponentScan;
                                import org.springframework.context.annotation.Configuration;
                                
                                @Configuration
                                @EntityScan(basePackages = "com.ecpnv.openrewrite.jdo2jpa")
                                @ComponentScan(basePackages = "com.ecpnv.openrewrite.jdo2jpa")
                                public class SomeConfiguration {
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void addEntityScanAnnotationsConditionallyHappyPath() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipes(new AddEntityScanAnnotationConditionally(),
                                new ShortenFullyQualifiedTypeReferences()),
                java(
                        """
                                import org.springframework.context.annotation.ComponentScan;
                                import org.springframework.context.annotation.Configuration;
                                
                                @Configuration
                                @ComponentScan(basePackages = "com.ecpnv.openrewrite.jdo2jpa")
                                @ComponentScan(basePackages = "com.ecpnv.openrewrite.java")
                                public class SomeConfiguration {
                                }
                                """
                        ,
                        """
                                import org.springframework.boot.autoconfigure.domain.EntityScan;
                                import org.springframework.context.annotation.ComponentScan;
                                import org.springframework.context.annotation.Configuration;
                                
                                @Configuration
                                @EntityScan(basePackages = {"com.ecpnv.openrewrite.java", "com.ecpnv.openrewrite.jdo2jpa"})
                                @ComponentScan(basePackages = "com.ecpnv.openrewrite.jdo2jpa")
                                @ComponentScan(basePackages = "com.ecpnv.openrewrite.java")
                                public class SomeConfiguration {
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void addEntityScanAnnotationConditionallyUnHappyPath() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipes(new AddEntityScanAnnotationConditionally(),
                                new ShortenFullyQualifiedTypeReferences()),
                java(
                        """
                                import org.springframework.context.annotation.Configuration;
                                
                                @Configuration
                                public class SomeConfiguration {
                                }
                                """
                )
        );
    }
}