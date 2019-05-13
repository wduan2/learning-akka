package learning

import akka.actor.{Actor, ActorSystem, Props}

import scala.io.StdIn


object StartStopActor {
  def props: Props = Props(new StartStopActor)
}

class StartStopActor extends Actor {
  // preRestart by default is delegate to postStop
  // postRestart by default is delegate to preStart
  override def preStart(): Unit = println(s"$self pre start")

  override def postStop(): Unit = println(s"$self post stop")

  override def receive: Receive = {
    case "fail" => throw new RuntimeException(s"$self failed")
  }
}

// configuration object using in creating an actor
object MasterActor {
  def props: Props = Props(new MasterActor)
}

class MasterActor extends Actor {
  val masterReceiveChild = "master-receive"

  override def preStart(): Unit = {
    println(s"$self pre start")
    context.actorOf(StartStopActor.props, "master-pre-start")
  }

  override def postStop(): Unit = println(s"$self post stop")

  override def receive: Receive = {
    case "create" =>
      // create an actor and inject it into the existing tree
      context.actorOf(StartStopActor.props, masterReceiveChild)
    case "failChild" =>
      // TODO: how to handle null?
      context.child(masterReceiveChild).get ! "fail"
    case "stop" => context.stop(self)
  }
}

object ActorHierarchyExperiments extends App {
  val system = ActorSystem("testSystem")

  // the creator actor
  val masterRef = system.actorOf(MasterActor.props, "master")

  masterRef ! "create"

  println(">>> Press ENTER to test failure <<<")

  try StdIn.readLine()
  finally masterRef ! "failChild"

  println(">>> Press ENTER to exit <<<")

  try StdIn.readLine()
  finally system.terminate()

  println(">>> Actor system terminated <<<")
}
