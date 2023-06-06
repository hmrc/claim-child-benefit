import sbt._

object AppDependencies {

  private val bootstrapVersion = "7.15.0"
  private val hmrcMongoVersion = "1.2.0"

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-28"    % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-28"           % hmrcMongoVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-metrix-play-28"    % hmrcMongoVersion,
    "uk.gov.hmrc"             %% "crypto-json-play-28"          % "7.3.0",
    "com.github.pathikrit"    %% "better-files"                 % "3.9.1",
    "uk.gov.hmrc.objectstore" %% "object-store-client-play-28"  % "1.1.0",
    "uk.gov.hmrc"             %% "internal-auth-client-play-28" % "1.4.0",
    "uk.gov.hmrc"             %% "crypto-json-play-28"          % "7.3.0",
    "org.typelevel"           %% "cats-core"                    % "2.8.0",
    "org.typelevel"           %% "cats-effect"                  % "3.4.0",
    "co.fs2"                  %% "fs2-core"                     % "3.3.0",
    "uk.gov.hmrc"             %% "domain"                       % "8.3.0-play-28",
    "org.apache.pdfbox"       %  "pdfbox"                       % "2.0.27"
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"     % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-28"    % hmrcMongoVersion,
    "org.mockito"             %% "mockito-scala"              % "1.17.12",
    "org.scalacheck"          %% "scalacheck"                 % "1.14.1",
    "com.github.tomakehurst"  %  "wiremock-standalone"        % "2.27.2",
    "org.scalatestplus"       %% "scalacheck-1-15"            % "3.2.10.0"
  ).map(_ % "test, it")
}
