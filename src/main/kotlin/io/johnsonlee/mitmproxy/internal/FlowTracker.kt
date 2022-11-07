package io.johnsonlee.mitmproxy.internal

import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import org.littleshoot.proxy.ActivityTracker
import org.littleshoot.proxy.FlowContext
import org.littleshoot.proxy.FullFlowContext
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import javax.net.ssl.SSLSession

internal class FlowTracker : ActivityTracker {

    private val logger = LoggerFactory.getLogger(FlowTracker::class.java)

    override fun clientConnected(clientAddress: InetSocketAddress) {
        logger.debug("Client connected: {}", clientAddress)
    }

    override fun clientSSLHandshakeSucceeded(clientAddress: InetSocketAddress, sslSession: SSLSession) {
        logger.debug("Client SSL handshake succeeded: {}", clientAddress)
    }

    override fun clientDisconnected(clientAddress: InetSocketAddress, sslSession: SSLSession?) {
        logger.debug("Client disconnected: {}", clientAddress)
    }

    override fun bytesReceivedFromClient(flowContext: FlowContext, numberOfBytes: Int) {
        logger.debug("Bytes received from client: {}", numberOfBytes)
    }

    override fun requestReceivedFromClient(flowContext: FlowContext, httpRequest: HttpRequest) {
        logger.debug("Request received from client: {}", httpRequest)
    }

    override fun bytesSentToServer(flowContext: FullFlowContext, numberOfBytes: Int) {
        logger.debug("Bytes sent to server: {}", numberOfBytes)
    }

    override fun requestSentToServer(flowContext: FullFlowContext, httpRequest: HttpRequest) {
        logger.debug("Request sent to server: {}", httpRequest)
    }

    override fun bytesReceivedFromServer(flowContext: FullFlowContext, numberOfBytes: Int) {
        logger.debug("Bytes received from server: {}", numberOfBytes)
    }

    override fun responseReceivedFromServer(flowContext: FullFlowContext, httpResponse: HttpResponse) {
        logger.debug("Response received from server: {}", httpResponse)
    }

    override fun bytesSentToClient(flowContext: FlowContext, numberOfBytes: Int) {
        logger.debug("Bytes sent to client: {}", numberOfBytes)
    }

    override fun responseSentToClient(flowContext: FlowContext, httpResponse: HttpResponse) {
        logger.debug("Response sent to client: {}", httpResponse)
    }

}
