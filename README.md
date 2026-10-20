<p align="center"><img src="docs/assets/banner.svg" width="100%" alt="chat-ads-eval banner"/></p>

<h1 align="center">chat-ads-eval</h1>

<p align="center">A contextual ad service using hybrid keyword and vector search to return a clearly labeled sponsored product kept separate from the chat answer, behind a CI/CD eval gate with deterministic checks and LLM-as-judge evaluation.</p>

<p align="center">
  <a href="https://github.com/dakshshukla10/chat-ads-eval/actions/workflows/ci.yml"><img src="https://img.shields.io/github/actions/workflow/status/dakshshukla10/chat-ads-eval/ci.yml?branch=main&style=for-the-badge&logo=githubactions&logoColor=white&label=CI" alt="CI status"/></a>
  <a href="https://github.com/dakshshukla10/chat-ads-eval/actions/workflows/eval-gate.yml"><img src="https://img.shields.io/github/actions/workflow/status/dakshshukla10/chat-ads-eval/eval-gate.yml?branch=main&style=for-the-badge&logo=githubactions&logoColor=white&label=Eval%20gate" alt="Eval gate status"/></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-green.svg?style=for-the-badge" alt="MIT license"/></a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-ED8B00.svg?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F.svg?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot 3.5"/>
  <img src="https://img.shields.io/badge/Redis-cache-DC382D.svg?style=for-the-badge&logo=redis&logoColor=white" alt="Redis"/>
  <img src="https://img.shields.io/badge/Postgres-pgvector-4169E1.svg?style=for-the-badge&logo=postgresql&logoColor=white" alt="Postgres with pgvector"/>
  <img src="https://img.shields.io/badge/Prometheus-metrics-E6522C.svg?style=for-the-badge&logo=prometheus&logoColor=white" alt="Prometheus"/>
  <img src="https://img.shields.io/badge/Grafana-dashboard-F46800.svg?style=for-the-badge&logo=grafana&logoColor=white" alt="Grafana"/>
</p>

The catalog, the eval prompts and the labeled judge cases are all synthetic, and nothing here has seen real traffic. The numbers below come from runs on that synthetic data.

## Why

Shopping assistants are becoming a place where people decide what to buy, and ads shown there have to meet three bars:

- The sponsored product fits what the shopper just asked for.
- Every ad says `Sponsored`, in a field of its own.
- Ad text never leaks into the assistant's answer.

Prompts, models and ranking rules change often, and any of those changes can quietly break one of the three. This repo treats the checks as code and runs them on every change.

## What it does

`POST /v1/ads/contextual` takes a shopper prompt (and, optionally, the answer the host assistant already wrote) and returns up to three labeled sponsored items.

```json
{
  "answer_slot": "Look for a light shoe with a wide toe box.",
  "sponsored": [
    {
      "product_id": "p-1001",
      "title": "Trailblaze Running Shoes",
      "label": "Sponsored",
      "reason": "Matches your request: running, shoe, marathon"
    }
  ]
}
```

`answer_slot` is the host's text, returned untouched, or null when none was sent. The service never writes it and never uses it to pick ads.

The pipeline is a Redis candidate cache, then retrieval, then the policy filter, then ranking. Retrieval is hybrid: Postgres full-text search and pgvector similarity, merged by reciprocal rank fusion. If Postgres or the embedder fails, an in-memory keyword retriever answers and the request is counted under `chatads_fallback_total{reason="backend_error"}`. Details are in [docs/architecture.md](docs/architecture.md).

An example of what hybrid retrieval buys: the prompt `runing shose` has no word in common with the catalog, so the keyword retriever returns nothing, while the vector side matches it through character trigrams. Seven of the 65 eval cases (six misspellings and the one-word prompt `coffee`) fail on the keyword baseline and pass on hybrid retrieval, and a test enforces both halves.

## Status

| Area | State | Notes |
| :-- | :-: | :-- |
| Service, policy filter, ranking | Done | Spring Boot 3.5, Java 21 |
| Hybrid retrieval | Done | `tsvector` with `ts_rank_cd` (not BM25) plus pgvector, fused with RRF |
| Embeddings | Done | Offline hashing embedder by default; OpenAI-compatible and Ollama clients behind env vars |
| Keyword fallback | Done | In-memory, counted as `backend_error` |
| Redis cache | Done | Caches candidates before the policy filter; tested against a real Redis |
| Deterministic eval checks | Done | 65 cases, run in-process and over HTTP |
| LLM-as-judge | Done | OpenAI-compatible client (Ollama works), rubric prompts, strict JSON parsing, calibration set |
| Eval gate thresholds | Done | `evals/thresholds.json`; the workflow fails below them |
| Token and cost metrics | Done | Real counts when the provider reports them, documented estimate otherwise |
| Grafana dashboard and alert rules | Done | Alert rules have promtool tests that run in CI |
| Dockerfile and image CI | Done | Smoke-tested in CI; pushed to GHCR from `main` |
| Kubernetes manifests | Done | Validated with kubeconform in CI; see the note under Deployment |
| Free-hosting setup | Done, not deployed yet | Landing page, `demo` profile, `render.yaml`; see Hosted demo |

What is still weak: the offline embedder matches spelling, not meaning. The judge model is small and noisy. The retrieval thresholds were tuned on the same synthetic set the gate uses. Nothing has been load tested.

## Eval gate

Two layers run on every pull request.

Deterministic checks run against the packaged service over HTTP:

| Check | What it verifies |
| :-- | :-- |
| `schema_valid` | Exact fields, non-blank strings, `answer_slot` null or text |
| `labeled_sponsored` | Every ad has the label `Sponsored` |
| `ad_not_in_answer` | Ad title, reason and id never appear inside `answer_slot` |
| `no_banned_category` | No ad from a banned category |
| `expected_category` | The top ad is in an expected category, or no ad is shown when none is expected |

`expected_category` runs against a synthetic catalog, so it checks the pipeline's wiring, not real-world relevance. Ten of the cases carry a neutral host answer, which gives `ad_not_in_answer` real text to inspect.

The LLM judge scores the top ad of each case from 1 to 5 against one rubric per dimension (relevance, ad-answer separation, safety) in [evals/judge/](evals/judge). A reply that is not exactly `{"score": n, "reason": "..."}` gets one retry and is then a format failure, never a guessed score. Before scoring the eval set, the workflow checks the judge against 36 labeled cases (34 count, two are borderline) and fails if agreement drops below the bar.

In the workflow the judge is `qwen2.5:1.5b-instruct` served by Ollama on the runner's CPU, so it needs no API key and runs on pull requests from forks. All pass bars are in [evals/thresholds.json](evals/thresholds.json); the workflow only passes a mode flag.

Measured on this synthetic data:

| Run | Result |
| :-- | :-- |
| Deterministic, hybrid retrieval | 65 of 65 cases pass |
| Deterministic, keyword baseline | 58 of 58 untagged cases pass; the 7 `hybrid_only` cases fail, as intended |
| Judge calibration, `qwen2.5:1.5b-instruct`, local run | 26 of 34 agree |
| Judge calibration, `qwen2.5:0.5b-instruct`, local run | 20 of 34 agree |
| Judge calibration, `qwen2.5:3b-instruct`, local run | 28 of 34 agree |

Scores over the eval set from the CI run on `main` (`qwen2.5:1.5b-instruct`, 104 judge calls, about 4 minutes on a GitHub-hosted runner; the whole eval-gate job took about 7 minutes):

| Dimension | Cases | Mean score | Share scoring 4 or 5 | Bar (mean, share) |
| :-- | --: | --: | --: | :-- |
| Relevance | 47 | 4.91 | 0.98 | 4.2, 0.85 |
| Ad-answer separation | 10 | 3.50 | 0.60 | 3.0, 0.50 |
| Safety | 47 | 4.68 | 0.85 | 4.2, 0.75 |

Calibration in the same CI run was 26 of 34, matching the local run. A local run of the same model scored separation 4.30 and 0.80, so ten cases is too few to pin that dimension down; the bar is loose for that reason. The 3B model (`qwen2.5:3b-instruct`) was tried locally and judged the eval set more harshly (relevance share 0.68, safety 0.64), so it would need its own bars; the workflow uses the 1.5B model.

The judge is a weak instrument. It sometimes invents a problem in a clean answer, and it judges only the top ad, so a multi-intent prompt can score well while one of its needs goes unserved. The bars are loose on purpose: they catch a retriever or rubric that breaks outright, not small drifts. Treat the calibration agreement as the honest measure of how much to trust the scores.

## Run it

You need JDK 21 (the Maven wrapper is included) and, for the optional pieces, Docker.

```bash
./mvnw verify                   # build and all tests; Docker is needed for the Postgres and Redis ones
./mvnw spring-boot:run          # keyword-only mode on :8080, no dependencies
```

Hybrid retrieval and the cache need Postgres and Redis:

```bash
cp .env.example .env            # set POSTGRES_PASSWORD, then reuse it as CHATADS_DB_PASSWORD
docker compose up -d redis postgres
set -a; source .env; set +a
export CHATADS_DB_URL=jdbc:postgresql://localhost:5432/chatads CHATADS_CACHE_ENABLED=true
./mvnw spring-boot:run
```

Ask for an ad:

```bash
curl -s -X POST localhost:8080/v1/ads/contextual \
  -H 'content-type: application/json' \
  -d '{"prompt":"noise cancelling headphones for my commute"}'
```

Run the deterministic gate against the running service:

```bash
./mvnw -q exec:java -Dexec.mainClass=dev.chatads.eval.EvalRunner
```

Run the judge locally with Ollama (no account or key; models are downloaded on first pull):

```bash
ollama pull qwen2.5:1.5b-instruct
export LLM_JUDGE_BASE_URL=http://localhost:11434/v1 LLM_JUDGE_MODEL=qwen2.5:1.5b-instruct
./mvnw -q exec:java -Dexec.mainClass=dev.chatads.eval.EvalRunner -Dexec.args="--mode all"
```

Any OpenAI-compatible server works the same way: set the base URL, the model and `LLM_JUDGE_API_KEY`. With none of the three set, the judge step prints SKIPPED.

To use a real embedding model, for example Ollama's `all-minilm`:

```bash
ollama pull all-minilm
export CHATADS_EMBEDDING_PROVIDER=ollama CHATADS_EMBEDDING_BASE_URL=http://localhost:11434 \
       CHATADS_EMBEDDING_MODEL=all-minilm CHATADS_EMBEDDING_DIMENSIONS=384 CHATADS_MIN_SIMILARITY=0.4
```

The catalog table is rebuilt automatically when the vector size changes. `CHATADS_MIN_SIMILARITY` has to be tuned per model; 0.4 here is a rough starting point, not a tuned value. Every setting is listed in [.env.example](.env.example).

Prometheus (http://localhost:9090) and Grafana (http://localhost:3000) start with `docker compose up -d prometheus grafana` and scrape the service on the host.

## Observability

The service exports these on `/actuator/prometheus`:

| Metric | Meaning |
| :-- | :-- |
| `http_server_requests_seconds_*` | Latency histogram for p50, p95 and p99 |
| `chatads_requests_total`, `chatads_sponsored_served_total` | Requests and ads returned |
| `chatads_policy_filtered_total` | Candidates the policy filter removed |
| `chatads_fallback_total{reason}` | `no_match`, `policy_filtered`, `backend_error`, `cache_error` |
| `chatads_cache_total{result}` | `hit`, `miss`, `error` |
| `chatads_llm_tokens_total{direction,operation,model,source}` | Tokens; `source` is `provider` or `estimate` |
| `chatads_llm_cost_usd_total{operation,model}` | Estimated dollars from [llm-prices.json](src/main/resources/llm-prices.json) |

The token and cost series come from real model calls, which with the offline embedder means none, so those two panels stay empty by default. With Ollama's `all-minilm` as the embedder, the token panel shows the counts the server reports. Cost stays at zero for any model that is not in the price table, which includes every local model. The price table lists two hosted models, copied from their public price pages; check them before relying on a dollar figure.

The dashboard in `observability/grafana/dashboards/overview.json` has latency percentiles, request and ad rates, cache hit ratio, fallbacks by reason, the share of requests with no ad, policy-filtered candidates, tokens and cost. `observability/alerts.yml` defines six alert rules (service down, p95 latency, 5xx share, backend fallback, cache errors, high no-ad share). Their thresholds are starting values, not measured ones. The rules have promtool unit tests, and a JUnit test checks that every metric named in the dashboard and the rules is one the service exports.

## Deployment

The multi-stage `Dockerfile` builds a non-root image. CI builds it, starts it with a read-only filesystem and a 512 MB limit, waits for the health check and checks that a request returns a `Sponsored` item. On pushes to `main` it is published to `ghcr.io/dakshshukla10/chat-ads-eval` using the workflow's own token.

`k8s/` holds a namespace, ConfigMap, Deployment, Service, a secret example and dev-only Postgres and Redis. See [k8s/README.md](k8s/README.md). CI validates the manifests with kubeconform against Kubernetes 1.30 schemas. The manifests were also applied once to a throwaway local kind cluster, where both app replicas and the dev dependencies became ready; that check is not part of CI and no real cluster has run them.

### Hosted demo

Live demo: TBD

The demo is meant for free hosting: a 512 MB container with a tenth of a CPU, Neon for Postgres and Upstash for Redis. [render.yaml](render.yaml) is a Render Blueprint for it (Docker runtime, free plan, Singapore). Nothing in the application is specific to Render, and the same image runs under plain `docker run` or `docker compose` on a VM.

Free instances sleep after about 15 idle minutes. The first request after that waits for the container to start, which took close to a minute in the measurements below. The landing page at `/` says so, and it states that the catalog is synthetic and the embeddings are the offline hash embedder.

Setting `SPRING_PROFILES_ACTIVE=demo` turns on the hosted configuration:

- Embeddings stay on the offline hash embedder, whatever `CHATADS_EMBEDDING_*` says.
- `/actuator/prometheus` is not exposed. Health and info are.
- Each client address gets 30 requests a minute on `/v1/`, then HTTP 429 with a `Retry-After` header. The counter lives in memory, so a restart resets it.
- The client address comes from `X-Forwarded-For` when the request arrives from a private-range proxy. I have not checked what Render's proxy sends, so look at the first 429 in the logs before trusting the per-address count.
- Redis and Postgres timeouts are longer, and Tomcat is capped at 16 threads.

The service has no endpoint that writes the catalog. The one thing a stranger can write is a cache entry for their own prompt, which expires after `CHATADS_CACHE_TTL` (ten minutes by default) and is never read as anything but candidates for that prompt.

| Variable | Purpose |
| :-- | :-- |
| `SPRING_PROFILES_ACTIVE=demo` | The hosted configuration above |
| `PORT` | HTTP port. The host sets it (10000 on Render); the default is 8080 |
| `CHATADS_DB_URL` | Postgres url. Either `jdbc:postgresql://...` or the `postgresql://user:password@host/db?sslmode=require` string a host such as Neon shows. Empty means the in-memory keyword retriever only |
| `CHATADS_DB_USER`, `CHATADS_DB_PASSWORD` | Optional when the url carries them; when set they win |
| `CHATADS_DB_CONNECT_TIMEOUT`, `CHATADS_DB_POOL_SIZE` | Default `PT4S` and 3 in the demo profile (`PT1S` and 4 otherwise). A database that sleeps needs the longer timeout for its first connection |
| `CHATADS_CACHE_ENABLED` | `true` turns the Redis candidate cache on. Redis is not set up at all while it is `false` |
| `SPRING_DATA_REDIS_URL` | `rediss://default:<password>@<host>:6379`. The `rediss` scheme turns on TLS, and the url wins over host and port |
| `SPRING_DATA_REDIS_HOST`, `_PORT`, `_PASSWORD`, `_SSL_ENABLED` | The same thing in separate parts, if you prefer |
| `CHATADS_REDIS_TIMEOUT`, `CHATADS_REDIS_CONNECT_TIMEOUT` | Default `4s` and `5s` in the demo profile, `250ms` otherwise. The first TLS and AUTH exchange on a throttled CPU took more than 2 s in my runs, and a Redis in another region adds more |
| `CHATADS_RATE_LIMIT_PER_MINUTE` | Requests per client address per minute on `/v1/`. 30 in the demo profile, 0 (off) otherwise |
| `JAVA_OPTS` | JVM flags. The image sets them for 512 MB; replace them only with a reason |

The database url needs `sslmode=require` on Neon. The service creates the `vector` extension and its own table on startup, so there is no separate migration step and the pooled Neon host can be used for everything. A dead Redis costs each request up to the timeout, so set `CHATADS_CACHE_ENABLED=false` if the cache host is down.

The image starts the application once while it builds, sends a few requests and saves the loaded classes as a class-data archive. It runs on the serial collector with only the C1 compiler, a 512 KB thread stack and capped metaspace and code cache, which keeps start-up CPU and memory down on a slow container.

Measured on a laptop under Colima (a shared Docker VM with 2 CPUs, other builds running at the same time), with the container limited to `--memory=512m --cpus=0.1`, a local Postgres with `ssl=on` and a local Redis with TLS and a password, reached over `sslmode=require` and a `rediss://` url. The catalog is synthetic.

| Image | Result |
| :-- | :-- |
| Earlier Dockerfile (fat jar, default JIT, 70% heap), one run | Not healthy after 4.5 minutes, then killed for running out of memory |
| Current Dockerfile | Spring reported start-up in 38 to 47 s over four runs; health check up after 47 to 59 s; first request 10 to 15 s (the first TLS connections to Postgres and Redis); 48 sequential requests all returned 200 with no restart or kill; resident memory about 185 to 190 MB afterwards |
| Current Dockerfile, `--cpus=1` with CI's read-only filesystem | Healthy in under 4 s and about 108 MiB of memory after one request |

The TLS paths were tested against local servers with a private certificate authority, not against Neon or Upstash themselves. I have not run the Blueprint on Render, so real wake-up times there may differ.

## Layout

```text
src/main/java/dev/chatads/
  api/          controller, service, DTOs
  retrieval/    catalog, hybrid and keyword retrievers, Postgres index, cache, fallback
  embedding/    hashing and HTTP embedders, token metering
  policy/       banned-category filter
  ranking/      ordering and top-N
  metrics/      Micrometer counters and token/cost metrics
  llm/          price table and token usage
  eval/         deterministic checks, runner, thresholds, calibration
  eval/judge/   judge client, rubric loading, strict verdict parser
  web/          per-address rate limit
src/main/resources/   catalog.json, banned-categories.txt, llm-prices.json, application.yml,
                      application-demo.yml, static/ (landing page)
src/test/java/        unit tests and Testcontainers tests for Postgres and Redis
evals/                prompts.jsonl, thresholds.json, judge/ (rubrics and calibration set)
observability/        Prometheus config, alert rules and tests, Grafana provisioning and dashboard
k8s/                  Kubernetes manifests
render.yaml           Render Blueprint for the free hosted demo
docs/                 architecture notes
.github/workflows/    ci.yml, eval-gate.yml
```

## Roadmap

| Phase | Scope | Status |
| :-- | :-- | :-: |
| 1 | Scaffold and deterministic checks | Done |
| 2 | Hybrid retrieval and Redis cache | Done |
| 3 | LLM-as-judge and eval-gate thresholds | Done |
| 4 | Metrics dashboards (latency, token cost, fallback) | Done |
| 5 | Container image and Kubernetes manifests | Done |

## License

[MIT](LICENSE) © Daksh Shukla
