package at.robert.tuhelper

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class Config {

    @Bean
    fun ktorHttpClient(): HttpClient {
        return HttpClient(CIO) {
        }
    }

}
