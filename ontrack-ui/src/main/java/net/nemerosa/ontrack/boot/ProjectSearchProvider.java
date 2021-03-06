package net.nemerosa.ontrack.boot;

import net.nemerosa.ontrack.model.structure.NameDescription;
import net.nemerosa.ontrack.model.structure.Project;
import net.nemerosa.ontrack.model.structure.SearchResult;
import net.nemerosa.ontrack.model.structure.StructureService;
import net.nemerosa.ontrack.ui.controller.URIBuilder;
import net.nemerosa.ontrack.ui.support.AbstractSearchProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class ProjectSearchProvider extends AbstractSearchProvider {

    private final StructureService structureService;

    @Autowired
    public ProjectSearchProvider(URIBuilder uriBuilder, StructureService structureService) {
        super(uriBuilder);
        this.structureService = structureService;
    }

    @Override
    public boolean isTokenSearchable(String token) {
        return Pattern.matches(NameDescription.NAME, token);
    }

    @Override
    public Collection<SearchResult> search(String token) {
        Optional<Project> oProject = structureService.findProjectByName(token);
        if (oProject.isPresent()) {
            Project project = oProject.get();
            return Collections.singletonList(
                    new SearchResult(
                            project.getEntityDisplayName(),
                            "",
                            uriBuilder.getEntityURI(project),
                            uriBuilder.getEntityPage(project),
                            100
                    )
            );
        } else {
            return Collections.emptyList();
        }
    }
}
