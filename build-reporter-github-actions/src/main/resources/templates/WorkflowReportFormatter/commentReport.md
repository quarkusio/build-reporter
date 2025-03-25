# Status for workflow `{report.workflowName}`

This is the status report for running `{report.workflowName}` on commit {report.sha}.

{#if report.cancelled}
> [!NOTE]
> :no_entry_sign: This build has been cancelled.

{/if}

{#if !includeStackTraces}
> [!WARNING]
> Unable to include the stracktraces as the report was too long. See annotations below for the details.

{/if}
{#if !includeFailureLinks}
> [!WARNING]
> Unable to include the failure links as the report was too long. See annotations below for the details.

{/if}

{#if report.failure}
{#if !report.jobsFailing}
> [!CAUTION]
> This workflow run has failed but no jobs reported an error. Something weird happened, please check [the workflow run page]({report.workflowRunUrl}) carefully: it might be an issue with the workflow configuration itself.

{#else}
## Failing Jobs

{#if !artifactsAvailable && !report.cancelled}:warning: Artifacts of the workflow run were not available thus the report misses some details.{/if}

| Status | Name | Step | Failures | Logs | Raw logs |{#if develocityEnabled} Build scan |{/if}
| :-:  | --  | --  | :-:  | :-:  | :-:  |{#if develocityEnabled} :-:  |{/if}
{#for job in report.jobs}
{#if workflowReportJobIncludeStrategy.include(report, job)}
| {job.conclusionEmoji} | {job.name} | {#if job.failingStep}`{job.failingStep}`{/if} | {#if job.reportedFailures}[Failures](#user-content-{job.failuresAnchor}){#else if job.failing}:warning: Check →{/if} | {#if job.url}[Logs]({job.url}){/if} | {#if job.rawLogsUrl}[Raw logs]({job.rawLogsUrl}){/if} |{#if develocityEnabled} {#if job.gradleBuildScanUrl}[:mag:]({job.gradleBuildScanUrl}){#else}:construction:{/if} |{/if}
{/if}
{/for}

{#if checkRun}
Full information is available in the [Build summary check run]({checkRun.htmlUrl}).
{/if}
{/if}
{#if develocityEnabled}{buildScansCheckRunMarker}{/if}

{#if report.errorDownloadingBuildReports}
> [!WARNING]
> Errors occurred while downloading the build reports. This report is incomplete.
{/if}

{#if report.reportedFailures}
## Failures

{#for job in report.jobsWithReportedFailures}
### :gear: {job.name} {#if job.failuresAnchor}<a href="#user-content-{job.failuresAnchor}" id="{job.failuresAnchor}">#</a>{/if}

{#if job.failingModules || job.skippedModules}
```diff
{#if job.failingModules}- Failing: {#for failingModule : job.firstFailingModules}{failingModule} {/for}{/if}{#if job.moreFailingModulesCount}and {job.moreFailingModulesCount} more{/if}
{#if job.skippedModules}! Skipped: {#for skippedModule : job.firstSkippedModules}{skippedModule} {/for}{/if}{#if job.moreSkippedModulesCount}and {job.moreSkippedModulesCount} more{/if}
```
{/if}

{#for module in job.modulesWithReportedFailures}
#### :package: {module.name ? module.name : "Root module"}

{#if module.testFailures}
{#for failure : module.testFailures}
<p>✖ <code>{failure.fullName.escapeHtml}</code>{#if failure.failureErrorLine} line <code>{failure.failureErrorLine}</code>{/if}{#if develocityEnabled && develocityUrl} - <a href="{develocityUrl}scans/tests?tests.container={failure.fullClassName}&tests.test={failure.name}">History</a>{/if}{#if includeFailureLinks} - {#if checkRun && failure.failureDetail}<a href="{checkRun.htmlUrl}#user-content-test-failure-{failure.fullClassName.toLowerCase}-{failure_count}">More details</a> - {/if}<a href="{failure.shortenedFailureUrl}">Source on GitHub</a>{/if}</p>

{#if failure.abbreviatedFailureDetail && includeStackTraces}
<details>

```
{failure.abbreviatedFailureDetail.trim}
```

</details>
{/if}

{/for}
{#else if module.projectReportFailure}
<p>✖ <code>{module.projectReportFailure.escapeHtml}</code></p>

{#else}
<p>We were unable to extract a useful error message.</p>

{/if}
{/for}
{#if job_hasNext}

---

{/if}
{/for}
{/if}
{#else if indicateSuccess}
:white_check_mark: The latest workflow run for the pull request has completed successfully.

It should be safe to merge provided you have a look at the other checks in the summary.

{#if hasOtherPendingCheckRuns}
> [!WARNING]
> There are other workflow runs running, you probably need to wait for their status before merging.

{/if}
{#if develocityEnabled}{buildScansCheckRunMarker}{/if}
{/if}

{#if report.flakyTests}

---

## Flaky tests{#if develocityEnabled && develocityUrl} - <a href="{develocityUrl}scans/tests">Develocity</a>{/if}

{#for job in report.jobsWithFlakyTests}
### :gear: {job.name}

{#for module in job.modulesWithFlakyTests}
#### :package: {module.name ? module.name : "Root module"}

{#for flakyTest : module.flakyTests}
<p>✖ <code>{flakyTest.fullName.escapeHtml}</code>{#if develocityEnabled && develocityUrl} - <a href="{develocityUrl}scans/tests?tests.container={flakyTest.fullClassName}&tests.test={flakyTest.name}">History</a>{/if}</p>

{#for flake : flakyTest.flakes}
{#if flake.message}- `{flake.message.escapeMarkdown}`{/if}{#if flake.type} - `{flake.type.escapeMarkdown}`{/if}

{#if flake.abbreviatedStackTrace && flake.abbreviatedStackTrace.trim && includeStackTraces}
<details>

```
{flake.abbreviatedStackTrace.trim}
```

</details>
{/if}

{/for}

{/for}
{/for}
{#if job_hasNext}

---

{/if}
{/for}
{/if}

{messageIdActive}
{workflowRunId}