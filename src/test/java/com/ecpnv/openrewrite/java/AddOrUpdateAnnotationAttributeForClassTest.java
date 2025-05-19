package com.ecpnv.openrewrite.java;

import com.ecpnv.openrewrite.jdo2jpa.BaseRewriteTest;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;

import static org.openrewrite.java.Assertions.java;

class AddOrUpdateAnnotationAttributeForClassTest extends BaseRewriteTest {

    @DocumentExample
    @Test
    void happyCase() {
        rewriteRun(spec -> spec.parser(PARSER)
                        .recipes(new AddOrUpdateAnnotationAttributeForClass(
                                "a.*",
                                "javax.persistence.Entity",
                                false,
                                "name",
                                "test",
                                null,
                                AddOrUpdateAnnotationAttribute.Operation.ADD)),
                java("""
                                package a;
                                
                                import javax.persistence.Entity;
                                
                                @Entity
                                public class SomeClass {
                                }
                                """,
                        """
                                package a;
                                
                                import javax.persistence.Entity;
                                
                                @Entity(name = "test")
                                public class SomeClass {
                                }
                                """));
    }

    @DocumentExample
    @Test
    void unhappyCase() {
        rewriteRun(spec -> spec.parser(PARSER)
                        .recipes(new AddOrUpdateAnnotationAttributeForClass(
                                "b.*",
                                "javax.persistence.Entity",
                                false,
                                "name",
                                "test",
                                null,
                                AddOrUpdateAnnotationAttribute.Operation.ADD)),
                java("""
                        package a;
                        
                        import javax.persistence.Entity;
                        
                        @Entity
                        public class SomeClass {
                        }
                        """));
    }

    @DocumentExample
    @Test
    void attribute() {
        rewriteRun(spec -> spec.parser(PARSER).recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.causeway"),
                java("""
                                package org.incode.module.document.dom.impl.applicability;
                                
                                import javax.persistence.Entity;
                                
                                @Entity
                                public class Applicability {
                                    public static String TABLE_NAME = "test";
                                }
                                """,
                        """
                                package org.incode.module.document.dom.impl.applicability;
                                
                                import javax.persistence.Entity;
                                import javax.persistence.EntityListeners;
                                
                                @Entity(name = Applicability.TABLE_NAME)
                                @EntityListeners(org.apache.isis.persistence.jpa.applib.integration.IsisEntityListener.class)
                                public class Applicability {
                                    public static String TABLE_NAME = "test";
                                }
                                """));
    }
}