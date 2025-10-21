# Kubernetes development

These instructions assume a Debian development environment.

No considerations are made in these instructions and notes around system security.

It is possible to run HDS on Kubernetes. This is an ideal way to test-run the system because it is ultimately deployed in Kubernetes.

## Preparation

In this example, `podman` is used as the build system for container images.

In this example, we are using the [k3s](https://k3s.io/) system to get a small cluster working on a lab machine running Debian [trixie](https://www.debian.org/releases/trixie/). Install a simple [k3s](https://k3s.io/) system so that there is a basic lab K8S system to work with.

To allow a local user to access the cluster using `kubectl`, the config needs to be made available. In this example, the current user is added to a new group and then the config file is made available to that group.

```
sudo groupadd k3s
sudo adduser "${USER}" k3s
```

Now edit `/etc/systemd/system/k3s.service` adding the following lines;

```
K3S_KUBECONFIG_MODE="640"
K3S_KUBECONFIG_GROUP="k3s"
```

Restart the host and observe the permissions on the `/etc/rancher/k3s/k3s.yaml` file;

```
-rw-r----- 1 root k3s ... /etc/rancher/k3s/k3s.yaml
```

Now the `kubectl` command should be able to be used by your regular user.

## Deploy database into the Kubernetes cluster

Load the manifest YAML files into the Kubernetes cluster in order to stand up the resources required to run the Postgres database server.

```
kubectl apply \
-f ./support/local-k8s-kind-development/haikudepotserver-pg-configmap.yaml \
-f ./support/local-k8s-kind-development/haikudepotserver-pg-volume.yaml \
-f ./support/local-k8s-kind-development/haikudepotserver-pg-app.yaml
```

Wait a short while for the system to settle and then check that the database is running.

```
kubectl get pods
```

A pod with prefix `haikudepotserver-pg` should be listed and eventually `1/1` should be up.

## Deploy graphics services application into the Kubernetes cluster

The HDS system manipulates graphics files as part of its operation. This functionality is supported by a container image that contains tools and an application server that provides an API to those tools. This application can be deployed with;

```
kubectl apply -f ./support/local-k8s-kind-development/haikudepotserver-server-graphics.yaml
```

Wait a short while for the system to settle and then check that the application is running.

```
kubectl get pods
```

A pod with prefix `haikudepotserver-haikudepotserver-server-graphics` should be listed and eventually `1/1` should be up.

## Deploy application into the Kubernetes cluster

Later when you wish to import the packages from a repository, the process can be time-consuming and require a large volume of traffic. To reduce the quantity of packages imported, an additional environment variable `HDS_REPOSITORY_IMPORT_ALLOWEDPKGNAMEPATTERN` can be set in the file `./support/local-k8s-kind-development/haikudepotserver-webapp.yaml` to something like `^.[Aa].+$` in order to only import those packages that have the second character as "A" in their name.

Also in the same YAML file, the `image` key should be upgraded to the latest release of the HDS application. You can find the latest version [here](https://github.com/haiku/haikudepotserver/tags).

Now we deploy the application into the Kubernetes cluster.

```
kubectl apply -f ./support/local-k8s-kind-development/haikudepotserver-webapp.yaml
```

Wait a short while for the system to settle and then check that the application is running.

```
kubectl get pods
```

A pod with prefix `haikudepotserver-webapp` should be listed and eventually `1/1` should be up.

The application can be accessed on http://localhost:30080 on the development host. Login as `root` with password `zimmer`.

## Get a `psql` session on the Postgres database

Get the name of the database pod; in this example `haikudepotserver-pg-f6d69987b-lsjkc` then run the following;

```
kubectl exec -it haikudepotserver-pg-f6d69987b-lsjkc -- \
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
docker build -f Dockerfile_webapp --load --tag haikudepotserver:999.999.999 .
docker build -f Dockerfile_server_graphics --load --tag haikudepotserver-server-graphics:999.999.999 .
```

Once this build is complete, export the images to tar files;

```bash
podman save --output /tmp/haikudepotserver-server-graphics-9.tar haikudepotserver-server-graphics:999.999.999
podman save --output /tmp/haikudepotserver-9.tar haikudepotserver:999.999.999
````

Now transport them to the k3s Kubernetes node's host disk system at `/var/lib/rancher/k3s/agent/images`.

Change the image specified in the Kubernetes `Deployment` manifests;

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