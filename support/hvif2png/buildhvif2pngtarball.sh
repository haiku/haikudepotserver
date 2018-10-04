#!/bin/bash

# --------------------------------------------
# This script will create a tarball file for installing the hvif2png tool into a
# suitable UNIX-like environment.  The launch script will support the use of
# 'macports' on the MacOS-X environment in order to have access to the 'libpng'
# software.The only argument to this script is the top of the build-products
# directory which is typically ".../haiku/generated".
# --------------------------------------------

syntax() {
	echo "buildhvif2pngtarball.sh <generated-directory>"
	exit 1
}

if [ $# -lt 1 ]; then
	syntax
fi

osName="$(echo `uname`| tr [:upper:] [:lower:])"
archName="$(uname -m)"
genDir="$1"
tmpDir="${genDir}/tmp/hvif2png_tarball"
version=`cat "${genDir}/build/haiku-revision"`
libPathVar="LD_LIBRARY_PATH"

if [ 'Darwin' == "$(uname)" ]; then
	libPathVar="DYLD_FALLBACK_LIBRARY_PATH"	
fi

if [ -z "${version}" ]; then
        echo "the version was not able to be determined"
        version='noversion'
fi

buildRootDir="${tmpDir}/hvif2png-${version}"

echo "generating tarball from '${genDir}' for version '${version}'"

# create the build directories
for d in bin lib; do
	if ! mkdir -p "${buildRootDir}/${d}" ; then
		echo "unable to create the tarball working directory; ${d}"
		exit 1
	fi
done

# copy the necessary files into the right source directory

cp "${genDir}/objects/${osName}/lib/libbe_build.so" "${buildRootDir}/lib"
cp "${genDir}/objects/${osName}/lib/libroot_build.so" "${buildRootDir}/lib"
cp "${genDir}/objects/${osName}/${archName}/release/tools/hvif2png/hvif2png" "${buildRootDir}/bin"

cat << EOF > "${buildRootDir}/bin/hvif2png.sh"
#!/bin/sh
HVIF2PNG_HOME="\$(dirname \$0)/.."
${libPathVar}=\${HVIF2PNG_HOME}/lib:\${${libPathVar}}

# support for MacOS-X macports
if [ -d /opt/local/lib ]; then
	${libPathVar}=\${${libPathVar}}:/opt/local/lib
fi

export ${libPathVar}
\${HVIF2PNG_HOME}/bin/hvif2png "\$@"
EOF

if ! chmod +x "${buildRootDir}/bin/hvif2png.sh"; then
	echo "unable to change the permission on the launch script"
	exit 1
fi

if ! tar -C "${tmpDir}" -czf "${genDir}/tmp/hvif2png-${version}-${archName}.tgz" "hvif2png-${version}" ; then
	echo "unable to create the tarball"
	exit 1
fi

echo "note that the libpng libraries must be available for the hvif2png software to work."
