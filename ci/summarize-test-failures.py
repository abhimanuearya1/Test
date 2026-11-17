"""Writes the failed on-device tests and their error messages to a small text file.

Usage: summarize-test-failures.py <output file>
"""
import glob
import sys
import xml.etree.ElementTree as ET

lines = []
for path in glob.glob("app/build/outputs/androidTest-results/connected/**/*.xml", recursive=True):
    for case in ET.parse(path).getroot().iter("testcase"):
        for failure in list(case.findall("failure")) + list(case.findall("error")):
            lines.append(f"{case.get('classname')}.{case.get('name')}")
            lines.extend("    " + text for text in (failure.text or "").strip().splitlines()[:25])
            lines.append("")
if not lines:
    lines = ["The Gradle test task failed but no failing test was recorded."]
with open(sys.argv[1], "w", encoding="utf-8") as out:
    out.write("\n".join(lines) + "\n")
print("\n".join(lines))
