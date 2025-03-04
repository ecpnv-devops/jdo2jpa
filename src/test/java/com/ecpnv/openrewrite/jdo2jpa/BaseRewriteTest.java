package com.ecpnv.openrewrite.jdo2jpa;

import org.junit.jupiter.api.BeforeAll;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.internal.JavaTypeCache;
import org.openrewrite.test.RewriteTest;

import com.ecpnv.openrewrite.util.JavaParserFactory;

/**
 * @author Patrick Deenen @ Open Circle Solutions
 */
public class BaseRewriteTest implements RewriteTest {

    static {
        System.setProperty("libraryOfAbstractClassName", "jdo2jpa-abstract");//hack to include test jar in rewrite recipe
    }

    public static final JavaParser.Builder<?, ?> PARSER = JavaParserFactory.create();

    @BeforeAll
    public static void setup() {
        // start with clean type cache (also makes it run faster)
        PARSER.typeCache(new JavaTypeCache());
    }
}
