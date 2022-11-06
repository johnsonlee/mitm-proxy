# mitm-proxy

A Man-In-The-Middle proxy server in Kotlin.

## Getting Started

```bash
docker-compose up -d --build
```

## Features

### Traffic Recording

The recorded flows can be discovered from [/api/flow](http://localhost:8080/api/flow/).

### Map To Local

```kotlin
val service: MappingService by context
// ...
service.mapToLocal(from, to)
```

### Map To Remote

```kotlin
val service: MappingService by context
// ...
service.mapToRemote(from, to)
```

## Download CA Certificates

Open [http://localhost:8080/ssl](http://localhost:8080/ssl) to download the CA certificates

## Install CA Certificates on Android Emulator

Please refer to https://docs.mitmproxy.org/stable/howto-install-system-trusted-ca-android/

