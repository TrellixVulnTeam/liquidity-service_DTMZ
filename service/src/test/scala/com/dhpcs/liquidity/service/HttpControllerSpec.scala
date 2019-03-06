package com.dhpcs.liquidity.service

import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.security.KeyPairGenerator
import java.security.interfaces.{RSAPrivateKey, RSAPublicKey}
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

import akka.actor.typed.ActorRefResolver
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshalling.PredefinedToEntityMarshallers
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.http.scaladsl.unmarshalling.PredefinedFromEntityUnmarshallers
import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.syntax.validated._
import com.dhpcs.liquidity.actor.protocol.ProtoBindings._
import com.dhpcs.liquidity.model.ProtoBindings._
import com.dhpcs.liquidity.model._
import com.dhpcs.liquidity.persistence.zone._
import com.dhpcs.liquidity.proto
import com.dhpcs.liquidity.proto.binding.ProtoBinding
import com.dhpcs.liquidity.service.HttpController.EventEnvelope
import com.dhpcs.liquidity.service.HttpControllerSpec.{publicKey, _}
import com.dhpcs.liquidity.ws.protocol.ProtoBindings._
import com.dhpcs.liquidity.ws.protocol._
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.util.Base64
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader, JWSObject, Payload}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
import org.scalatest.FreeSpec
import play.api.libs.json.{JsObject, JsValue, Json}

import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.concurrent.duration._

class HttpControllerSpec extends FreeSpec with ScalatestRouteTest {

  "HttpController" - {
    "rejects access" - {
      "when no bearer token is presented" in {
        val getRequest = RequestBuilding
          .Get("/akka-management")
        getRequest ~> httpController.route(enableClientRelay = true) ~> check {
          assert(status === StatusCodes.Unauthorized)
          assert(
            header[`WWW-Authenticate`].contains(
              `WWW-Authenticate`(
                HttpChallenges
                  .oAuth2(realm = null)
                  .copy(
                    params = Map(
                      "error" ->
                        "Bearer token authorization must be presented."
                    )
                  )
              )
            )
          )
          import PredefinedFromEntityUnmarshallers.stringUnmarshaller
          assert(entityAs[String] === StatusCodes.Unauthorized.defaultMessage)
        }
      }
      "when the token is not a signed JWT" in {
        val getRequest =
          RequestBuilding
            .Get("/akka-management")
            .withHeaders(Authorization(OAuth2BearerToken("")))
        getRequest ~> httpController.route(enableClientRelay = true) ~> check {
          assert(status === StatusCodes.Unauthorized)
          assert(
            header[`WWW-Authenticate`].contains(
              `WWW-Authenticate`(
                HttpChallenges
                  .oAuth2(realm = null)
                  .copy(
                    params = Map("error" -> "Token must be a signed JWT.")
                  )
              )
            )
          )
          import PredefinedFromEntityUnmarshallers.stringUnmarshaller
          assert(entityAs[String] === StatusCodes.Unauthorized.defaultMessage)
        }
      }
      "when the token payload is not JSON" in {
        val getRequest =
          RequestBuilding
            .Get("/akka-management")
            .withHeaders(
              Authorization(
                OAuth2BearerToken(
                  {
                    val signedJwt = new JWSObject(
                      new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .build(),
                      new Payload("")
                    )
                    signedJwt.sign(new RSASSASigner(rsaPrivateKey))
                    signedJwt.serialize()
                  }
                )
              )
            )
        getRequest ~> httpController.route(enableClientRelay = true) ~> check {
          assert(status === StatusCodes.Unauthorized)
          assert(
            header[`WWW-Authenticate`].contains(
              `WWW-Authenticate`(
                HttpChallenges
                  .oAuth2(realm = null)
                  .copy(
                    params = Map("error" -> "Token payload must be JSON.")
                  )
              )
            )
          )
          import PredefinedFromEntityUnmarshallers.stringUnmarshaller
          assert(entityAs[String] === StatusCodes.Unauthorized.defaultMessage)
        }
      }
      "when the token claims do not contain a subject" in {
        val getRequest =
          RequestBuilding
            .Get("/akka-management")
            .withHeaders(
              Authorization(
                OAuth2BearerToken(
                  {
                    val signedJwt = new SignedJWT(
                      new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .build(),
                      new JWTClaimsSet.Builder()
                        .issueTime(Date.from(Instant.now()))
                        .notBeforeTime(Date.from(Instant.now()))
                        .expirationTime(
                          Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)))
                        .build()
                    )
                    signedJwt.sign(new RSASSASigner(rsaPrivateKey))
                    signedJwt.serialize()
                  }
                )
              )
            )
        getRequest ~> httpController.route(enableClientRelay = true) ~> check {
          assert(status === StatusCodes.Unauthorized)
          assert(
            header[`WWW-Authenticate`].contains(
              `WWW-Authenticate`(
                HttpChallenges
                  .oAuth2(realm = null)
                  .copy(
                    params =
                      Map("error" -> "Token claims must contain a subject.")
                  )
              )
            )
          )
          import PredefinedFromEntityUnmarshallers.stringUnmarshaller
          assert(entityAs[String] === StatusCodes.Unauthorized.defaultMessage)
        }
      }
      "when the token subject is not an RSA public key" in {
        val getRequest =
          RequestBuilding
            .Get("/akka-management")
            .withHeaders(
              Authorization(
                OAuth2BearerToken(
                  {
                    val signedJwt = new SignedJWT(
                      new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .build(),
                      new JWTClaimsSet.Builder()
                        .subject("")
                        .issueTime(Date.from(Instant.now()))
                        .notBeforeTime(Date.from(Instant.now()))
                        .expirationTime(
                          Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)))
                        .build()
                    )
                    signedJwt.sign(new RSASSASigner(rsaPrivateKey))
                    signedJwt.serialize()
                  }
                )
              )
            )
        getRequest ~> httpController.route(enableClientRelay = true) ~> check {
          assert(status === StatusCodes.Unauthorized)
          assert(
            header[`WWW-Authenticate`].contains(
              `WWW-Authenticate`(
                HttpChallenges
                  .oAuth2(realm = null)
                  .copy(
                    params = Map(
                      "error" ->
                        "Token subject must be an RSA public key."
                    )
                  )
              )
            )
          )
          import PredefinedFromEntityUnmarshallers.stringUnmarshaller
          assert(entityAs[String] === StatusCodes.Unauthorized.defaultMessage)
        }
      }
      "when the token is not signed by the subject's private key" in {
        val otherRsaPrivateKey = {
          val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
          keyPairGenerator.initialize(2048)
          keyPairGenerator.generateKeyPair.getPrivate
        }
        val getRequest =
          RequestBuilding
            .Get("/akka-management")
            .withHeaders(
              Authorization(
                OAuth2BearerToken(
                  {
                    val signedJwt = new SignedJWT(
                      new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .build(),
                      new JWTClaimsSet.Builder()
                        .subject(
                          Base64.encode(rsaPublicKey.getEncoded).toString)
                        .issueTime(Date.from(Instant.now()))
                        .notBeforeTime(Date.from(Instant.now()))
                        .expirationTime(
                          Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)))
                        .build()
                    )
                    signedJwt.sign(new RSASSASigner(otherRsaPrivateKey))
                    signedJwt.serialize()
                  }
                )
              )
            )
        getRequest ~> httpController.route(enableClientRelay = true) ~> check {
          assert(status === StatusCodes.Unauthorized)
          assert(
            header[`WWW-Authenticate`].contains(
              `WWW-Authenticate`(
                HttpChallenges
                  .oAuth2(realm = null)
                  .copy(
                    params = Map(
                      "error" ->
                        ("Token must be signed by subject's private key and " +
                          "used between nbf and iat claims.")
                    )
                  )
              )
            )
          )
          import PredefinedFromEntityUnmarshallers.stringUnmarshaller
          assert(entityAs[String] === StatusCodes.Unauthorized.defaultMessage)
        }
      }
      "when the not-before claim has not passed" in {
        val getRequest =
          RequestBuilding
            .Get("/akka-management")
            .withHeaders(
              Authorization(
                OAuth2BearerToken(
                  {
                    val signedJwt = new SignedJWT(
                      new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .build(),
                      new JWTClaimsSet.Builder()
                        .subject(
                          Base64.encode(rsaPublicKey.getEncoded).toString)
                        .issueTime(Date.from(Instant.now()))
                        .notBeforeTime(
                          Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)))
                        .expirationTime(
                          Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)))
                        .build()
                    )
                    signedJwt.sign(new RSASSASigner(rsaPrivateKey))
                    signedJwt.serialize()
                  }
                )
              )
            )
        getRequest ~> httpController.route(enableClientRelay = true) ~> check {
          assert(status === StatusCodes.Unauthorized)
          assert(
            header[`WWW-Authenticate`].contains(
              `WWW-Authenticate`(
                HttpChallenges
                  .oAuth2(realm = null)
                  .copy(
                    params = Map(
                      "error" ->
                        ("Token must be signed by subject's private key and " +
                          "used between nbf and iat claims.")
                    )
                  )
              )
            )
          )
          import PredefinedFromEntityUnmarshallers.stringUnmarshaller
          assert(entityAs[String] === StatusCodes.Unauthorized.defaultMessage)
        }
      }
      "when the expires at claim has passed" in {
        val getRequest =
          RequestBuilding
            .Get("/akka-management")
            .withHeaders(
              Authorization(
                OAuth2BearerToken(
                  {
                    val signedJwt = new SignedJWT(
                      new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .build(),
                      new JWTClaimsSet.Builder()
                        .subject(
                          Base64.encode(rsaPublicKey.getEncoded).toString)
                        .issueTime(Date.from(Instant.now()))
                        .notBeforeTime(Date.from(Instant.now()))
                        .expirationTime(
                          Date.from(Instant.now().minus(5, ChronoUnit.MINUTES)))
                        .build()
                    )
                    signedJwt.sign(new RSASSASigner(rsaPrivateKey))
                    signedJwt.serialize()
                  }
                )
              )
            )
        getRequest ~> httpController.route(enableClientRelay = true) ~> check {
          assert(status === StatusCodes.Unauthorized)
          assert(
            header[`WWW-Authenticate`].contains(
              `WWW-Authenticate`(
                HttpChallenges
                  .oAuth2(realm = null)
                  .copy(
                    params = Map(
                      "error" ->
                        ("Token must be signed by subject's private key and " +
                          "used between nbf and iat claims.")
                    )
                  )
              )
            )
          )
          import PredefinedFromEntityUnmarshallers.stringUnmarshaller
          assert(entityAs[String] === StatusCodes.Unauthorized.defaultMessage)
        }
      }
      "when the subject is not an administrator" in {
        val (otherRsaPrivateKey: RSAPrivateKey,
             otherRsaPublicKey: RSAPublicKey) = {
          val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
          keyPairGenerator.initialize(2048)
          val keyPair = keyPairGenerator.generateKeyPair
          (keyPair.getPrivate, keyPair.getPublic)
        }
        val getRequest =
          RequestBuilding
            .Get("/akka-management")
            .withHeaders(
              Authorization(
                OAuth2BearerToken(
                  {
                    val signedJwt = new SignedJWT(
                      new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .build(),
                      new JWTClaimsSet.Builder()
                        .subject(
                          Base64.encode(otherRsaPublicKey.getEncoded).toString)
                        .issueTime(Date.from(Instant.now()))
                        .notBeforeTime(Date.from(Instant.now()))
                        .expirationTime(
                          Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)))
                        .build()
                    )
                    signedJwt.sign(new RSASSASigner(otherRsaPrivateKey))
                    signedJwt.serialize()
                  }
                )
              )
            )
        getRequest ~> httpController.route(enableClientRelay = true) ~> check {
          assert(status === StatusCodes.Forbidden)
          import PredefinedFromEntityUnmarshallers.stringUnmarshaller
          assert(entityAs[String] === StatusCodes.Forbidden.defaultMessage)
        }
      }
    }
    "proxies /akka-management to akkaManagement" in {
      val getRequest =
        RequestBuilding
          .Get("/akka-management")
          .withHeaders(Authorization(OAuth2BearerToken(selfSignedJwt)))
      getRequest ~> httpController.route(enableClientRelay = true) ~> check {
        assert(status === StatusCodes.OK)
        import PredefinedFromEntityUnmarshallers.stringUnmarshaller
        assert(entityAs[String] === "akka-management")
      }
    }
    "provides diagnostic information" - {
      "for events" in {
        val getRequest = RequestBuilding
          .Get(
            Uri.Empty.withPath(
              Uri.Path("/diagnostics/events") / s"zone-${zone.id.value}"
            )
          )
          .withHeaders(Authorization(OAuth2BearerToken(selfSignedJwt)))
        implicit val timeout: RouteTestTimeout = RouteTestTimeout(5.seconds)
        getRequest ~> httpController.route(enableClientRelay = true) ~> check {
          assert(status === StatusCodes.OK)
          assert(entityAs[JsValue] === Json.parse(s"""
               |[{
               |  "sequenceNr" : 0,
               |  "event" : {
               |    "remoteAddress" : "wAACAA==",
               |    "publicKey" : "${publicKey.value.base64()}",
               |    "timestamp" : "1514156286183",
               |    "event" : {
               |      "zoneCreatedEvent" : {
               |        "zone" : {
               |          "id" : "32824da3-094f-45f0-9b35-23b7827547c6",
               |          "equityAccountId" : "0",
               |          "members" : [ {
               |              "id" : "0",
               |              "ownerPublicKeys" : [ "${publicKey.value
                                                       .base64()}" ],
               |              "ownerPublicKeys" : [ "${publicKey.value
                                                       .base64()}" ],
               |              "name" : "Dave"
               |          } ],
               |          "accounts" : [ {
               |              "id" : "0",
               |              "ownerMemberIds" : [ "0" ]
               |          } ],
               |          "created" : "1514156286183",
               |          "expires" : "1516748286183",
               |          "name" : "Dave's Game"
               |        }
               |      }
               |    }
               |  }
               |},
               |{
               |  "sequenceNr" : 1,
               |  "event" : {
               |    "remoteAddress" : "wAACAA==",
               |    "publicKey" : "${publicKey.value.base64()}",
               |    "timestamp" : "1514156287183",
               |    "event" : {
               |      "memberCreatedEvent" : {
               |        "member" : {
               |          "id" : "1",
               |          "ownerPublicKeys" : [ "${publicKey.value.base64()}" ],
               |          "name" : "Jenny"
               |        }
               |      }
               |    }
               |  }
               |},
               |{
               |  "sequenceNr" : 2,
               |  "event" : {
               |    "remoteAddress" : "wAACAA==",
               |    "publicKey" : "${publicKey.value.base64()}",
               |    "timestamp" : "1514156288183",
               |    "event" : {
               |      "accountCreatedEvent" : {
               |        "account" : {
               |          "id" : "1",
               |          "name" : "Jenny's Account",
               |          "ownerMemberIds" : [ "1" ]
               |        }
               |      }
               |    }
               |  }
               |},
               |{
               |  "sequenceNr" : 3,
               |  "event" : {
               |    "remoteAddress" : "wAACAA==",
               |    "publicKey" : "${publicKey.value.base64()}",
               |    "timestamp" : "1514156289183",
               |    "event" : {
               |      "transactionAddedEvent" : {
               |        "transaction" : {
               |          "id" : "0",
               |          "from" : "0",
               |          "to" : "1",
               |          "value" : "5000000000000000000000",
               |          "creator" : "0",
               |          "created" : "1514156289183",
               |          "description" : "Jenny's Lottery Win"
               |        }
               |      }
               |    }
               |  }
               |}]
             """.stripMargin))
        }
      }
      "for zones" in {
        val getRequest = RequestBuilding
          .Get(
            Uri.Empty.withPath(
              Uri.Path("/diagnostics/zone") / zone.id.value
            )
          )
          .withHeaders(Authorization(OAuth2BearerToken(selfSignedJwt)))
        getRequest ~> httpController.route(enableClientRelay = true) ~> check {
          assert(status === StatusCodes.OK)
          assert(entityAs[JsValue] === Json.parse(s"""
               |{
               |  "zone" : {
               |    "id" : "32824da3-094f-45f0-9b35-23b7827547c6",
               |    "equityAccountId" : "0",
               |    "members" : [ {
               |      "id" : "0",
               |      "ownerPublicKeys": [ "${publicKey.value.base64()}" ],
               |      "name":"Dave"
               |    } ],
               |    "accounts" : [ {
               |      "id" :"0",
               |      "ownerMemberIds" : [ "0" ]
               |    } ],
               |    "created" : "1514156286183",
               |    "expires" : "1516748286183",
               |    "name" : "Dave's Game"
               |  }
               |}
             """.stripMargin))
        }
      }
    }
    "provides ready information" in {
      val getRequest = RequestBuilding.Get("/ready")
      getRequest ~> httpController.route(enableClientRelay = true) ~> check {
        assert(status === StatusCodes.OK)
        import PredefinedFromEntityUnmarshallers.stringUnmarshaller
        assert(entityAs[String] == "OK")
      }
    }
    "provides alive information" in {
      val getRequest = RequestBuilding.Get("/alive")
      getRequest ~> httpController.route(enableClientRelay = true) ~> check {
        assert(status === StatusCodes.OK)
        import PredefinedFromEntityUnmarshallers.stringUnmarshaller
        assert(entityAs[String] == "OK")
      }
    }
    "provides version information" in {
      val getRequest = RequestBuilding.Get("/version")
      getRequest ~> httpController.route(enableClientRelay = true) ~> check {
        assert(status === StatusCodes.OK)
        val buildInfo = entityAs[JsObject]
        assert((buildInfo \ "version").as[String] == BuildInfo.version)
        assert(
          (buildInfo \ "builtAtString").as[String] == BuildInfo.builtAtString)
        assert(
          (buildInfo \ "builtAtMillis")
            .as[String] == BuildInfo.builtAtMillis.toString)
      }
    }
    "accepts CreateZoneCommands" - {
      "with JSON encoding" in {
        val putRequest = RequestBuilding
          .Put("/zone")
          .withHeaders(
            `Remote-Address`(RemoteAddress(remoteAddress)),
            Authorization(OAuth2BearerToken(selfSignedJwt))
          )
          .withEntity(
            ContentTypes.`application/json`,
            s"""
               |{
               |  "equityOwnerPublicKey": "${publicKey.value.base64()}",
               |  "equityOwnerName": "Dave",
               |  "name": "Dave's Game"
               |}
          """.stripMargin
          )
        putRequest ~> httpController.route(enableClientRelay = true) ~> check {
          assert(status === StatusCodes.OK)
          assert(entityAs[JsValue] === Json.parse(s"""
               |{
               |  "createZoneResponse": {
               |    "success": {
               |      "zone": {
               |        "id" : "32824da3-094f-45f0-9b35-23b7827547c6",
               |        "equityAccountId" : "0",
               |        "members" : [ {
               |          "id" : "0",
               |          "ownerPublicKeys": [ "${publicKey.value.base64()}" ],
               |          "name":"Dave"
               |        } ],
               |        "accounts" : [ {
               |        "id" :"0",
               |          "ownerMemberIds" : [ "0" ]
               |        } ],
               |        "created" : "1514156286183",
               |        "expires" : "1516748286183",
               |        "name" : "Dave's Game"
               |      }
               |    }
               |  }
               |}
           """.stripMargin))
        }
      }
      "with Protobuf encoding" in {
        val putRequest = RequestBuilding
          .Put("/zone")
          .withHeaders(
            `Remote-Address`(RemoteAddress(remoteAddress)),
            Authorization(OAuth2BearerToken(selfSignedJwt)),
            Accept(
              MediaRange(
                MediaType.customBinary(mainType = "application",
                                       subType = "x-protobuf",
                                       comp = MediaType.NotCompressible)
              )
            )
          )
          .withEntity(
            ContentType(
              MediaType.customBinary(mainType = "application",
                                     subType = "x-protobuf",
                                     comp = MediaType.NotCompressible)
            ),
            ProtoBinding[CreateZoneCommand,
                         proto.ws.protocol.CreateZoneCommand,
                         Any]
              .asProto(
                CreateZoneCommand(
                  equityOwnerPublicKey = publicKey,
                  equityOwnerName = zone
                    .members(
                      zone.accounts(zone.equityAccountId).ownerMemberIds.head)
                    .name,
                  equityOwnerMetadata = None,
                  equityAccountName = None,
                  equityAccountMetadata = None,
                  name = zone.name,
                  metadata = None
                )
              )(())
              .toByteArray
          )
        putRequest ~> httpController.route(enableClientRelay = true) ~> check {
          assert(status === StatusCodes.OK)
          import PredefinedFromEntityUnmarshallers.byteArrayUnmarshaller
          assert(
            entityAs[Array[Byte]] ===
              ProtoBinding[ZoneResponse, proto.ws.protocol.ZoneResponse, Any]
                .asProto(CreateZoneResponse(zone.valid))(())
                .asMessage
                .toByteArray
          )
        }
      }
    }
    "accepts ZoneCommands" - {
      "with JSON encoding" in {
        val putRequest = RequestBuilding
          .Put(
            Uri.Empty.withPath(
              Uri.Path("/zone") / zone.id.value
            )
          )
          .withHeaders(
            `Remote-Address`(RemoteAddress(remoteAddress)),
            Authorization(OAuth2BearerToken(selfSignedJwt))
          )
          .withEntity(
            ContentTypes.`application/json`,
            s"""
               |{
               |  "changeZoneNameCommand": {
               |  }
               |}
          """.stripMargin
          )
        putRequest ~> httpController.route(enableClientRelay = true) ~> check {
          assert(status === StatusCodes.OK)
          assert(entityAs[JsValue] === Json.parse(s"""
               |{
               |  "changeZoneNameResponse": {
               |    "success": ""
               |  }
               |}
           """.stripMargin))
        }
      }
      "with Protobuf encoding" in {
        val putRequest = RequestBuilding
          .Put(
            Uri.Empty.withPath(
              Uri.Path("/zone") / zone.id.value
            )
          )
          .withHeaders(
            `Remote-Address`(RemoteAddress(remoteAddress)),
            Authorization(OAuth2BearerToken(selfSignedJwt)),
            Accept(
              MediaRange(
                MediaType.customBinary(mainType = "application",
                                       subType = "x-protobuf",
                                       comp = MediaType.NotCompressible)
              )
            )
          )
          .withEntity(
            ContentType(
              MediaType.customBinary(mainType = "application",
                                     subType = "x-protobuf",
                                     comp = MediaType.NotCompressible)
            ),
            ProtoBinding[ZoneCommand, proto.ws.protocol.ZoneCommand, Any]
              .asProto(
                ChangeZoneNameCommand(name = None)
              )(())
              .asMessage
              .toByteArray
          )
        putRequest ~> httpController.route(enableClientRelay = true) ~> check {
          assert(status === StatusCodes.OK)
          import PredefinedFromEntityUnmarshallers.byteArrayUnmarshaller
          assert(
            entityAs[Array[Byte]] ===
              ProtoBinding[ZoneResponse, proto.ws.protocol.ZoneResponse, Any]
                .asProto(ChangeZoneNameResponse(().valid))(())
                .asMessage
                .toByteArray
          )
        }
      }
    }
    "notifies zone notification watchers" - {
      "with JSON encoding" in {
        val getRequest = RequestBuilding
          .Get(
            Uri.Empty.withPath(
              Uri.Path("/zone") / zone.id.value
            )
          )
          .withHeaders(
            `Remote-Address`(RemoteAddress(remoteAddress)),
            Authorization(OAuth2BearerToken(selfSignedJwt))
          )
        getRequest ~> httpController.route(enableClientRelay = true) ~> check {
          assert(status === StatusCodes.OK)
          assert(entityAs[JsValue] === Json.parse(s"""
               |[{
               |  "zoneStateNotification" : {
               |    "zone" : {
               |      "id" : "32824da3-094f-45f0-9b35-23b7827547c6",
               |      "equityAccountId" : "0",
               |      "members" : [ {
               |          "id" : "0",
               |          "ownerPublicKeys" : [ "${publicKey.value.base64()}" ],
               |          "ownerPublicKeys" : [ "${publicKey.value.base64()}" ],
               |          "name" : "Dave"
               |      } ],
               |      "accounts" : [ {
               |          "id" : "0",
               |          "ownerMemberIds" : [ "0" ]
               |      } ],
               |      "created" : "1514156286183",
               |      "expires" : "1516748286183",
               |      "name" : "Dave's Game"
               |    },
               |    "connectedClients" : {
               |      "HttpControllerSpec" : "${publicKey.value.base64()}"
               |    }
               |  }
               |},
               |{
               |  "memberCreatedNotification" : {
               |    "member" : {
               |      "id" : "1",
               |      "ownerPublicKeys" : [ "${publicKey.value.base64()}" ],
               |      "name" : "Jenny"
               |    }
               |  }
               |},
               |{
               |  "accountCreatedNotification" : {
               |    "account" : {
               |      "id" : "1",
               |      "name" : "Jenny's Account",
               |      "ownerMemberIds" : [ "1" ]
               |    }
               |  }
               |},
               |{
               |  "transactionAddedNotification" : {
               |    "transaction" : {
               |      "id" : "0",
               |      "from" : "0",
               |      "to" : "1",
               |      "value" : "5000000000000000000000",
               |      "creator" : "0",
               |      "created" : "1514156289183",
               |      "description" : "Jenny's Lottery Win"
               |    }
               |  }
               |}]
             """.stripMargin))
        }
      }
      "with Protobuf encoding" in {
        val getRequest = RequestBuilding
          .Get(
            Uri.Empty.withPath(
              Uri.Path("/zone") / zone.id.value
            )
          )
          .withHeaders(
            `Remote-Address`(RemoteAddress(remoteAddress)),
            Authorization(OAuth2BearerToken(selfSignedJwt)),
            Accept(
              MediaRange(
                MediaType.customBinary(mainType = "application",
                                       subType = "x-protobuf",
                                       comp = MediaType.NotCompressible,
                                       params = Map("delimited" -> "true"))
              )
            )
          )
        getRequest ~> httpController.route(enableClientRelay = true) ~> check {
          assert(status === StatusCodes.OK)
          import PredefinedFromEntityUnmarshallers.byteStringUnmarshaller
          assert(
            proto.ws.protocol.ZoneNotificationMessage.streamFromDelimitedInput(
              new ByteArrayInputStream(entityAs[ByteString].toArray)
            ) === zoneNotifications.map(
              zoneNotification =>
                ProtoBinding[ZoneNotification,
                             proto.ws.protocol.ZoneNotification,
                             Any].asProto(zoneNotification)(()).asMessage
            )
          )
        }
      }
    }
  }

  private[this] val httpController = new HttpController(
    ready = requestContext => {
      import PredefinedToEntityMarshallers.StringMarshaller
      requestContext.complete("OK")
    },
    alive = requestContext => {
      import PredefinedToEntityMarshallers.StringMarshaller
      requestContext.complete("OK")
    },
    isAdministrator = publicKey =>
      Future.successful(
        publicKey.value.toByteArray.sameElements(rsaPublicKey.getEncoded)),
    akkaManagement = requestContext => {
      import PredefinedToEntityMarshallers.StringMarshaller
      requestContext.complete("akka-management")
    },
    events = (persistenceId: String, _: Long, _: Long) =>
      if (persistenceId != zone.id.persistenceId)
        Source.empty
      else
        Source(
          Seq(
            ZoneCreatedEvent(
              zone
            ),
            MemberCreatedEvent(
              Member(
                id = MemberId("1"),
                ownerPublicKeys = Set(publicKey),
                name = Some("Jenny"),
                metadata = None
              )
            ),
            AccountCreatedEvent(
              Account(
                id = AccountId("1"),
                ownerMemberIds = Set(MemberId("1")),
                name = Some("Jenny's Account"),
                metadata = None
              )
            ),
            TransactionAddedEvent(
              Transaction(
                id = TransactionId("0"),
                from = AccountId("0"),
                to = AccountId("1"),
                value = BigDecimal("5000000000000000000000"),
                creator = MemberId("0"),
                created = zone.created.plusMillis(3000),
                description = Some("Jenny's Lottery Win"),
                metadata = None
              )
            )
          ).zipWithIndex.map {
            case (event, index) =>
              val zoneEventEnvelope = ZoneEventEnvelope(
                remoteAddress = Some(remoteAddress),
                publicKey = Some(publicKey),
                timestamp = zone.created.plusMillis(index * 1000L),
                zoneEvent = event
              )
              EventEnvelope(
                sequenceNr = index.toLong,
                event = ProtoBinding[ZoneEventEnvelope,
                                     proto.persistence.zone.ZoneEventEnvelope,
                                     ActorRefResolver]
                  .asProto(zoneEventEnvelope)(ActorRefResolver(system.toTyped))
              )
          }),
    zoneState = zoneId =>
      Future.successful(
        proto.persistence.zone
          .ZoneState(
            zone =
              if (zoneId != zone.id)
                None
              else
                Some(
                  ProtoBinding[Zone, proto.model.Zone, Any].asProto(
                    zone
                  )(())),
            balances = Map.empty,
            connectedClients = Seq.empty
          )
    ),
    execZoneCommand = (remoteAddress, publicKey, zoneId, zoneCommand) =>
      Future.successful(
        if (remoteAddress == HttpControllerSpec.remoteAddress &&
            publicKey == HttpControllerSpec.publicKey)
          if (zoneCommand == CreateZoneCommand(
                equityOwnerPublicKey = publicKey,
                equityOwnerName = zone
                  .members(
                    zone.accounts(zone.equityAccountId).ownerMemberIds.head)
                  .name,
                equityOwnerMetadata = None,
                equityAccountName = None,
                equityAccountMetadata = None,
                name = zone.name,
                metadata = None
              ))
            CreateZoneResponse(zone.valid)
          else if (zoneId == zone.id &&
                   zoneCommand == ChangeZoneNameCommand(name = None))
            ChangeZoneNameResponse(().valid)
          else fail()
        else fail()
    ),
    zoneNotificationSource = (_, _, zoneId) =>
      if (zoneId != zone.id) Source.empty
      else Source(zoneNotifications),
    pingInterval = 3.seconds
  )

}

object HttpControllerSpec {

  private val remoteAddress = InetAddress.getByName("192.0.2.0")
  private val (rsaPrivateKey: RSAPrivateKey, rsaPublicKey: RSAPublicKey) = {
    val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
    keyPairGenerator.initialize(2048)
    val keyPair = keyPairGenerator.generateKeyPair
    (keyPair.getPrivate, keyPair.getPublic)
  }
  private val selfSignedJwt = {
    val signedJwt = new SignedJWT(
      new JWSHeader.Builder(JWSAlgorithm.RS256)
        .build(),
      new JWTClaimsSet.Builder()
        .subject(Base64.encode(rsaPublicKey.getEncoded).toString)
        .issueTime(Date.from(Instant.now()))
        .notBeforeTime(Date.from(Instant.now()))
        .expirationTime(Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)))
        .build()
    )
    signedJwt.sign(new RSASSASigner(rsaPrivateKey))
    signedJwt.serialize()
  }
  private val publicKey = PublicKey(rsaPublicKey.getEncoded)
  private val zone = {
    val created = Instant.ofEpochMilli(1514156286183L)
    val equityAccountId = AccountId(0.toString)
    val equityAccountOwnerId = MemberId(0.toString)
    Zone(
      id = ZoneId("32824da3-094f-45f0-9b35-23b7827547c6"),
      equityAccountId,
      members = Map(
        equityAccountOwnerId -> Member(
          equityAccountOwnerId,
          ownerPublicKeys = Set(publicKey),
          name = Some("Dave"),
          metadata = None
        )
      ),
      accounts = Map(
        equityAccountId -> Account(
          equityAccountId,
          ownerMemberIds = Set(equityAccountOwnerId),
          name = None,
          metadata = None
        )
      ),
      transactions = Map.empty,
      created = created,
      expires = created.plus(java.time.Duration.ofDays(30)),
      name = Some("Dave's Game"),
      metadata = None
    )
  }

  private val zoneNotifications = Seq(
    ZoneStateNotification(
      Some(zone),
      connectedClients = Map("HttpControllerSpec" -> publicKey)
    ),
    MemberCreatedNotification(
      Member(
        id = MemberId("1"),
        ownerPublicKeys = Set(publicKey),
        name = Some("Jenny"),
        metadata = None
      )
    ),
    AccountCreatedNotification(
      Account(
        id = AccountId("1"),
        ownerMemberIds = Set(MemberId("1")),
        name = Some("Jenny's Account"),
        metadata = None
      )
    ),
    TransactionAddedNotification(
      Transaction(
        id = TransactionId("0"),
        from = AccountId("0"),
        to = AccountId("1"),
        value = BigDecimal("5000000000000000000000"),
        creator = MemberId("0"),
        created = zone.created.plusMillis(3000),
        description = Some("Jenny's Lottery Win"),
        metadata = None
      )
    )
  )

}
