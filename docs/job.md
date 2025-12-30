# Jobs

## Background

The job system is the means by which HDS is able to run asynchronous tasks such as;

- scheduled maintenance
- download and integration of data from a Haiku package repository
- reports

## Schema

The jobs are stored in the database in the following schema;

![Data model](images/img-datamodel-job.svg)

`job` is the master table for the job. The `job_data` can be either supplied or generated data for the job; the two situations can be differentiated via the `job_data_type` table.

Each entry in the `job` table has a `specification` column entry; this contains a JSON-serialized copy of the object that specified the job to run.

Each `job` table row has a `job_assignment` row as well. The `job_assignment` is the means by which the job can be locked for running. Using this locking mechanism, two instances of HDS are not able to run the same Job at the same time. The Postgres "skip lock" system is used to avoid an instance of HDS picking up a Job that another HDS is running.

## Architecture

The job system is distributed such that multiple instances of HDS are able to operate at once and any instance is able to pick-up a job and run it. The system uses Postgres `NOTIFY` / `LISTEN` to signal to all instances that new jobs are available to pickup.

To avoid two or more instances of HDS picking up the same job, the system uses Postgres "skip locks" ensuring that only one HDS instance will process a job.

See the class `DbDistributedJobServiceImpl` for the implementation of this system.

## Garbage Collection

The HDS system will be frequently running jobs and their details will pile-up in the database. To expunge expired jobs, a periodic task runs as part of the maintenance system. See the class `JobGarbageCollectionJobRunner` for details.