// Seed job created during the jenkins initialization
job('seed-job') {
    displayName('Seed Job')
    description('Dynamic seed job for Ceph Jenkins pipelines')

    // Build retention policy
    logRotator {
        numToKeep(10)
    }

    // Agent node label
    label('ceph-seed-agent')

    // Repo and Branch parameters
    parameters {
        stringParam('REPO_URL', 'https://github.com/deepssin/rc-testing.git', 'The Git repository containing your DSL scripts')
        stringParam('BRANCH_NAME', 'main', 'The branch to check out and process')
    }

    // Clone user repo
    scm {
        git {
            remote {
                url('${REPO_URL}')
            }
            branch('${BRANCH_NAME}')
        }
    }

    // Process DSL scripts present in the repository
    steps {
        jobDsl {
            // Targets the DSL files from `jobs` directory in the repo
            targets 'jobs/*.groovy'
            
            // Ignore existing jobs
            removedJobAction('IGNORE')
            removedViewAction('IGNORE')
            lookupStrategy('SEED_JOB')
        }
    }
}