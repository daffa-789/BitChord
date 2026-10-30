#!/bin/bash
# Offline audit helper: which Material icons does the desktop UI actually use,
# and how many of them live in the small material-icons-core jar instead of the
# 37 MB extended one?
set -u
cd "/c/Users/Daffa/Desktop/Folder Space AI/Folder Space Semester 7/Bit Chord" || exit 1
LIB=desktopApp/build/windows-portable/BitChord/lib
CORE_JAR=$(ls "$LIB"/org.jetbrains.compose.material.material-icons-core-*.jar 2>/dev/null | head -1)

unzip -l "$CORE_JAR" | grep -oE 'Icons\$(Filled|Rounded|Outlined|Sharp|TwoTone)\$[A-Za-z0-9_]+\.class' |
  sed 's/^Icons\$[A-Za-z]*\$//; s/\.class$//' | grep -v 'Kt$' | sort -u > /tmp/core_icons.txt

grep -rhoE 'Icons\.(Default|Filled|Outlined|Rounded|Sharp|TwoTone|AutoMirrored\.[A-Za-z]+)\.[A-Za-z0-9_]+' \
  --include=*.kt desktopApp/src desktopUi/src | sed 's/.*\.//' | sort -u > /tmp/used_icons.txt

echo "core      : $(wc -l < /tmp/core_icons.txt) nama ikon"
echo "dipakai   : $(wc -l < /tmp/used_icons.txt) nama ikon"
echo "ada di core      : $(comm -12 /tmp/core_icons.txt /tmp/used_icons.txt | wc -l)"
echo "perlu extended   : $(comm -13 /tmp/core_icons.txt /tmp/used_icons.txt | wc -l)"
echo "--- masih butuh extended ---"
comm -13 /tmp/core_icons.txt /tmp/used_icons.txt | tr '\n' ' '
echo
