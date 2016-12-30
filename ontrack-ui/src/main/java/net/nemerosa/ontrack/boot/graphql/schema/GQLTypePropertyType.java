package net.nemerosa.ontrack.boot.graphql.schema;

import graphql.schema.GraphQLObjectType;
import net.nemerosa.ontrack.boot.graphql.support.GraphqlUtils;
import org.springframework.stereotype.Component;

import static graphql.schema.GraphQLObjectType.newObject;

/**
 * Description of a {@link net.nemerosa.ontrack.model.structure.PropertyTypeDescriptor}.
 */
@Component
public class GQLTypePropertyType implements GQLType {

    public static final String PROPERTY_TYPE = "PropertyType";

    @Override
    public GraphQLObjectType getType() {
        return newObject()
                .name(PROPERTY_TYPE)
                .field(GraphqlUtils.stringField("typeName", "Qualified type name"))
                .field(GraphqlUtils.stringField("name", "Display type name"))
                .field(GraphqlUtils.stringField("description", "Short description for the type"))
                .build();
    }

}
