package de.agilecoders.elasticsearch.logger.core.store

import akka.util.Timeout

/**
 * A FutureResponse represents a response of a buffered store call.
 */
trait FutureResponse {
    protected val timeout: Timeout

    /**
     * blocks current thread until response is arrived.
     *
     * @param timeout time to wait for response
     */
    def await(timeout: Timeout): Unit

    /**
     * blocks current thread until response is arrived. This method uses the
     * default `Configuration.shutdownAwaitTimeout`.
     */
    def await(): Unit = await(timeout)

    /**
     * blocks current thread until response is arrived and returns the result. This method uses the
     * default `Configuration.shutdownAwaitTimeout`.
     *
     * @return response as `AnyRef`
     */
    def get(): AnyRef = get(timeout)

    /**
     * blocks current thread until response is arrived and returns the result.
     *
     * @return response as `AnyRef`
     */
    def get(timeout: Timeout): AnyRef
}
