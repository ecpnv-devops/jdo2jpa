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

import com.ecpnv.openrewrite.java.MoveAnnotationsToAttribute;


class MoveAnnotationsToAttributeTest extends BaseRewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(PARSER).recipe(new MoveAnnotationsToAttribute(Constants.Jpa.UNIQUE_CONSTRAINT_ANNOTATION_FULL,
                Constants.Jpa.TABLE_ANNOTATION_FULL, Constants.Jpa.TABLE_ARGUMENT_UNIQUE_CONSTRAINTS));
    }

    @DocumentExample
    @Test
    void addEntityAnnotationAlongPersistanceCapable() {
        rewriteRun(//language=java
                //language=java
                java(
                        """
                                import javax.persistence.Table;
                                import javax.persistence.UniqueConstraint;
                                
                                @UniqueConstraint(name = "SomeEntityNameUnique", columnNames = {"name"})
                                @Table(schema = "schemaName")
                                public class SomeEntity {
                                        private int id;
                                        private String name;
                                }
                                """,
                        """
                                import javax.persistence.Table;
                                import javax.persistence.UniqueConstraint;
                                
                                @javax.persistence.Table( schema = "schemaName", uniqueConstraints = {@javax.persistence.UniqueConstraint(name = "SomeEntityNameUnique", columnNames = {"name"})})
                                public class SomeEntity {
                                        private int id;
                                        private String name;
                                }
                                """
                )
        );
    }
}
