import play.sbt.routes.RoutesKeys
import uk.gov.hmrc.DefaultBuildSettings.targetJvm
import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings

lazy val microservice = Project("claim-child-benefit", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin, BuildInfoPlugin)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(
    majorVersion        := 0,
    scalaVersion        := "2.13.10",
    targetJvm           := "jvm-11",
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    // https://www.scala-lang.org/2021/01/12/configuring-and-suppressing-warnings.html
    // suppress warnings in generated routes files
    scalacOptions ++= Seq(
      "-Wconf:src=routes/.*:s",
      "-Xfatal-warnings",
      "-feature",
      "-deprecation",
      "-Xlint"
    ),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    buildInfoKeys := Seq[BuildInfoKey](name, version, PlayKeys.playDefaultPort),
    buildInfoPackage := "buildinfo",
    RoutesKeys.routesImport ++= Seq(
      "models._",
      "models.dmsa._",
      "java.time.LocalDate"
    )
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
  unmanagedResourceDirectories += baseDirectory.value / "test-utils" / "resources",
  unmanagedResourceDirectories += baseDirectory.value / "it" / "resources"
)