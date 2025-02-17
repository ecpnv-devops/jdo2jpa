package com.ecpnv.openrewrite.util;

import lombok.experimental.UtilityClass;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;

import com.ecpnv.openrewrite.jdo2jpa.Constants;

/**
 * Utility class for creating a {@link JavaParser.Builder} instance with a fixed set of constant resource libraries
 * and uses defined JVM libraries.
 * <p>
 * For testing purposes an additional test library file is included when defined as a system property.
 * <p>
 * @author Wouter Veltmaat @ Open Circle Solutions
 */
@UtilityClass
public class JavaParserFactory {

    public static JavaParser.Builder<? extends JavaParser, ?> create() {
        return create(new InMemoryExecutionContext());
    }

    public static JavaParser.Builder<? extends JavaParser, ?> create(ExecutionContext ctx) {
        final String[] classPath = System.getProperty("java.class.path").split(System.getProperty("path.separator"));
        String[] resourceClasspath = new String[]{
                Constants.Jpa.CLASS_PATH,
                Constants.Jdo.CLASS_PATH,
                Constants.SPRING_CONTEXT_CLASS_PATH,
                Constants.SPRING_BOOT_AUTOCONFIGURATION_CLASS_PATH,
                Constants.LOMBOK_CLASS_PATH};
        //hack to include a jar file for testing extending abstract classes
        final String additionalLibraryFileForAbstractClassName = System.getProperty("libraryOfAbstractClassName");
        if (StringUtils.isNoneBlank(additionalLibraryFileForAbstractClassName)) {
            resourceClasspath = ArrayUtils.add(resourceClasspath, additionalLibraryFileForAbstractClassName);
        }

        return JavaParser.fromJavaVersion().classpath(classPath).classpathFromResources(ctx, resourceClasspath);
    }
}
