#!/bin/bash

# This script finds all exported packages in the list of java and
# javafx modules (all whose name has the prefix "java") for the JVM
# that is in the PATH and constructs corresponding --add-opens
# arguments for the JVM.
for module in `java --list-modules | grep "^java"`;
do
    cleaned_up_module_name=`echo $module | tr @ " " | awk '{ print $1 }'`;
    for package in `java -d $cleaned_up_module_name | grep "^exports " | sed 's/^exports //g'`;
    do
	echo -n "--add-opens=$cleaned_up_module_name/$package=ALL-UNNAMED ";
    done
done

# Extra: for package manager, when uninstalling a package
echo -n "--add-opens=java.base/sun.net.www.protocol.jar=ALL-UNNAMED "

# Extra: for Spark package
echo -n "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED "

# Extra: for Java3D/JOGL
echo -n "--add-opens=javafx.graphics/com.sun.javafx.tk=ALL-UNNAMED --add-opens=javafx.graphics/com.sun.javafx.tk.quantum=ALL-UNNAMED --add-opens=javafx.graphics/com.sun.glass.ui=ALL-UNNAMED"