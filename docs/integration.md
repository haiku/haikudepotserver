# Integration

The integration mechanisms use the HDS [APIs](api.md) and especially the [jobs APIs](api.md#background-jobs). It would be prudent to become familiar with these techniques.

## Localization

To facilitate an external system to manage localizations for HDS, interfaces are provided for this. This section outlines those integrations.

### Obtaining Data from HDS

The remote localization system will need to be able to obtain localization data from HDS for initial population of the data as well as later reconciliation. The remote system will need to execute the following operations to obtain the necessary data;

- *Reference data*; can be obtained with `.../miscellaneous-job/queue-reference-dump-export-job`. This will contain the list of the natural languages supported by the system. The schema for this data is contained in `dumpexportreference.json`.
- *Repositories*; can be obtained with `.../repository-job/queue-repository-dump-export-job`. This will provide a list of repository sources. The schema for this data is contained in `dumpexportrepository.json`.
- *Package data*; containing the (repository supplied) english localizations can be obtained with `.../pkg-job/queue-pkg-dump-export-job`. This will need to be invoked for each repository source that the remote system is interested in and the caller will need to supply the natural language of interest (English) as well as the repository source code. The package names may overlap between repositories in cases where the same package is supplied by two or more repositories. The schema for this data is contained in `dumpexportpkg.json`.
- *Localizations*; containing the human-supplied translations for packages in various languages can be obtained with `.../pkg-job/queue-pkg-dump-localization-export-job`. The schema for this data is contained in `dumpexportpkglocalization.json`. There will be an entry in this data for each package that could be localized. Use this list as a list of packages that can be localized. Some packages suffixed with, for example, `_x86` will twin with a main package and it is the main package that needs to be localized and not the `_x86` suffixed variant. There are other suffixes that apply in this case. Rather than re-implement this algorithm, it would be better and safer to simply use the downloaded data to provide this information.

### Upload Data to HDS

See the example in `support/client-tools/run_pkg_localization_import.py`.