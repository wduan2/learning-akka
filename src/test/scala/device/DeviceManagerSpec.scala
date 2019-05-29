package device

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.testkit.TestProbe
import org.scalatest.{FlatSpec, Matchers}


class DeviceManagerSpec extends FlatSpec
  with Matchers {

  implicit val system: ActorSystem = ActorSystem("test")

  "A DeviceManager actor" should "be able to register a device group actor" in {
    val probe = TestProbe()
    val managerActor = system.actorOf(DeviceManager.props())

    managerActor.tell(DeviceManager.RequestTrackDevice("group1", "device1"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)
    val group1Device1 = probe.lastSender

    managerActor.tell(DeviceManager.RequestTrackDevice("group2", "device1"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)
    val group2Device2 = probe.lastSender

    // the 'lastSender' is the Device actor instead of the DeviceGroup actor
    group1Device1 should !==(group2Device2)
  }

  "A DeviceManager actor" should "be able to list active groups" in {
    val probe = TestProbe()
    val managerActor = system.actorOf(DeviceManager.props())

    managerActor.tell(DeviceManager.RequestTrackDevice("group1", "device1"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)

    managerActor.tell(DeviceManager.RequestTrackDevice("group2", "device1"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)

    managerActor.tell(DeviceManager.RequestGroupList(requestId = 0), probe.ref)
    probe.expectMsg(DeviceManager.ReplyGroupList(requestId = 0, Set("group1", "group2")))
  }

  "A DeviceManager actor" should "be able to list active groups after one shuts down" in {
    val probe = TestProbe()
    val managerActor = system.actorOf(DeviceManager.props())

    managerActor.tell(DeviceManager.RequestTrackDevice("group1", "device1"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)

    managerActor.tell(DeviceManager.RequestTrackDevice("group2", "device1"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)

    managerActor.tell(DeviceManager.RequestGroupList(requestId = 0), probe.ref)
    probe.expectMsg(DeviceManager.ReplyGroupList(requestId = 0, Set("group1", "group2")))

    managerActor.tell(DeviceManager.RequestGroup(requestId = 1, "group1"), probe.ref)

    // pattern match and return the actor ref
    val groupActor = probe.expectMsgPF() {
      case DeviceManager.ReplyGroup(_, ref) => ref
    }

    probe.watch(groupActor)
    groupActor ! PoisonPill
    probe.expectTerminated(groupActor)

    probe.awaitAssert {
      managerActor.tell(DeviceManager.RequestGroupList(requestId = 2), probe.ref)
      probe.expectMsg(DeviceManager.ReplyGroupList(requestId = 2, Set("group2")))
    }
  }
}
