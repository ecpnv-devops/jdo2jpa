package com.ecpnv.openrewrite.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;

import static org.openrewrite.java.Assertions.java;

import com.ecpnv.openrewrite.jdo2jpa.BaseRewriteTest;

/**
 * A test class extending BaseRewriteTest to validate the functionality of the
 * AddAnnotationNameAttributeForKeywords recipe.
 *
 * The AddAnnotationNameAttributeForKeywordsTest class is used to ensure the recipe correctly identifies
 * annotations of a specified type and adds the appropriate name attribute for specific field names
 * that match the defined keywords. The tests verify the proper handling of annotations and attributes
 * in the source code when matched against configured conditions.
 *
 * @author Wouter Veltmaat @ Open Circle Solutions
 */
class AddAnnotationNameAttributeForKeywordsTest extends BaseRewriteTest {

    /**
     * Tests the functionality of the AddAnnotationNameAttributeForKeywords recipe
     * to ensure that it correctly adds a name attribute to the specified annotation
     * for fields matching the provided keywords.
     *
     * The happyPath test validates the following:
     * - Fields annotated with the specified annotation type are checked against the configured keywords.
     * - If a field name matches any of the keywords, a name attribute is added to the annotation.
     * - The name attribute is formatted correctly, using the provided escape string as necessary.
     * - Existing annotations with the correct attribute are left unchanged.
     *
     * This test case uses input Java source code with fields intentionally named to match or not match
     * the defined keywords and verifies that the expected transformations occur.
     */
    @DocumentExample
    @Test
    void happyPath() {

        rewriteRun(spec -> spec.parser(PARSER).recipes(
                        new AddAnnotationNameAttributeForKeywords(
                                "javax.persistence.Column",
                                "name",
                                "key,user, group",
                                "\\\"")),
                //language=java
                java(
                        """
                                package a;
                                
                                import javax.persistence.*;
                                
                                @Entity
                                public class SomeClass {
                                
                                    @Id
                                    @Column()
                                    String key;
                                
                                    @Column(name = "user")
                                    String user;
                                
                                    @Column(name = "userGroup")
                                    String group;
                                }
                                """,
                        """
                                package a;
                                
                                import javax.persistence.*;
                                
                                @Entity
                                public class SomeClass {
                                
                                    @Id
                                    @Column(name = "\\\"key\\\"")
                                    String key;
                                
                                    @Column(name = "\\\"user\\\"")
                                    String user;
                                
                                    @Column(name = "userGroup")
                                    String group;
                                }
                                """
                )
        );
    }
}