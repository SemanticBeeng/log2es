package de.agilecoders.logback.elasticsearch.actor

import akka.actor._
import akka.routing.{RoundRobinRouter, Broadcast}
import ch.qos.logback.classic.spi.ILoggingEvent
import de.agilecoders.logback.elasticsearch._
import de.agilecoders.logback.elasticsearch.actor.Reaper.WatchMe
import de.agilecoders.logback.elasticsearch.conf.{DependencyHolder, Configuration}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.util.Random

object Worker extends DependencyHolder {
    private[this] lazy val configuration = dependencies.configuration

    /**
     * create actor `Props` for `Worker` actor
     *
     * @return new `Props` instance
     */
    def props() = Props(classOf[Worker]).withRouter(RoundRobinRouter(nrOfInstances = configuration.noOfWorkers))

    /**
     * create and start a new scheduler that sends `FlushQueue` messages to given `Worker` actor.
     *
     * @param context the actor context
     * @param configuration the configuration
     * @return new scheduler
     */
    protected[Worker] def newScheduler(context: ActorContext, configuration: Configuration): Cancellable = {
        val startDelay = Duration(100 + new Random().nextInt(configuration.flushInterval), TimeUnit.MILLISECONDS)
        val interval = Duration(configuration.flushInterval, TimeUnit.MILLISECONDS)

        context.system.scheduler.schedule(startDelay, interval, context.self, flushQueue)
    }

}

/**
 * Actor that is responsible for queuing, converting and sending of log messages.
 *
 * @author miha
 */
class Worker() extends Actor with DefaultSupervisor with ActorLogging with DefaultMessageHandler with DependencyHolder {
    private[this] lazy val configuration = newConfiguration()
    private[this] lazy val converter: ActorRef = newConverter()
    private[this] lazy val indexSender: ActorRef = newSender()
    private[this] lazy val scheduler: Cancellable = newScheduler(configuration)

    override protected def onMessage = {
        case Converted(message: AnyRef) => indexSender ! message

        case event: ILoggingEvent => converter.tell(event, indexSender)
    }

    override protected def onFlushQueue(message: FlushQueue) = {
        log.debug(s"received flush queue action from $sender")

        flush()
    }

    /**
     * forwards the poison pill
     */
    private[this] def forward(pill: PoisonPill): Unit = {
        converter ! Broadcast(pill)
        indexSender ! Broadcast(pill)
    }

    /**
         * flush sender to queue to elasticsearch
         */
    private[this] def flush() {
        indexSender ! Broadcast(flushQueue)
    }

    /**
     * initialize all actors and scheduler before actor is started and
     * receives its first message
     */
    override def preStart() {
        super.preStart()

        log.info(s"startup worker actor: ${hashCode()}")

        context.system.eventStream.publish(WatchMe(self))
    }

    /**
     * after actor was stopped, the scheduler must be stopped too
     */
    override def postStop() {
        flush()
        forward(PoisonPill.getInstance)

        scheduler.cancel()

        context.stop(converter)
        context.stop(indexSender)

        log.info(s"shutting down worker actor: ${hashCode()}")

        super.postStop()
    }

    /**
     * loads the configuration instance
     */
    protected def newConfiguration(): Configuration = dependencies.configuration

    /**
     * creates a new converter actor reference
     */
    protected def newConverter(): ActorRef = dependencies.newConverter(context)

    /**
     * creates a new sender actor reference
     */
    protected def newSender(): ActorRef = dependencies.newSender(context)

    /**
     * creates a new scheduler instance
     */
    protected def newScheduler(configuration: Configuration): Cancellable = Worker.newScheduler(context, configuration)

}
