# logback-sqs
A Amazon SQS logback appender

## Features
The write request is async (AmazonSQSAsyncClient)
You can set a message max size
If one sets a threadPool value, a 'newFixedThreadPool' is created, otherwise a newCachedThreadPool instance. 