import sbt.*

object AppDependencies {

  private val bootstrapVersion = "8.4.0"
  private val hmrcMongoVersion = "1.7.0"

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-30"    % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-metrix-play-30"    % hmrcMongoVersion,
    "uk.gov.hmrc"             %% "crypto-json-play-30"          % "7.6.0",
    "uk.gov.hmrc.objectstore" %% "object-store-client-play-30"  % "2.0.0",
    "uk.gov.hmrc"             %% "internal-auth-client-play-30" % "1.9.0",
    "uk.gov.hmrc"             %% s"domain-play-30"              % "9.0.0",
    "com.github.pathikrit"    %% "better-files"                 % "3.9.1",
    "org.typelevel"           %% "cats-core"                    % "2.8.0",
    "org.typelevel"           %% "cats-effect"                  % "3.4.0",
    "co.fs2"                  %% "fs2-core"                     % "3.3.0",
    "org.apache.pdfbox"       %  "pdfbox"                       % "2.0.27"
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"     % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-30"    % hmrcMongoVersion,
    "org.scalatest"           %% "scalatest"               % "3.2.15",
    "org.scalacheck"          %% "scalacheck"              % "1.17.0",
  ).map(_ % Test)
}
