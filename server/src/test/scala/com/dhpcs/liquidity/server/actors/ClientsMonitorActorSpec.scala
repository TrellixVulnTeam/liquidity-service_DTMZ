package com.dhpcs.liquidity.server.actors

import akka.testkit.TestProbe
import com.dhpcs.liquidity.server.actors.ClientsMonitorActor.{ActiveClientsSummary, GetActiveClientsSummary}
import org.scalatest.WordSpec

class ClientsMonitorActorSpec extends WordSpec with ClusteredAndPersistentActorSystem {

  "A ClientsMonitorActor" should {
    "report on the active clients" in {
      val testProbe = TestProbe()
      val clientsMonitor = system.actorOf(ClientsMonitorActor.props, "clients-monitor")
      try {
        testProbe.send(
          clientsMonitor,
          GetActiveClientsSummary
        )
        testProbe.expectMsg(ActiveClientsSummary(Seq.empty))
      } finally system.stop(clientsMonitor)
    }
  }
}
