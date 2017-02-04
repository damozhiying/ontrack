package net.nemerosa.ontrack.extension.neo4j;

import net.nemerosa.ontrack.extension.support.AbstractExtensionFeature;
import net.nemerosa.ontrack.model.extension.ExtensionFeatureOptions;
import org.springframework.stereotype.Component;

@Component
public class Neo4JExtensionFeature extends AbstractExtensionFeature {

    public Neo4JExtensionFeature() {
        super("neo4j", "Neo4J", "Export to Neo4J", ExtensionFeatureOptions.DEFAULT
                .withGui(true)
        );
    }

}