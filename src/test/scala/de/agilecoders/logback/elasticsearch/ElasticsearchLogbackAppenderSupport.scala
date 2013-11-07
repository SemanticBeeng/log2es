package de.agilecoders.logback.elasticsearch

import akka.actor.ActorDSL._
import akka.actor.{ActorDSL, ActorRef, ActorSystem}
import akka.testkit.{TestKit, ImplicitSender}
import ch.qos.logback.classic.spi.{LoggingEvent, ILoggingEvent}
import ch.qos.logback.classic.{Level, LoggerContext}
import com.google.common.base.Stopwatch
import com.twitter.ostrich.stats.{Distribution, Stats}
import de.agilecoders.logback.elasticsearch.actor.Reaper.AllSoulsReaped
import de.agilecoders.logback.elasticsearch.appender.ActorBasedElasticSearchLogbackAppender
import java.util.concurrent.TimeUnit
import org.joda.time.DateTime
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.duration._

/**
 * helper class for all appender tests
 *
 * @author miha
 */
protected class ElasticsearchLogbackAppenderSupport(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with
WordSpecLike with Matchers with BeforeAndAfterAll {
    lazy val appender = new ActorBasedElasticSearchLogbackAppender()

    var timeout: FiniteDuration = 15.seconds
    val timer: Stopwatch = new Stopwatch()

    override protected def afterAll(): Unit = {
        try {
            appender.stop()
            expectMsg(timeout, "Dead")
        } finally {
            timer.stop()

            Console.out.append("\n\n-----------\nMetrics:\n")
            Stats.get().metrics foreach (logMetrics _)
            Console.out.append("\n\nCounters:\n")
            Stats.get().counters foreach (logCounters _)
            Console.out.append("\n\nGauges:\n")
            Stats.get().gauges foreach (logGauges _)
            Console.out.append("\n\nDuration: " + timer.elapsed(TimeUnit.MILLISECONDS) + "ms")
        }
    }

    private def logMetrics(t: (String, Distribution)): Unit = Console.out.append(s"${t._1}: ${t._2.toJson()}\n")

    private def logCounters(t: (String, Long)): Unit = Console.out.append(s"${t._1}: ${t._2}\n")

    private def logGauges(t: (String, Double)): Unit = Console.out.append(s"${t._1}: ${t._2}\n")

    override protected def beforeAll(): Unit = {
        timer.start()
        appender.setContext(new LoggerContext)
        appender.start()

        val allSoulsReapedWatcher: ActorRef = ActorDSL.actor(new Act {
            become {
                case message: AllSoulsReaped => testActor ! "Dead"
            }
        })
        _system.eventStream.subscribe(allSoulsReapedWatcher, classOf[AllSoulsReaped])
    }

    protected final def waitForEmptyQueue() {
        Thread.sleep(5000)
        appender.router ! alive

        expectMsg(timeout, imAlive)
    }

    protected def newEvent(): ILoggingEvent = newEvent(scala.math.random)

    protected def newEvent(number: Double): ILoggingEvent = {
        val event = new LoggingEvent()

        event.setMessage("message" + number)
        event.setThreadName("thread" + number)
        event.setLevel(Level.WARN)
        event.setLoggerName("logger" + number)
        event.setTimeStamp(DateTime.now().getMillis)

        event
    }
}
