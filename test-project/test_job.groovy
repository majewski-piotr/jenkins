job('project1/job1') {
    description('A simple example job')
    triggers {
        scm('H/15 * * * *')
    }
    steps {
        shell('echo Hello, Jenkins!')
    }
}
