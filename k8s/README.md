# Kubernetes deployment

The manifests target Kubernetes 1.30. The app runs two replicas with a 512Mi memory limit per replica. Postgres and Redis are separate services. The image runs as user 10001 with a read-only root filesystem and writable temporary storage at `/tmp`.

## Apply order

Run these commands from the repository root against your intended cluster context. The image must be readable by the cluster. For a private registry, configure an image pull secret on the Deployment first. For a release, replace `latest` with the published commit tag or an image digest.

```sh
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml
export CHATADS_DB_PASSWORD='set-a-local-password'
kubectl -n chat-ads create secret generic chat-ads-secrets \
  --from-literal=CHATADS_DB_PASSWORD="$CHATADS_DB_PASSWORD" \
  --from-literal=CHATADS_EMBEDDING_API_KEY=unused-for-hash
unset CHATADS_DB_PASSWORD
```

`secret.example.yaml` documents the keys only. Keep the real secret outside version control. Use the same database password on the Postgres service. The `hash` embedding provider needs no API key.

For a local cluster, start the disposable dependencies before the app:

```sh
kubectl apply -f k8s/dev-deps.yaml
kubectl -n chat-ads rollout status deployment/postgres --timeout=180s
kubectl -n chat-ads rollout status deployment/redis --timeout=180s
kubectl apply -k k8s
kubectl -n chat-ads rollout status deployment/chat-ads-eval --timeout=180s
kubectl -n chat-ads port-forward service/chat-ads-eval 8080:80
```

`dev-deps.yaml` is for local clusters only. Postgres data is lost when its pod is removed. Redis persistence is disabled. Use separately managed dependencies for production. Kustomize includes the app, namespace and config. It excludes the secret example and development dependencies.

In another terminal:

```sh
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS http://127.0.0.1:8080/v1/ads/contextual \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"running shoes for a marathon"}'
```

Stop port forwarding with Ctrl-C. Remove local resources with `kubectl delete namespace chat-ads` when finished.

## Configuration

Change `configmap.yaml` for service addresses, cache settings and the embedding provider. `CHATADS_DB_URL` points to `postgres:5432`; Redis uses `redis:6379`. Both names resolve inside the `chat-ads` namespace. Without `CHATADS_DB_URL`, the service uses in-memory keyword retrieval. Restart the Deployment after changing config or secrets:

```sh
kubectl -n chat-ads rollout restart deployment/chat-ads-eval
```

External embedding endpoints also need `CHATADS_EMBEDDING_BASE_URL`, `CHATADS_EMBEDDING_MODEL` and, when required, `CHATADS_EMBEDDING_DIMENSIONS` in the ConfigMap. Store their API key in `chat-ads-secrets`.

## Prometheus scraping

Pod annotations expose the scrape path and port. Prometheus must have pod discovery and permission to list and watch pods in this namespace. Annotations alone do not enable scraping. Add this scrape configuration to an existing Prometheus instance with that access:

```yaml
scrape_configs:
  - job_name: chat-ads
    kubernetes_sd_configs:
      - role: pod
        namespaces:
          names: [chat-ads]
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
        action: keep
        regex: "true"
      - source_labels: [__meta_kubernetes_pod_container_port_name]
        action: keep
        regex: http
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_path]
        target_label: __metrics_path__
      - source_labels: [__address__, __meta_kubernetes_pod_annotation_prometheus_io_port]
        regex: '([^:]+)(?::\d+)?;(\d+)'
        replacement: '$1:$2'
        target_label: __address__
```

## Validation

CI validates the resource manifests against Kubernetes 1.30 schemas in strict mode. Kustomization is a client-side file and is excluded from the resource schema check. To run the same check locally:

```sh
docker run --rm -v "$PWD/k8s:/k8s:ro" ghcr.io/yannh/kubeconform:v0.6.7 \
  -strict -summary -kubernetes-version 1.30.0 \
  /k8s/namespace.yaml /k8s/configmap.yaml /k8s/secret.example.yaml \
  /k8s/deployment.yaml /k8s/service.yaml /k8s/dev-deps.yaml
kubectl apply --dry-run=client -k k8s
```

The client dry run needs a reachable cluster for API discovery.

Local verification passed on a disposable Kubernetes 1.30.0 cluster. The client dry run and apply succeeded, both app replicas and the development dependencies became ready, and HTTP checks through the Service returned health status `UP` and a labeled sponsored item. The test used the locally built image with an image name override and `IfNotPresent` pull policy. The cluster was removed after the checks. Registry publishing still needs a CI run on a push to `main`.
