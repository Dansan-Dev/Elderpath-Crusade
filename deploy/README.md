# Deploying the match server (ARCH-033–036, FEAT-008 phase 4a)

Deployment infrastructure for the headless match server (`server` module /
`ServerLauncher`) only. Explicitly **not** included in this pass, per the user's own
scoping decision: a matchmaking/lobby service, lobby UI, or reconnect/session support.
Those are FEAT-008's remaining, still-unstarted phase.

Also per the user's decision: this targets a **real Kubernetes cluster**, not fly.io.
fly.io is skipped for now — see "If fly.io comes back" below for what would need to
change to support it later.

## Files

- `deploy/server/Dockerfile` — multi-stage build: `eclipse-temurin:17-jdk` runs
  `./gradlew :server:installDist`, `eclipse-temurin:17-jre` runs the result as a
  non-root user.
- `deploy/k8s/namespace.yaml` — the `elderpath` namespace everything else lives in.
- `deploy/k8s/pool-deployment.yaml` — `Deployment` + `Service` + `HorizontalPodAutoscaler`
  for a pool of pairing-free match servers (see below).
- `deploy/k8s/job-template.yaml` — a `Job` + `Service` template, **not applied by
  anything today**, documenting the shape a future matchmaking controller would need
  to generate per matched pair of humans.

## Building the image

```sh
cd /path/to/elderpath-crusade   # repo root — the Dockerfile needs this as its build context
docker build -f deploy/server/Dockerfile -t elderpath-server:dev .
```

**This requires a real local `assets/` directory to already exist** (the one checked
out from wherever your actual game assets live — `assets/` is gitignored and was never
part of this repo's git history). The build stage copies the whole repo, including
`assets/`, and the runtime stage copies `assets/` again into the final image so
`ServerLauncher`'s `--assets assets` can read `data/pieces.yaml` etc. at startup.

This cloud sandbox has neither a real `assets/` directory nor a reachable Docker
daemon, so **this image has not been built or run here** — only the Gradle step inside
it (`./gradlew :server:installDist`) was verified directly, outside Docker, with the
project's normal test suite. Build and smoke-test the image yourself on a machine (or
CI) that has the real assets before trusting it in a cluster.

## Deploying to Oracle Cloud (OKE, Always Free) — Sprint 65

The target for this deployment: Oracle's Always Free tier is the one combination of
free Kubernetes *and* free compute that doesn't just mean "free control plane, pay for
the nodes" — an OKE Basic cluster has no control-plane fee, and Ampere A1 (**arm64**)
worker nodes are free up to the tenancy's Always Free allocation (2 OCPU / 12 GB total
as of the 2026 reduction — down from 4/24, still enough for this workload). Both
manifests here already assume this: `nodeSelector: kubernetes.io/arch: arm64`, trimmed
resource requests/limits and HPA bounds sized to fit that allocation (see
`pool-deployment.yaml`'s own comments for the arithmetic), and the pool's `Service` set
to `LoadBalancer` with annotations pinning OCI's load balancer to the flexible shape at
10 Mbps — the bandwidth Always Free's one included load balancer has historically
covered. **Verify that bandwidth figure yourself** (OCI console → Governance →
Limits, Quotas and Usage, or just check your bill after a day) before trusting it —
this doc can't see your tenancy's actual entitlements, and it's the one part of this
whole setup that could cost real money if Oracle's free allowance differs from what's
documented here.

1. **Create the cluster.** OCI Console → Developer Services → Kubernetes Clusters
   (OKE) → Create Cluster → **Quick Create**. Pick **Basic** cluster type (no
   control-plane fee) and, for the node pool, shape `VM.Standard.A1.Flex` with OCPUs/
   memory inside your Always Free allowance (e.g. 2 OCPU / 12 GB across 1–2 nodes —
   check the console's own Always Free badge/warning on the shape picker, it flags
   this directly). The quick-create wizard also provisions the VCN/subnets for you.
2. **Point `kubectl` at it.** Install the `oci` CLI, run `oci setup config` once
   (API signing key), then:
   ```sh
   oci ce cluster create-kubeconfig --cluster-id <cluster-ocid> \
       --file $HOME/.kube/config --region <region> --token-version 2.0.0
   kubectl get nodes   # should list your arm64 node(s)
   ```
3. **Build and push the image for arm64** (see the Dockerfile's own header comment
   for the exact `docker buildx` command) to OCIR, Oracle's container registry:
   ```sh
   docker buildx build --platform linux/arm64 -f deploy/server/Dockerfile \
       -t <region-key>.ocir.io/<tenancy-namespace>/elderpath-server:latest --load .
   docker login <region-key>.ocir.io -u '<tenancy-namespace>/<your-username>'
       # password: an Auth Token from OCI Console → Profile → Auth Tokens (NOT your
       # console password — OCIR never accepts that)
   docker push <region-key>.ocir.io/<tenancy-namespace>/elderpath-server:latest
   ```
   Either mark the OCIR repository **Public** (Developer Services → Container
   Registry → the repo → Repository Visibility — then no pull secret is needed at
   all) or create one and reference it via the commented-out `imagePullSecrets` in
   both manifests:
   ```sh
   kubectl create secret docker-registry ocir-pull-secret -n elderpath \
       --docker-server=<region-key>.ocir.io \
       --docker-username='<tenancy-namespace>/<your-username>' \
       --docker-password='<the same auth token>'
   ```
4. **Fill in the image placeholder** in `pool-deployment.yaml` (`your-registry/
   elderpath-server:latest` → the OCIR path above) and apply:
   ```sh
   kubectl apply -f deploy/k8s/namespace.yaml -f deploy/k8s/pool-deployment.yaml
   kubectl get svc -n elderpath elderpath-server-pool -w   # wait for EXTERNAL-IP
   ```
   Once an external IP shows up, that's the address to point a game client at, port
   `4567` — a solo player vs. SmartBot match, same as the earlier "Files" description.
5. `job-template.yaml` still isn't applied by anything (see below) — it's unaffected
   by any of this beyond the same arm64/OCIR adjustments already made to it.

## The core design tension, and how these two manifests each resolve it

`ServerLauncher` is designed around **one authoritative match per process** (see
`elderpath-architecture.md`) — the process exits once the match ends or sits idle too
long. That's a job's lifecycle, not an ordinary long-lived stateless web service, and
it doesn't fit a plain `Deployment` + `Service` cleanly: a `Service` load-balances each
new TCP connection to an arbitrary ready Pod, so two specific humans who both want to
play *each other* would likely land on two different Pods and never meet. Fixing that
needs something to pair them and hand both of them the same Pod's address — a
matchmaking layer, which is explicitly out of scope here.

Two patterns, two different fits, both included so the gap is visible rather than
hidden behind one manifest that looks complete but isn't:

- **`pool-deployment.yaml`** — the one case a plain `Deployment`+`Service`+`HPA` *does*
  correctly serve without any matchmaking: pairing-free play, where it genuinely
  doesn't matter which Pod a client lands on (a solo human vs. the built-in SmartBot,
  one per connection). Here, `ServerLauncher`'s own one-match-per-process exit *is* the
  "job" lifecycle — Kubernetes' ordinary Pod restart (`restartPolicy: Always`, implicit
  for a Deployment) recycles a finished Pod into a fresh one ready for the next
  connection, with no extra orchestration needed. This is deployable today.
- **`job-template.yaml`** — the honest shape for pairing *two specific* humans: one
  `Job` (run-to-completion, matching the real lifecycle) + one per-Job `Service` per
  matched pair. **Nothing creates these Jobs today.** A future matchmaking controller
  would need to: accept a match request, pick/generate `${MATCH_ID}`/`${SEED}`/deck
  args, apply a Job+Service from this template, and hand both clients that Service's
  address. None of that controller, nor the lobby UI to request a match in the first
  place, nor reconnect/session tokens, exist yet — all FEAT-008 phase 4b+.

## Health checks

`ServerLauncher --health-port N` (0 = disabled, the previous/test-suite default)
starts a tiny built-in-JDK HTTP server (`com.sun.net.httpserver`, no new dependency)
alongside the game's own TCP port:

- `GET /healthz/live` — 200 while the match tick loop has ticked recently (a
  heartpulse timestamp updated every tick; stale past `max(5s, 20×--tick-ms)` means
  the loop is stuck, not just that no client happens to be connected). A failing
  liveness probe should restart the Pod.
- `GET /healthz/ready` — 200 while the process is still accepting/running, 503 once
  it has decided to shut down (match ended, idle timeout, stdin closed, parent gone).
  A failing readiness probe should stop routing *new* connections here without
  killing the Pod — lets an in-progress match's existing connection finish cleanly
  while new traffic goes elsewhere.
- `GET /diag` (Sprint 64) — a JSON body of rolling tick-timing stats (`tickCount`,
  `budgetMs`, `lastTickMs`, `avgTickMs`, `maxTickMs`, `overBudgetCount`), on the same
  port whenever `--health-port` is set. This is the match-server half of "diagnose
  performance/delays" — a sustained `overBudgetCount` climb, or `avgTickMs` close to
  `budgetMs`, means this instance can't keep up with its own tick rate. There's no
  client-side equivalent exposed here — the game's own in-game debug overlay
  (`F3`, client-only, see `elderpath-architecture.md`'s "Diagnostics" section) covers
  frame time/FPS/heap/GC/music, which has nothing to do with a dedicated server.

Known limitation, stated plainly rather than implied away: these are HTTP-level
checks on a separate listener, not a check that the *game* TCP port itself is
healthy. A port left listening but failing to `accept()` wouldn't necessarily show up
here. Good enough for this pass's scope (basic liveness/readiness for scaling); a
future improvement could have `/healthz/ready` also verify the game port is still
open, or report richer state (seats filled, match phase) once that's actually needed
by something downstream (e.g. a matchmaking controller deciding which pool Pod to use).

Covered by `HealthServerTest` (unit-level, the HTTP handlers against fake
suppliers) and `ServerProcessTest.healthPort_servesLivenessAndReadinessOverHttp`
(`@RequiresAssets`, a real child-process `ServerLauncher` hit over real HTTP) —
`LaunchOptionsTest` covers the new `--health-port` flag's parsing.

## If fly.io comes back

The Docker image here is fly.io-agnostic — `fly launch`/`fly deploy` can run any
container image, including this one, via a `fly.toml` (not written here, since the
user's decision for this pass was "skip fly.io, real Kubernetes cluster"). What would
need to change: fly.io's own primitives (Fly Machines, fly-replay-based routing) stand
in for `Deployment`/`Service`/`Job`, and fly.io's health checks use `fly.toml`'s own
`[[services.http_checks]]`/`[[services.tcp_checks]]` blocks pointed at the same
`/healthz/live`/`/healthz/ready` endpoints and the same game port — those are
unaffected by hosting choice. The underlying one-match-per-process vs.
pairing-two-specific-humans tension above is identical on fly.io; nothing about
switching hosts solves it either.
