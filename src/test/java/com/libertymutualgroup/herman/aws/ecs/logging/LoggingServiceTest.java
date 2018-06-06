package com.libertymutualgroup.herman.aws.ecs.logging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.amazonaws.services.ecs.model.RegisterTaskDefinitionResult;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.atlassian.bamboo.build.logger.BuildLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.libertymutualgroup.herman.task.ecs.ECSPushTaskProperties;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.mockito.Matchers;

public class LoggingServiceTest {

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_NullSplunkUrl() {
        // GIVEN
        BuildLogger logger = mock(BuildLogger.class);
        String splunkUrl = null;
        ECSPushTaskProperties ecsPushTaskProperties = new ECSPushTaskProperties();

        // WHEN
        new LoggingService(logger).withSplunkInstanceValues(splunkUrl, ecsPushTaskProperties);

        // THEN
        // expect error
    }

    @Test
    public void provideSplunkLog() throws Exception {
        // GIVEN
        BuildLogger logger = mock(BuildLogger.class);

        // Create Task Result
        final String family = "test-ecs-push";
        final Integer revision = 34;
        TaskDefinition taskDefinition = new TaskDefinition()
            .withFamily(family)
            .withRevision(revision);
        RegisterTaskDefinitionResult taskResult = new RegisterTaskDefinitionResult()
            .withTaskDefinition(taskDefinition);

        // Set up mock Splunk instances
        final String splunkInstancesFile = "/config/plugin-tasks.yml";
        final String splunkInstancesYml = IOUtils.toString(this.getClass().getResourceAsStream(splunkInstancesFile));

        // Get first splunk instance as a test
        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        ECSPushTaskProperties ecsPushTaskProperties = objectMapper
            .readValue(splunkInstancesYml, ECSPushTaskProperties.class);
        SplunkInstance splunkInstance = ecsPushTaskProperties.getSplunkInstances().stream().findFirst()
            .orElseThrow(() -> new RuntimeException("There are no mock Splunk instances"));

        // Set up test logging service
        final LoggingService loggingService = new LoggingService(logger)
            .withSplunkInstanceValues(splunkInstance.getHttpEventCollectorUrl(), ecsPushTaskProperties);

        // WHEN
        loggingService.provideSplunkLog(taskResult);

        // THEN
        verify(logger, times(1)).addBuildLogEntry(Matchers.contains("Splunk Logs"));
        verify(logger, times(1)).addBuildLogEntry(Matchers.contains(splunkInstance.getWebUrl()));
        verify(logger, times(1)).addBuildLogEntry(Matchers.contains(family + "-" + revision));
    }
}