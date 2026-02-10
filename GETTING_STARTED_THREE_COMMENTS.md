# Step-by-step: Preferred Shaman, Add email, Live read suite list

**Status:** All three are implemented in `jobs/pipelines/release_tracker_workflow/Jenkinsfile` and `jobs/release_tracker_workflow.groovy`: Shaman is the only path used for scheduling; `EMAIL_RECIPIENTS` sends mail in `post { always { } }`; `SUITE_LIST_SOURCE` (file or URL) is read in **Resolve suite list** and each suite is scheduled and aggregated.

This guide documents the three comments on the RC testing flow:

1. **Preferred route is Shaman** – Use Shaman (wait for build, then `--sha1`) as the default path.
2. **Add email** – Send an email when the run finishes (e.g. with build link + results).
3. **Live read from a set of tests for a release** – Read the list of suites to run from a config (file or URL) per release instead of a single hardcoded suite.

---

## 1. Preferred route is Shaman

**Goal:** The pipeline always uses the Shaman path: trigger build → wait until that SHA1 is on Shaman → schedule suites with `--sha1 <that_sha1>`.

### Steps

**Step 1.1** – Ensure “Wait for Shaman” runs before “Schedule suites”  
The Jenkinsfile already has this order. Confirm the **Wait for Shaman** stage runs and uses the **same SHA1** as the build (no fallback to “latest” for scheduling).

**Step 1.2** – Use Shaman SHA1 for suites  
In **Schedule suites**, pass `env.SHA1` (the commit you built and waited for) to `teuthology-suite --sha1 ${env.SHA1}`. The current pipeline does this.

**Step 1.3** – Don’t use custom repo unless explicitly chosen  
Keep the default behavior “Shaman only.” If you later add a parameter like `USE_CUSTOM_REPO_URL`, default it to `false` so the preferred route remains Shaman.

**Check:** Run the pipeline with a real build; after “Wait for Shaman” the next stage should schedule with that SHA1 and suites should install from Shaman.

---

## 2. Add email

**Goal:** When the pipeline finishes (success or failure), send an email with a short summary (build link, suite link, pass/fail if available).

### Steps

**Step 2.1** – Add a parameter for recipients  
In the pipeline parameters, add:

- `EMAIL_RECIPIENTS` (string, optional): comma-separated addresses to notify. Leave empty to skip email.

**Step 2.2** – Add a “Send email” stage or post action  
Two options:

- **Option A – Jenkins Mailer / Extended Email:**  
  - Install the “Email Extension” (or “Mailer”) plugin if not already.  
  - In the pipeline `post` block, add something like:

    ```groovy
    post {
        always {
            script {
                if (params.EMAIL_RECIPIENTS?.trim()) {
                    emailext(
                        to: "${params.EMAIL_RECIPIENTS}",
                        subject: "RC testing flow: ${env.JOB_NAME} #${env.BUILD_NUMBER} - ${currentBuild.currentResult}",
                        body: "Build: ${env.BUILD_URL}\nResult: ${currentBuild.currentResult}\n\nSee tracker note for details.",
                        mimeType: 'text/plain'
                    )
                }
            }
        }
    }
    ```

- **Option B – Shell script (e.g. `mail` or `sendmail`):**  
  On the agent, run a script that sends mail (e.g. `echo "Body" | mail -s "Subject" recipient@example.com`). Use `params.EMAIL_RECIPIENTS` and the same summary.

**Step 2.3** – Include useful content in the body  
At minimum: build URL, result (SUCCESS/FAILURE), and “See tracker note for details.” Optionally append the first few lines of `tracker_note.txt` or the aggregate table if available.

**Check:** Run the pipeline with `EMAIL_RECIPIENTS` set; after the run, the inbox should receive one email per build.

---

## 3. Live read from a set of tests for a particular release

**Goal:** The list of suites to run (e.g. smoke, rados, rgw, fs) is read at runtime for the given release (e.g. from a file or URL), not hardcoded as a single `SUITE_NAME`.

### Steps

**Step 3.1** – Define where the list lives  
Choose one (or both and prefer one):

- **File in repo:** e.g. `config/suites_reef_18.2.8.yaml` or `config/suites_${branch}_${version}.yaml` with content like:

  ```yaml
  suites:
    - smoke
    - rados
    - rgw
  ```

- **URL:** e.g. `https://your-server/config/suites?branch=reef&version=18.2.8` returning the same structure or a simple list.

**Step 3.2** – Add a parameter for the source  
In the pipeline parameters, add:

- `SUITE_LIST_SOURCE` (string, optional): path to a file (relative to workspace after checkout) or URL. Examples: `config/suites_reef_18.2.8.yaml`, `https://...`.  
- If empty, fall back to existing `SUITE_NAME` (single suite) so current behavior is unchanged.

**Step 3.3** – Implement “live read” in the pipeline  
In the **Schedule suites** stage (or a preceding “Resolve suite list” stage):

1. If `SUITE_LIST_SOURCE` is set:
   - If it looks like a URL (starts with `http`), use `curl` or `readFile` from a `sh` step that fetches it, then parse YAML/JSON/list.
   - If it’s a file path, use `readFile` (and optionally `readYaml` if you use the Pipeline Utility Steps plugin).
2. Parse the content into a list of suite names (e.g. `['smoke', 'rados', 'rgw']`).
3. If the list is empty, fall back to `params.SUITE_NAME` (single suite).

**Step 3.4** – Schedule one run per suite (or one run with multiple suites, depending on teuthology)  
- If teuthology expects **one suite per run:** loop over the suite list and for each suite call your existing “schedule suite and wait” logic (or schedule all then wait once, depending on how you want to handle parallelism).  
- If teuthology supports **multiple suites in one run:** pass the list to the API/CLI as needed.

**Step 3.5** – Aggregate and report for all runs  
When you have multiple runs (one per suite), aggregate results from each run (e.g. collect each run’s pass/fail, then build one table). Post that combined table and all run links in the tracker note and in the email.

**Check:** For a release that has a config file or URL, run the pipeline with `SUITE_LIST_SOURCE` set; the pipeline should run exactly the suites listed there and report on all of them.

---

## Summary checklist

| Comment | What to do |
|--------|------------|
| **Preferred route is Shaman** | Keep “Wait for Shaman” before “Schedule suites”; use `env.SHA1` for `--sha1`; no custom repo by default. |
| **Add email** | Add `EMAIL_RECIPIENTS` parameter; in `post { always { ... } }` send email (e.g. `emailext`) with build link + result + pointer to tracker. |
| **Live read suite list** | Add `SUITE_LIST_SOURCE` (file path or URL); in pipeline read and parse it into a list of suites; schedule (and optionally wait) for each; aggregate all runs into one table and post to tracker + email. |

After implementing, run one full RC flow with Shaman, email recipients set, and a suite list file/URL to verify all three behaviors end-to-end.
