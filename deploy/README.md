# Deploying the match server (ARCH-033–036, FEAT-008 phase 4a)

Deployment infrastructure for the headless match server (`server` module /
`ServerLauncher`) only. Explicitly **not** included in this pass, per the user's own
scoping decision: a matchmaking/lobby service, lobby UI, or reconnect/session support.
Those are FEAT-008's remaining, still-unstarted phase.

Originally targeted a **real Kubernetes cluster** (Oracle Cloud's Always Free OKE), not
fly.io, per the user's own decision. A fly.io path was added later (Sprint 70) as a
fallback after actually hitting Oracle's Always Free Ampere A1 capacity exhaustion in
practice — see "Deploying to fly.io" below.

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
- `deploy/fly/fly.toml` — fly.io equivalent of the two Kubernetes manifests above, for
  when Kubernetes hosting isn't available (see "Deploying to fly.io" below).

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

Start-to-finish: creating the free account through an actually-reachable dedicated
server. Oracle's Always Free tier is the one combination of free Kubernetes *and*
free compute that doesn't just mean "free control plane, pay for the nodes" — an OKE
Basic cluster has no control-plane fee, and Ampere A1 (**arm64**) worker nodes are
free up to the tenancy's Always Free allocation (2 OCPU / 12 GB total as of the 2026
reduction — down from 4/24, still enough for this workload). Both manifests here
already assume this: `nodeSelector: kubernetes.io/arch: arm64`, trimmed resource
requests/limits and HPA bounds sized to fit that allocation (see
`pool-deployment.yaml`'s own comments for the arithmetic), and the pool's `Service`
set to `LoadBalancer` with annotations pinning OCI's load balancer to the flexible
shape at 10 Mbps — the bandwidth Always Free's one included load balancer has
historically covered. **Verify that bandwidth figure yourself** (OCI console →
Governance → Limits, Quotas and Usage, or just check your bill after a day) before
trusting it — this doc can't see your tenancy's actual entitlements, and it's the one
part of this whole setup that could cost real money if Oracle's free allowance
differs from what's documented here.

### 0. Create the Always Free account

1. Go to Oracle's cloud signup page and start a free account. You'll need: an email
   address, a phone number (for SMS verification), and **a credit/debit card** — Oracle
   requires one even for the Always Free tier, for identity verification. It is not
   charged unless you explicitly upgrade to a paid tier later; several people have
   reported small temporary authorization holds during signup that are reversed
   automatically, which is normal.
2. **Pick your home region carefully.** This is the one choice in the whole signup
   flow you can't change afterward for this tenancy. Pick whichever region is
   geographically closest to you/your players — it's where "Always Free" resources
   actually need to be created (some regions don't have Ampere A1 capacity at all;
   if your first choice turns out to be short on free-tier ARM capacity later, see the
   capacity note in step 3, you may end up wishing you'd picked a different one, but
   you can't undo the region choice, only work around it).
3. Verify your email, fill in the account/billing details, verify the phone number
   and card, and wait for the tenancy to finish provisioning (usually a few minutes,
   OCI emails you once it's ready). Log into the OCI Console.
4. Before building anything, sanity-check your Always Free entitlements actually
   match what this guide assumes: **Console → Governance & Administration → Limits,
   Quotas and Usage** → filter for "Compute" (look for the `VM.Standard.A1.Flex`
   shape's OCPU/memory limit) and "Load Balancer". If these numbers differ from
   "2 OCPU / 12 GB" and "one 10 Mbps flexible LB" assumed throughout this doc and the
   manifests, adjust `pool-deployment.yaml`'s resources/HPA/LB annotations to match
   *your* actual numbers before applying anything.

### 1. Install the `oci` CLI and point it at your tenancy

```sh
# macOS/Linux installer (see Oracle's docs for Windows):
bash -c "$(curl -L https://raw.githubusercontent.com/oracle/oci-cli/master/scripts/install/install.sh)"
oci setup config   # generates an API signing key pair + ~/.oci/config; follow its prompts
```
`oci setup config` either uploads the generated public key to your OCI user for you,
or tells you to paste it in yourself: **Console → Profile icon (top right) → User
Settings → API Keys → Add API Key**. Confirm it worked:
```sh
oci iam region list   # should print a table, not an auth error
```

### 2. Create the OKE cluster

1. **Console → Developer Services → Containers & Artifacts → Kubernetes Clusters
   (OKE) → Create Cluster → Quick Create.**
2. Name it, pick **Basic** cluster type (no control-plane fee — this is the whole
   point), **Public** Kubernetes API endpoint (simplest to start with; a Private
   endpoint needs a bastion/VPN, out of scope here), and **Public** worker nodes.
3. Node pool: shape **`VM.Standard.A1.Flex`**, OCPUs/memory inside your Always Free
   allowance from step 0.4 (e.g. 2 OCPU / 12 GB as a single node to start — you can
   add more nodes later within the same allowance). The shape picker flags Always
   Free eligibility directly; trust that over anything written here if they disagree.
4. Click **Create Cluster** and wait (typically 5–15 minutes). The quick-create
   wizard provisions the VCN/subnets/load balancer subnet for you — nothing else to
   configure there.
5. **Known Always Free hiccup:** Ampere A1 capacity is sometimes exhausted in a given
   region/availability domain, surfacing as an "out of host capacity" error on the
   node pool. If you hit this, retrying (sometimes repeatedly, sometimes hours/days
   later) or trying a different availability domain within the same region are the
   usual workarounds — there's no paid fast-path around it on Always Free.

### 3. Point `kubectl` at the new cluster

Console → your cluster's detail page → **Access Cluster** button → it gives you the
exact command for your cluster (same shape as this):
```sh
oci ce cluster create-kubeconfig --cluster-id <cluster-ocid> \
    --file $HOME/.kube/config --region <region> --token-version 2.0.0
kubectl get nodes -o wide   # should list your node(s), ready, arch=arm64
```

### 4. Get the real game assets and this repo onto a machine with Docker

`assets/` (`data/pieces.yaml`, `abilities.yaml`, `spells.yaml`, images, audio, fonts,
...) is gitignored and was never in this repo's history — you need the real directory
on whatever machine builds the image, from wherever your own copy of it lives. Clone
this repo there too if it isn't already.

### 5. Set up OCIR (Oracle's container registry) and an auth token

1. Note your **region key** (e.g. `iad` for Ashburn — the OCIR hostname is
   `<region-key>.ocir.io`) and **tenancy namespace** (Console → Profile icon →
   Tenancy: `<name>` — the namespace is shown on that page, not the tenancy name
   itself).
2. Generate an auth token: **Console → Profile icon → User Settings → Auth Tokens →
   Generate Token.** Copy it immediately — OCI shows it exactly once and never again.
3. `docker login <region-key>.ocir.io -u '<tenancy-namespace>/<your-oci-username>'`,
   paste the auth token as the password (**not** your console password — OCIR never
   accepts that).

### 6. Build and push the image for arm64

```sh
docker buildx build --platform linux/arm64 -f deploy/server/Dockerfile \
    -t <region-key>.ocir.io/<tenancy-namespace>/elderpath-server:latest --push .
```
(`--push` builds straight to the registry — simplest when cross-building. If you're
building natively on an arm64 host, e.g. an Apple Silicon Mac, `--load` then a
separate `docker push` works too and skips the cross-build step entirely.)

Cross-building `linux/arm64` from an x86_64 host runs under QEMU emulation and is
noticeably slower for a full Gradle build than a native build — budget extra time (or
do this step on an arm64 machine if one's available, even a temporary cloud VM).

Either mark the OCIR repository **Public** (Developer Services → Container Registry
→ the repo → **Repository Visibility** — then no pull secret is needed at all, the
simplest option for a first attempt) or keep it private and create a pull secret,
referenced via the commented-out `imagePullSecrets` in both manifests:
```sh
kubectl create secret docker-registry ocir-pull-secret -n elderpath \
    --docker-server=<region-key>.ocir.io \
    --docker-username='<tenancy-namespace>/<your-oci-username>' \
    --docker-password='<the same auth token>'
```

### 7. Apply the manifests

Edit `deploy/k8s/pool-deployment.yaml` locally — replace the `image:` placeholder
(`your-registry/elderpath-server:latest`) with the real OCIR path from step 6. (This
is a personal, account-specific edit — don't commit your own OCIR path back to this
repo.) Then:
```sh
kubectl apply -f deploy/k8s/namespace.yaml -f deploy/k8s/pool-deployment.yaml
kubectl get pods -n elderpath -w          # wait for Running (startupProbe can take ~30s)
kubectl get svc -n elderpath elderpath-server-pool -w   # wait for EXTERNAL-IP
```
If a pod sits in `ImagePullBackOff`, it's almost always the repo-visibility/pull-secret
step above; `kubectl describe pod -n elderpath <pod>` says exactly which.

### 8. Verify it's actually healthy (optional, but worth doing once)

The Service only forwards the game port (4567) — `/healthz/*` and `/diag` are
reachable from inside the cluster (that's all a liveness/readiness probe needs), not
through the LoadBalancer's external IP. To check them yourself from outside:
```sh
kubectl port-forward -n elderpath <pod-name> 8080:8080
curl http://localhost:8080/healthz/live    # OK
curl http://localhost:8080/diag            # tick-timing JSON
```

### 9. Connect to it

**The shipped graphical game has no "join a remote server by address" screen yet**
(FEAT-008's still-pending lobby/IP-entry UI, see "The core design tension" below) —
don't go looking for one in the game's menus. The one thing that can connect to an
arbitrary host/port today is the headless CLI playtest client (`cli` module,
FEAT-009), built for exactly this kind of testing:
```sh
./gradlew :cli:run --args="--mode join --host <EXTERNAL-IP> --port 4567 --assets /path/to/assets"
```
That gets you a real P1-vs-SmartBot match against the pod on OKE, played out as text
in your terminal — proof the whole chain (OKE → LoadBalancer → pod → `MatchServer`)
actually works, even without a graphical client to point at it yet.

`job-template.yaml` still isn't applied by anything (see below) — unaffected by any
of this beyond the same arm64/OCIR adjustments already made to it.

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

## Deploying to fly.io (Sprint 70 — fallback for Oracle A1 capacity exhaustion)

Added as a parallel/fallback track after actually hitting Oracle's Always Free Ampere
A1 "out of host capacity" error in `eu-stockholm-1` (a single-AD region — no other AD
in the same region to fall back to). fly.io has **no free tier** (unlike Oracle's
intended-to-be-free OKE path) — it's pay-as-you-go from account creation, and a
**stopped Machine costs $0**, so with `ServerLauncher`'s existing idle-timeout/match-end
exit behavior unchanged, real monthly cost for hobby-scale traffic should stay low
(a `shared-cpu-1x`/256MB Machine is roughly $2/month *while running*) but it is a real,
non-zero cost, unlike Oracle's. No capacity lottery either way, though — fly.io is
ordinary paid compute, not a scarce free allocation.

The same Docker image works unchanged — `deploy/server/Dockerfile` is fly.io-agnostic,
same as it's cloud-agnostic for Kubernetes. `deploy/fly/fly.toml` recreates both
Kubernetes manifests' patterns using fly.io's own primitives instead:

- **`pool-deployment.yaml`'s pairing-free pool** (one human vs. SmartBot, any ready
  instance will do) → Fly Machines + Fly Proxy. `auto_stop_machines = "stop"` +
  `auto_start_machines = true` + `min_machines_running = 0` is fly.io's answer to the
  same "a finished match's process exits, something must bring up a fresh one for the
  next player" problem Kubernetes' Pod-restart-on-exit solves — arguably a *better* fit
  here, since Fly natively scales to zero between players instead of needing restart
  recycling as a workaround.
- **`job-template.yaml`'s per-matched-pair Job+Service template** (still unwired on the
  Kubernetes side too — no matchmaking controller exists yet, see "The core design
  tension" above) → fly.io's Machines API
  ([fly.io/docs/machines](https://fly.io/docs/machines/)). A future matchmaking
  controller would call that API directly to create one Machine per matched pair (with
  `--p1-deck`/`--p2-deck`/`--seed` passed in as that Machine's own process args), hand
  both clients its address, and let it exit/self-destroy on match end exactly as
  `ServerLauncher` already does — actually *less* orchestration surface than the
  Kubernetes path, since a Fly Machine is individually addressable by design and doesn't
  need a separate per-match Service object created and torn down alongside it.

### Using it

```sh
cd /path/to/elderpath-crusade   # repo root — same build-context requirement as the Docker/K8s path
fly launch --config deploy/fly/fly.toml --no-deploy
# decline anything fly offers to attach (Postgres/Redis) — this app needs neither
fly deploy --config deploy/fly/fly.toml
```

No `--dockerfile` flag needed — `fly.toml`'s own `[build].dockerfile` already points at
`../server/Dockerfile`, relative to `fly.toml`'s own directory (`deploy/fly/`), **not**
the repo root and **not** wherever `fly` is invoked from. This was learned the hard way
(Sprint 70's actual first deploy attempt): `fly launch` initially auto-wrote a bare
`deploy/server/Dockerfile` into `[build].dockerfile`, which flyctl then resolved relative
to `fly.toml`'s directory, doubling up to `deploy/fly/deploy/server/Dockerfile` and
failing outright. If you ever re-run `fly launch` and it overwrites this line again with
a repo-root-relative path, fix it back to `../server/Dockerfile`.

`fly launch`'s first run creates the actual fly.io app (the `app = "elderpath-server"`
name in `fly.toml` must be globally unique across all of fly.io — change it if told it's
taken) and asks a couple of setup questions; every `fly deploy` after that just ships
the config as-is.

**Partially verified (Sprint 70):** the Dockerfile-path issue above was hit and fixed
against a real `fly launch`/`fly deploy` run. The rest of the config (the top-level
`[checks]` stanza for `/healthz/live`/`/healthz/ready`, kept internal rather than
publicly exposed, the same way `pool-deployment.yaml`'s Service only exposes the game
port and lets kubelet read health straight from the Pod; the `[[services]]`
auto-stop/auto-start behavior) is still unconfirmed beyond that — keep fixing forward
from whatever `fly deploy`/`fly status`/the running Machine's own behavior reports.

The underlying one-match-per-process vs. pairing-two-specific-humans tension described
above is identical on fly.io — switching hosts doesn't solve it; the still-missing
matchmaking controller is exactly as unbuilt here as it is on the Kubernetes path.
