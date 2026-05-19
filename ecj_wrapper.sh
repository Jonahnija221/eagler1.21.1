#!/bin/bash
ARGS=()
skip_next=false
for arg in "$@"; do
    if [ "$skip_next" = true ]; then
        skip_next=false
        continue
    fi
    if [ "$arg" = "-h" ]; then
        skip_next=true
        continue
    fi
    # Wait, gradle might pass `-J...` which are JVM arguments? No, they go to jvmArgs.
    # What about `@...` files? ECJ supports arg files!
    ARGS+=("$arg")
done
# Run ECJ, passing -proceedOnError 
ECJ_JAR_PATH="/Users/Samar/Downloads/1.21.1-Deobfuscated-main 3/ecj.jar"
java -cp "$ECJ_JAR_PATH" org.eclipse.jdt.internal.compiler.batch.Main -proceedOnError "${ARGS[@]}"
