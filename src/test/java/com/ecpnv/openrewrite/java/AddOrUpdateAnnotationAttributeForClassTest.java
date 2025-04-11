package com.ecpnv.openrewrite.java;

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
}