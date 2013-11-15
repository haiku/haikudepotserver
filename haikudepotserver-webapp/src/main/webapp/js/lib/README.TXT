This directory contains JavaScript libraries which are used for the Haiku Depot Server's web user interface.  These
libraries are downloaded when the first build is done from the command line; use "mvn compile" from the command line
at the reactor level to make this happen.  The actual process occurs using an ant script that is invoked from the
maven build.