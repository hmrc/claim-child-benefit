import uk.gov.hmrc.DefaultBuildSettings.targetJvm
import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings

lazy val microservice = Project("claim-child-benefit", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .settings(
    majorVersion        := 0,
    scalaVersion        := "2.13.10",
    targetJvm           := "jvm-11",
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    // https://www.scala-lang.org/2021/01/12/configuring-and-suppressing-warnings.html
    // suppress warnings in generated routes files
    scalacOptions += "-Wconf:src=routes/.*:s",
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
  )
  .settings(inConfig(Test)(testSettings): _*)
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)
  .settings(inConfig(IntegrationTest)(itSettings): _*)
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(CodeCoverageSettings.settings: _*)
  .settings(PlayKeys.playDefaultPort := 11305)

lazy val testSettings: Seq[Def.Setting[_]] = Seq(
  unmanagedSourceDirectories += baseDirectory.value / "test-utils" / "src",
  unmanagedResourceDirectories += baseDirectory.value / "test-utils" / "resources"
)

lazy val itSettings: Seq[Def.Setting[_]] = Seq(
  unmanagedSourceDirectories += baseDirectory.value / "test-utils" / "src",
  unmanagedResourceDirectories += baseDirectory.value / "test-utils" / "resources"
)