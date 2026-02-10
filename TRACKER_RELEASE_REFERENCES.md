# Tracker release references (Approach A pipeline)

The release-tracker workflow pipeline is parameterized so one job can drive multiple Redmine release trackers. Use **TRACKER_ISSUE_ID**, **CEPH_BRANCH**, and **RELEASE_VERSION** to target a specific release.

---

## Trackers

Set **TRACKER_ISSUE_ID** to the Redmine issue ID for the release you are testing (e.g. from tracker.ceph.com). No default is set; you must supply the ID when you want to post to a tracker.

**Example parameters for a release:**
- `TRACKER_ISSUE_ID` = issue number (e.g. 72316, 73906)
- `CEPH_BRANCH` = branch (e.g. tentacle, reef)
- `RELEASE_VERSION` = version (e.g. 20.1.0, 18.2.8)

All other parameters (CEPH_REPO, BUILD_JOB_NAME, SKIP_*) stay as default unless you need to override. The pipeline will post build + suite links and draft release notes to the chosen tracker issue when **SKIP_TRACKER_UPDATE** is false and **TRACKER_ISSUE_ID** is set.

---

## Flow alignment with tracker

Your flow matches how the tracker release process is intended to work. Comparison:

| Step | Your flow | Tracker | Current pipeline |
|------|-----------|---------------------------|------------------|
| **1** | SHA1 | “Current SHA1: see in note below” / branch + commit | ✅ Resolve SHA from branch checkout |
| **2** | branch/ceph-ci ref | “branch to build from: reef” (ref = branch or SHA) | ✅ CEPH_BRANCH (+ optional CEPH_SHA1 on build) |
| **3** | trigger/ensure build for SHA1 | “Someone creates the packages” → Shaman builds (Build 1, 2, …) | ✅ Trigger Jenkins build with CEPH_SHA1. ⚠️ Build must publish to Shaman for next step |
| **4** | wait until Shaman ready | Builds listed with Shaman URLs (e.g. shaman.ceph.com/…/9191375ab…) | ❌ **Missing.** We wait for Jenkins build only; we don’t poll Shaman for this SHA1 |
| **5** | schedule suites | smoke, rados, rgw, fs, orch, rbd, krbd, upgrade/… (Pulpito links) | ✅ Schedule teuthology suites. ⚠️ Today we use `getUpstreamBuildDetails(branch)` = *latest* on branch, not necessarily *our* build’s SHA1 |
| **6** | poll Paddles for completion | Runs show as PASSED/FAILED per suite | ✅ `teuthology-wait` polls Paddles until run completes |
| **7** | aggregate pass/fail | Tracker table: smoke PASSED, rados FAILED, … | ❌ **Missing.** We post run links but don’t aggregate per-suite pass/fail |

So your flow is **in line with what the tracker does**; the current pipeline is only **partially** aligned. To match it fully we’d add:

1. **Wait until Shaman ready**  
   After “Build”, poll until the **same SHA1** appears on Shaman for the branch/platform (e.g. `getUpstreamBuildDetails.check_sha_exists_in_platform(branch, platform, sha1)` in a loop with backoff). Depends on your Jenkins/build system publishing that build to Shaman.

2. **Schedule suites using *that* SHA1**  
   Use the SHA1 we built (and confirmed on Shaman) as `--sha1` for `teuthology-suite`, instead of “latest for branch”.

3. **Aggregate pass/fail**  
   After `teuthology-wait`, query Paddles (e.g. `ResultsReporter().get_jobs(run_name, fields=['description','status'])`) and group by suite; output (and optionally post to tracker) a table: smoke → pass/fail, rados → pass/fail, etc., like the tracker’s QE table.

---

## Shaman: use SHA1 end-to-end (fast and reliable)

Shaman uses the **git SHA1** as the build id (e.g. `shaman.ceph.com/.../9191375ab5b1e911954d9f7c2b9cd180c08dc881/`). So we can use **one identifier (SHA1)** for: trigger build → wait for Shaman → schedule suites. No separate “Shaman build ID” needed; whatever is faster and reliable = **SHA1**.

- **After your build** (Jenkins or other) produces a commit SHA1 and publishes to Shaman, **wait until that SHA1 appears** on Shaman, then **pass that same SHA1** to `teuthology-suite --sha1 <sha1>`.

A small helper script is provided so the pipeline can “wait until Shaman ready” by SHA1:

| Script | Purpose |
|--------|--------|
| **`scripts/wait_for_shaman_sha1.py`** | Polls Shaman until the given SHA1 exists for branch/platform(s). Exits 0 when ready, 1 on timeout. Prints the SHA1 so you can use it for `teuthology-suite --sha1`. |

**Usage (from pipeline or shell):**
```bash
# Wait up to 1 hour, poll every 60s; default platforms: ubuntu-jammy-default,centos-9-default
python3 scripts/wait_for_shaman_sha1.py --branch reef --sha1 9191375ab5b1e911954d9f7c2b9cd180c08dc881

# Custom timeout and platforms
python3 scripts/wait_for_shaman_sha1.py --branch reef --sha1 abc123 --timeout 7200 --platform centos-9-default
```

Then use that SHA1 when scheduling:
```bash
teuthology-suite --suite smoke --ceph reef --sha1 9191375ab5b1e911954d9f7c2b9cd180c08dc881 ...
```

If your release builds go under a different Shaman ref (e.g. `reef-release`), pass it as `--branch reef-release`. The script only talks to Shaman’s “latest” API; it does not require teuthology to be installed.
