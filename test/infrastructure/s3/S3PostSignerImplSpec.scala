package infrastructure.s3

import com.amazonaws.auth.SdkClock.MockClock
import com.amazonaws.auth.{AWSCredentials, AWSCredentialsProvider, AWSSessionCredentials, BasicAWSCredentials, BasicSessionCredentials}
import infrastructure.s3.awsclient.{AwsCredentials, JavaAWSClientBasedS3PostSigner, S3PostSignerImpl}
import java.time.Instant
import java.util
import java.util.Date
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.prop.PropertyChecks
import org.scalatest.{Matchers, WordSpec}
import scala.util.Random

class S3PostSignerImplSpec extends WordSpec with Matchers with PropertyChecks {

  "java and scala signers" should {
    "work the same" in {

      implicit val sensibleString = Arbitrary(Gen.identifier.map(_.replaceAll("\u0000", "")))

      forAll() { (userSpecifiedExpirateDate: Date, bucketName: String, s3Key: String, acl: String) =>
        val sessionToken = if (Random.nextBoolean()) Some("token") else None

        val credentialsProvider = new AWSCredentialsProvider {
          def refresh(): Unit = ()
          def getCredentials: AWSCredentials =
            sessionToken
              .map { token =>
                new BasicSessionCredentials("accessKey", "secretKey", token)
              }
              .getOrElse(
                new BasicAWSCredentials("accessKey", "secretKey")
              )
        }

        val sdkClock   = new MockClock(new Date())
        val javaSigner = new JavaAWSClientBasedS3PostSigner("eu-west-2", credentialsProvider, sdkClock)

        val scalaSigner =
          new S3PostSignerImpl(
            AwsCredentials("accessKey", "secretKey", sessionToken),
            "eu-west-2",
            () => Instant.ofEpochMilli(sdkClock.currentTimeMillis()))

        val usingJava = javaSigner.presignForm(
          userSpecifiedExpirateDate,
          bucketName,
          s3Key,
          acl,
          new util.HashMap[String, String]()
        )

        val usingScala =
          scalaSigner.presignForm(userSpecifiedExpirateDate, bucketName, s3Key, acl, new util.HashMap[String, String]())

        usingJava shouldBe usingScala
      }

    }
  }

}
