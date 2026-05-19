import json
import re
import os

with open("errors.json", "r") as f:
    errors = json.load(f)

# Group errors by file
files = {}
for err in errors:
    files.setdefault(err["file"], []).append(err)

def patch_file(file_path, file_errors):
    if not os.path.exists(file_path):
        return
    
    with open(file_path, "r") as f:
        lines = f.readlines()
    
    # Track modifications
    mods = 0
    
    # 1. DensityFunctionTypes protected -> public
    if "DensityFunctionTypes" in file_path:
        for i in range(len(lines)):
            if "protected record" in lines[i] or "protected enum" in lines[i] or "protected static" in lines[i]:
                lines[i] = lines[i].replace("protected ", "public ")
                mods += 1

    # Apply specific fix based on line content
    # For ambiguous createIdToValueFunction
    # ValueLists.createIdToValueFunction( -> ValueLists.createIdToValueFunction(
    for i in range(len(lines)):
        if "ValueLists.createIdToValueFunction(" in lines[i]:
            if "ValueLists.OutOfBoundsHandling" in lines[i]:
                pass # usually fine
                
    # Instead of fixing generic errors line by line, just erase the method body of specific purely server or worldgen methods, OR add generic casts.
    
    if mods > 0:
        with open(file_path, "w") as f:
            f.writelines(lines)
        print(f"Patched {file_path}")

for file_path, file_errors in files.items():
    patch_file(file_path, file_errors)
