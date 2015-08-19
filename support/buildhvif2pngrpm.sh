#!/bin/sh

# --------------------------------------------
# This script will create an RPM file for installing the hvif2png tool into a
# suitable RPM-based linux environment.  This was build in order to support
# deployment along side the Haiku Depot Web application server.  Note that
# The RPM will require libpng to be installed.  A full build of Haiku may be
# required in order that the version 'href' is populated properly.
# The only argument to this script is the top of the build-products directory
# which is typically ".../haiku/generated".
# -------------------------------------------

syntax() {
	echo "build_hvif2png_rpm <generated-directory>"
	exit 1
}

if [ $# -lt 1 ]; then
	syntax
fi

if ! command -v rpmbuild > /dev/null; then
	echo "unable to find the rpmbuild command to produce an rpm"
	exit 1
fi

osName="$(echo `uname`| tr [:upper:] [:lower:])"
archName="$(uname -m)"
genDir="$1"
tmpDir="${genDir}/tmp/hvif2png_rpm"
version=`cat "${genDir}/build/haiku-revision" | sed -e "s/[^0-9]//g"`

case "${archName}" in
	"i586"|"i386"|"i686") archName=x86 ;;
esac

if [ -z "${version}" ]; then
        echo "the version was not able to be determined - it may be necessary to undertake a full build to get the version populated"
	exit 1
fi

prepLeaf="hvif2png-1.${version}-1.${archName}"

echo "generating rpm from '${genDir}' for version '1.${version}'"

# create the RPM directories

for d in BUILD RPMS SOURCES SPECS SRPMS "BUILDROOT/${prepLeaf}/opt/hvif2png/bin" "BUILDROOT/${prepLeaf}/opt/hvif2png/lib" ; do
	if ! mkdir -p "${tmpDir}/${d}" ; then
		echo "unable to create the rpm working directory; ${tmpDir}/${d}"
		exit 1
	fi
done

# copy the necessary files into the right source directory

cp "${genDir}/objects/${osName}/lib/libbe_build.so" "${tmpDir}/BUILDROOT/${prepLeaf}/opt/hvif2png/lib"

if [ "$?" -ne 0 ]; then
	echo "unable to copy the libbe_build.so library";
	exit 1
fi

cp "${genDir}/objects/${osName}/lib/libroot_build.so" "${tmpDir}/BUILDROOT/${prepLeaf}/opt/hvif2png/lib"

if [ "$?" -ne 0 ]; then
	echo "unable to copy the libroot_build.so library"
	exit 1
fi

cp "${genDir}/objects/${osName}/${archName}/release/tools/hvif2png/hvif2png" "${tmpDir}/BUILDROOT/${prepLeaf}/opt/hvif2png/bin"

if [ "$?" -ne 0 ]; then
	echo "unable to copy the hvif2png binary"
	exit 1
fi

cat << EOF > "${tmpDir}/BUILDROOT/${prepLeaf}/opt/hvif2png/bin/hvif2png.sh"
#!/bin/sh
HVIF2PNG_HOME=/opt/hvif2png
export LD_LIBRARY_PATH=\${HVIF2PNG_HOME}/lib:${LD_LIBRARY_PATH}
\${HVIF2PNG_HOME}/bin/hvif2png "\$@"
EOF

# build the SPEC file.

cat << EOF > "${tmpDir}/SPECS/hvif2png.spec"
%define _unpackaged_files_terminate_build 0
Name: hvif2png
Version: 1.${version}
Release: 1
Summary: hvif2png
License: MIT
Group: haiku
Packager: haiku
autoprov: yes
autoreq: yes
BuildArch: $(arch)
BuildRoot: ${tmpDir}

%description
This tool allows HVIF formatted vector artwork to be rastered to PNG bitmap images.

%install

%files
%dir %attr(775,root,root) "/opt/hvif2png"
%dir %attr(775,root,root) "/opt/hvif2png/lib"
%dir %attr(775,root,root) "/opt/hvif2png/bin"
%attr(444,root,root)  "/opt/hvif2png/lib/libbe_build.so"
%attr(444,root,root)  "/opt/hvif2png/lib/libroot_build.so"
%attr(755,root,root)  "/opt/hvif2png/bin/hvif2png"
%attr(755,root,root)  "/opt/hvif2png/bin/hvif2png.sh"

EOF

# now produce the RPM

if ! rpmbuild -vv -bb --buildroot "${tmpDir}/BUILDROOT/${prepLeaf}" --define "_topdir ${tmpDir}" "${tmpDir}/SPECS/hvif2png.spec"; then
	echo "unable to create the rpm"
	exit 1
fi

