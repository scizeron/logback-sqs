/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.github.scizeron.logback.appender;

import static java.lang.String.format;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.concurrent.Executors;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.spi.DeferredProcessingAware;
import ch.qos.logback.core.status.ErrorStatus;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.auth.SystemPropertiesCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.internal.StaticCredentialsProvider;
import com.amazonaws.services.sqs.AmazonSQSAsyncClient;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;

/**
 * 
 * @author stfciz
 *
 *         2 juin 2015
 */
public class SqsAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

  private String accessKey;

  private String secretKey;

  private String queueUrl;

  private int threadPool = 0;

  private int maxMessageSizeInKB = 256;

  private AmazonSQSAsyncClient sqs = null;

  /**
   * It is the encoder which is ultimately responsible for writing the event to
   * an {@link OutputStream}.
   */
  private Encoder<ILoggingEvent> encoder;
  
  /**
   * 
   * @return
   */
  private AWSCredentialsProvider getCredentials() {
    return new AWSCredentialsProviderChain(new StaticCredentialsProvider(
        new AppenderCredentials()), new SystemPropertiesCredentialsProvider(),
        new EnvironmentVariableCredentialsProvider(),
        new ProfileCredentialsProvider(),
        new InstanceProfileCredentialsProvider());
  }

  @Override
  protected void append(ILoggingEvent eventObject) {
    if (!isStarted()) {
      return;
    }
    subAppend(eventObject);
  }

  /**
   * 
   * @param eventObject
   */
  private void subAppend(ILoggingEvent eventObject) {
    if (!isStarted()) {
      return;
    }

    try {
      // this step avoids LBCLASSIC-139
      if (eventObject instanceof DeferredProcessingAware) {
        ((DeferredProcessingAware) eventObject).prepareForDeferredProcessing();
      }

      this.encoder.doEncode(eventObject);

    } catch (IOException ioe) {
      this.started = false;
      addStatus(new ErrorStatus("IO failure in appender", this, ioe));
    }
  }

  /**
   * 
   * @author stfciz
   *
   * 2 juin 2015
   */
  private class SqsOutputStreamAdapter extends OutputStream {

    @Override
    public void write(int b) throws IOException {
    }

    @Override
    public void write(byte[] bytes) throws IOException {
      if (bytes == null || bytes.length == 0) {
        return;
      }

      final String msg = new String(bytes);

      if (bytes.length > maxMessageSizeInKB * 1024) {
        addWarn(format("Logging event '%s' exceeds the maximum size of %dkB",
            msg, maxMessageSizeInKB));
        return;
      }

      sqs.sendMessageAsync(new SendMessageRequest(queueUrl, msg),
          new AsyncHandler<SendMessageRequest, SendMessageResult>() {
            public void onError(Exception exception) {
              addWarn(format("Appender '%s' failed to send logging event '%s' to '%s'", getName(), msg, queueUrl), exception);
            }
            public void onSuccess(SendMessageRequest request, SendMessageResult result) {
              /** noop **/
            }
          });
    }
  }

  @Override
  public void start() {
    try {
      if (this.encoder == null) {
        addStatus(new ErrorStatus("No encoder set for the appender named \"" + name + "\".", this));
        return;
      }

      close();

      this.sqs = new AmazonSQSAsyncClient(getCredentials(),
          this.threadPool > 0 ? Executors.newFixedThreadPool(this.threadPool)
              : Executors.newCachedThreadPool());
      this.sqs.setEndpoint(new URI(this.queueUrl).getHost());
      this.encoder.init(new SqsOutputStreamAdapter());

      super.start();

    } catch (Exception e) {
      addError(this.getClass() + " start failure", e);
    }
  }

  @Override
  public void stop() {
    close();
    super.stop();
  }

  /**
   * 
   */
  private void close() {
    if (this.sqs != null) {
      this.sqs.shutdown();
      this.sqs = null;
    }
  }

  public String getAccessKey() {
    return accessKey;
  }

  public void setAccessKey(String accessKey) {
    this.accessKey = accessKey;
  }

  public String getSecretKey() {
    return secretKey;
  }

  public void setSecretKey(String secretKey) {
    this.secretKey = secretKey;
  }

  public String getQueueUrl() {
    return queueUrl;
  }

  public void setQueueUrl(String queueUrl) {
    this.queueUrl = queueUrl;
  }



  /**
   * 
   * @param sqs
   */
  public void setSqs(AmazonSQSAsyncClient sqs) {
    this.sqs = sqs;
  }

  /**
   * 
   * @param encoder
   */
  public void setEncoder(Encoder<ILoggingEvent> encoder) {
    this.encoder = encoder;
  }

  /**
   * 
   * @param threadPool
   */
  public void setThreadPool(int threadPool) {
    this.threadPool = threadPool;
  }

  /**
   * 
   * @author Bellevue
   *
   */
  private class AppenderCredentials implements AWSCredentials {

    public String getAWSAccessKeyId() {
      return accessKey;
    }

    public String getAWSSecretKey() {
      return secretKey;
    }
  }

  public void setMaxMessageSizeInKB(int maxMessageSizeInKB) {
    this.maxMessageSizeInKB = maxMessageSizeInKB;
  }

}
