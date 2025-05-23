# upscan-initiate <a name="top"></a>

Microservice for initiating the upload of files created externally to HMRC estate. These could be from members of the public or third-party services.
This service is not for transfer of files from one HMRC service to another. Please speak to us for advice on file transfer integration.

[ ![Download](https://api.bintray.com/packages/hmrc/releases/upscan-initiate/images/download.svg) ](https://bintray.com/hmrc/releases/upscan-initiate/_latestVersion)

# TLDR

- *We strongly advise against hardcoding the "fields" in the response of `initiate` and `v2/initiate`. These are subject to change.*
- *The file must be the last field in the actual upload request.*
- *You must use multipart encoding (multipart/form-data) NOT application/x-www-form-urlencoded. The error message returned by AWS is obscure when the wrong content type is used.*

# Upscan user manual

## Contents
1. [Introduction](#introduction)
2. [File upload workflow](#workflow)
3. [Service usage](#service)
  a. [Consuming services](#service__consuming-services)
  b. [Requesting a URL to upload to](#service__request)
  c. [The file upload](#service__upload)
  d. [File upload outcome](#service__uoutcome)
  e. [File processing outcome](#service__poutcome)
    i. [Success](#service__poutcome__success)
    ii. [Failure](#service__poutcome__failure)
4. [Error handling](#error)
5. [Design considerations](#design)
  a. [Uploading multiple files](#design__multiple)
  b. [Security](#design__security)
  c. [File metadata](#design__metadata)
6. [Architecture of the service](#architecture)
7. [Running and maintenance of the service](#run)
  a. [Running locally](#run__local)
8. [Appendix](#appendix)
  a. [Quick reference figures](#appendix__figures)
  b. [Related projects, useful links](#appendix__links)
    i. [Testing](#appendix__links__testing)
    ii. [Slack](#appendix__links__slack)
  c. [License](#appendix__license)

## Introduction <a name="introduction"></a>

In this "user manual" the collection of microservices that make up Upscan are discussed, not just `upscan-initiate`. This documentation is here as `upscan-initiate` is the microservice which developers will interact with directly.

The Upscan service allows consuming services to orchestrate the uploading of files. Upscan provides
temporary storage of the uploaded file, ensures that the file isn't harmful (doesn't contain viruses) and verifies against predefined restrictions provided by the consuming service (e.g. file type & file size).
Once the upload URL has been requested, upload and verification of a file are performed asynchronously without the involvement of the consuming service.

[[Back to the top]](#top)

## File upload workflow <a name="workflow"></a>

* Consuming service requests upload of a single file. It makes a HTTP POST call to the `/upscan/initiate` endpoint with details of the requested upload (including a callback URL and optional file size constraints)
* Upscan service replies with a template of the HTTP POST form that will be used to upload the file. A unique file reference is also provided to allow the consuming service to correlate the upload request with notifications to the callback URL provided
* Consuming service passes the form details to the end-user, either via a control on a webpage or to an internal/external service which will upload the file
* The end user uploads the file.  This must take place within 7 days of the upload being initiated.
* Upscan service performs all checks on the uploaded file
* If the file passes all checks, a notification is sent as a POST request to a consuming service. This notification contains the URL to GET the file from
* Consuming service downloads the file using the provided URL or passes this URL on to another service which will make use of the file location.  This download URL only remains valid for a limited duration (see [Success](#service__poutcome__success))
* After some time the file is automatically removed from the remote storage. Upscan does NOT keep files indefinitely
* If the file fails a check, a notification is sent to the consuming service containing information on the failed check. The file is unavailable for retrieval
* If the consuming service fails to respond to the callback request (e.g. the consuming service is down, the consuming service answered with an HTTP status code other than 2xx), the callback will be retried up to a maximum of 30 retries. The time interval between the retries is 60 seconds.
Configuration of these values is here (https://github.com/hmrc/upscan-infrastructure/blob/master/modules/sqs/main.tf)

Please view the [Upscan Service & Flow Overview in Confluence](https://confluence.tools.tax.service.gov.uk/pages/viewpage.action?pageId=101663507) for a more visual representation.

[[Back to the top]](#top)

## Service usage <a name="service"></a>

### Consuming Services <a name="service__consuming-services"></a>

A _consuming service_ is the intermediary between the end-user and Upscan, responsible for initiating upload requests and configuring the parameters of the upload itself (minimum and maximum file sizes, permitted MIME types and redirect URLs, etc.)

Consuming services must identify themselves to `upscan-initiate` in one of two ways:

 1. Via the `User-Agent` header, which must always be provided
 2. By including a `"consumingService"` field in the request body, which overrides the value provided in the `User-Agent` header and may be useful if your use-case involves proxies or requires _logical_ consuming services that differ from the actual service that initiates the request

Consuming services may configure permitted MIME-types in [upscan-app-config](https://github.com/hmrc/upscan-app-config) (a default set is used in the case that no consuming service-specific configuration is provided.)

### Requesting a URL to upload to <a name="service__request"></a>

The consuming service makes a POST request to `/upscan/initiate` or `upscan/v2/initiate`.
The service must also provide a callbackUrl for asynchronous notification of the outcome of an upload. The callback will be made from inside the MDTP environment. Hence, the callback URL should comprise the MDTP internal callback address and not the public domain address.

**Note:** `callbackUrl` must use the `https` protocol.
(Although this rule is relaxed when testing locally with [upscan-stub](https://github.com/hmrc/upscan-stub) rather than [upscan-initiate](https://github.com/hmrc/upscan-initiate).
In this stubbed scenario a `callbackUrl` referring to localhost may still specify `http` as the protocol.)

Session-ID / Request-ID headers are used to link the file with the user's journey.

*Note:* If you are using `[http-verbs](https://github.com/hmrc/http-verbs)` to call Upscan, all the headers will be set automatically
(See: [HttpVerb.scala](https://github.com/hmrc/http-verbs/blob/2807dc65f64009bd7ce1f14b38b356e06dd23512/src/main/scala/uk/gov/hmrc/http/HttpVerb.scala#L53))

The service replies with a pre-filled template for the upload of the file.
The JSON response contains a globally unique identifier for the upload (known as both _reference_ and _key_).
The consuming service can use this identifier to correlate the subsequent file processing outcome with this upload initiation (see [File processing outcome](#service__poutcome)).
This identifier can also be used to track the journey of an upload through the various Upscan services.  If you require support from the Upscan team in relation to a particular
upload, please provide this reference.


### POST upscan/v2/initiate

The S3 rest api is able to redirect on successful uploads. It does not support redirects on errors however. Version 2 of `upscan/initiate` returns the url [upscan-upload-proxy](https://github.com/hmrc/upscan-upload-proxy) that sits in front of S3 to gracefully handle S3 errors.  

Example `upscan/v2/initiate` request:

```json
{
    "callbackUrl": "https://myservice.com/callback",
    "successRedirect": "https://myservice.com/nextPage",
    "errorRedirect": "https://myservice.com/errorPage",
    "minimumFileSize" : 0,
    "maximumFileSize" : 1024
}
```

Another example `upscan/v2/initiate` requests that explicitly identifies the consuming service:

```json
{
  "callbackUrl": "https://myservice.com/callback",
  "successRedirect": "https://myservice.com/nextPage",
  "errorRedirect": "https://myservice.com/errorPage",
  "minimumFileSize" : 0,
  "maximumFileSize" : 1024,
  "consumingService": "some-consuming-service"
}
```

#### HTTP Headers:

| Header name|Description|Required|
|--------------|-----------|--------|
| User-Agent | Identifier of the service that calls upscan | yes |
| X-Session-ID | Identifier of the user's session | no  |
| X-Request-ID | Identifier of the user's request | no |

#### Body parameters:

| Parameter name|Description|Required|
|--------------|-----------|--------|
|callbackUrl   |Url that will be called to report the outcome of file checking and upload, including retrieval details if successful. Notification format is detailed further down in this file. Must be https.| yes|
|successRedirect|Url to redirect to after file has been successfully uploaded.|no|
|errorRedirect|Url to redirect to if error encountered during upload.|no|
|minimumFileSize|Minimum file size (in Bytes). Default is 0.|no|
|maximumFileSize|Maximum file size (in Bytes). Cannot be greater than 1024MB. Default is 100MB.|no|

Example response

```json
{
    "reference": "11370e18-6e24-453e-b45a-76d3e32ea33d",
    "uploadRequest": {
        "href": "https://xxxx/upscan-upload-proxy/bucketName",
        "fields": {
            "acl": "private",
            "key": "11370e18-6e24-453e-b45a-76d3e32ea33d",
            "policy": "xxxxxxxx==",
            "x-amz-algorithm": "AWS4-HMAC-SHA256",
            "x-amz-credential": "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
            "x-amz-date": "yyyyMMddThhmmssZ",
            "x-amz-meta-callback-url": "https://myservice.com/callback",
            "x-amz-signature": "xxxx",
            "success_action_redirect": "https://myservice.com/nextPage",
            "error_action_redirect": "https://myservice.com/errorPage"
        }
    }
}
```

*Note:* We recommend that the response fields are not hardcoded as these are subject to change 

The `href` in the response is for a proxy that sits in front of S3 to handle error responses.

S3 will return errors in `application/xml` in the following format:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Error>
  <Code>NoSuchKey</Code>
  <Message>The resource you requested does not exist</Message>
  <Resource>/mybucket/myfoto.jpg</Resource> 
  <RequestId>4442587FB7D0A2F9</RequestId>
</Error>
```

For v2, how such an error is returned depends upon whether the `error_action_redirect` form field was set.

If set, the [upscan-upload-proxy](https://github.com/hmrc/upscan-upload-proxy) will redirect to the specified URL.  
Details of the error will be supplied to this URL as query parameters, with the names `errorCode`, `errorMessage`, `errorResource` and `errorRequestId`.

The query parameter named `key` contains the globally unique identifier that was allocated by the initiate request 
to identify the upload (and was returned as `reference` in the initiate response).

```
HTTP Response Code: 303
Header ("Location" -> "https://myservice.com/errorPage?key=11370e18-6e24-453e-b45a-76d3e32ea33d&errorCode=NoSuchKey&errorMessage=The+resource+you+requested+does+not+exist&errorResource=/mybucket/myfoto.jpg&errorRequestId=4442587FB7D0A2F9")
```

If a redirect URL is not set, the proxy will respond with the failure status code.
The details of the error along with the key will be available from the JSON body that has the following structure:

```
{
 "key": "11370e18-6e24-453e-b45a-76d3e32ea33d",
 "errorCode": "NoSuchKey",
 "errorMessage": "The resource you requested does not exist",
 "errorResource": "/mybucket/myfoto.jpg",
 "errorRequestId": "4442587FB7D0A2F9"
}
```

All error fields are optional.

If an `error_action_redirect` is specified that does not represent a valid URL, the response will be a Bad Request:

```json
{"message":"Unable to build valid redirect URL for error action"}
```


[[Back to the top]](#top)

### POST upscan/initiate 

Example request:

```json
{
    "callbackUrl": "https://myservice.com/callback",
    "minimumFileSize" : 0,
    "maximumFileSize" : 1024
}
```

#### HTTP Headers:

| Header name|Description|Required|
|--------------|-----------|--------|
| User-Agent | Identifier of the service that calls upscan | yes |
| X-Session-ID | Identifier of the user's session | no  |
| X-Request-ID | Identifier of the user's request | no |

#### Body parameters:

| Parameter name|Description|Required|
|--------------|-----------|--------|
|callbackUrl   |Url that will be called to report the outcome of file checking and upload, including retrieval details if successful. Notification format is detailed further down in this file. Must be https.| yes|
|minimumFileSize|Minimum file size (in Bytes). Default is 0.|no|
|maximumFileSize|Maximum file size (in Bytes). Cannot be greater than 1024MB. Default is 100MB.|no|
|successRedirect|Url to redirect to after file has been successfully uploaded.|no|


Example Response:

```json
{
    "reference": "11370e18-6e24-453e-b45a-76d3e32ea33d",
    "uploadRequest": {
        "href": "https://bucketName.s3.eu-west-2.amazonaws.com",
        "fields": {
            "acl": "private",
            "key": "11370e18-6e24-453e-b45a-76d3e32ea33d",
            "policy": "xxxxxxxx==",
            "x-amz-algorithm": "AWS4-HMAC-SHA256",
            "x-amz-credential": "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
            "x-amz-date": "yyyyMMddThhmmssZ",
            "x-amz-meta-callback-url": "https://myservice.com/callback",
            "x-amz-signature": "xxxx"
        }
    }
}
```

[[Back to the top]](#top)

### The file upload <a name="service__upload"></a>

In order to upload the file, the following form is sent as the body of a POST request:

```
<form method="POST" href="...value of the href from the response above...">
    <input type="hidden" name="x-amz-algorithm" value="AWS4-HMAC-SHA256">
    ... all the fields returned in "fields" map in the response above ...
    <input type="file" name="file" required/> <- form field representing the file to upload
    <input type="submit" value="OK"/>
</form>
```

**Note: You should consider implementing validation on the frontend to prevent submitting the form if a file has not been selected (such as marking the file input `required`.)**

- You must make this request client side. Making this request server side defeats the objective of Upscan, which is to virus scan files before they are allowed on MDTP. 
- You must use multipart encoding (`multipart/form-data`) NOT `application/x-www-form-urlencoded`. If you use` application/x-www-form-urlencoded`, AWS will return a response from which this error is not clear.
- The 'file' field must be the last field in the submitted form.

[[Back to the top]](#top)

### File upload outcome <a name="service__uoutcome"></a>

If the POST is not successful, the service will return a HTTP error code (4xx, 5xx). The response body will contain XML encoded details of the problem. See the Error handling section for details.

If the POST is successful, the service returns a HTTP 204 response with an empty body.

[[Back to the top]](#top)

### File processing outcome <a name="service__poutcome"></a>

A callback notification provides details of the file processing outcome.
This contains a _reference_ that correlates with that contained in the upload initiation request (see [Requesting a URL to upload to](#service__request)).

#### Success <a name="service__poutcome__success"></a>

When a file is successfully uploaded it is processed by [upscan-verify](https://github.com/hmrc/upscan-verify) to check for viruses & that it is of an allowed file type.

If these checks pass, the file is made available for retrieval & the Upscan service will make a POST request to the URL specified as the 'callbackUrl' by the consuming service with the following body:

```json
{
    "reference": "11370e18-6e24-453e-b45a-76d3e32ea33d",
    "downloadUrl": "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
    "fileStatus": "READY",
    "uploadDetails": {
        "fileName": "test.pdf",
        "fileMimeType": "application/pdf",
        "uploadTimestamp": "2018-04-24T09:30:00Z",
        "checksum": "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
        "size": 987
    }
}
```

Note the block entitled 'uploadDetails', see the Confluence page 'Upscan & Non-Repudiation Service' in the Platform Services space usage of this information:

- `fileName` - File name as it was provided by the user
- `fileMimeType` - Detected MIME type of the file. Please note that this refers to actual contents  
of the file, not to the name (if user uploads PDF document named `data.png`, it will be detected as a `application/pdf`)
- `uploadTimestamp` - The timestamp of the file upload
- `checksum` - The SHA256 hash of the uploaded file
- `size` - The size of the uploaded file (in bytes)

The downloadUrl will expire after 1 day by default.  This can be configured on a per-consuming service basis.  
For example, to limit to 1 hour add the following configuration (substituting the appropriate `User-Agent` service identifier) to [upscan-notify.conf](https://github.com/hmrc/app-config-base/blob/master/upscan-notify.conf):

```
consuming-services {
  <user-agent-of-service> {
    aws {
      s3 {
        urlExpirationPeriod = 1.hour
      }
    }
  }
}
```

[[Back to the top]](#top)

#### Failure <a name="service__poutcome__failure"></a>

The list of failure reasons is as follows:

- `QUARANTINE` - the file has failed virus scanning
- `REJECTED` - the [file's detected mime-type is not allowed for the service](https://github.com/hmrc/upscan-app-config/blob/master/production/verify.yaml) or the [file's extension is not allowed for the detected mime-type](https://github.com/hmrc/upscan-verify/blob/master/conf/extensionsAllowList.conf)
- `UNKNOWN` - there is some other problem with the file

These reasons form one of the following JSON responses sent to the callback URL:


```json
{
    "reference" : "11370e18-6e24-453e-b45a-76d3e32ea33d",
    "fileStatus" : "FAILED",
    "failureDetails": {
        "failureReason": "QUARANTINE",
        "message": "e.g. This file has a virus"
    }
}
```

```json
{
    "reference" : "11370e18-6e24-453e-b45a-76d3e32ea33d",
    "fileStatus" : "FAILED",
    "failureDetails": {
        "failureReason": "REJECTED",
        "message": "MIME type $mime is not allowed for service $service-name"
    }
}
```

```json
{
    "reference" : "11370e18-6e24-453e-b45a-76d3e32ea33d",
    "fileStatus" : "FAILED",
    "failureDetails": {
        "failureReason": "UNKNOWN",
        "message": "Something unknown happened"
    }
}
```

You or the Upscan service team can use the unique file reference to find out more using the Upscan observability tools.

[[Back to the top]](#top)

## Error handling <a name="error"></a>

This document indicates the responses from Upscan components, including error/failure cases.

The actual file upload is to an AWS endpoint and the responses come straight from AWS. These responses are documented here: https://docs.aws.amazon.com/AmazonS3/latest/API/ErrorResponses.html

[[Back to the top]](#top)

## Design considerations <a name="design"></a>

### Uploading multiple files <a name="design__multiple"></a>
Upscan supports single file uploads. If a consuming service needs to upload multiple files during one user's journey, it must make multiple independent calls to upscan-initiate.

[[Back to the top]](#top)

### Security <a name="design__security"></a>
The callback URL provided by the service will be sent in plain text through the network and will be visible to anyone receiving or inspecting the upload request details.

Because of this, the URL must not:
- Point to a host that is accessible from outside MDTP.
- Contain sensitive data (e.g. user identifiers, session tokens, &c.)

[[Back to the top]](#top)

### File metadata <a name="design__metadata"></a>
Upscan intentionally doesn't allow consuming services to attach metadata or tags to the uploaded file. It is expected that the consuming service will use the globally unique file reference to correlate any file metadata to a successfully uploaded file when it is notified of the successful upload.

Upscan must not be used to route or transfer files between different services on MDTP - it is for files to be uploaded into the HMRC estate.

[[Back to the top]](#top)

## Architecture of the service <a name="architecture"></a>

Please see the Upscan Confluence page for architecture overview and details.

[[Back to the top]](#top)

## Running and maintenance of the service <a name="run"></a>

### Running locally <a name="run__local"></a>

In order to run the service against one of HMRC AWS accounts {labs, live} it's necessary to have an AWS user with the proper role. See [UpScan Accounts/roles](https://github.com/hmrc/aws-users/blob/master/AccountLinks.md) for proper details.

Prerequisites:

- AWS user with correct roles & MFA enabled
- AWS credential configuration set up according to this document [aws-credential-configuration](https://github.com/hmrc/aws-users), with the credentials below:

- Python 2.7 installed
- Botocore and awscli python modules installed locally:
  - Linux:

		```
		sudo pip install botocore
		sudo pip install awscli
		```

  - Mac (Mac has issues with pre-installed version of ```six``` as discussed [here](https://github.com/pypa/pip/issues/3165) ):

		```
		sudo pip install botocore --ignore-installed six
		sudo pip install awscli --ignore-installed six
		```

In order to run the app against the lab environment it's necessary to run the following commands:

```
export AWS_S3_BUCKET_INBOUND=name of inbound s3 bucket you would like to use
export AWS_DEFAULT_PROFILE=name of proper profile in ~/.aws/credentials file
./aws-profile sbt
```
These commands will give you an access to SBT shell where you can run the service using 'run' or 'start' commands.

[[Back to the top]](#top)

## Appendix <a name="appendix"></a>
### Quick reference figures <a name="appendix__figures"></a>

| Metric                                                  | Value             | Comments |
| ------------------------------------------------------- | ------------------|----------|
| Expiration of S3 upload pre-signed URL                  | 7 days            | A relatively long period, since we can't control exactly when users will initiate the upload process |
| Expiration of S3 download pre-signed URL (scanned docs) | 6 hours (default) | Configurable per-service up to 6 hours. Upscan is not intended as a storage solution for services, for storage needs beyond 6 hours, integrate with [object-store](https://github.com/hmrc/object-store)   |
| Callback request retry time                             | 60 seconds        |          |
| Maximum callback notification retries                   | 30                |          |

[[Back to the top]](#top)

### Related projects, useful links: <a name="appendix__links"></a>

* [upscan-verify](https://github.com/hmrc/upscan-verify) - service responsible for verifying the health of uploaded files
* [upscan-notify](https://github.com/hmrc/upscan-notify) - service responsible for notifying consuming services about the status of uploaded files
* [upscan-upload-proxy](https://github.com/hmrc/upscan-upload-proxy) - service that sits in front of S3 to gracefully handle S3 errors (v2 flow only)  
* [upscan-infrastructue](https://github.com/hmrc/upscan-infrastructure) - AWS infrastructure provisioning scripts

[[Back to the top]](#top)

#### Testing <a name="appendix__links__testing"></a>
* [upscan-listener](https://github.com/hmrc/upscan-listener) - service used in testing to receive callbacks from `upscan-notify`
* [upscan-stub](https://github.com/hmrc/upscan-stub) - service used locally (via `ServiceManager`) to stub `upscan-initiate`, `upscan-verify`, `upscan-notify` and uploads to AWS S3.
* [upscan-acceptance-tests](https://github.com/hmrc/upscan-acceptance-tests) - end-to-end acceptance tests of the upscan ecosystem
* [upscan-performance-tests](https://github.com/hmrc/upscan-performance-tests) - performance tests of the upscan ecosystem

[[Back to the top]](#top)

#### Slack <a name="appendix__links__slack"></a>
* [#team-platops](https://hmrcdigital.slack.com/messages/T04RY81HB/)
* [#event-upscan](https://hmrcdigital.slack.com/messages/C8XPL559N)

[[Back to the top]](#top)

### License <a name="appendix__license"></a>

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")

[[Back to the top]](#top)
