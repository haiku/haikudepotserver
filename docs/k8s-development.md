# Kubernetes development

These instructions assume a Debian development environment.

No considerations are made in these instructions and notes around system security.

It is possible to run HDS on Kubernetes. This is an ideal way to test-run the system because it is ultimately deployed in Kubernetes.

## Preparation

1. Install the necessary software
   - Docker
   - [Kubernetes in Docker](https://kind.sigs.k8s.io/) (KIND)
2. Create a directory on your local disk to store data such as the data for the Postgres database used by HDS; referred to in this text as `${K8S_DATA_DIRECTORY}`.

## Setup a kubernetes cluster in Docker

Edit the file `support/k8s-development/haikudepotserver-kind-cluster.yaml` value `...extraMounts.hostPath` to point to your `${K8S_DATA_DIRECTORY}`. Now create the cluster;

```
kind create cluster --name haikudepotserver \
--config support/local-k8s-kind-development/haikudepotserver-kind-cluster.yaml
```

Check that the cluster is operational;

```
kubectl cluster-info --context kind-haikudepotserver
```

## Deploy database into the Kubernetes cluster

Load the manifest YAML files into the Kubernetes cluster in order to stand up the resources required to run the Postgres database server.

```
kubectl --context kind-haikudepotserver apply \
-f ./support/local-k8s-kind-development/haikudepotserver-pg-configmap.yaml \
-f ./support/local-k8s-kind-development/haikudepotserver-pg-volume.yaml \
-f ./support/local-k8s-kind-development/haikudepotserver-pg-app.yaml
```

Wait a short while for the system to settle and then check that the database is running.

```
kubectl --context kind-haikudepotserver get pods
```

A pod with prefix `haikudepotserver-pg` should be listed and eventually `1/1` should be up.

## Deploy graphics services application into the Kubernetes cluster

The HDS system manipulates graphics files as part of its operation. This functionality is supported by a container image that contains tools and an application server that provides an API to those tools. This application can be deployed with;

```
kubectl --context kind-haikudepotserver apply \
-f ./support/local-k8s-kind-development/haikudepotserver-server-graphics.yaml
```

Wait a short while for the system to settle and then check that the application is running.

```
kubectl --context kind-haikudepotserver get pods
```

A pod with prefix `haikudepotserver-haikudepotserver-server-graphics` should be listed and eventually `1/1` should be up.

## Deploy application into the Kubernetes cluster

Later when you wish to import the packages from a repository, the process can be time-consuming and require a large volume of traffic. To reduce the quantity of packages imported, an additional environment variable `HDS_REPOSITORY_IMPORT_ALLOWEDPKGNAMEPATTERN` can be set in the file `./support/local-k8s-kind-development/haikudepotserver-webapp.yaml` to something like `^.[Aa].+$` in order to only import those packages that have the second character as "A" in their name.

Also in the same YAML file, the `image` key should be upgraded to the latest release of the HDS application. You can find the latest version [here](https://github.com/haiku/haikudepotserver/tags).

Now we deploy the application into the Kubernetes cluster.

```
kubectl --context kind-haikudepotserver apply \
-f ./support/local-k8s-kind-development/haikudepotserver-webapp.yaml
```

Wait a short while for the system to settle and then check that the application is running.

```
kubectl --context kind-haikudepotserver get pods
```

A pod with prefix `haikudepotserver-webapp` should be listed and eventually `1/1` should be up.

The application can be accessed on `http://localhost:8090` on the development host. Login as `root` with password `zimmer`.

## Get a `psql` session on the Postgres database

```
kubectl --context kind-haikudepotserver exec -it haikudepotserver-pg-f6d69987b-lsjkc -- \
psql --password -U haikudepotserver haikudepotserver
```

## Get application logs

```
kubectl --context kind-haikudepotserver logs deployment/haikudepotserver-webapp -f
```

## Build locally, deploy to the local cluster
    
In the sample files...

- `haikudepotserver-webapp.yaml`
- `haikudepotserver-server-graphics.yaml`

...you will see that a remote image is specified for the application. For example, `ghcr.io/haiku/haikudepotserver:1.0.160`. If you want to run a locally built container image, first build the images. This is done by creating the `Dockerfile`-s and then building the contains and tagging them.

```bash
make dockerfiles
docker build -f Dockerfile_webapp --tag haikudepotserver:999.999.999 .
docker build -f Dockerfile_server_graphics --tag haikudepotserver-server-graphics:999.999.999 .
```

Once this build is complete, load the built images into the cluster;

```
kind load docker-image haikudepotserver:999.999.999 --name haikudepotserver
kind load docker-image `haikudepotserver-server-graphics:999.999.999` --name haikudepotserver
```

Change the image specified in the Kubernetes manifests;

- in the `haikudepotserver-webapp.yaml` set the image to `haikudepotserver:999.999.999`
- in the `haikudepotserver-server-graphics.yaml` set the image to `haikudepotserver-server-graphics:999.999.999`

and then re-apply these two manifests to the cluster.

## Setup and import a repository

1. Authenticate as `root` to the web interface
2. Visit the list of repositories
3. Add a new repository with code `haikuports`, name `HaikuPorts`
4. Add a new repository source with code `haikuports_x86_64`
5. Add a new repository source mirror with URL `https://eu.hpkg.haiku-os.org:443/haikuports/master/x86_64/current`
6. Choose the "Trigger import" link on the repository source view
7. Observe the logs from the HDS application
   ```
   kubectl --context kind-haikudepotserver logs deployment/haikudepotserver-webapp -f
   ```
8. Wait for the import process to complete with the message `...(repositoryhpkringress); finish` in the logs
9. View the web interface home page and observe that packages are listed

## Delete cluster from Docker

```
kind delete cluster --name haikudepotserver
```