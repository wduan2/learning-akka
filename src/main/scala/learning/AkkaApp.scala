package learning

import akka.actor.{Actor, ActorSystem, Props}

import scala.io.StdIn


// configuration object using in creating an actor
object PrintMyActorRefActor {
  def props: Props = Props(new PrintMyActorRefActor)
}

class PrintMyActorRefActor extends Actor {
  override def receive: Receive = {
    case "printit" =>
      // create an actor and inject it into the existing tree
      val secondRef = context.actorOf(Props.empty, "second-actor")
      println(s"Second: $secondRef")
  }
}

object ActorHierarchyExperiments extends App {
  val system = ActorSystem("testSystem")

  // the creator actor
  val firstRef = system.actorOf(PrintMyActorRefActor.props, "first-actor")
  println(s"First: $firstRef")
  firstRef ! "printit"

  println(">>> Press ENTER to exit <<<")
  try StdIn.readLine()
  finally system.terminate()
  println(">>> Actor system terminated <<<")
}
