#!/usr/bin/env python3
import sys
import re
import xml.etree.ElementTree as ET

def parse_instrumentation_to_junit(input_path: str, output_path: str):
    with open(input_path, "r", encoding="utf-8", errors="replace") as f:
        content = f.read()

    test_cases = []
    current_test = {}
    current_stack = []
    in_stack = False

    for line in content.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: class="):
            current_test["class"] = line.split("=", 1)[1].strip()
        elif line.startswith("INSTRUMENTATION_STATUS: test="):
            current_test["test"] = line.split("=", 1)[1].strip()
        elif line.startswith("INSTRUMENTATION_STATUS: stack="):
            in_stack = True
            current_stack = [line.split("=", 1)[1].strip()]
        elif in_stack:
            if line.startswith("INSTRUMENTATION_STATUS") or line.startswith("INSTRUMENTATION_RESULT"):
                in_stack = False
                current_test["stack"] = "\n".join(current_stack)
                current_stack = []
            else:
                current_stack.append(line)

        if line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            try:
                code = int(line.split(":", 1)[1].strip())
            except ValueError:
                continue

            if code == 1:
                # Test started
                pass
            elif code in (0, -1, -2):
                if in_stack:
                    in_stack = False
                    current_test["stack"] = "\n".join(current_stack)
                    current_stack = []
                current_test["code"] = code
                if "test" in current_test:
                    test_cases.append(dict(current_test))
                current_test = {}

    time_match = re.search(r"Time:\s*([\d\.]+)", content)
    total_time = time_match.group(1) if time_match else "0"

    failures = sum(1 for t in test_cases if t.get("code") != 0)

    testsuite = ET.Element("testsuite", {
        "name": "com.infillion.truex.reference.manualcsai.FunctionalUiTestSuite",
        "tests": str(len(test_cases)),
        "failures": str(failures),
        "errors": "0",
        "skipped": "0",
        "time": total_time,
    })

    for t in test_cases:
        tc = ET.SubElement(testsuite, "testcase", {
            "classname": t.get("class", "UnknownClass"),
            "name": t.get("test", "unknownTest"),
            "time": "0",
        })
        if t.get("code") != 0:
            failure = ET.SubElement(tc, "failure", {
                "message": f"Test failed with status code {t.get('code')}",
            })
            failure.text = t.get("stack", "No stacktrace recorded")

    tree = ET.ElementTree(testsuite)
    ET.indent(tree, space="  ")
    tree.write(output_path, encoding="utf-8", xml_declaration=True)
    print(f"Generated JUnit XML with {len(test_cases)} tests ({failures} failures) at {output_path}")

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: parse_instrumentation_to_junit.py <input_instrumentation.txt> <output_junit.xml>")
        sys.exit(1)
    parse_instrumentation_to_junit(sys.argv[1], sys.argv[2])
