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
The service replies with JSON containg two URLs: one that allows to upload the file using PUT method and the second
one that later allows to dowload the file (TBD: download link is likely to be removed when virus scan and callback
functionality will be removed). Here is a sample response from the service:
```
{
    "_links": {
        "upload": {
            "href": "https://upload.aws.com?someParams=1234
            "method": "PUT"
        },
        "download": {
                    "href": "https://upload.aws.com?someParams=1234
                    "method": "GET"
                }
    }
}
```
Calling service should pass upload url to the client and let him upload the file. 

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

In order to run the app against lab environment it's neeeded to run the following commands:
```
export AWS_DEFAULT_PROFILE=name_of_proper_profile_in_dot_aws_credentials_file
./aws-profile sbt
```
These commands will give you an access to SBT shell where you can run the service using 'run' or 'start' commands.

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
