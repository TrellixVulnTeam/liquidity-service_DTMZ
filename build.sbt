import org.scalafmt.sbt.ScalafmtPlugin
import scala.sys.process._

lazy val `ws-protocol` = project
  .in(file("ws-protocol"))
  .settings(
    name := "liquidity-ws-protocol"
  )
  .settings(
    libraryDependencies := Seq.empty,
    coverageEnabled := false,
    crossPaths := false,
    Compile / unmanagedResourceDirectories ++=
      (Compile / PB.protoSources).value.map(_.asFile),
    homepage := Some(url("https://github.com/dhpcs/liquidity/")),
    startYear := Some(2015),
    description := "Virtual currencies for Monopoly and other board and " +
      "tabletop games.",
    licenses += "Apache-2.0" -> url(
      "https://www.apache.org/licenses/LICENSE-2.0.txt"),
    organization := "com.dhpcs",
    organizationHomepage := Some(url("https://www.dhpcs.com/")),
    organizationName := "dhpcs",
    developers := List(
      Developer(
        id = "dhpiggott",
        name = "David Piggott",
        email = "david@piggott.me.uk",
        url = url("https://www.dhpiggott.net/")
      )
    ),
    scmInfo := Some(
      ScmInfo(
        browseUrl = url("https://github.com/dhpcs/liquidity/"),
        connection = "scm:git:https://github.com/dhpcs/liquidity.git",
        devConnection = Some("scm:git:git@github.com:dhpcs/liquidity.git")
      )
    ),
    releaseEarlyEnableInstantReleases := false,
    releaseEarlyNoGpg := true,
    releaseEarlyWith := BintrayPublisher,
    releaseEarlyEnableSyncToMaven := false,
    bintrayOrganization := Some("dhpcs")
  )

lazy val model = project
  .in(file("model"))
  .settings(
    Compile / PB.protoSources ++= (`ws-protocol` / Compile / PB.protoSources).value,
    Compile / PB.targets := Seq(
      scalapb.gen(
        flatPackage = true,
        javaConversions = false,
        grpc = false,
        singleLineToProtoString = true
      ) -> (Compile / sourceManaged).value
    )
  )
  .settings(libraryDependencies ++= Seq(
    "com.thesamet.scalapb" %% "scalapb-runtime" %
      scalapb.compiler.Version.scalapbVersion % ProtocPlugin.ProtobufConfig,
    "org.typelevel" %% "cats-core" % "1.2.0",
    "com.squareup.okio" % "okio" % "1.15.0",
    "com.typesafe.akka" %% "akka-actor-typed" % "2.5.14"
  ))

lazy val server = project
  .in(file("server"))
  .settings(
    version := "git describe --always --dirty".!!.trim()
  )
  .dependsOn(model)
  .settings(libraryDependencies ++= Seq(
    "com.chuusai" %% "shapeless" % "2.3.3",
    "com.typesafe.akka" %% "akka-slf4j" % "2.5.14",
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "com.lightbend.akka.discovery" %% "akka-discovery-config" % "0.17.0",
    "com.lightbend.akka.discovery" %% "akka-discovery-aws-api-async" % "0.17.0",
    "com.lightbend.akka.management" %% "akka-management-cluster-http" % "0.17.0",
    "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % "0.17.0",
    "com.typesafe.akka" %% "akka-cluster-typed" % "2.5.14",
    "com.typesafe.akka" %% "akka-cluster-sharding-typed" % "2.5.14",
    "com.typesafe.akka" %% "akka-cluster-tools" % "2.5.14",
    "com.typesafe.akka" %% "akka-persistence-typed" % "2.5.14",
    "com.typesafe.akka" %% "akka-persistence-query" % "2.5.14",
    "com.github.dnvriend" %% "akka-persistence-jdbc" % "3.4.0",
    "org.tpolecat" %% "doobie-core" % "0.5.2",
    "org.tpolecat" %% "doobie-hikari" % "0.5.2",
    "mysql" % "mysql-connector-java" % "5.1.47",
    "com.typesafe.akka" %% "akka-http" % "10.1.4",
    "com.typesafe.akka" %% "akka-stream-typed" % "2.5.14",
    "com.thesamet.scalapb" %% "scalapb-json4s" % "0.7.1",
    "com.typesafe.play" %% "play-json" % "2.6.10",
    "de.heikoseeberger" %% "akka-http-play-json" % "1.21.0",
    "com.pauldijou" %% "jwt-play-json" % "0.17.0"
  ))
  .settings(libraryDependencies ++= Seq(
    "com.h2database" % "h2" % "1.4.197" % Test,
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % "2.5.14" % Test,
    "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.1.1" % Test,
    "org.scalatest" %% "scalatest" % "3.0.5" % Test,
    "com.typesafe.akka" %% "akka-http-testkit" % "10.1.4" % Test,
    "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.14" % Test
  ))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .settings(inConfig(IntegrationTest)(ScalafmtPlugin.scalafmtConfigSettings))
  .settings(libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.0.5" % IntegrationTest,
    "com.typesafe.akka" %% "akka-http-testkit" % "10.1.4" % IntegrationTest,
    "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.14" % IntegrationTest
  ))
  .enablePlugins(
    BuildInfoPlugin,
    JavaAppPackaging,
    DockerPlugin
  )
  .settings(
    buildInfoPackage := "com.dhpcs.liquidity.server",
    buildInfoUsePackageAsPath := true,
    buildInfoKeys := Seq(version),
    buildInfoOptions ++= Seq(BuildInfoOption.BuildTime, BuildInfoOption.ToMap),
    Docker / packageName := "liquidity",
    dockerBaseImage := "openjdk:10-jre-slim"
  )
