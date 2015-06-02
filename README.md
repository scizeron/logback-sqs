# logback-sqs
A Amazon SQS logback appender

## Features
The write request is async (AmazonSQSAsyncClient)
You can set a message max size
If one sets a threadPool value, a 'newFixedThreadPool' is created, otherwise a newCachedThreadPool instance. 

## Credentials
Aws credentials can be provided in different ways, the discovery follows this order : 
 - accessKey & secretKey are in the logback file SqsAppender configuration
 - or in the jvm properties : aws.accessKeyId & aws.secretKey 
 - or in the env properties : AWS_ACCESS_KEY_ID, AWS_SECRET_KEY
 - or via aws profile
 - or via ec2 instance profile 
