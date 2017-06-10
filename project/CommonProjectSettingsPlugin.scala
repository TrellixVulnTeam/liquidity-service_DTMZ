import sbt.Keys._
import sbt._
import scoverage.ScoverageKeys._

object CommonProjectSettingsPlugin extends AutoPlugin {

  private lazy val scalaSettings = Seq(
    scalaVersion := "2.12.2",
    crossScalaVersions := Seq("2.11.11", "2.12.2"),
    // See https://tpolecat.github.io/2017/04/25/scalac-flags.html for explanations. 2.11 doesn't support all of these,
    // so we simply don't set any of them when building for 2.11. The 2.12 build will pick up any issues anyway.
    scalacOptions in Compile ++= (CrossVersion.binaryScalaVersion(scalaVersion.value) match {
      case "2.12" =>
        Seq(
          "-deprecation",
          "-encoding",
          "utf-8",
          "-explaintypes",
          "-feature",
          "-unchecked",
          // TODO: Reorganise the Protobuf containing modules such that _just_ the generated code can be excluded from
          // all these checks, then enable fatal-warnings.
          // "-Xfatal-warnings",
          "-Xlint:adapted-args",
          "-Xlint:by-name-right-associative",
          "-Xlint:constant",
          "-Xlint:delayedinit-select",
          "-Xlint:doc-detached",
          "-Xlint:inaccessible",
          "-Xlint:infer-any",
          "-Xlint:missing-interpolator",
          "-Xlint:nullary-override",
          "-Xlint:nullary-unit",
          "-Xlint:option-implicit",
          "-Xlint:package-object-classes",
          "-Xlint:poly-implicit-overload",
          "-Xlint:private-shadow",
          "-Xlint:stars-align",
          "-Xlint:type-parameter-shadow",
          "-Xlint:unsound-match",
          "-Yno-adapted-args",
          "-Ywarn-dead-code",
          "-Ywarn-extra-implicit",
          "-Ywarn-inaccessible",
          "-Ywarn-infer-any",
          "-Ywarn-nullary-override",
          "-Ywarn-nullary-unit",
          "-Ywarn-numeric-widen",
          "-Ywarn-unused:implicits",
          "-Ywarn-unused:imports",
          "-Ywarn-unused:locals",
          "-Ywarn-unused:params",
          "-Ywarn-unused:patvars",
          "-Ywarn-unused:privates",
          "-Ywarn-value-discard"
        )
      case "2.11" => Seq("-encoding", "utf-8")
    })
  )

  private lazy val resolverSettings = Seq(
    resolvers += Resolver.bintrayRepo("dhpcs", "maven"),
    conflictManager := ConflictManager.strict
  )

  private lazy val coverageSettings = Seq(
    coverageExcludedFiles := ".*/target/.*"
  )

  private lazy val publishSettings = Seq(
    organization := "com.dhpcs"
  )

  override def trigger: PluginTrigger = allRequirements

  override def projectSettings: Seq[Setting[_]] =
    scalaSettings ++
      resolverSettings ++
      coverageSettings ++
      addCommandAlias("validate", ";scalafmtTest; test; multi-jvm:test") ++
      publishSettings

}
