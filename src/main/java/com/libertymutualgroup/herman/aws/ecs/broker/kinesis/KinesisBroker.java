/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.libertymutualgroup.herman.aws.ecs.broker.kinesis;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.model.AddTagsToStreamRequest;
import com.amazonaws.services.kinesis.model.CreateStreamRequest;
import com.amazonaws.services.kinesis.model.DeleteStreamRequest;
import com.amazonaws.services.kinesis.model.DescribeStreamRequest;
import com.amazonaws.services.kinesis.model.DescribeStreamResult;
import com.amazonaws.services.kinesis.model.EncryptionType;
import com.amazonaws.services.kinesis.model.ListStreamsRequest;
import com.amazonaws.services.kinesis.model.ListStreamsResult;
import com.amazonaws.services.kinesis.model.ListTagsForStreamRequest;
import com.amazonaws.services.kinesis.model.ListTagsForStreamResult;
import com.amazonaws.services.kinesis.model.ResourceNotFoundException;
import com.amazonaws.services.kinesis.model.StartStreamEncryptionRequest;
import com.amazonaws.services.kinesis.model.StreamDescription;
import com.amazonaws.services.kinesis.model.Tag;
import com.atlassian.bamboo.build.logger.BuildLogger;
import com.libertymutualgroup.herman.aws.ecs.EcsPushDefinition;
import com.libertymutualgroup.herman.aws.ecs.cluster.EcsClusterMetadata;
import com.libertymutualgroup.herman.task.common.CommonTaskProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KinesisBroker {

    private static final Logger LOGGER = LoggerFactory.getLogger(KinesisBroker.class);

    private BuildLogger buildLogger;
    private AmazonKinesis client;
    private EcsClusterMetadata clusterMetadata;
    private EcsPushDefinition definition;
    private CommonTaskProperties taskProperties;

    public KinesisBroker(BuildLogger buildLogger, AmazonKinesis client, EcsClusterMetadata clusterMetadata,
        EcsPushDefinition definition, CommonTaskProperties taskProperties) {
        this.buildLogger = buildLogger;
        this.client = client;
        this.clusterMetadata = clusterMetadata;
        this.definition = definition;
        this.taskProperties = taskProperties;
    }

    public void brokerStream(KinesisStream stream) {
        try {
            // Describe the Stream and check if it already exists
            DescribeStreamRequest describeStreamRequest = new DescribeStreamRequest().withStreamName(stream.getName());
            StreamDescription streamDescription = client.describeStream(describeStreamRequest).getStreamDescription();
            buildLogger
                .addBuildLogEntry(String
                    .format("Stream %s has a status of %s.%n", stream.getName(), streamDescription.getStreamStatus()));

            if ("DELETING".equals(streamDescription.getStreamStatus())) {
                buildLogger.addBuildLogEntry(String.format("Stream %s is being deleted.", stream.getName()));
            }

            // Wait for the stream to become active if it is not yet ACTIVE.
            if (!"ACTIVE".equals(streamDescription.getStreamStatus())) {
                waitForStreamToBecomeAvailable(stream.getName());
            }
        } catch (ResourceNotFoundException ex) {
            LOGGER.debug("Stream not found: " + stream.getName(), ex);
            buildLogger.addBuildLogEntry(String.format("Stream %s does not exist. Creating it now.", stream.getName()));

            CreateStreamRequest createStreamRequest = new CreateStreamRequest();
            createStreamRequest.setStreamName(stream.getName());
            createStreamRequest.setShardCount(stream.getShardCount());
            client.createStream(createStreamRequest);

            // Stream is now created. Waiting for it to become active so we can add tags and encryption.
            try {
                waitForStreamToBecomeAvailable(stream.getName());

                // Add tags to stream
                AddTagsToStreamRequest addTagsToStreamRequest = new AddTagsToStreamRequest();
                addTagsToStreamRequest.setStreamName(stream.getName());
                addTagsToStreamRequest
                    .addTagsEntry(this.taskProperties.getSbuTagKey(), clusterMetadata.getNewrelicSbuTag());
                addTagsToStreamRequest
                    .addTagsEntry(this.taskProperties.getOrgTagKey(), clusterMetadata.getNewrelicOrgTag());
                addTagsToStreamRequest.addTagsEntry(this.taskProperties.getAppTagKey(), definition.getAppName());
                addTagsToStreamRequest
                    .addTagsEntry(this.taskProperties.getClusterTagKey(), clusterMetadata.getClusterId());
                client.addTagsToStream(addTagsToStreamRequest);

                // Add encryption to stream
                StartStreamEncryptionRequest startStreamEncryptionRequest = new StartStreamEncryptionRequest();
                startStreamEncryptionRequest.setEncryptionType(EncryptionType.KMS);
                startStreamEncryptionRequest.setKeyId("alias/aws/kinesis");
                startStreamEncryptionRequest.setStreamName(stream.getName());
                client.startStreamEncryption(startStreamEncryptionRequest);
            } catch (Exception e) {
                LOGGER.debug("Stream did not become active: " + stream.getName(), e);
                buildLogger.addErrorLogEntry(String
                    .format("Stream %s never became active, failed to add tags or failed to start encryption: %s",
                        stream.getName(), e.getMessage()));
            }
        } catch (Exception e) {
            LOGGER.debug("Stream did not become active: " + stream.getName(), e);
            buildLogger.addErrorLogEntry(String
                .format("Stream %s never became active while trying to check if it already exists.", stream.getName()));
        }
    }

    private void deleteStream(String streamName) {
        try {
            DeleteStreamRequest deleteStreamRequest = new DeleteStreamRequest();
            deleteStreamRequest.setStreamName(streamName);
            client.deleteStream(deleteStreamRequest);
        } catch (Exception e) {
            LOGGER.debug("Error deleting stream: " + streamName, e);
            buildLogger.addErrorLogEntry(String.format("Error deleting Stream %s.", streamName));
        }
    }

    private void waitForStreamToBecomeAvailable(String streamName) throws InterruptedException {
        buildLogger.addBuildLogEntry(String.format("Waiting for Stream %s to become ACTIVE...%n", streamName));

        long startTime = System.currentTimeMillis();
        long endTime = startTime + TimeUnit.MINUTES.toMillis(10);
        while (System.currentTimeMillis() < endTime) {
            Thread.sleep(TimeUnit.SECONDS.toMillis(20));

            try {
                DescribeStreamRequest describeStreamRequest = new DescribeStreamRequest();
                describeStreamRequest.setStreamName(streamName);
                // ask for no more than 10 shards at a time -- this is an optional parameter
                describeStreamRequest.setLimit(10);
                DescribeStreamResult describeStreamResponse = client.describeStream(describeStreamRequest);

                String streamStatus = describeStreamResponse.getStreamDescription().getStreamStatus();
                buildLogger.addBuildLogEntry(String.format("Current state: %s", streamStatus));

                if ("ACTIVE".equals(streamStatus)) {
                    return;
                }
            } catch (ResourceNotFoundException ex) {
                // ResourceNotFound means the stream doesn't exist yet,
                // so ignore this error and just keep polling.
                LOGGER.debug("Stream does not exist: " + streamName, ex);
            } catch (AmazonServiceException ase) {
                throw ase;
            }
        }

        throw new RuntimeException(String.format("Stream %s never became active", streamName));
    }

    public void checkStreamsToBeDeleted() {
        ListStreamsRequest listStreamsRequest = new ListStreamsRequest();
        ListStreamsResult listStreamsResult = client.listStreams(listStreamsRequest);
        List<String> streamNames = listStreamsResult.getStreamNames();

        for (String streamName : streamNames) {
            ListTagsForStreamRequest listTagsForStreamRequest = new ListTagsForStreamRequest();
            listTagsForStreamRequest.setStreamName(streamName);
            ListTagsForStreamResult listTagsForStreamResult = client.listTagsForStream(listTagsForStreamRequest);
            List<Tag> tags = listTagsForStreamResult.getTags();

            for (Tag tag : tags) {
                if (definition.getAppName().equals(tag.withKey(this.taskProperties.getAppTagKey()).getValue())) {
                    if (definition.getStreams() != null) {
                        List<KinesisStream> kinesisStreams = definition.getStreams();
                        List<String> kinesisStreamNames = new ArrayList<>();
                        for (KinesisStream ks : kinesisStreams) {
                            kinesisStreamNames.add(ks.getName());
                        }

                        if (!kinesisStreamNames.contains(streamName)) {
                            deleteStream(streamName);
                            buildLogger.addBuildLogEntry(String.format("Deleted Stream %s.", streamName));
                        }
                    } else {
                        deleteStream(streamName);
                        buildLogger.addBuildLogEntry(String.format("Deleted Stream %s.", streamName));
                    }
                }
            }
        }

    }
}
