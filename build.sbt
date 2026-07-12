import Dependencies.*

scalaVersion := "3.8.4"
version := "0.1.0-SNAPSHOT"
organization := "com.adamnfish"
organizationName := "adamnfish"

lazy val commonSettings = Seq(
  libraryDependencies += munit % Test
)

lazy val root = (project in file("."))
  .aggregate(common, footballdata, dataService, api, devServer)
  .settings(
    name := "football-blackjack"
  )

lazy val common = (project in file("backend/common"))
  .settings(commonSettings)
  .settings(
    name := "common",
    libraryDependencies ++= Seq(
      munitScalacheck % Test
    ) ++ circe
  )

lazy val footballdata = (project in file("backend/footballdata"))
  .dependsOn(common)
  .settings(commonSettings)
  .settings(
    name := "footballdata",
    libraryDependencies ++= Seq(
    ) ++ circe ++ sttp
  )

lazy val dataService = (project in file("backend/data-service"))
  .dependsOn(common, footballdata)
  .settings(commonSettings)
  .settings(
    name := "data-service",
    libraryDependencies ++= Seq(
      lambdaCore,
      lambdaEvents
    ),
    excludeDependencies ++= Seq(
      ExclusionRule("software.amazon.awssdk", "netty-nio-client"),
      ExclusionRule("software.amazon.awssdk", "apache-client")
    )
  )

lazy val api = (project in file("backend/api"))
  .dependsOn(common)
  .settings(commonSettings)
  .settings(
    name := "api",
    libraryDependencies ++= Seq(
      lambdaCore,
      lambdaEvents
    ),
    excludeDependencies ++= Seq(
      ExclusionRule("software.amazon.awssdk", "netty-nio-client"),
      ExclusionRule("software.amazon.awssdk", "apache-client")
    ),
    assembly / assemblyJarName := "api.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", _*) => MergeStrategy.discard
      case _                        => MergeStrategy.first
    }
  )

lazy val devServer = (project in file("backend/dev-server"))
  .dependsOn(common)
  .settings(commonSettings)
  .settings(
    name := "dev-server",
    libraryDependencies ++= Seq(
      cask
    ),
    assembly / assemblyJarName := "dev-server.jar",
    assembly / assemblyMergeStrategy := {
      // Undertow's XNIO discovers its provider via META-INF/services
      case PathList("META-INF", "services", _*) => MergeStrategy.concat
      case PathList("META-INF", _*)             => MergeStrategy.discard
      case _                                    => MergeStrategy.first
    }
  )
