package learning


/**
 * Define the message objects
 */
final case class ReadTemperature(requestId: Long)
final case class RespondTemperature(requestId: Long, value: Option[Double])

object DeviceActors extends App {

}
