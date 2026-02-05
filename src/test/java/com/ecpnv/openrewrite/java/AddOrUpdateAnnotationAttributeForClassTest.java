package com.ecpnv.openrewrite.java;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;

import static org.openrewrite.java.Assertions.java;

import com.ecpnv.openrewrite.jdo2jpa.BaseRewriteTest;

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
                //language=java
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
                //language=java
                java("""
                        package a;
                        
                        import javax.persistence.Entity;
                        
                        @Entity
                        public class SomeClass {
                        }
                        """));
    }

    @Disabled("the integration is disabled in the yml configuration")
    @DocumentExample
    @Test
    void attribute() {
        rewriteRun(spec -> spec.parser(PARSER).recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.causeway"),
                //language=java
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

    @DocumentExample
    @Test
    void addAttributeWhenMissing() {
        rewriteRun(spec -> spec.parser(PARSER).recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.optional"),
                //language=java
                java("""
                                import java.math.BigDecimal;
                                import javax.persistence.Column;
                                import javax.persistence.Entity;
                                
                                @Entity
                                public class SomeEntity {
                                    @Column(scale = 2)
                                    public BigDecimal shouldHavePrecisionOfDefaultLength;
                                    @Column(length = 8, scale = 2)
                                    public BigDecimal shouldHavePrecisionOf8;
                                    @Column
                                    public BigDecimal noPrecision;
                                    @Column(length = 8)
                                    public BigDecimal noPrecisionWhenNoScale;
                                }
                                """,
                        """
                                import java.math.BigDecimal;
                                import javax.persistence.Column;
                                import javax.persistence.Entity;
                                
                                @Entity
                                public class SomeEntity {
                                    @Column(precision = 19, scale = 2)
                                    public BigDecimal shouldHavePrecisionOfDefaultLength;
                                    @Column(precision = 8, scale = 2)
                                    public BigDecimal shouldHavePrecisionOf8;
                                    @Column
                                    public BigDecimal noPrecision;
                                    @Column(length = 8)
                                    public BigDecimal noPrecisionWhenNoScale;
                                }
                                """));
    }
}