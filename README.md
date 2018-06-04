# upscan-initiate

Microservice for initiating the upload of files created externally to HMRC estate, namely from members of the public.
This is not intended to be used for transfer of files from one HMRC service to another, for this you need to intergrate
directly with the file transfer service.

[![Build Status](https://travis-ci.org/hmrc/upscan-initiate.svg)](https://travis-ci.org/hmrc/upscan-initiate) [ ![Download](https://api.bintray.com/packages/hmrc/releases/upscan-initiate/images/download.svg) ](https://bintray.com/hmrc/releases/upscan-initiate/_latestVersion)

# Upscan user manual

## Introduction

Upscan service allows consuming services to orchestrate the process of uploading files by the end users. Upscan provides
temporary storage of uploaded file, ensures that the file isn't harmful (doesn't contain viruses) and verifies
against predefined preconditions (like maximum size).
The process of uploading and verifying of uploaded file is performed asynchronously without involvement of the consuming service.

## File upload workflow

* Consuming service requests upload of a single file. It makes a HTTP POST call to `/upscan/initiate` endpoint with details of
requested upload, including size constraints and callback URL 
* Upscan service replies with template of the HTTP POST form that should be used to upload the file and with unique reference
of the upload that can be later used by the consumer service to identify the file
* Consuming service returns the form to the user, and the form is  displayed in the end user'ss browser.
* End user uploads the file using their web browser.
* In the background, Upscan service performs all necessary check of the file
* If file is safe, Upscan service notifies consuming service, using previously provided callback URL. The callback body contains an URL to fetch the uploaded file
* Consuming service downloads the file using provided URL. The link is valid for specified time (TODO - not specified yet)
* After specified time the file is automatically removed from the remote storage. Upscan does NOT keep files indefinitely.
* If the file is harmful (e.g. contains viruses), Upscan notifies consuming service using previously provided callback URL. The callback
body contains detailed information about issues with the file.
* If consuming service fails to accept callback (service is down, answered with HTTP status other than 2xx), the callback will be retried
after specified time (TODO agree on that).

## Service usage

In order to initiate upload, consuming service have to make a POST request to `https://upscan-initiate.public.mdtp/upscan/initiate` endpoint
with details about expected upload, which include additional metadata, additional constraints
about content type and size, and callback URL which will be used to notify user. Here is an example of the request:
```
{
	"callbackUrl": "http://myservice.com/callback",
	"minimumFileSize" : 0,
	"maximumFileSize" : 1024,
	"expectedContentType": "application/xml"
}
```
Meaning of parameters:

| Parameter name|Description|Required|
|--------------|-----------|--------|
|callbackUrl   |Url that will be called after file will be successfully processed| yes|
|minimumFileSize|Minimum file size, if not specified any file size is allowed|no|
|maximumFileSize|Maximum file size, if not specified, global maximum file size will be applied (by default 100MB)|no|
|expectedContentType|Expected MIME type of uploaded file|no|

The service replies with JSON containg reference of the upload and information about the POST form that has to be sent in order to upload the file:
```
{
    "reference": "11370e18-6e24-453e-b45a-76d3e32ea33d",
    "uploadRequest": {
        "href": "https://bucketName.s3.eu-west-2.amazonaws.com",
        "fields": {
            "Content-Type": "application/xml",
            "acl": "private",
            "key": "xxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
            "policy": "xxxxxxxx==",
            "x-amz-algorithm": "AWS4-HMAC-SHA256",
            "x-amz-credential": "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
            "x-amz-date": "yyyyMMddThhmmssZ",
            "x-amz-meta-callback-url": "http://myservice.com/callback",
            "x-amz-signature": "xxxx"
        }
    }
}
```
In order to upload the file, initiating microservice or the client should send the following form:
```
<form method="POST" href="...value of the href from the response above...">
    <input type="hidden" name="x-amz-algorithm" value="AWS4-HMAC-SHA256">
    ... all the fields returned in "fields" map in the response above ...
    <input type="file" name="file"/> <- form field representing the file to upload
    <input type="submit" value="OK"/>
</form>
```

You can also post this form programmatically from your backend code or make an async call using AJAX. 
It's important that the form will use multipart encoding (`multipart/form-data`), NOT `application/x-www-form-urlencoded`.
If you use application/x-www-form-urlencoded, AWS will be returning confusing error messages.
It's also essential that the 'file' field of the form is the last one.

When form will be submitted succesfully, the service will return HTTP 204 response with empty body. In case of any error,
the service will return proper HTTP error response (4xx, 5xx) with details of the problem encoded as XML document. See Error handling
section for details.

When file is successfully uploaded by the user and checked by antivirus scanner, Upscan service
will make a POST call to the URL specified as a 'callbackUrl' parameter with the following body:

```
{
    "reference" : "11370e18-6e24-453e-b45a-76d3e32ea33d",
    "fileStatus" : "READY",
    "url" : "https://bucketName.s3.eu-west-2.amazonaws.com?1235676"
}
```

If a virus has been detected in the file, or if there is any other problem with the file, the following JSON is
sent to the callback URL:

```
{
    "reference" : "11370e18-6e24-453e-b45a-76d3e32ea33d",
    "fileStatus" : "FAILED",
    "failureDetails": {
        "failureReason": "QUARANTINED",
        "message": "This file has a virus"
    }
}
```

```
{
    "reference" : "11370e18-6e24-453e-b45a-76d3e32ea33d",
    "fileStatus" : "FAILED",
    "failureDetails": {
        "failureReason": "REJECTED",
        "message": "MIME type $mime is not allowed for service $service-name"
    }
}
```

At the moment, the list of failure reasons is as follows, but additional ones may be added:
- `QUARANTINED` if a file has a virus
- `REJECTED` if a file is not of an allowed type for the service
- `UNKNOWN` if an unexpected error has occurred

## Whitelisting client services
Upscan supports configuration of supported file types on a per-service basis.

Calling services must therefore identify themselves in requests via the `User-Agent` header.
If the supplied value is not in Upscan's list of allowed services then the `/initiate` call will fail with a `403` error.

In addition to returning a `403` error, Upscan will log details of the Forbidden request. For example:

```json
{
    "app":"upscan-initiate",
    "message":"Invalid User-Agent: [Some(my-unknown-service-name)].",
    "logger":"application",
    "level":"WARN"
}
```

*Note:* If you are using [http-verbs](https://github.com/hmrc/http-verbs) to call Upscan, then the `User-Agent` header will be set automatically.
(See: [HttpVerb.scala](https://github.com/hmrc/http-verbs/blob/2807dc65f64009bd7ce1f14b38b356e06dd23512/src/main/scala/uk/gov/hmrc/http/HttpVerb.scala#L53))


To start the onboarding process for a new service, please contact the Upscan team via [#team-plat-services](https://hmrcdigital.slack.com/messages/C705QD804).


## Error handling

In case of problems with uploading the file (file too small, too large, configuration problems), AWS
will send response that complies with this document: https://docs.aws.amazon.com/AmazonS3/latest/API/ErrorResponses.html

## Design considerations

The Upscan service supports uploading of single files only. If a consuming service wants to upload multiple files during one user's journey,
it should make multiple independent calls to upscan-initiate.

The callback URL provided by the service will be send in plain text through the network and visible to the end user. Because of this, the URL shouldn't point to the host that is publicly available from outside of MDTP network. It also shouldn't  contain any sensitive data (e.g. user identifiers, session tokens)
The callback will be done from the inside of MDTP environment, so bear that in mind when specifying
callback URL.

Upscan intentionally doesn't allow consuming services to attach metadata no tags to the uploaded file. It is expected that the service
using Upscan will keep track of requested uploads, and keep their own metadata internally.

Upscan should NOT be used to route or transfer files between different services on the platform - it is for end users to upload files into the HMRC estate.

# Architecture of the service

![Architecture](architecture.png)

The service heavily relies on Amazon Web Services to provide desired functionality. The most important
AWS futures that are used by the service are:
* [Pre-signed POST requests](https://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-UsingHTTPPOST.html) - to allow end users to upload file directly to S3
* [Pre-signed URLs](https://docs.aws.amazon.com/AmazonS3/latest/dev/ShareObjectPreSignedURL.html) - to allow consuming service to download uploaded file directly from S3

Upscan consists of the following components:
* **MDTP microservices**
  * **upscan-initiate service** - standalone service running on MDTP platform which creates pre-signed POST
forms that can be later used by consuming service to upload the file
  * **upscan-notify service** - standalone service running on MDTP platform which polls outbound and quarantine event queues for results
of file verification. This service makes calls to consuming services using callback urls provided at the beginning of the process
* **AWS components**
  * **inbound S3 bucket** - AWS S3 bucket used by users to upload their files
  * **inbound event queue** - AWS SQS queue that contains events triggered by uploads to inbound S3 bucket
  * **outbound S3 bucket** - AWS S3 bucket containing files that have been verified by antivirus and are clean
  * **outbound event queue** - AWS SQS queue that contains events triggered by copying clean files to outbound S3 bucket
  * **quarantine S3 bucket** - whenever antivirus scanner detects virus in the file, it creates and object in this bucket that contains details of infected file
  * **quarantine event queue** - AWS SQS queue that contains events triggered by detecting viruses in files
* **Other services**
  * **antivirus scanner** - component running on AWS that fetches files form inbound S3 bucket, scans them and if these are clean, moves to outbound bucket

# Running and maintenance of the service

## Running locally

In order to run the service against one of HMRC accounts (labs, live) it's necessary to have an AWS accounts with proper
role. See [UpScan Accounts/roles](https://github.com/hmrc/aws-users/blob/master/AccountLinks.md)
for proper details.

Prerequisites:
- AWS accounts with proper roles setup
- Proper AWS credential configuration set up according to this document [aws-credential-configuration](https://github.com/hmrc/aws-users), with the credentials below:
```
[webops-users]
mfa_serial = arn:aws:iam::638924580364:mfa/YOUR_AWS_USER_NAME
aws_access_key_id = YOUR_ACCESS_KEY_HERE
aws_secret_access_key = YOUR_SECRET_KEY_HERE
output = json
region = eu-west-2

[upscan-labs-engineer]
source_profile = webops-users
role_arn = arn:aws:iam::063874132475:role/RoleServiceUpscanEngineer
mfa_serial = arn:aws:iam::638924580364:mfa/YOUR_AWS_USER_NAME
aws_access_key_id = YOUR_ACCESS_KEY_HERE
aws_secret_access_key = YOUR_SECRET_KEY_HERE
output = json
region = eu-west-2
```
- Working AWS MFA authentication
- Have python 2.7 installed
- Install botocore and awscli python modules locally:
  - For Linux:
```
sudo pip install botocore
sudo pip install awscli
```
  - For Mac (Mac has issues with pre-installed version of ```six``` as discussed [here](https://github.com/pypa/pip/issues/3165) ):
```
sudo pip install botocore --ignore-installed six
sudo pip install awscli --ignore-installed six
```

In order to run the app against lab environment it's necessary to run the following commands:
```
export AWS_S3_BUCKET_INBOUND=name of inbound s3 bucket you would like to use
export AWS_DEFAULT_PROFILE=name of proper profile in ~/.aws/credentials file
./aws-profile sbt
```
These commands will give you an access to SBT shell where you can run the service using 'run' or 'start' commands.
 

## Related projects, useful links:

* [upscan-verify](https://github.com/hmrc/upscan-verify) - service responsible for verifying the health of uploaded files
* [upscan-notify](https://github.com/hmrc/upscan-notify) - service responsible for notifying consuming services about the status of uploaded files
* [upscan-infrastructue](https://github.com/hmrc/upscan-infrastructure) - AWS infrastructure provisioning scripts

### Testing
* [upscan-listener](https://github.com/hmrc/upscan-listener) - service used in testing to receive callbacks from `upscan-notify`
* [upscan-stub](https://github.com/hmrc/upscan-stub) - service used locally (via `ServiceManager`) to stub `upscan-initiate`, `upscan-verify`, `upscan-notify` and uploads to AWS S3.
* [upscan-acceptance-tests](https://github.com/hmrc/upscan-acceptance-tests) - end-to-end acceptance tests of the upscan ecosystem
* [upscan-performance-tests](https://github.com/hmrc/upscan-performance-tests) - performance tests of the upscan ecosystem

### Slack
* [#team-plat-services](https://hmrcdigital.slack.com/messages/C705QD804/)
* [#event-upscan](https://hmrcdigital.slack.com/messages/C8XPL559N)

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")

### TO DO
* Resolve file constraints (mimetypes)
