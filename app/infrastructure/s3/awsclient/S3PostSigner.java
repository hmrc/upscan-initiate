package infrastructure.s3.awsclient;

import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Map;

public interface S3PostSigner {
    String buildEndpoint(String bucketName);

    Map<String, String> presignForm(Date userSpecifiedExpirationDate, String bucketName, String key, String acl, Map<String, String> additionalMetadata)
            throws UnsupportedEncodingException;
}
