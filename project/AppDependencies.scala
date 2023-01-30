import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  private val bootstrapVersion = "7.10.0"
  private val hmrcMongoVersion = "0.73.0"

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-28"    % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-28"           % hmrcMongoVersion,
    "com.github.pathikrit"    %% "better-files"                 % "3.9.1",
    "uk.gov.hmrc.objectstore" %% "object-store-client-play-28"  % "1.0.0",
    "uk.gov.hmrc"             %% "internal-auth-client-play-28" % "1.2.0",
    "org.typelevel"           %% "cats-core"                    % "2.8.0",
    "uk.gov.hmrc"             %% "crypto-json-play-28"          % "7.3.0"
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"     % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-28"    % hmrcMongoVersion,
    "org.mockito"             %% "mockito-scala"              % "1.16.42",
    "org.scalacheck"          %% "scalacheck"                 % "1.14.1",
    "com.github.tomakehurst"  %  "wiremock-standalone"        % "2.27.2"
  ).map(_ % "test, it")
}
