# hvif2png

These instructions assume a Debian linux host.

Software on the Haiku uses the HVIF file format to represent icons.  This format is vector-based rather than using bitmaps however a web-browser is generally unable to render HVIF so HDS renders the HVIF data to PNG for use in a web browser. 

Part of the [Haiku Source Code](https://www.haiku-os.org/development) includes
a tool called `hvif2png` that can be used for this purpose. You can build this tool from source.

- [Setup](https://www.haiku-os.org/guides/building/) a Haiku build environment
- Install the necessary PNG support libraries; see the `Dockerfile` and look for `libpng16-16`
- Build `hvif2png` for your build system with `jam -q "<build>hvif2png"` from the `${HAIKU_GENERATED}` directory.
- Run the packaging script with `python3 support/hvif2pngtarball.py "${HAIKU_GENERATED}"`. This will create a new file in `${HAIKU_GENERATED}` such as `hvif2png-hrev1234-linux-x86_64.tgz`.
- Unpack the tar-ball where you intend to use it on your system; for example in `/opt/haiku`.
- Test the binary with, for example, `/opt/haiku/hvif2png-hrev1234/bin/hvif2png.sh` which should render the syntax.

For a Debian-based linux system, it would also be possible to unpack the `hvif2png` prepared tar-ball at `support/deployment/hvif2png...linux-x86_64.tgz`.