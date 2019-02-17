package com.dhpcs.liquidity.service.actor

import java.net.InetSocketAddress
import java.nio.channels.ServerSocketChannel
import java.util.UUID

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import com.dhpcs.liquidity.actor.protocol.zonemonitor._
import com.dhpcs.liquidity.model.ZoneId
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, FreeSpec}

class ZoneMonitorActorSpec extends FreeSpec with BeforeAndAfterAll {

  private[this] val testKit = ActorTestKit(
    "zoneMonitorActorSpec",
    ConfigFactory.parseString(s"""
         |akka {
         |  loglevel = "WARNING"
         |  actor.provider = "cluster"
         |  remote.artery {
         |    enabled = on
         |    transport = tcp
         |    canonical.hostname = "localhost"
         |    canonical.port = $akkaRemotingPort
         |  }
         |  cluster {
         |    seed-nodes = ["akka://zoneMonitorActorSpec@localhost:$akkaRemotingPort"]
         |    jmx.enabled = off
         |  }
         |}
       """.stripMargin)
  )

  "ZoneMonitorActor" - {
    "provides a summary of the active zones" in {
      val zoneMonitor = testKit.spawn(ZoneMonitorActor.behavior, "zoneMonitor")
      val testProbe = testKit.createTestProbe[Set[ActiveZoneSummary]]()
      val activeZoneSummary = ActiveZoneSummary(
        zoneId = ZoneId(UUID.randomUUID().toString),
        members = 0,
        accounts = 0,
        transactions = 0,
        metadata = None,
        connectedClients = Map.empty
      )
      zoneMonitor ! UpsertActiveZoneSummary(testProbe.ref, activeZoneSummary)
      zoneMonitor ! GetActiveZoneSummaries(testProbe.ref)
      testProbe.expectMessage(Set(activeZoneSummary))
    }
  }

  private[this] lazy val akkaRemotingPort = {
    val serverSocket = ServerSocketChannel.open().socket()
    serverSocket.bind(new InetSocketAddress("localhost", 0))
    val port = serverSocket.getLocalPort
    serverSocket.close()
    port
  }

  override protected def afterAll(): Unit = testKit.shutdownTestKit()

}
