/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ecpnv.openrewrite.jdo2jpa;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;

import static org.openrewrite.java.Assertions.java;

import com.ecpnv.openrewrite.java.AddAnnotationConditionally;

/**
 * @author Patrick Deenen @ Open Circle Solutions
 */
class AddAnnotationConditionallyTest extends BaseRewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(PARSER).recipe(new AddAnnotationConditionally(
                "@Column\\(.*jdbcType\\s*=\\s*\"CLOB\".*\\)",
                "javax.persistence.Lob", "@Lob"));
    }

    /**
     * Adds a `@Lob` annotation to fields annotated with `@Column` where the `@Column` annotation
     * specifies a `jdbcType` of "CLOB". This transformation also ensures that the necessary
     * `javax.persistence.Lob` import is added to the class if not already present.
     */
    @DocumentExample
    @Test
    void addEntityAnnotationAlongPersistanceCapable() {
        rewriteRun(//language=java
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Column;
                                
                                public class SomeEntity {
                                    private int id;
                                    @Column(allowsNull = "true", jdbcType = "CLOB", sqlType = "LONGVARCHAR")
                                    private String notes;
                                }
                                """,
                        """
                                import javax.jdo.annotations.Column;
                                import javax.persistence.Lob;
                                
                                public class SomeEntity {
                                    private int id;
                                    @Column(allowsNull = "true", jdbcType = "CLOB", sqlType = "LONGVARCHAR")
                                    @Lob
                                    private String notes;
                                }
                                """
                )
        );
    }
}
