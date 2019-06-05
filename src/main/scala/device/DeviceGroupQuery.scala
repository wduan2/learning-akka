package device

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}

import scala.concurrent.duration.FiniteDuration

object DeviceGroupQuery {

  case object CollectionTimeout

  def props(actorToDeviceId: Map[ActorRef, String],
            requestId: Long,
            requester: ActorRef,
            timeout: FiniteDuration): Props = {
    Props(new DeviceGroupQuery(actorToDeviceId, requestId, requester, timeout))
  }
}

/**
  * 1. Create a separate actor for tracking each query to keep DeviceGroup actor simple and clean
  * 2. Each DeviceGroupQuery uses scheduler to create the deadline
  * 3. Use context.become to dynamically change the 'receive' behavior
  *
  * to verify:
  * - 'context.watch' allows the parent actor to receive the 'Terminate' message
  * - when does the returning 'Receive' of the 'waitingForReplies' get invoked?
  *
  * @param actorToDeviceId
  * @param requestId
  * @param requester
  * @param timeout
  */
class DeviceGroupQuery(actorToDeviceId: Map[ActorRef, String],
                       requestId: Long,
                       requester: ActorRef,
                       timeout: FiniteDuration) extends Actor with ActorLogging {

  import DeviceGroupQuery._
  import context.dispatcher

  val queryTimeoutTimer = context.system
    .scheduler
    .scheduleOnce(timeout, self, CollectionTimeout)

  override def preStart(): Unit = {
    actorToDeviceId.keysIterator.foreach { deviceActor =>
      context.watch(deviceActor)
      deviceActor ! Device.ReadTemperature(0)
    }
  }

  override def postStop(): Unit = {
    queryTimeoutTimer.cancel()
  }

  override def receive: Receive =
    waitingForReplies(Map.empty, actorToDeviceId.keySet)

  def waitingForReplies(repliesSoFar: Map[String, DeviceGroup.TemperatureReading],
                        stillWaiting: Set[ActorRef]): Receive = {
    case Device.RespondTemperature(0, valueOption) =>
      val deviceActor = sender()
      val reading = valueOption match {
        case Some(value) => DeviceGroup.Temperature(value)
        case None => DeviceGroup.TemperatureNotAvailable
      }

      receivedResponse(deviceActor, reading, stillWaiting, repliesSoFar)
    case Terminated(deviceActor) =>
      receivedResponse(deviceActor, DeviceGroup.DeviceNotAvailable, stillWaiting, repliesSoFar)
    case CollectionTimeout =>
      val timedOutReplies =
        stillWaiting.map { deviceActor =>
          val deviceId = actorToDeviceId(deviceActor)
          deviceId -> DeviceGroup.DeviceTimedOut
        }
      requester ! DeviceGroup.RespondAllTemperatures(requestId, repliesSoFar ++ timedOutReplies)
      context.stop(self)
  }

  def receivedResponse(deviceActor: ActorRef,
                       reading: DeviceGroup.TemperatureReading,
                       stillWaiting: Set[ActorRef],
                       repliesSoFar: Map[String, DeviceGroup.TemperatureReading]): Unit = {
    // prevent the 'Terminated' message from overwriting the received reading temperature
    context.unwatch(deviceActor)

    val deviceId = actorToDeviceId(deviceActor)
    val newStillWaiting = stillWaiting - deviceActor
    val newRepliesSoFar = repliesSoFar + (deviceId -> reading)

    if (newStillWaiting.isEmpty) {
      requester ! DeviceGroup.RespondAllTemperatures(requestId, newRepliesSoFar)
      context.stop(self)
    } else {
      context.become(waitingForReplies(newRepliesSoFar, newStillWaiting))
    }
  }
}
