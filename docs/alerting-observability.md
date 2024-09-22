# Alerting and Observability

## Alert for Repositories Missing Updates

HPKR data is imported for Repository Sources such as `haikuports_x86_64`. This import process occurs on demand and also on a schedule. If there is some problem with the import process then it will be likely to continue to be a problem. This may be due to networking issues, data-processing issues and so on.

HDS can alert when there have not been any updates for a long period of time. It is possible to configure a threshold in hours. If there have been no `PkgVersion`-s modified on the `RepositorySoure` for more than the threshold, an alert email is sent. The body is something like this;

```
2 Repository Sources are absent an update in an appropriate time-frame;

- `haikuports_x86_64` expected within 1 hours; has been 3 hours
- `haikuports_x86_gcc2` expected within 1 hours; has been 3 hours

Unless there is a reason for the outage, check the HDS logs for information about the cause of the issue.
```

To configure the alert, set the value `expectedUpdateFrequencyHours` on `RepositorySource` and configure the property `hds.alerts.repository-absent-updates.to` to specify the recipients of the alert. For a Java `.properties` list, the value can be a comma-separated list.