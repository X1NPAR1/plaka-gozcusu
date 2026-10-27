"""Count JUnit results under every build/test-results directory (passed/failed/skipped)."""
import glob
import xml.etree.ElementTree as ET

total = failed = skipped = 0
for path in glob.glob("**/build/test-results/**/TEST-*.xml", recursive=True):
    suite = ET.parse(path).getroot()
    total += int(suite.get("tests", 0))
    failed += int(suite.get("failures", 0)) + int(suite.get("errors", 0))
    skipped += int(suite.get("skipped", 0))
print(f"total={total} passed={total - failed - skipped} failed={failed} skipped={skipped}")
