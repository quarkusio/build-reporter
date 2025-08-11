## Test Failures

{#if !includeStackTraces}
> [!WARNING]
> Unable to include the stracktraces as the report was too long. See annotations below for the details.

{/if}
{#if !includeFailureLinks}
> [!WARNING]
> Unable to include the failure links as the report was too long. See annotations below for the details.

{/if}

{#for job in report.jobsWithReportedFailures}
### :gear: {job.name} {#if job.reportedFailures}<a href="#user-content-{job.failuresAnchor}" id="{job.failuresAnchor}">#</a>{/if}

{#if job.failingModules || job.skippedModules}
```diff
{#if job.failingModules}- Failing: {#for failingModule : job.firstFailingModules}{failingModule} {/for}{/if}{#if job.moreFailingModulesCount}and {job.moreFailingModulesCount} more{/if}
{#if job.skippedModules}! Skipped: {#for skippedModule : job.firstSkippedModules}{skippedModule} {/for}{/if}{#if job.moreSkippedModulesCount}and {job.moreSkippedModulesCount} more{/if}
```
{/if}

{#for module in job.modulesWithReportedFailures}
#### :package: {module.name ? module.name : "Root module"}

{#if module.testFailures}
```diff
# Tests:    {module.testCount}
+ Success:  {module.successCount}
- Failures: {module.failureCount}
- Errors:   {module.errorCount}
! Skipped:  {module.skippedCount}
```

{#for failure : module.testFailures}
<p>:x: <code>{failure.fullName.escapeHtml}</code>{#if failure.failureErrorLine} line <code>{failure.failureErrorLine}</code>{/if}{#if develocityEnabled && develocityUrl} - <a href="{develocityUrl}scans/tests?tests.container={failure.fullClassName}&tests.test={failure.name}">History</a>{/if}{#if includeFailureLinks} <a id="test-failure-{failure.fullClassName.toLowerCase}-{failure_count}"></a> - <a href="{failure.shortenedFailureUrl}">Source on GitHub</a> - <a href="#user-content-build-summary-top">🠅</a>{/if}</p>

{#if (failure.abbreviatedFailureDetail && includeStackTraces) || (report.sameRepository && failure.failureErrorLine)}
<details>

{#if failure.abbreviatedFailureDetail && includeStackTraces}
```
{failure.abbreviatedFailureDetail.trim}
```
{/if}

{#if report.sameRepository && failure.failureErrorLine}
{failure.shortenedFailureUrl}
{/if}
</details>
{/if}

{/for}
{#else if module.projectReportFailure}
<p>:x: <code>{module.projectReportFailure.escapeHtml}</code></p>

{#else}
<p>We were unable to extract a useful error message.</p>

{/if}
{/for}
{#if job_hasNext}

---

{/if}
{/for}

{#if report.flakyTests}
## Flaky tests{#if develocityEnabled && develocityUrl} - <a href="{develocityUrl}scans/tests">Develocity</a>{/if}

{#for job in report.jobsWithFlakyTests}
### :gear: {job.name}

{#for module in job.modulesWithFlakyTests}
#### :package: {module.name ? module.name : "Root module"}

{#for flakyTest : module.flakyTests}
<p>:x: <code>{flakyTest.fullName.escapeHtml}</code>{#if develocityEnabled && develocityUrl} - <a href="{develocityUrl}scans/tests?tests.container={flakyTest.fullClassName}&tests.test={flakyTest.name}">History</a>{/if}</p>

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
