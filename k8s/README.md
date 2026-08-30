# Kubernetes manifests (ntccpay)

The Postgres **Secret is intentionally not committed**. It is created from the
same local `secrets/` files that `docker-compose.yml` uses, so local Docker and
cluster deployments share one source of truth.

## Apply order

```bash
# 1. Create the Secret from the local secret files (values never in git)
kubectl -n ntccpay create secret generic postgres-credentials \
  --from-file=username=secrets/postgres_user.txt \
  --from-file=password=secrets/postgres_password.txt \
  --from-file=database=secrets/postgres_db.txt \
  --dry-run=client -o yaml | kubectl apply -f -

# 2. Namespace
kubectl apply -f k8s/namespace.yaml

# 3. Postgres (Service + StatefulSet)
kubectl apply -f k8s/postgres.yaml

# 4. App (Deployment + Service)
kubectl apply -f k8s/app.yaml
```

## Verify

```bash
kubectl -n ntccpay get pods,services,pvc
kubectl -n ntccpay logs statefulset/postgres -f
# Talk to the DB locally:
kubectl -n ntccpay port-forward svc/postgres 5432:5432
```

## Security notes

- The app container receives DB credentials as env vars via `secretKeyRef`.
  Visible via `kubectl exec ... env` — acceptable for app processes; mount the
  Secret as files if you want the same file-based approach Postgres uses.
- Kubernetes Secrets are base64-encoded, **not encrypted at rest by default**.
  For production, enable etcd encryption at rest, restrict access with RBAC,
  and consider Sealed Secrets or the External Secrets Operator for GitOps.
- `k8s/app.yaml` references `ghcr.io/ntccpay/auth-api:latest` — replace with the
  image your CI publishes (e.g. `./gradlew bootBuildImage` output).
