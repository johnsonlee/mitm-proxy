package io.johnsonlee.mitmproxy.controller

import io.johnsonlee.mitmproxy.internal.ssl.PEM_CERT_FILE_PATH
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/ssl")
class SslController {

    @GetMapping("")
    fun cert(): ResponseEntity<Resource> {
        val cert = ClassPathResource(PEM_CERT_FILE_PATH)
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .header(HttpHeaders.CONTENT_LENGTH, cert.contentLength().toString())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=mitmproxy.pem")
                .body(cert)
    }

}