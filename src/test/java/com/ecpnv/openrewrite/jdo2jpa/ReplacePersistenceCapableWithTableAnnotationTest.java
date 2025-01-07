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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

/**
 * ReplacePersistenceCapableWithTableAnnotationTest is a test class that verifies the functionality
 * of the ReplacePersistenceCapableWithTableAnnotation recipe. This recipe is used to replace
 * the `@javax.jdo.annotations.PersistenceCapable` annotation in Java classes with the
 * corresponding `@javax.persistence.Table` annotation to ensure compatibility with JPA.
 * <p>
 * The test validates the following:
 * - The identification of classes annotated with `@PersistenceCapable`.
 * - The addition of the `@Table` annotation, including the transfer of properties like `schema`.
 * - Proper handling of imports when transforming the annotations.
 * <p>
 * This class implements RewriteTest, which provides the framework for specifying and running
 * rewrite tests. The test is designed to ensure the accurate and consistent application of
 * the recipe.
 *
 * @author Patrick Deenen @ Open Circle Solutions
 */
class ReplacePersistenceCapableWithTableAnnotationTest implements RewriteTest {
	@Override
	public void defaults(RecipeSpec spec) {
		spec.parser(JavaParser.fromJavaVersion()
						.classpathFromResources(new InMemoryExecutionContext(), AddEntityAnnotation.CLASS_PATH, "jdo-api"))
				.recipe(new ReplacePersistenceCapableWithTableAnnotation());
	}

	/**
	 * `replaceWithTableAnnotation()`: Tests the transformation of a Java class annotated with
	 * `@PersistenceCapable` into a class annotated with both `@PersistenceCapable` and `@Table`,
	 * ensuring schema information is correctly applied.
	 */
	@DocumentExample
	@Test
	void replaceWithTableAnnotation() {
		rewriteRun(//language=java
				//language=java
				java(
						"import java.util.List;\n" +
								"\n" +
								"import javax.jdo.annotations.PersistenceCapable;\n" +
								"\n" +
								"@PersistenceCapable(schema = \"schemaName\")\n" +
								"public class SomeEntity {\n" +
								"    private int id;\n" +
								"    private List<String> listofStrings;\n" +
								"}",
						"import java.util.List;\n" +
								"\n" +
								"import javax.jdo.annotations.PersistenceCapable;\n" +
								"import javax.persistence.Table;\n" +
								"\n" +
								"@PersistenceCapable(schema = \"schemaName\")\n" +
								"@Table(schema = \"schemaName\")\n" +
								"public class SomeEntity {\n" +
								"    private int id;\n" +
								"    private List<String> listofStrings;\n" +
								"}"
				)
		);
	}
}