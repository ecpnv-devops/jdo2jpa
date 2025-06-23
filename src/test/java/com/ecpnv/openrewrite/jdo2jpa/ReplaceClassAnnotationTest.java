package com.ecpnv.openrewrite.jdo2jpa;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.SourceSpec;

import static org.openrewrite.java.Assertions.java;

public class ReplaceClassAnnotationTest extends BaseRewriteTest {

    @DocumentExample
    @Test
    void happyPath1() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipes(new ReplaceClassAnnotation(
                                "@EntityListeners(org.apache.isis.persistence.jpa.applib.integration.IsisEntityListener.class)",
                                "@EntityListeners(OrmEntityListener.class)",
                                "org.estatio.base.prod.integration.OrmEntityListener")),
                java("""
                                        package org.apache.isis.persistence.jpa.applib.integration;
                                
                                        public class IsisEntityListener {
                                        }
                                """,
                        SourceSpec::skip),
                java("""
                                        package org.estatio.base.prod.integration;
                                
                                        public class OrmEntityListener {
                                        }
                                """,
                        SourceSpec::skip),
                java(
                        """
                                package org.incode.module.document.dom.impl.applicability;
                                
                                import javax.persistence.Entity;
                                import javax.persistence.EntityListeners;
                                
                                @Entity(name = Applicability.TABLE_NAME)
                                @EntityListeners(org.apache.isis.persistence.jpa.applib.integration.IsisEntityListener.class)
                                public class Applicability {
                                    public static String TABLE_NAME = "test";
                                }
                                """
                ,
                        """
                                package org.incode.module.document.dom.impl.applicability;
                                
                                import org.estatio.base.prod.integration.OrmEntityListener;
                                
                                import javax.persistence.Entity;
                                import javax.persistence.EntityListeners;
                                
                                @Entity(name = Applicability.TABLE_NAME)
                                @EntityListeners(OrmEntityListener.class)
                                public class Applicability {
                                    public static String TABLE_NAME = "test";
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void happyPath2() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipes(new ReplaceClassAnnotation(
                                "@EntityListeners(IsisEntityListener.class)",
                                "@EntityListeners(OrmEntityListener.class)",
                                "org.estatio.base.prod.integration.OrmEntityListener")),
                java("""
                                        package org.apache.isis.persistence.jpa.applib.integration;
                                
                                        public class IsisEntityListener {
                                        }
                                """,
                        SourceSpec::skip),
                java("""
                                        package org.estatio.base.prod.integration;
                                
                                        public class OrmEntityListener {
                                        }
                                """,
                        SourceSpec::skip),
                java(
                        """
                                package org.incode.module.document.dom.impl.applicability;
                                
                                import org.apache.isis.persistence.jpa.applib.integration.IsisEntityListener;
                                
                                import javax.persistence.Entity;
                                import javax.persistence.EntityListeners;
                                
                                @Entity(name = Applicability.TABLE_NAME)
                                @EntityListeners(IsisEntityListener.class)
                                public class Applicability {
                                    public static String TABLE_NAME = "test";
                                }
                                """
                ,
                        """
                                package org.incode.module.document.dom.impl.applicability;
                                
                                import org.estatio.base.prod.integration.OrmEntityListener;
                                
                                import javax.persistence.Entity;
                                import javax.persistence.EntityListeners;
                                
                                @Entity(name = Applicability.TABLE_NAME)
                                @EntityListeners(OrmEntityListener.class)
                                public class Applicability {
                                    public static String TABLE_NAME = "test";
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void happyPath3() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipes(new ReplaceClassAnnotation(
                                "@EntityListeners(org.apache.isis.persistence.jpa.applib.integration.IsisEntityListener.class)",
                                "@EntityListeners(OrmEntityListener.class)",
                                "org.estatio.base.prod.integration.OrmEntityListener")),
                java("""
                                        package org.estatio.base.prod.integration;
                                
                                        public class OrmEntityListener {
                                        }
                                """,
                        SourceSpec::skip),
                java(
                        """
                                package org.estatio.base.prod.util.dom;
                                
                                import lombok.AllArgsConstructor;
                                import lombok.NoArgsConstructor;
                                
                                import javax.persistence.Entity;
                                import javax.persistence.EntityListeners;
                                import javax.persistence.Id;
                                
                                @Entity
                                @EntityListeners(org.apache.isis.persistence.jpa.applib.integration.IsisEntityListener.class)
                                @NoArgsConstructor
                                @AllArgsConstructor
                                public class SomeEntity {
                                
                                    @Id
                                    private Long id;
                                
                                }
                                """
                ,
                        """
                                package org.estatio.base.prod.util.dom;
                                
                                import lombok.AllArgsConstructor;
                                import lombok.NoArgsConstructor;
                                import org.estatio.base.prod.integration.OrmEntityListener;
                                
                                import javax.persistence.Entity;
                                import javax.persistence.EntityListeners;
                                import javax.persistence.Id;
                                
                                @Entity
                                @EntityListeners(OrmEntityListener.class)
                                @NoArgsConstructor
                                @AllArgsConstructor
                                public class SomeEntity {
                                
                                    @Id
                                    private Long id;
                                
                                }
                                """
                )
        );
    }

}
