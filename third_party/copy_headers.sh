#!/usr/bin/env bash

output_directory="$1" #CMAKE_ARCHIVE_OUTPUT_DIRECTORY
echo "output_directory: $output_directory"
array=($(find ./ -not -path "*/.*" -and -name "*.h" -and -not -name "* *") $(find ./ -not -path "*/.*" -and -name "*.hpp" -and -not -name "* *"))
echo "copying headers: ${array[@]}"

for v in "${array[@]}"; do
        header_directory="${output_directory}/$(dirname "${v}")"
        if [ ! -d "${header_directory}" ]; then
                mkdir -p "${header_directory}"
        fi
        cp -f "$v" "${header_directory}/"
done
