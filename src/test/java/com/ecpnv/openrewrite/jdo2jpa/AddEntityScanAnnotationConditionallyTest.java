package com.ecpnv.openrewrite.jdo2jpa;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.ShortenFullyQualifiedTypeReferences;

import static org.openrewrite.java.Assertions.java;

class AddEntityScanAnnotationConditionallyTest extends BaseRewriteTest {

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
}