# Driver Settings

Driver settings is a textual format that describes a tree structure of key-value pairs.  This is used, as the name describes, to configure drivers in the Haiku operating system.  You can find out more about this format by looking for the file ```driver_settings.cpp```.

This file format is also used in the ```repo.info``` file at the top-level of a Haiku repository directory.  Because of this, this larger project needs to be able to parse this simple file format.  This module is responsible for providing a simple parser.