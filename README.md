# Pokémon rankings API

REST API that answers three questions on top of [PokéAPI](https://pokeapi.co/api/v2/):

1. the 5 heaviest Pokémon
2. the 5 highest Pokémon
3. the 5 Pokémon with the most base experience

Stack: Java 25 (LTS), Spring Boot 4.1, Maven.

## Run it

```bash
mvn verify                  # compiles, runs all tests and enforces the 90% coverage gate
mvn spring-boot:run         # starts on :8080 (first start takes a few seconds, see "Loading strategy")
```

Docker:

```bash
docker build -t pokemon-rankings .
docker run -p 8080:8080 pokemon-rankings                          # production profile: Swagger UI off
docker run -p 8080:8080 -e OPENAPI_ENABLED=true pokemon-rankings  # same, with Swagger UI on /swagger-ui.html
```

`mvn spring-boot:run` uses the default profile, where Swagger UI is always on.

| Endpoint | Description |
| --- | --- |
| `GET /api/v1/pokemon/heaviest` | 5 heaviest |
| `GET /api/v1/pokemon/highest` | 5 highest |
| `GET /api/v1/pokemon/most-experienced` | 5 with the most base experience |
| `GET /actuator/health/liveness`, `/actuator/health/readiness` | Kubernetes probes |
| `GET /swagger-ui.html`, `/v3/api-docs` | OpenAPI (disabled in the `prod` profile unless `OPENAPI_ENABLED=true`) |

Every ranking accepts `?forms=` (case-insensitive):

| `forms` | Meaning |
| --- | --- |
| `DEFAULT_ONLY` (default) | Only the main form of each species |
| `ALL` | Every entry of PokéAPI's `/pokemon`; several forms of one species can share the top 5 |
| `BEST_PER_SPECIES` | Every form competes, but each species appears at most once |

```json
{
  "metric": "WEIGHT",
  "forms": "DEFAULT_ONLY",
  "results": [
    { "id": 1, "name": "example", "species": "example", "defaultForm": true,
      "heightMeters": 1.7, "weightKilograms": 90.5, "baseExperience": 267 }
  ]
}
```

An invalid `forms` value returns `400` as an RFC 9457 problem detail listing the allowed values. While the data is not
loaded the rankings return `503`.

## Decisions

### The data source cannot answer the questions directly

`GET /pokemon` only returns names and URLs. `weight`, `height` and `base_experience` live in `GET /pokemon/{id}`, and the
API offers no sorting or filtering. Computing any ranking therefore needs the whole catalogue: one listing call plus one
detail call per entry (~1,350 today).

### Loading strategy: one shared snapshot, loaded at startup

* The catalogue is downloaded **once**, into an immutable `PokemonSnapshot`, and the three rankings (and every `forms`
  mode) are computed from it. Requests never call PokéAPI: it is slow for this access pattern, it asks clients to cache,
  and it would make this API depend on its availability on every request.
* The snapshot is published with a single atomic swap (`AtomicReference`), so readers never see a half-built catalogue.
* A scheduled job refreshes it (default every 24 h, `pokeapi.refresh-interval`). A failed refresh is logged and the
  previous snapshot keeps being served.
* The first load runs in an `ApplicationRunner`, which executes before the readiness state becomes
  `ACCEPTING_TRAFFIC`: the readiness probe stays down until the data is there, with no custom health indicator.
* **Failure policy.** If any request still fails after its retries, the *whole* load fails and nothing is published. A
  ranking computed on an incomplete catalogue would be silently wrong, which is worse than an error. If the very first
  load fails, the application fails to start and the orchestrator restarts it.
* The listing response includes `count`; the client refuses a listing whose size differs from it, so a catalogue that
  outgrows `pokeapi.list-limit` cannot silently truncate the data.

### Efficient download

* One request per entry on **virtual threads** (Java 21+), bounded by a `Semaphore` (`pokeapi.concurrency`, default 16)
  so PokéAPI is not flooded.
* The JDK `HttpClient` negotiates HTTP/2, which multiplexes the requests over few connections.
* Each request is retried with exponential backoff (`pokeapi.max-attempts`, `pokeapi.initial-backoff`) and has connect
  and read timeouts.
* **Retries are selective.** Network failures, 5xx, 408 and 429 are retried. Any other 4xx (a 404 for a URL the
  listing just returned, a 400) is permanent: the gateway wraps it in `PermanentPokeApiException` and the loader fails
  at once instead of burning every attempt and its backoff on something that cannot succeed.
* The retry loop is ~20 lines (`Retry`) instead of a library: it keeps the dependency list small and is trivial to test.

### What counts as "a Pokémon": the `forms` parameter

PokéAPI's `/pokemon` lists *battle forms*, not species. Besides the ~1,025 default forms it contains ~325 alternative
forms (Mega, Gigantamax, Eternamax, regional variants) with ids from 10001, and they dominate the rankings (Eternatus
Eternamax is 100 m tall). The statement does not say which reading is intended, so the API supports three and the
default is the most natural one:

* `DEFAULT_ONLY`: one entry per species, its main form (`is_default`).
* `ALL`: every entry. The same species can appear several times.
* `BEST_PER_SPECIES`: deduplicates by species (`species.name`, already in the detail response, no extra calls). It
  deduplicates *before* cutting to 5, so the result always has 5 entries when there are at least 5 species.

`is_default` from the detail response is used instead of "id < 10000": it does not depend on a numbering convention
PokéAPI does not guarantee. The filter runs in memory, so all modes share one download.

### Other details

* **Ties** are broken by lowest id, so results are deterministic (and tests stable).
* **`base_experience` can be `null`** for some forms: those entries are excluded from the base-experience ranking only.
* **Units.** PokéAPI returns height in decimetres and weight in hectograms; the API returns metres and kilograms.
  Rankings are computed on the raw integers.
* **Three endpoints** rather than a generic `/rankings?metric=`: they map one-to-one to the three requirements, are
  self-describing in OpenAPI and need no validation of a metric parameter. The implementation behind them is a single
  `RankingService.top(metric, forms)`.
* **Java 25 (LTS)**, not the newest release: tooling (JaCoCo, Mockito, Spring Boot) lags behind each JDK release, and
  a non-LTS release is hard to justify in something called "production ready".

## Testing

`mvn verify` runs everything and **fails the build if line, instruction or branch coverage drops below 90%**
(JaCoCo `check`, `coverage.minimum` in the `pom.xml`). The report is in `target/site/jacoco/index.html`.
Branch coverage is gated too because line coverage alone hides untested `if`/`else` paths.
Only `PokemonApplication` (the `main` method) is excluded.

* Unit tests: ranking logic (all `forms` modes, ties, nulls, limit), snapshot lifecycle, retry/backoff, concurrency
  bound, interruption handling, PokéAPI client mapping (`MockRestServiceServer`), controller and error handling.
* Integration test: the full application against an in-process PokéAPI stub (`StubPokeApi`, JDK `HttpServer`), over
  real HTTP. It also asserts that PokéAPI is queried exactly once per entry at startup and never per request.
  Tests never call the real PokéAPI.

## Production readiness

* Liveness and readiness probes (Actuator); readiness is down until the data is loaded.
* Externalised configuration (`application.yml`, overridable with environment variables) and a `prod` profile with
  structured ECS JSON logs.
* Graceful shutdown, timeouts and retries on every outbound call.
* Consistent error format (RFC 9457 problem details).
* OpenAPI / Swagger UI, off by default in the `prod` profile (the Docker image) so the API surface is not advertised
  in production; `-e OPENAPI_ENABLED=true` turns it on.
* Multi-stage `Dockerfile`, non-root user, JVM sized from container memory.

## Not done (and what I would do next)

* **Metrics and alerting**: Micrometer metrics for snapshot age, load duration and refresh failures.
* **Degraded start**: today a failed first load stops the application. Alternative: start, keep readiness down and
  retry in the background, so a PokéAPI outage during a deployment does not crash-loop the pod.
* **Cheaper refreshes**: conditional requests (`ETag`/`If-None-Match`) or only re-reading entries that changed, instead
  of downloading all ~1,350 again.
* **Persisting the snapshot** (file/Redis) so restarts and multiple replicas do not each hammer PokéAPI.
* **Circuit breaker / rate limiting** (Resilience4j) around the client and on the public endpoints.
* **`limit` parameter** and pagination if rankings other than the top 5 are ever needed.
* **WireMock** for richer failure-injection tests (latency, 429s, malformed bodies) than the JDK-based stub.
* **CI pipeline** (build, tests, coverage gate, image publishing), plus mutation testing (PIT) for the ranking logic.
* **Authentication** if the API stops being internal.
* **Startup time**: AOT cache / CDS (Project Leyden support in Java 25) and layered jars for the image.
