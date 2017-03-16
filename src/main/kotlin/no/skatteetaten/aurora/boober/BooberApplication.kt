package no.skatteetaten.aurora.boober

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary


@SpringBootApplication
class BooberApplication

fun main(args: Array<String>) {
    SpringApplication.run(BooberApplication::class.java, *args)
}

@Configuration
class ObjectMapperFactory {

    @Bean
    @Primary
    fun mapper(): ObjectMapper {
        return jacksonObjectMapper().let{ it.setSerializationInclusion(JsonInclude.Include.NON_NULL)}
    }
}
