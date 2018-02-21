# upscan-initiate

Microservice for initiating the upload of files created externally to HMRC estate, namely from members of the public.
This is not intended to be used for transfer of files from one HMRC service to another, for this you need to intergrate
directly with the file transfer service.

[![Build Status](https://travis-ci.org/hmrc/upscan-initiate.svg)](https://travis-ci.org/hmrc/upscan-initiate) [ ![Download](https://api.bintray.com/packages/hmrc/releases/upscan-initiate/images/download.svg) ](https://bintray.com/hmrc/releases/upscan-initiate/_latestVersion)

# Usage

The microservice provides one endpoint that allows to request upload of single file.
To initiate the upload it's needed to make POST request to the '/upscan/initiate' endpoint. The request
should contain details about expected upload, which include additional metadata, additional constraints
about content type and size, and callback URL which will be used to notify user. Here is an example of the request:
```
{
	"callbackUrl": "http://myservice.com/callback?fileId=123",
	"minimumFileSize" : 0,
	"maximumFileSize" : 1024,
	"expectedMimeType": "application/xml"
}
```
Meaning of parameters:

| Parameter name|Description|Required|
|--------------|-----------|--------|
|callbackUrl   |Url that will be called after file will be successfuly processed| yes|
|minimumFileSize|Minimum file size, if not specified any file size is allowed|no|
|maximumFileSize|Maximum file size, if not specified, global maximum file size will be applied (by default 100MB)|no|
|expectedMimeType|Expected MIME type of uploaded file|no|

The service replies with JSON containg reference of the upload and information about the POST form that has to be sent in order to upload the file:
```
{
    "reference": "11370e18-6e24-453e-b45a-76d3e32ea33d",
    "uploadRequest": {
        "href": "https://bucketName.s3.eu-west-2.amazonaws.com",
        "fields": {
            "X-Amz-Algorithm": "AWS4-HMAC-SHA256",
            "X-Amz-Expiration": "2018-02-09T12:35:45.297Z",
            "X-Amz-Signature": "xxxx",
            "key": "xxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
            "acl": "public-read",
            "X-Amz-Credential": "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
            "policy": "xxxxxxxx=="
        }
    }
}
```
In order to upload the file, initiating microservice or the client should send the following form:
```
<form method="POST" href="...value of the href from the response above...">
    <input type="hidden" name="X-Amz-Algorithm" value="AWS4-HMAC-SHA256">
    ... all the fields returned in "fields" map in the response above ...
    <input type="file" name="file"/> <- form field representing the file to upload
    <input type="submit" value="OK"/>
</form>
```

# Error handling

In case of problems with uploading the file (file too small, too large, configuration problems), AWS
will sent response that complies with this document: https://docs.aws.amazon.com/AmazonS3/latest/API/ErrorResponses.html

# Additional notes

The service is not implemented yet. Among others, storage of metadata, constraint validation, virus scanning and
notifications using callbacks do not work yet.

# Running locally

In order to run the service against one of HMRC accounts (labs, live) it's needed to have an AWS accounts with proper
role. See [UpScan Accounts/roles](https://github.tools.tax.service.gov.uk/HMRC/aws-users/blob/master/AccountLinks.md)
for proper details.

Prerequisites:
- AWS accounts with proper roles setup
- Proper AWS credential configuration set up according to this document [aws-credential-configuration](https://github.tools.tax.service.gov.uk/HMRC/aws-users), with the credentials below:
```
[upscan-service-prototypes-engineer]
source_profile = webops-users
aws_access_key_id = YOUR_ACCESS_KEY_HERE
aws_secret_access_key = YOUR_SECRET_KEY_HERE
output = json
region = eu-west-2
mfa_serial = arn:aws:iam::638924580364:mfa/your.username
role_arn = arn:aws:iam::415042754718:role/RoleServicePrototypesEngineer

[webops-users]
aws_access_key_id = YOUR_ACCESS_KEY_HERE
aws_secret_access_key = YOUR_SECRET_KEY_HERE
mfa_serial = arn:aws:iam::638924580364:mfa/your.username
region = eu-west-2
role_arn = arn:aws:iam::415042754718:role/RoleServicePrototypesEngineer
```
- Working AWS MFA authentication
- Have python 2.7 installed
- Install botocore and awscli python modules locally:
  - For Linux:
```
sudo pip install botocore
sudo pip install awscli
```
  - For Mac (Mac has issues with pre-installed version of ```six``` as discussed [here](https://github.com/pypa/pip/issues/3165):
```
sudo pip install botocore --ignore-installed six
sudo pip install awscli --ignore-installed six
```

In order to run the app against lab environment it's neeeded to run the following commands:
```
export AWS_DEFAULT_PROFILE=name_of_proper_profile_in_dot_aws_credentials_file
./aws-profile sbt
```
These commands will give you an access to SBT shell where you can run the service using 'run' or 'start' commands.

### Tests

Upscan service has end-to-end acceptance tests which can be found in https://github.com/hmrc/upscan-acceptance-tests repository
### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
