package net.nemerosa.ontrack.model.security;

import lombok.Data;

/**
 * Association of a project role and a projet ID.
 */
@Data
public class ProjectRoleAssociation {

    private final int projectId;
    private final ProjectRole projectRole;

    public boolean isGranted(Class<? extends ProjectFunction> fn) {
        return projectRole.isGranted(fn);
    }
}
