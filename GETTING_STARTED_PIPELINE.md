# Getting started with the release tracker pipeline

This guide gets you from zero to a first run of the release tracker workflow pipeline.

---

## 1. Prerequisites

| Requirement | Details |
|-------------|--------|
| **Jenkins** | With Pipeline and Job DSL plugins. Your Jenkins: **http://10.0.188.125/** (or :8080 if Jenkins runs on that port). |
| **Agent** | A node with label `teuthology-agent` (or change the label in the Jenkinsfile). That agent needs: `teuthology` installed (with venv), `getUpstreamBuildDetails.py`, network access to Shaman, Paddles, and Redmine. |
| **Redmine API key** | For posting notes to the tracker. Get it at [tracker.ceph.com → My account](https://tracker.ceph.com/my/account). |
| **Git repo** | A repo Jenkins can clone that will contain the pipeline (e.g. your fork of `ceph-jenkins` or this workspace). |

---

## 2. Add the pipeline to your repo

You need two things in your repo:

1. **Job definition** (so Jenkins creates the job)  
2. **Pipeline script** (the actual workflow)

### Option A: Use Job DSL (recommended)

Your Jenkins already has a **seed job** that runs `jobs/*.groovy` from a repo. Do this:

1. **Put the Job DSL file in the repo**  
   Copy (or add) this file into your repo as **`jobs/release_tracker_workflow.groovy`**:

   - It defines a pipeline job named `release_tracker_workflow`.
   - It points the job’s Pipeline script to `jobs/pipelines/release_tracker_workflow/Jenkinsfile` from the **same repo**.

2. **Put the Jenkinsfile and scripts in the repo**  
   Under **`jobs/pipelines/release_tracker_workflow/`** you need:

   - `Jenkinsfile` – the pipeline (stages: checkout, approval, build, wait Shaman, schedule suite, aggregate, post Redmine, draft notes).
   - `scripts/redmine_post_note.sh` – posts the note to Redmine (optional; pipeline can fall back to inline curl/Python).

   Under **`scripts/`** at repo root (or next to the Jenkinsfile):

   - `wait_for_shaman_sha1.py` – polls Shaman until the SHA1 is ready.
   - `aggregate_suite_results.py` – fetches job results from Paddles and builds the pass/fail table.

3. **Point the seed job at your repo**  
   In Jenkins, open the seed job. Set:

   - **REPO_URL** = your repo (e.g. `https://github.com/vamahaja/ceph-jenkins.git` or the one that contains the files above).
   - **BRANCH_NAME** = branch where you added the files (e.g. `main`).

4. **Run the seed job**  
   Run the seed job once. It will create/update the **release_tracker_workflow** job from `jobs/release_tracker_workflow.groovy`.

### Option B: Create the job manually

If you don’t use Job DSL:

1. In Jenkins: **New Item** → **Pipeline**.
2. Name: `release_tracker_workflow`.
3. Under **Pipeline**:
   - **Definition**: “Pipeline script from SCM”.
   - **SCM**: Git.
   - **Repository URL**: your repo.
   - **Script Path**: `jobs/pipelines/release_tracker_workflow/Jenkinsfile` (must exist in that repo).

Save. The first run will use whatever is in the repo at that path.

### Option C: Test locally without pushing to any repo

Use your **local workspace** as the pipeline source so you can test on Jenkins at http://10.0.188.125/ before pushing to GitHub.

**1. Make your workspace a Git repo (if it isn’t already)**  
In your workspace directory (e.g. `daily_smoke_suite`):

```bash
cd /home/ubuntu/workspace/daily_smoke_suite   # or your actual path
git init
git add .
git commit -m "WIP: release tracker pipeline"
```

**2. Let Jenkins see that folder**  
- If Jenkins runs in **Podman/Docker** (your `deploy.sh`): the script already mounts the repo into the container as `/workspace/daily_smoke_suite` (`-v $(pwd):/workspace/daily_smoke_suite:ro`). Run `deploy.sh` from inside your workspace so `$(pwd)` is the pipeline repo. If Jenkins is already running without that mount, stop it, add that line to `scripts/deploy.sh` if missing, and run `deploy.sh` again.
- If Jenkins runs **on the same host** (not in a container), use a path the Jenkins process can read in step 3 (e.g. `file:///home/ubuntu/workspace/daily_smoke_suite`).

**3. Create the job in Jenkins (no Seed, no remote repo)**  
1. Open **http://10.0.188.125/** (or :8080) → **New Item**.  
2. Name: `release_tracker_workflow` → **Pipeline** → **OK**.  
3. Under **Pipeline**:
   - **Definition**: “Pipeline script from SCM”.
   - **SCM**: **Git**.
   - **Repository URL**: `file:///workspace/daily_smoke_suite` (path **inside** the container; if you didn’t mount, use the path the Jenkins process can read, e.g. `file:///home/ubuntu/workspace/daily_smoke_suite` on the host).
   - **Branch**: `*/*` or `main` (or your current branch).
   - **Script Path**: `jobs/pipelines/release_tracker_workflow/Jenkinsfile`.
4. **Save**.

If Jenkins refuses `file://` (e.g. “Repository URL is blocked”), you may need to allow local repos: **Manage Jenkins** → **Security** → **Script Security** / “In-process Script Approval”, or set the system property `-Dhudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT=true` and restart Jenkins.

**4. Run a safe test**  
- **Build with Parameters** → check **SKIP_BUILD**, **SKIP_INTEGRATION_TESTS**, **SKIP_TRACKER_UPDATE** → **Build**.  
- When it pauses at **Approval gate**, open the run and click **Proceed**.  
- Check **Build Artifacts** for `tracker_note.txt` and `release_notes_draft.md`.

After you’re happy, push the same repo to GitHub and switch this job to the remote URL (or use Seed) so others can use it.

---

## 3. Add the Redmine credential in Jenkins

1. **Jenkins** → **Manage Jenkins** → **Credentials** → **(domain)** → **Add Credentials**.
2. **Kind**: Secret text.  
3. **Secret**: your Redmine API key (from tracker.ceph.com).  
4. **ID**: **`redmine-api-key`** (the pipeline expects this exact ID).  
5. Save.

---

## 4. How to test on your Jenkins (http://10.0.188.125/)

Steps below use **http://10.0.188.125/** as the Jenkins URL. If Jenkins is on port 8080, use **http://10.0.188.125:8080/** instead.

### Get the job in Jenkins

1. Open Jenkins in your browser: **http://10.0.188.125/** (or http://10.0.188.125:8080/ if that’s where it runs).
2. Run the **Seed** job once (so the release tracker job is created):
   - Click **Seed** in the job list.
   - Click **Build with Parameters**.
   - **REPO_URL**: repo that has the pipeline, e.g. `https://github.com/vamahaja/ceph-jenkins.git`.
   - **BRANCH_NAME**: e.g. `main`.
   - Click **Build**. Wait until the build finishes (check Build History).
3. Go back to the **Dashboard**. You should see **release_tracker_workflow**. Click it.

### Run a safe test (no build, no suites, no Redmine)

1. In **release_tracker_workflow**, click **Build with Parameters**.
2. Set:
   - **SKIP_BUILD**: ✓ (checked)
   - **SKIP_INTEGRATION_TESTS**: ✓ (checked)
   - **SKIP_TRACKER_UPDATE**: ✓ (checked)
   - Other parameters can stay default.
3. Click **Build**.
4. In **Build History**, click the new run (e.g. **#1**). The pipeline will do **Checkout and Resolve SHA1**, then stop at **Approval gate**.
5. On that run page, click **Proceed** (or **Input required** → **Proceed**) to continue.
6. When it’s done, click **Build Artifacts** (or the run’s left sidebar). You should see **tracker_note.txt** and **release_notes_draft.md**. The build should show as **Success** (blue).

If **release_tracker_workflow** doesn’t appear after Seed: ensure `jobs/release_tracker_workflow.groovy` exists in the repo at **REPO_URL** on **BRANCH_NAME**, then run **Seed** again.

---

## 5. How to test (step by step, by feature)

Use these runs to verify the pipeline and the three features (Shaman, email, live suite list) without affecting production.

### 5.1 Fastest smoke test (no build, no suites, no tracker)

**Goal:** Confirm job runs, checkout works, approval gate appears, artifacts are created.

1. **Build with Parameters**, set:
   - **SKIP_BUILD**: ✓  
   - **SKIP_INTEGRATION_TESTS**: ✓  
   - **SKIP_TRACKER_UPDATE**: ✓  
2. Click **Build**. When it pauses at **Approval gate**, click **Proceed**.
3. **Check:** Run finishes; **Tag and artifacts** produces `tracker_note.txt` and `release_notes_draft.md`; no Redmine post, no email (unless you set EMAIL_RECIPIENTS).

---

### 4.2 Test email

**Goal:** Confirm email is sent when the run finishes.

1. Set **EMAIL_RECIPIENTS** to your address (e.g. `you@example.com`).
2. Use the same safe run as 5.1: **SKIP_BUILD** ✓, **SKIP_INTEGRATION_TESTS** ✓, **SKIP_TRACKER_UPDATE** ✓.
3. Click **Build** → **Proceed** at the gate.
4. **Check:** After the run, you receive one email with subject like `RC testing release_tracker_workflow #N - SUCCESS` and body with build URL and a short summary.

**Note:** Jenkins must be able to send mail (built-in Mailer or SMTP configured). If mail fails, the pipeline logs “Email failed: …” and continues.

---

### 5.3 Test live suite list (SUITE_LIST_SOURCE)

**Goal:** Confirm the pipeline reads a list of suites from a file (or URL) and runs each.

1. **Add a suite list file in your repo**, e.g. `config/suites_test.yaml` in the same repo as the Jenkinsfile:
   ```yaml
   suites:
     - smoke
   ```
   Or one suite per line (e.g. `smoke` only for a quick test).
2. **Build with Parameters**, set:
   - **SUITE_LIST_SOURCE**: `config/suites_test.yaml` (relative to workspace).
   - **SKIP_BUILD**: ✓ (optional; or run a real build).
   - **SKIP_TRACKER_UPDATE**: ✓.
3. Run the job and **Proceed** at the gate. The **Resolve suite list** stage should log “Suites to run: smoke”; **Schedule suites** runs that one suite (and waits for Shaman if you didn’t skip integration tests).
4. **Check:** Console shows “Resolve suite list” and “Suites to run: …”; if integration tests run, you get at least one teuthology run and an aggregate table.

To test with a **URL**: host the same content somewhere and set **SUITE_LIST_SOURCE** to that URL (e.g. `https://raw.githubusercontent.com/.../suites_test.yaml`).

---

### 4.4 Test Shaman path (full integration, no tracker post)

**Goal:** Confirm build → wait for Shaman → schedule with `--sha1` works.

1. **Build with Parameters**, set:
   - **SKIP_BUILD**: ☐ (run the build).
   - **SKIP_INTEGRATION_TESTS**: ☐ (run Wait for Shaman + Schedule suites).
   - **SKIP_TRACKER_UPDATE**: ✓ (don’t post to Redmine).
   - **SUITE_NAME**: `smoke` (or leave default).
   - **SUITE_LIST_SOURCE**: leave empty (single suite).
2. Click **Build** → **Proceed** at the gate.
3. **Check:**  
   - **Build** stage triggers `sample-ceph-pipeline` with the resolved SHA1.  
   - **Wait for Shaman** runs `wait_for_shaman_sha1.py` until that SHA1 appears (or times out).  
   - **Schedule suites** runs `teuthology-suite … --sha1 <SHA1>`.  
   - **Aggregate results** produces `aggregate_table.txt`; **Tag and artifacts** archives it and `tracker_note.txt`.

If `wait_for_shaman_sha1.py` or the teuthology agent isn’t on the node, those steps may fail; the pipeline still shows the intended flow.

---

### 5.5 Full run (real release, tracker + optional email)

After 5.1–5.4 look good:

1. Set **TRACKER_ISSUE_ID** to the real tracker (e.g. `72316`), **SKIP_TRACKER_UPDATE**: ☐.
2. Optionally set **EMAIL_RECIPIENTS** and/or **SUITE_LIST_SOURCE**.
3. Run the job; **Proceed** at the gate. The pipeline will post the note to Redmine and send email if recipients are set.

---

## 6. First run (safe / dry run) — quick reference

To test without touching the tracker or running heavy steps:

1. Open **release_tracker_workflow** → **Build with Parameters**.
2. Set **SKIP_TRACKER_UPDATE**: ✓ and optionally **SKIP_BUILD** ✓, **SKIP_INTEGRATION_TESTS** ✓.
3. Click **Build** → **Proceed** at the approval gate.

The run will execute the enabled stages only; no Redmine post when SKIP_TRACKER_UPDATE is true.

---

## 7. Full run (for a real release)

1. In the repo, ensure the **branch** you use (e.g. `reef`) has the commit you want to release (or the pipeline will use the tip of that branch).
2. Run **release_tracker_workflow** with parameters for the release, e.g.:
   - **TRACKER_ISSUE_ID**: `73906` (reef v18.2.8) or `72316` (tentacle 20.1.0).
   - **CEPH_BRANCH**: `reef` or `tentacle`.
   - **RELEASE_VERSION**: `18.2.8` or `20.1.0`.
   - **SKIP_TRACKER_UPDATE**: **false** (so it posts to the tracker).
3. When the pipeline pauses at **Approval gate**, get lead approvals offline, then click **Proceed**.
4. The pipeline will: build (if not skipped), wait for Shaman (if scripts are present), run the suite, aggregate results, post a note to the tracker with build link + Pulpito link + pass/fail table, and archive draft release notes.

---

## 8. Where things live (summary)

| What | Where |
|------|--------|
| Job DSL | `jobs/release_tracker_workflow.groovy` in your repo |
| Pipeline script | `jobs/pipelines/release_tracker_workflow/Jenkinsfile` in your repo |
| Redmine script | `jobs/pipelines/release_tracker_workflow/scripts/redmine_post_note.sh` |
| Wait for Shaman | `scripts/wait_for_shaman_sha1.py` (repo root or `scripts/`) |
| Aggregate results | `scripts/aggregate_suite_results.py` |
| Tracker / params | See [TRACKER_RELEASE_REFERENCES.md](TRACKER_RELEASE_REFERENCES.md) |

If any of these files are missing in your repo, add them (from this workspace or from the snippets in the plan) so the pipeline can find them when it runs.
