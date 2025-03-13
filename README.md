# Jenkins Job DSL Repository

This repository contains Job DSL scripts used to define and manage Jenkins jobs programmatically. With these scripts, you can automatically create, update, or remove jobs in your Jenkins instance via a seed job.

## Repository Structure


```
.
├── project1/
│   ├── job1.groovy  # Example job DSL script for job1
│   └── job2.groovy  # Example job DSL script for job2
├── README.md       # This file
```

- **jobs/**: Contains all the Job DSL Groovy scripts. Each script defines one or more Jenkins jobs.
- **README.md**: Provides an overview and instructions for using the repository.

## Prerequisites

- **Jenkins:** A running Jenkins instance.
- **Job DSL Plugin:** Install the [Job DSL Plugin](https://plugins.jenkins.io/job-dsl/) on your Jenkins instance.
- **Seed Job:** A Jenkins job that checks out this repository and processes the DSL scripts.

## Using the Seed Job

To load these DSL scripts into Jenkins, create a new Pipeline job (often called a seed job) with the following configuration:

```groovy
pipeline {
    agent any
    stages {
        stage('Checkout DSL Repository') {
            steps {
                git url: 'https://github.com/majewski-piotr/jenkins.git', branch: 'main'
            }
        }
        stage('Process DSL Scripts') {
            steps {
                jobDsl targets: '**/*.groovy',
                       sandbox: false,
                       removedJobAction: 'DELETE',
                       removedViewAction: 'DELETE',
                       lookupStrategy: 'JENKINS_ROOT'
            }
        }
    }
}

