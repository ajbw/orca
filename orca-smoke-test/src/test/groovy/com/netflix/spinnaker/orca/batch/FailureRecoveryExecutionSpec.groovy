package com.netflix.spinnaker.orca.batch

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.job.builder.JobBuilder

import static com.netflix.spinnaker.orca.TaskResult.Status.FAILED
import static com.netflix.spinnaker.orca.TaskResult.Status.SUCCEEDED

class FailureRecoveryExecutionSpec extends BatchExecutionSpec {

    def startTask = Stub(Task)
    def recoveryTask = Mock(Task)
    def endTask = Mock(Task)

    def "if the first task completes normally the recovery task does not run"() {
        given:
        startTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)

        when:
        def jobExecution = launchJob()

        then:
        1 * endTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)

        and:
        0 * recoveryTask._

        and:
        jobExecution.exitStatus == ExitStatus.COMPLETED
    }

    def "if the first task fails the recovery task is run"() {
        given:
        startTask.execute(_) >> new DefaultTaskResult(FAILED)

        when:
        def jobExecution = launchJob()

        then:
        1 * recoveryTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)
        1 * endTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)

        and:
        jobExecution.exitStatus == ExitStatus.COMPLETED
    }

    @Override
    protected Job configureJob(JobBuilder jobBuilder) {
        def step1 = steps.get("StartStep")
            .tasklet(TaskTaskletAdapter.decorate(startTask))
            .build()
        def step2 = steps.get("RecoveryStep")
            .tasklet(TaskTaskletAdapter.decorate(recoveryTask))
            .build()
        def step3 = steps.get("EndStep")
            .tasklet(TaskTaskletAdapter.decorate(endTask))
            .build()
        jobBuilder.start(step1)
            .on(ExitStatus.FAILED.exitCode).to(step2).next(step3)
            .from(step1).next(step3)
            .build()
            .build()
    }
}
