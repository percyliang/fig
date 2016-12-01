import de.johoop.testngplugin.TestNGPlugin._

name := "fig"

version := "1.0"

scalaVersion := "2.11.8"

resolvers in Global += Resolver.sbtPluginRepo("releases")

javacOptions in Global ++= Seq("-target", "1.8")

lazy val commonSettings = testNGSettings ++ Seq(
  testNGVersion := "6.9.10")

lazy val fig = project
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "gov.nist.math" % "jama" % "1.0.3",
      "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided")
  )

lazy val examples = project
  .dependsOn(fig)
  .settings(commonSettings)
  .settings(
    mainClass in Compile := Some("Sample"))

lazy val servlet = project
  .dependsOn(fig)
  .enablePlugins(JettyPlugin)
  .settings(commonSettings)
