package io.johnsonlee.mitmproxy

import io.johnsonlee.mitmproxy.internal.middleware.BootstrapMiddlewares
import io.johnsonlee.mitmproxy.internal.util.MillisecondsDuration
import org.littleshoot.proxy.HttpProxyServerBootstrap
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.ServiceLoader
import java.util.function.Supplier
import kotlin.reflect.KProperty

@JvmSynthetic
internal inline operator fun <reified T> ApplicationContext.getValue(thisRef: Any?, property: KProperty<*>?): T {
    return getBean(T::class.java)
}

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
            startup(args, ServiceLoader.load(Middleware::class.java).toList())
        }

        @JvmStatic
        fun startup(args: Array<String>, vararg middlewares: Middleware): ConfigurableApplicationContext {
            return startup(args, middlewares.toList())
        }

        @JvmStatic
        fun startup(args: Array<String>, middlewares: List<Middleware>): ConfigurableApplicationContext {
            return runApplication<MitmProxyApplication>(*args).apply {
                registerBootstrapMiddlewares(middlewares)
            }
        }

        @JvmSynthetic
        private fun ConfigurableApplicationContext.registerBootstrapMiddlewares(middlewares: List<Middleware>) {
            val bd = GenericBeanDefinition().apply {
                setBeanClass(BootstrapMiddlewares::class.java)
                instanceSupplier = Supplier { BootstrapMiddlewares.of(middlewares) }
            }
            (beanFactory as BeanDefinitionRegistry).registerBeanDefinition(bd.beanClass.canonicalName, bd)
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
