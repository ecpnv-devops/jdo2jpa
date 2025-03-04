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
                        .recipes(new AddEntityScanAnnotationConditionally(null, null),
                                new ShortenFullyQualifiedTypeReferences()),
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
                                @EntityScan({"com.ecpnv.openrewrite.jdo2jpa"})
                                @ComponentScan
                                public class SomeConfiguration {
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void addConfigurationEntityScanAnnotationConditionallyHappyPath() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipes(new AddEntityScanAnnotationConditionally("config", null),
                                new ShortenFullyQualifiedTypeReferences()),
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
                                @EntityScan({"com.ecpnv.openrewrite.jdo2jpa"})
                                @ComponentScan
                                public class SomeConfiguration {
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void addConfigurationAndEntitiesEntityScanAnnotationConditionallyHappyPath() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipes(new AddEntityScanAnnotationConditionally("config", "entities"),
                                new ShortenFullyQualifiedTypeReferences()),
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
                                @EntityScan({"com.ecpnv.openrewrite.jdo2jpa.entities"})
                                @ComponentScan
                                public class SomeConfiguration {
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void addClassNameEntityScanAnnotationConditionallyHappyPath() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipes(new AddEntityScanAnnotationConditionally(null, null),
                                new ShortenFullyQualifiedTypeReferences()),
                java(
                        """
                                package com.ecpnv.openrewrite.jdo2jpa;
                                
                                import org.springframework.context.annotation.ComponentScan;
                                import org.springframework.context.annotation.Configuration;
                                
                                @Configuration
                                @ComponentScan("com.ecpnv.openrewrite.jdo2jpa")
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
                                @EntityScan({"com.ecpnv.openrewrite.jdo2jpa"})
                                @ComponentScan("com.ecpnv.openrewrite.jdo2jpa")
                                public class SomeConfiguration {
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void addClassEntityScanAnnotationConditionallyHappyPath() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipes(new AddEntityScanAnnotationConditionally(null, null),
                                new ShortenFullyQualifiedTypeReferences()),
                java(
                        """
                                package com.ecpnv.openrewrite.jdo2jpa;
                                
                                import org.springframework.context.annotation.ComponentScan;
                                import org.springframework.context.annotation.Configuration;
                                
                                @Configuration
                                @ComponentScan({SomeConfiguration.class})
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
                                @EntityScan({"com.ecpnv.openrewrite.jdo2jpa"})
                                @ComponentScan({SomeConfiguration.class})
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
                        .recipes(new AddEntityScanAnnotationConditionally(null, null),
                                new ShortenFullyQualifiedTypeReferences()),
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