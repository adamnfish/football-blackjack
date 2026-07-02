import sbt.*

object Dependencies {
  val circeVersion = "0.14.16"
  val circe = Seq(
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion
  )
  
  lazy val munit = "org.scalameta" %% "munit" % "1.3.3"
}
