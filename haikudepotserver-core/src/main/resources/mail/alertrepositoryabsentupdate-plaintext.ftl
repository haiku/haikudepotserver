${repositorySourceAbsentUpdates?size} Repository Sources are absent an update in an appropriate time-frame;

<#list repositorySourceAbsentUpdates as repositorySourceAbsentUpdate>
- `${repositorySourceAbsentUpdate.repositorySourceCode}` expected within ${repositorySourceAbsentUpdate.expectedUpdateFrequencyHours} hours; has been <#if repositorySourceAbsentUpdate.lastUpdateAgoHours??>${repositorySourceAbsentUpdate.lastUpdateAgoHours} hours<#else>never</#if>
</#list>

Unless there is a reason for the outage, check the HDS logs for information about the cause of the issue.