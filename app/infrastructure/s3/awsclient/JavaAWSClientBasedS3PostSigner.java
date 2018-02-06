package infrastructure.s3.awsclient;

import com.amazonaws.DefaultRequest;
import com.amazonaws.SdkClientException;
import com.amazonaws.auth.*;
import com.amazonaws.auth.SdkClock.Instance;
import com.amazonaws.auth.internal.AWS4SignerRequestParams;
import com.amazonaws.auth.internal.AWS4SignerUtils;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.util.BinaryUtils;
import com.amazonaws.util.StringUtils;
import infrastructure.s3.awsclient.S3PostSigner;
import sun.misc.BASE64Encoder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

public class JavaAWSClientBasedS3PostSigner implements S3PostSigner {

    private String regionName;

    private AWSCredentialsProvider credentialsProvider;

    public JavaAWSClientBasedS3PostSigner(String regionName, AWSCredentialsProvider awsCredentialsProvider) {
        this.regionName = regionName;
        this.credentialsProvider = awsCredentialsProvider;
    }

    @Override
    public String buildEndpoint(String bucketName) {
        return "https://" + bucketName + "." + RegionUtils.getRegion(regionName).getServiceEndpoint("s3");
    }

    @Override
    public Map<String, String> presignForm(Date userSpecifiedExpirationDate, String bucketName, String key, String acl,
                                           Map<String, String> additionalMetadata)
            throws UnsupportedEncodingException {
        AWSCredentials credentials = credentialsProvider.getCredentials();
        if (!this.isAnonymous(credentials)) {
            AWSCredentials sanitizedCredentials = this.sanitizeCredentials(credentials);
            String timeStamp = AWS4SignerUtils.formatTimestamp(Instance.get().currentTimeMillis());

            AWS4SignerRequestParams signerRequestParams = new AWS4SignerRequestParams(new DefaultRequest("s3"), null, this.regionName, "s3", "AWS4-HMAC-SHA256", null);
            byte[] signingKey = this.newSigningKey(sanitizedCredentials, signerRequestParams.getFormattedSigningDate(), signerRequestParams.getRegionName(), signerRequestParams.getServiceName());

            Map<String, String> fields = new HashMap<>();

            if (sanitizedCredentials instanceof AWSSessionCredentials) {
                fields.put("X-Amz-Security-Token", ((AWSSessionCredentials) sanitizedCredentials).getSessionToken());
            }
            String signingCredentials = credentials.getAWSAccessKeyId() + "/" + signerRequestParams.getScope();
            fields.put("X-Amz-Algorithm", "AWS4-HMAC-SHA256");
            fields.put("X-Amz-Credential", signingCredentials);
            fields.put("X-Amz-Date", timeStamp);

            for (Map.Entry<String, String> field : additionalMetadata.entrySet()) {
                fields.put("X-Amz-Meta-" + field.getKey(), field.getValue());
            }

            String policy = buildPolicy(userSpecifiedExpirationDate, bucketName, key, acl, additionalMetadata, sanitizedCredentials, timeStamp, signingCredentials);

            String encodedPolicy = new BASE64Encoder().encode(policy.getBytes("UTF-8")).replaceAll("\n", "").replaceAll("\r", "");
            fields.put("policy", encodedPolicy);
            byte[] signature = this.sign(encodedPolicy, signingKey, SigningAlgorithm.HmacSHA256);
            fields.put("X-Amz-Signature", BinaryUtils.toHex(signature));
            fields.put("acl", acl);
            fields.put("key", key);
            return fields;
        } else {
            return new HashMap<>();
        }
    }

    private String buildPolicy(Date userSpecifiedExpirationDate, String bucketName, String key, String acl, Map<String, String> additionalMetadata, AWSCredentials sanitizedCredentials, String timeStamp, String signingCredentials) {
        StringBuilder metadataJsonPart = new StringBuilder();
        for (Map.Entry<String, String> field : additionalMetadata.entrySet()) {
            metadataJsonPart.append("{\"X-Amz-Meta-" + field.getKey() + "\": \"" + field.getValue() + "\"},"); //TODO escaping
        }

        return "{ \"expiration\": \"" + ISO_INSTANT.format(userSpecifiedExpirationDate.toInstant()) + "\"," +
                "\"conditions\": [" +
                "{\"bucket\": \"" + bucketName + "\"}," +
                "{\"acl\": \"" + acl + "\"}," +
                ((sanitizedCredentials instanceof AWSSessionCredentials) ?
                        ("{\"x-amz-security-token\": \"" + ((AWSSessionCredentials) sanitizedCredentials).getSessionToken() + "\"},") : "") +
                "{\"x-amz-credential\": \"" + signingCredentials + "\"}," +
                "{\"x-amz-algorithm\": \"AWS4-HMAC-SHA256\"}," +
                "{\"key\": \"" + key + "\"}," +
                metadataJsonPart +
                "{\"x-amz-date\": \"" + timeStamp + "\" }]}";
    }

    protected AWSCredentials sanitizeCredentials(AWSCredentials credentials) {
        String accessKeyId = null;
        String secretKey = null;
        String token = null;
        synchronized (credentials) {
            accessKeyId = credentials.getAWSAccessKeyId();
            secretKey = credentials.getAWSSecretKey();
            if (credentials instanceof AWSSessionCredentials) {
                token = ((AWSSessionCredentials) credentials).getSessionToken();
            }
        }

        if (secretKey != null) {
            secretKey = secretKey.trim();
        }

        if (accessKeyId != null) {
            accessKeyId = accessKeyId.trim();
        }

        if (token != null) {
            token = token.trim();
        }

        return credentials instanceof AWSSessionCredentials ? new BasicSessionCredentials(accessKeyId, secretKey, token) : new BasicAWSCredentials(accessKeyId, secretKey);
    }

    private boolean isAnonymous(AWSCredentials credentials) {
        return credentials instanceof AnonymousAWSCredentials;
    }

    private byte[] newSigningKey(AWSCredentials credentials, String dateStamp, String regionName, String serviceName) {
        byte[] kSecret = ("AWS4" + credentials.getAWSSecretKey()).getBytes(Charset.forName("UTF-8"));
        byte[] kDate = this.sign(dateStamp, kSecret, SigningAlgorithm.HmacSHA256);
        byte[] kRegion = this.sign(regionName, kDate, SigningAlgorithm.HmacSHA256);
        byte[] kService = this.sign(serviceName, kRegion, SigningAlgorithm.HmacSHA256);
        return this.sign("aws4_request", kService, SigningAlgorithm.HmacSHA256);
    }

    private byte[] sign(String stringData, byte[] key, SigningAlgorithm algorithm) throws SdkClientException {
        byte[] data = stringData.getBytes(StringUtils.UTF8);
        try {
            Mac mac = algorithm.getMac();
            mac.init(new SecretKeySpec(key, algorithm.toString()));
            return mac.doFinal(data);
        } catch (Exception var5) {
            throw new SdkClientException("Unable to calculate a request signature: " + var5.getMessage(), var5);
        }
    }

}
