import sbt.*

object Dependencies {
  val circeVersion = "0.14.16"
  val circe = Seq(
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion
  )

  lazy val sttp = Seq(
    "com.softwaremill.sttp.client4" %% "core" % "4.0.25"
  )

  val awsJavaSdkVersion = "2.47.0"
  lazy val lambdaCore = "com.amazonaws" % "aws-lambda-java-core" % "1.4.0"
  lazy val lambdaEvents = "com.amazonaws" % "aws-lambda-java-events" % "3.16.1"
  lazy val awsCrtClient =
    "software.amazon.awssdk" % "aws-crt-client" % awsJavaSdkVersion

  lazy val cask = "com.lihaoyi" %% "cask" % "0.11.3"

  lazy val munit = "org.scalameta" %% "munit" % "1.3.3"
}
