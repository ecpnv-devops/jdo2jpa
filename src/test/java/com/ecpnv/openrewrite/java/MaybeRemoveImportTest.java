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
package com.ecpnv.openrewrite.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;

import static org.openrewrite.java.Assertions.java;

import com.ecpnv.openrewrite.jdo2jpa.BaseRewriteTest;
import com.ecpnv.openrewrite.jdo2jpa.Constants;


/**
 * @author Patrick Deenen @ Open Circle Solutions
 */
class MaybeRemoveImportTest extends BaseRewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(PARSER).recipe(new MaybeRemoveImport(Constants.Jdo.COLUMN_ANNOTATION_FULL));
    }

    /**
     * Verifies the successful removal of the `@Column` annotation from the provided Java code.
     * <p>
     * The method executes a rewrite process on a given snippet of Java code, where the
     * `@Column` annotation is defined, and confirms that the resulting output no longer
     * includes the annotation.
     * <p>
     * This test ensures that the rewrite operation properly identifies and removes the
     * specified import and related annotation from the source code when applied.
     */
    @DocumentExample
    @Test
    void removeAnnotation() {
        rewriteRun(//language=java
                java(
                        """
                                import javax.jdo.annotations.Column;
                                
                                public class SomeEntity {}
                                """,
                        """
                                public class SomeEntity {}
                                """
                )
        );
    }

    /**
     * Ensures that the `@Column` annotation on a class is not removed or altered during the rewrite process.
     * This test verifies the persistence of the annotation in the provided Java code.
     * <p>
     * The method runs a rewrite process using a Java code snippet as input and validates
     * that the annotated class structure remains unchanged.
     */
    @DocumentExample
    @Test
    void doNotRemoveAnnotation() {
        rewriteRun(//language=java
                java(
                        """
                                import javax.jdo.annotations.Column;
                                
                                @Column
                                public class SomeEntity {}
                                """
                )
        );
    }
}
