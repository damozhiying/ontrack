package net.nemerosa.ontrack.extension.github;

import com.google.common.collect.Sets;
import net.nemerosa.ontrack.extension.git.GitExtensionFeature;
import net.nemerosa.ontrack.extension.github.client.OntrackGitHubClientFactory;
import net.nemerosa.ontrack.extension.github.model.GitHubEngineConfiguration;
import net.nemerosa.ontrack.extension.github.service.GitHubConfigurationService;
import net.nemerosa.ontrack.extension.github.service.GitHubIssueServiceConfiguration;
import net.nemerosa.ontrack.extension.issues.export.IssueExportServiceFactory;
import net.nemerosa.ontrack.extension.issues.model.IssueServiceConfiguration;
import net.nemerosa.ontrack.extension.scm.SCMExtensionFeature;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GitHubIssueServiceExtensionTest {

    private GitHubIssueServiceExtension extension;
    private IssueServiceConfiguration configuration;
    private GitHubConfigurationService configurationService;

    @Before
    public void init() {
        configurationService = mock(GitHubConfigurationService.class);
        OntrackGitHubClientFactory gitHubClientFactory = mock(OntrackGitHubClientFactory.class);
        IssueExportServiceFactory issueExportServiceFactory = mock(IssueExportServiceFactory.class);
        extension = new GitHubIssueServiceExtension(
                new GitHubExtensionFeature(new GitExtensionFeature(new SCMExtensionFeature())),
                configurationService,
                gitHubClientFactory,
                issueExportServiceFactory
        );
        GitHubEngineConfiguration engineConfiguration = new GitHubEngineConfiguration(
                "test",
                "url",
                "",
                "",
                ""
        );
        configuration = new GitHubIssueServiceConfiguration(
                engineConfiguration,
                "nemerosa/ontrack"
        );
    }

    @Test
    public void issueServiceIdentifierContainsBothConfigurationAndRepository() {
        when(configurationService.getConfiguration("Test")).thenReturn(
                new GitHubEngineConfiguration(
                        "Test",
                        null,
                        null, null, null
                )
        );
        IssueServiceConfiguration configuration = extension.getConfigurationByName("Test:nemerosa/ontrack");
        assertEquals("github", configuration.getServiceId());
        assertEquals("Test:nemerosa/ontrack", configuration.getName());
        assertTrue(configuration instanceof GitHubIssueServiceConfiguration);
        GitHubIssueServiceConfiguration issueServiceConfiguration = (GitHubIssueServiceConfiguration) configuration;
        assertEquals("Test", issueServiceConfiguration.getConfiguration().getName());
        assertEquals("https://github.com", issueServiceConfiguration.getConfiguration().getUrl());
        assertEquals("nemerosa/ontrack", issueServiceConfiguration.getRepository());
    }

    @Test
    public void getIssueId_full() {
        Optional<String> o = extension.getIssueId(configuration, "#12");
        assertTrue(o.isPresent());
        assertEquals("12", o.get());
    }

    @Test
    public void getIssueId_numeric() {
        Optional<String> o = extension.getIssueId(configuration, "12");
        assertTrue(o.isPresent());
        assertEquals("12", o.get());
    }

    @Test
    public void getIssueId_not_valid() {
        Optional<String> o = extension.getIssueId(configuration, "mm");
        assertFalse(o.isPresent());
    }

    @Test
    public void containsIssueKey_one_in_none() {
        assertFalse(extension.containsIssueKey(configuration, "12", Collections.emptySet()));
    }

    @Test
    public void containsIssueKey_one() {
        assertTrue(extension.containsIssueKey(configuration, "12", Sets.newHashSet("12")));
    }

    @Test
    public void containsIssueKey_none_in_one() {
        assertFalse(extension.containsIssueKey(configuration, "8", Sets.newHashSet("12")));
    }

    @Test
    public void containsIssueKey_one_in_two() {
        assertTrue(extension.containsIssueKey(configuration, "12", Sets.newHashSet("8", "12")));
    }

    @Test
    public void containsIssueKey_none_in_two() {
        assertFalse(extension.containsIssueKey(configuration, "24", Sets.newHashSet("8", "12")));
    }

    @Test
    public void containsIssueKey_jira_in_none() {
        assertFalse(extension.containsIssueKey(configuration, "ITEACH-14", Collections.emptySet()));
    }

    @Test
    public void containsIssueKey_jira_in_one() {
        assertFalse(extension.containsIssueKey(configuration, "ITEACH-14", Sets.newHashSet("15")));
    }

    @Test
    public void containsIssueKey_jira_in_two() {
        assertFalse(extension.containsIssueKey(configuration, "ITEACH-14", Sets.newHashSet("15", "22")));
    }

    @Test
    public void extractIssueKeysFromMessage_none() {
        Set<String> keys = extension.extractIssueKeysFromMessage(configuration, "TEST-1 No GitHub issue");
        assertTrue(keys.isEmpty());
    }

    @Test
    public void extractIssueKeysFromMessage_one() {
        Set<String> keys = extension.extractIssueKeysFromMessage(configuration, "#12 One GitHub issue");
        assertEquals(
                Sets.newHashSet("12"),
                keys
        );
    }

    @Test
    public void extractIssueKeysFromMessage_two() {
        Set<String> keys = extension.extractIssueKeysFromMessage(configuration, "#12 Two GitHub #45 issue");
        assertEquals(
                Sets.newHashSet("12", "45"),
                keys
        );
    }

    @Test
    public void getIssueId_no_prefix() {
        assertEquals(14, extension.getIssueId("14"));
    }

    @Test
    public void getIssueId_with_prefix() {
        assertEquals(14, extension.getIssueId("#14"));
    }


}