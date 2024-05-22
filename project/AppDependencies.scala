import sbt.*

object AppDependencies {

  private val bootstrapVersion = "8.5.0"
  private val hmrcMongoVersion = "1.9.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-30"    % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-metrix-play-30"    % hmrcMongoVersion,
    "uk.gov.hmrc"             %% "crypto-json-play-30"          % "7.6.0",
    "uk.gov.hmrc.objectstore" %% "object-store-client-play-30"  % "1.3.0",
    "uk.gov.hmrc"             %% "internal-auth-client-play-30" % "2.0.0",
    "uk.gov.hmrc"             %% s"domain-play-30"              % "9.0.0",
    "com.github.pathikrit"    %% "better-files"                 % "3.9.2",
    "org.apache.pdfbox"       %  "pdfbox"                       % "3.0.2"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"  % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-30" % hmrcMongoVersion,
    "org.scalatest"           %% "scalatest"               % "3.2.18",
    "org.scalacheck"          %% "scalacheck"              % "1.18.0"
  ).map(_ % Test)
}
