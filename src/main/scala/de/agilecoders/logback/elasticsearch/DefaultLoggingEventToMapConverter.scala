package de.agilecoders.logback.elasticsearch

import ch.qos.logback.classic.spi.{StackTraceElementProxy, IThrowableProxy, ILoggingEvent}
import scala.collection.mutable
import collection.JavaConversions._
import java.lang.Object
import scala.Predef.String
import scala.collection.Map
import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormatter, ISODateTimeFormat}
import de.agilecoders.logback.elasticsearch.actor.Configuration


/**
 * Default implementation of `ILoggingEventToMapTransformer`
 *
 * @author miha
 */
case class DefaultLoggingEventToMapConverter(var configuration: Configuration) extends ILoggingEventToMapTransformer {
    private[this] val formatter: DateTimeFormatter = ISODateTimeFormat.dateTime()

    override def toMap(event: ILoggingEvent) = {
        val json: mutable.Map[String, Any] = mutable.Map[String, Any]()

        addBaseValues(json, event)

        if (configuration.addArguments && event.getArgumentArray != null && event.getArgumentArray.length > 0) {
            json.put(Keys.arguments, transformArguments(event.getArgumentArray))
        }

        val mdc = event.getMDCPropertyMap
        if (configuration.addMdc && mdc != null && !mdc.isEmpty) {
            json.put(Keys.mdc, transformMdc(mdc))
        }

        if (configuration.addCaller && event.hasCallerData) {
            addCaller(json, event.getCallerData)
        }

        if (configuration.addStacktrace && event.getThrowableProxy != null) {
            json.put(Keys.stacktrace, transformThrowable(event.getThrowableProxy))
        }

        mapAsScalaMap(json.asInstanceOf[Map[String, Object]])
    }

    /**
     * adds a set of base values to given json structure.
     *
     * @param json  the json structure (map) to add values to.
     * @param event the logging event.
     */
    def addBaseValues(json: mutable.Map[String, Any], event: ILoggingEvent) {
        if (configuration.addTimestamp) {
            json.put(Keys.timestamp, event.getTimeStamp)
        }

        if (configuration.addDate) {
            json.put(Keys.date, transformDate(event.getTimeStamp))
        }

        if (configuration.addLevel) {
            json.put(Keys.level, event.getLevel.levelStr)
        }

        if (configuration.addMessage) {
            json.put(Keys.message, event.getFormattedMessage)
        }

        if (configuration.addLogger) {
            json.put(Keys.logger, event.getLoggerName)
        }

        if (configuration.addThread) {
            json.put(Keys.thread, event.getThreadName)
        }

        if (event.getMarker != null) {
            json.put(Keys.marker, event.getMarker.getName)
        }
    }

    /**
     * adds the caller data to given json structure.
     *
     * @param json       the json structure (map) to add values to.
     * @param callerData the caller data as array of { @link StackTraceElement}
     */
    def addCaller(json: java.util.Map[String, Any], callerData: Array[StackTraceElement]) = {
        json.put(Keys.caller, for (i <- Range(0, callerData.length)) yield transformCaller(callerData(i)))
    }

    def transformCaller(stackTraceElement: StackTraceElement): Map[String, Any] = {
        val map = mutable.Map[String, Any]()

        map.put(Keys.file, stackTraceElement.getFileName)
        map.put(Keys.clazz, stackTraceElement.getClassName)
        map.put(Keys.method, stackTraceElement.getMethodName)
        map.put(Keys.line, stackTraceElement.getLineNumber)
        map.put(Keys.nativeValue, stackTraceElement.isNativeMethod)

        map.toMap
    }

    /**
     * transforms a mdc property map into a json structure.
     *
     * @param mdc the mdc properties to transform
     * @return the transformed value
     */
    def transformMdc(mdc: java.util.Map[String, String]): Object = mdc


    def transformThrowable(throwable: IThrowableProxy): Map[String, Any] = {
        val map = mutable.Map[String, Any]()

        map.put(Keys.clazz, throwable.getClassName)
        map.put(Keys.message, throwable.getMessage)
        map.put(Keys.stacktrace, transformStackTraceElement(throwable))

        if (throwable.getCause != null) {
            map.put(Keys.cause, transformThrowable(throwable.getCause))
        }

        map.toMap
    }

    def transformStackTraceElement(throwable: IThrowableProxy): Seq[String] = {
        val elementProxies: Array[StackTraceElementProxy] = throwable.getStackTraceElementProxyArray
        val totalFrames = elementProxies.length - throwable.getCommonFrames

        for (i <- Range(0, totalFrames)) yield elementProxies(i).getStackTraceElement.toString
    }

    /**
     * transforms a timestamp into its date string format.
     *
     * @param timestamp the timestamp to transform
     * @return string representation of date (defined by timestamp)
     */
    def transformDate(timestamp: Long): String = new DateTime(timestamp).toString(formatter)

    def transformArguments(argumentArray: Array[Object]): Array[Object] = argumentArray
}

/**
 * defines all available keys of json structure.
 */
object Keys {
    val line = "line"
    val mdc = "mdc"
    val arguments = "arguments"
    val cause = "cause"
    val caller = "caller"
    val file = "file"
    val clazz = "class"
    val method = "method"
    val nativeValue = "native"
    val stacktrace = "stacktrace"
    val timestamp = "timestamp"
    val marker = "marker"
    val message = "message"
    val level = "level"
    val logger = "logger"
    val thread = "thread"
    val date = "date"
}

/**
 * Defines the interface of all `ILoggingEvent` to `Map`
 * transformer. This transformer is used to create a json representation
 * of `ILoggingEvent` that matches the elasticsearch mapping.
 *
 * @author miha
 */
trait ILoggingEventToMapTransformer {

    /**
     * transforms an `ILoggingEvent` to a `Map` according to the
     * elasticsearch mapping.
     *
     * @param event the logging event to transform
     * @return logging event as map
     */
    def toMap(event: ILoggingEvent): Map[String, Any]

}