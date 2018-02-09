package infrastructure.s3

import com.amazonaws.util.{BinaryUtils, StringUtils}
import java.nio.charset.Charset
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

trait PolicySigner {
  def signPolicy(
    credentials: AwsCredentials,
    formattedSigningDate: String,
    regionName: String,
    encodedPolicy: String): String
}

object PolicySigner extends PolicySigner {

  def signPolicy(
    credentials: AwsCredentials,
    formattedSigningDate: String,
    regionName: String,
    encodedPolicy: String): String = {
    val signingKey      = newSigningKey(credentials, formattedSigningDate, regionName, "s3")
    val policySignature = sign(encodedPolicy, signingKey)
    BinaryUtils.toHex(policySignature)
  }

  private def newSigningKey(credentials: AwsCredentials, dateStamp: String, regionName: String, serviceName: String) = {
    val kSecret  = ("AWS4" + credentials.secretKey).getBytes(Charset.forName("UTF-8"))
    val kDate    = sign(dateStamp, kSecret)
    val kRegion  = sign(regionName, kDate)
    val kService = sign(serviceName, kRegion)
    sign("aws4_request", kService)
  }

  private def sign(stringData: String, key: Array[Byte]): Array[Byte] = {
    val data      = stringData.getBytes(StringUtils.UTF8)
    val algorithm = "HmacSHA256"
    val mac       = Mac.getInstance(algorithm)
    mac.init(new SecretKeySpec(key, algorithm))
    mac.doFinal(data)
  }

}
