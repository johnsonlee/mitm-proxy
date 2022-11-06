package io.johnsonlee.mitmproxy

import io.johnsonlee.mitmproxy.internal.MillisecondsDuration
import org.littleshoot.proxy.HttpProxyServerBootstrap
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.ConfigurableApplicationContext
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.reflect.KProperty

@SpringBootApplication
class MitmProxyApplication(
        @Autowired private val bootstrap: HttpProxyServerBootstrap
) : CommandLineRunner {

    override fun run(vararg args: String) {
        val duration = MillisecondsDuration()
        val server = bootstrap.start()
        logger.info("Mitm proxy started at ${server.listenAddress} in $duration ms")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MitmProxyApplication::class.java)

        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<MitmProxyApplication>(*args)
        }

        @JvmStatic
        fun startup(args: Array<String>): ConfigurableApplicationContext {
            return runApplication<MitmProxyApplication>(*args)
        }

        @JvmStatic
        fun shutdown(port: Int) {
            (URL("http://localhost:${port}/actuator/shutdown").openConnection() as HttpURLConnection).run {
                doInput = true
                requestMethod = "POST"
                connect()
                inputStream.bufferedReader().use(BufferedReader::readText)
            }.also(::println)
        }

    }

}

inline operator fun <reified T> ConfigurableApplicationContext.getValue(thisRef: Any?, property: KProperty<*>?): T {
    return getBean(T::class.java)
}
