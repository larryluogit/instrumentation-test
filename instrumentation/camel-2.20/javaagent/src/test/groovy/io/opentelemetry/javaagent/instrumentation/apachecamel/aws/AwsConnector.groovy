/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.aws

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.BucketNotificationConfiguration
import com.amazonaws.services.s3.model.ObjectListing
import com.amazonaws.services.s3.model.QueueConfiguration
import com.amazonaws.services.s3.model.S3Event
import com.amazonaws.services.s3.model.S3ObjectSummary
import com.amazonaws.services.s3.model.SetBucketNotificationConfigurationRequest
import com.amazonaws.services.sns.AmazonSNSAsyncClient
import com.amazonaws.services.sns.model.CreateTopicResult
import com.amazonaws.services.sqs.AmazonSQSAsyncClient
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest
import com.amazonaws.services.sqs.model.PurgeQueueRequest
import com.amazonaws.services.sqs.model.ReceiveMessageRequest

class AwsConnector {

  private AmazonSQSAsyncClient sqsClient
  private AmazonS3Client s3Client
  private AmazonSNSAsyncClient snsClient

  static liveAws() {
    AwsConnector awsConnector = new AwsConnector()

    awsConnector.sqsClient = AmazonSQSAsyncClient.asyncBuilder()
      .build()
    awsConnector.s3Client = AmazonS3Client.builder()
      .build()
    awsConnector.snsClient = AmazonSNSAsyncClient.asyncBuilder()
      .build()

    return awsConnector
  }

  AmazonSQSAsyncClient getSqsClient() {
    return sqsClient
  }

  AmazonS3Client getS3Client() {
    return s3Client
  }

  AmazonSNSAsyncClient getSnsClient() {
    return snsClient
  }

  def createQueue(String queueName) {
    println "Create queue ${queueName}"
    return sqsClient.createQueue(queueName).getQueueUrl()
  }

  def getQueueArn(String queueUrl) {
    println "Get ARN for queue ${queueUrl}"
    return sqsClient.getQueueAttributes(
      new GetQueueAttributesRequest(queueUrl)
        .withAttributeNames("QueueArn")).getAttributes()
      .get("QueueArn")
  }

  private static final String SNS_POLICY = "{" +
    "  \"Statement\": [" +
    "    {" +
    "      \"Effect\": \"Allow\"," +
    "      \"Principal\": \"*\"," +
    "      \"Action\": \"sns:Publish\"," +
    "      \"Resource\": \"%s\"" +
    "    }]" +
    "}"

  def setQueuePublishingPolicy(String queueUrl, String queueArn) {
    println "Set policy for queue ${queueArn}"
    sqsClient.setQueueAttributes(queueUrl, Collections.singletonMap("Policy", String.format(SQS_POLICY, queueArn)))
  }

  private static final String SQS_POLICY = "{" +
    "  \"Statement\": [" +
    "    {" +
    "      \"Effect\": \"Allow\"," +
    "      \"Principal\": \"*\"," +
    "      \"Action\": \"sqs:SendMessage\"," +
    "      \"Resource\": \"%s\"" +
    "    }]" +
    "}"

  def createBucket(String bucketName) {
    println "Create bucket ${bucketName}"
    s3Client.createBucket(bucketName)
  }

  def deleteBucket(String bucketName) {
    println "Delete bucket ${bucketName}"
    ObjectListing objectListing = s3Client.listObjects(bucketName)
    Iterator<S3ObjectSummary> objIter = objectListing.getObjectSummaries().iterator()
    while (objIter.hasNext()) {
      s3Client.deleteObject(bucketName, objIter.next().getKey())
    }
    s3Client.deleteBucket(bucketName)
  }

  def enableS3ToSqsNotifications(String bucketName, String sqsQueueArn) {
    println "Enable notification for bucket ${bucketName} to queue ${sqsQueueArn}"
    BucketNotificationConfiguration notificationConfiguration = new BucketNotificationConfiguration()
    notificationConfiguration.addConfiguration("sqsQueueConfig",
      new QueueConfiguration(sqsQueueArn, EnumSet.of(S3Event.ObjectCreatedByPut)))
    s3Client.setBucketNotificationConfiguration(new SetBucketNotificationConfigurationRequest(
      bucketName, notificationConfiguration))
  }

  def createTopicAndSubscribeQueue(String topicName, String queueArn) {
    println "Create topic ${topicName} and subscribe to queue ${queueArn}"
    CreateTopicResult ctr = snsClient.createTopic(topicName)
    snsClient.subscribe(ctr.getTopicArn(), "sqs", queueArn)
    return ctr.getTopicArn()
  }

  def receiveMessage(String queueUrl) {
    println "Receive message from queue ${queueUrl}"
    return sqsClient.receiveMessage(new ReceiveMessageRequest(queueUrl).withWaitTimeSeconds(20))
  }

  def purgeQueue(String queueUrl) {
    println "Purge queue ${queueUrl}"
    sqsClient.purgeQueue(new PurgeQueueRequest(queueUrl))
  }

  def publishSampleNotification(String topicArn) {
    snsClient.publish(topicArn, "Hello There")
  }
}
