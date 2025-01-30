package com.ecpnv.openrewrite.jdo2jpa;

public final class Constants {

    public final static String REWRITE_ANNOTATION_PREFIX = "/*~~>*/";

    public static class Jdo {
        public final static String CLASS_PATH = "jdo-api";
        public final static String BASE_PACKAGE = "javax.jdo.annotations.";

        public final static String COLUMN_ANNOTATION_NAME = "Column";
        public final static String COLUMN_ANNOTATION_FULL = BASE_PACKAGE + COLUMN_ANNOTATION_NAME;
        public final static String DISCRIMINATOR_ANNOTATION_NAME = "Discriminator";
        public final static String DISCRIMINATOR_ANNOTATION_FULL = BASE_PACKAGE + DISCRIMINATOR_ANNOTATION_NAME;
        public final static String PERSISTENCE_CAPABLE_ANNOTATION_NAME = "PersistenceCapable";
        public final static String PERSISTENCE_CAPABLE_ANNOTATION_FULL = BASE_PACKAGE + PERSISTENCE_CAPABLE_ANNOTATION_NAME;
        public final static String PERSISTENT_ANNOTATION_NAME = "Persistent";
        public final static String PERSISTENT_ANNOTATION_FULL = BASE_PACKAGE + PERSISTENT_ANNOTATION_NAME;
        public final static String PERSISTENT_ARGUMENT_DEPENDENT_ELEMENT = "dependentElement";
        public final static String PERSISTENT_ARGUMENT_DEFAULT_FETCH_GROUP = "defaultFetchGroup";

    }

    public static class Jpa {
        public final static String CLASS_PATH = "jakarta.persistence-api";
        public final static String BASE_PACKAGE = "javax.persistence.";

        public final static String COLUMN_ANNOTATION_NAME = "Column";
        public final static String COLUMN_ANNOTATION_FULL = BASE_PACKAGE + COLUMN_ANNOTATION_NAME;
        public final static String DISCRIMINATOR_VALUE_ANNOTATION_NAME = "DiscriminatorValue";
        public final static String DISCRIMINATOR_VALUE_ANNOTATION_FULL = BASE_PACKAGE + DISCRIMINATOR_VALUE_ANNOTATION_NAME;
        public final static String ENTITY_ANNOTATION_NAME = "Entity";
        public final static String ENTITY_ANNOTATION_FULL = BASE_PACKAGE + ENTITY_ANNOTATION_NAME;
        public final static String ONE_TO_MANY_ANNOTATION_NAME = "OneToMany";
        public final static String ONE_TO_MANY_ANNOTATION_FULL = BASE_PACKAGE + ONE_TO_MANY_ANNOTATION_NAME;
        public final static String ONE_TO_MANY_ARGUMENT_MAPPED_BY = "mappedBy";
        public final static String MANY_TO_ONE_ANNOTATION_NAME = "ManyToOne";
        public final static String MANY_TO_ONE_ANNOTATION_FULL = BASE_PACKAGE + MANY_TO_ONE_ANNOTATION_NAME;
        public final static String TABLE_ANNOTATION_NAME = "Table";
        public final static String TABLE_ANNOTATION_FULL = BASE_PACKAGE + TABLE_ANNOTATION_NAME;
        public final static String TABLE_ARGUMENT_SCHEMA = "schema";
        public final static String TABLE_ARGUMENT_UNIQUE_CONSTRAINTS = "uniqueConstraints";
        public final static String UNIQUE_CONSTRAINT_ANNOTATION_NAME = "UniqueConstraint";
        public final static String UNIQUE_CONSTRAINT_ANNOTATION_FULL = BASE_PACKAGE + UNIQUE_CONSTRAINT_ANNOTATION_NAME;

        public final static String CASCADE_TYPE_FULL = BASE_PACKAGE + "CascadeType";
        public final static String FETCH_TYPE_FULL = BASE_PACKAGE + "FetchType";

    }

    public final static String LOMBOK_CLASS_PATH = "lombok";
    public final static String SPRING_CLASS_PATH = "spring-context";
}
