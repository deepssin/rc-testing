/**
 * Release tracker workflow: SHA1 -> build -> wait Shaman -> schedule suites -> aggregate -> report to Redmine.
 * Requires: Jenkins credential 'redmine-api-key' (Secret text) for tracker.ceph.com.
 */
pipelineJob('release_tracker_workflow') {
    displayName('Release Tracker Workflow')
    description('Automates tracker flow: resolve SHA1, approval gate, build, wait Shaman, schedule suites, aggregate results, post to Redmine. Requires redmine-api-key credential.')

    definition {
        cpsScm {
            scm {
                git {
                    remote { url("https://github.com/deepssin/rc-testing.git") }
                    branches("main")
                }
            }
            scriptPath("jobs/pipelines/release_tracker_workflow/Jenkinsfile")
        }
    }

    parameters {
        stringParam("CEPH_REPO", "https://github.com/ceph/ceph.git", "Ceph repository URL")
        stringParam("CEPH_BRANCH", "reef", "Ceph branch (e.g. reef, tentacle)")
        stringParam("RELEASE_VERSION", "20.1.0", "Release version")
        stringParam("TRACKER_ISSUE_ID", "72316", "Redmine tracker issue ID")
        stringParam("BUILD_JOB_NAME", "sample-ceph-pipeline", "Jenkins job that builds Ceph RPMs")
        stringParam("SUITE_NAME", "smoke", "Single suite when SUITE_LIST_SOURCE is empty")
        stringParam("SUITE_LIST_SOURCE", "", "Live read: file path or URL of suite list for this release (e.g. config/suites_reef.yaml). Empty = use SUITE_NAME.")
        stringParam("PADDLES_URL", "http://paddles.front.sepia.ceph.com/", "Paddles base URL for aggregation")
        stringParam("EMAIL_RECIPIENTS", "", "Comma-separated emails to notify when run finishes. Empty = no email.")
        booleanParam("SKIP_BUILD", false, "Skip build (use existing SHA1 on Shaman)")
        booleanParam("SKIP_INTEGRATION_TESTS", false, "Skip teuthology suite")
        booleanParam("SKIP_TRACKER_UPDATE", false, "Do not post to Redmine")
    }
}
