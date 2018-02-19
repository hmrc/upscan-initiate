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
	"callbackUrl": "http://myservice.com/callback?fileId=123"
}
```
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

# Additional notes

The service is not implemented yet. Among others, storage of metadata, constraint validation, virus scanning and
notifications using callbacks do not work yet.

# Running locally

In order to run the service against one of HMRC accounts (labs, live) it's needed to have an AWS accounts with proper
role. See (https://github.tools.tax.service.gov.uk/HMRC/aws-users/blob/master/AccountLinks.md) UpScan Accounts/roles
for proper details.

Prerequisites:
- AWS accounts with proper roles setup
- Proper AWS credential configuration set up according to this document (https://github.tools.tax.service.gov.uk/HMRC/aws-users#aws-credential-configuration)
- Working AWS MFA authentication
- Install botocore and awscli modules locally:
-- For Linux:
```
sudo pip install botocore
sudo pip install awscli
```
-- For Mac (Mac has issues with pre-installed version of ```six``` as discussed (https://github.com/pypa/pip/issues/3165#here):
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
