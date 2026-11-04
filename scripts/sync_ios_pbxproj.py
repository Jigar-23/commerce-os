#!/usr/bin/env python3
import os
import sys
import hashlib
import re

PBX_PATH = '/Users/jigar/Desktop/new-project/commerce-os/apps/ios/CommerceOS.xcodeproj/project.pbxproj'
SRC_ROOT = '/Users/jigar/Desktop/new-project/commerce-os/apps/ios/CommerceOS'

def make_id(key):
    return hashlib.md5(key.encode('utf-8')).hexdigest()[:24].upper()

def sync():
    with open(PBX_PATH, 'r') as f:
        pbx = f.read()

    # Find missing swift files
    missing = []
    for root, dirs, files in os.walk(SRC_ROOT):
        for file in sorted(files):
            if file.endswith('.swift'):
                full_path = os.path.join(root, file)
                rel_path = os.path.relpath(full_path, SRC_ROOT)
                # Check if already present in pbxproj
                pattern = rf'/\*\s*{re.escape(file)}\s*\*/'
                if not re.search(pattern, pbx):
                    missing.append((file, rel_path))

    if not missing:
        print("All Swift files are already registered in project.pbxproj!")
        return 0

    print(f"Registering {len(missing)} Swift files into project.pbxproj...")

    build_files = []
    file_refs = []
    sources_entries = []
    group_entries = []

    for file, rel_path in missing:
        file_ref_id = make_id(file + '_fileRef')
        build_file_id = make_id(file + '_buildFile')

        build_files.append(f"\t\t{build_file_id} /* {file} in Sources */ = {{isa = PBXBuildFile; fileRef = {file_ref_id} /* {file} */; }};")
        file_refs.append(f"\t\t{file_ref_id} /* {file} */ = {{isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = \"{rel_path}\"; sourceTree = \"<group>\"; }};")
        sources_entries.append(f"\t\t\t\t{build_file_id} /* {file} in Sources */,")
        group_entries.append(f"\t\t\t\t{file_ref_id} /* {file} */,")
        print(f"  + {file} ({rel_path})")

    # 1. Insert into PBXBuildFile section
    build_file_marker = "/* Begin PBXBuildFile section */\n"
    if build_file_marker in pbx:
        pbx = pbx.replace(build_file_marker, build_file_marker + "\n".join(build_files) + "\n", 1)
    else:
        print("ERROR: Could not find PBXBuildFile section")
        return 1

    # 2. Insert into PBXFileReference section
    file_ref_marker = "/* Begin PBXFileReference section */\n"
    if file_ref_marker in pbx:
        pbx = pbx.replace(file_ref_marker, file_ref_marker + "\n".join(file_refs) + "\n", 1)
    else:
        print("ERROR: Could not find PBXFileReference section")
        return 1

    # 3. Insert into Sources build phase
    sources_pattern = r'(3F646237A37D8DFAA6777362 /\* Sources \*/ = \{[^}]*files = \(\n)'
    m_sources = re.search(sources_pattern, pbx)
    if m_sources:
        insert_pos = m_sources.end()
        pbx = pbx[:insert_pos] + "\n".join(sources_entries) + "\n" + pbx[insert_pos:]
    else:
        print("ERROR: Could not find Sources build phase 3F646237A37D8DFAA6777362")
        return 1

    # 4. Insert into CommerceOS group children
    group_pattern = r'(EFC3C8D0D1748F9930926686 /\* CommerceOS \*/ = \{[^}]*children = \(\n)'
    m_group = re.search(group_pattern, pbx)
    if m_group:
        insert_pos = m_group.end()
        pbx = pbx[:insert_pos] + "\n".join(group_entries) + "\n" + pbx[insert_pos:]
    else:
        print("ERROR: Could not find CommerceOS group EFC3C8D0D1748F9930926686")
        return 1

    with open(PBX_PATH, 'w') as f:
        f.write(pbx)

    print("Successfully updated project.pbxproj!")
    return 0

if __name__ == '__main__':
    sys.exit(sync())
