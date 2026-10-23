#!/usr/bin/env python3
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Aggregates the native build statistics collected by the Native Build Stats workflow.
#
# Usage: native-build-stats.py <stats-dir> <output-dir>
#
# <stats-dir> is laid out as <category>/<module>/{build-output-stats.json,build-metrics.json,runner-size.txt}.
# Writes modules.csv and build-steps.csv to <output-dir> and prints a markdown summary to stdout.

import csv
import json
import statistics
import sys
from collections import defaultdict
from pathlib import Path

MB = 1024 * 1024

COLUMNS = [
    "category",
    "module",
    "status",
    "runner_mb",
    "image_total_mb",
    "code_area_mb",
    "image_heap_mb",
    "resources_mb",
    "resources_count",
    "reachable_types",
    "reachable_methods",
    "reachable_fields",
    "reflection_types",
    "reflection_methods",
    "reflection_fields",
    "jni_types",
    "native_build_secs",
    "gc_secs",
    "gc_pct",
    "peak_rss_mb",
    "quarkus_build_secs",
    "quarkus_steps_cpu_secs_excl_native",
    "graalvm_version",
]

NATIVE_IMAGE_STEP = "NativeImageBuildStep"


def get(data, *path):
    for key in path:
        if not isinstance(data, dict) or key not in data:
            return None
        data = data[key]
    # GraalVM reports -1 for values that are not available
    return None if data == -1 else data


def mb(value):
    return round(value / MB, 1) if isinstance(value, (int, float)) else None


def load_json(path):
    if not path.is_file():
        return None
    try:
        return json.loads(path.read_text())
    except (OSError, ValueError) as e:
        print(f"WARNING: failed to read {path}: {e}", file=sys.stderr)
        return None


def module_row(category, module_dir, step_durations):
    row = {"category": category, "module": module_dir.name}

    stats = load_json(module_dir / "build-output-stats.json")
    metrics = load_json(module_dir / "build-metrics.json")
    runner_size = module_dir / "runner-size.txt"

    row["status"] = "ok" if stats and runner_size.is_file() else "failed"

    if runner_size.is_file():
        row["runner_mb"] = mb(int(runner_size.read_text().strip()))

    if stats:
        row["graalvm_version"] = get(stats, "general_info", "graalvm_version")
        row["image_total_mb"] = mb(get(stats, "image_details", "total_bytes"))
        row["code_area_mb"] = mb(get(stats, "image_details", "code_area", "bytes"))
        row["image_heap_mb"] = mb(get(stats, "image_details", "image_heap", "bytes"))
        row["resources_mb"] = mb(get(stats, "image_details", "image_heap", "resources", "bytes"))
        row["resources_count"] = get(stats, "image_details", "image_heap", "resources", "count")
        for kind in ("types", "methods", "fields"):
            row[f"reachable_{kind}"] = get(stats, "analysis_results", kind, "reachable")
            row[f"reflection_{kind}"] = get(stats, "analysis_results", kind, "reflection")
        row["jni_types"] = get(stats, "analysis_results", "types", "jni")
        total_secs = get(stats, "resource_usage", "total_secs")
        gc_secs = get(stats, "resource_usage", "garbage_collection", "total_secs")
        row["native_build_secs"] = round(total_secs, 1) if total_secs else None
        row["gc_secs"] = round(gc_secs, 1) if gc_secs is not None else None
        if total_secs and gc_secs is not None:
            row["gc_pct"] = round(100 * gc_secs / total_secs, 1)
        row["peak_rss_mb"] = mb(get(stats, "resource_usage", "memory", "peak_rss_bytes"))

    if metrics:
        duration = metrics.get("duration")
        row["quarkus_build_secs"] = round(duration / 1000, 1) if duration else None
        non_native_ms = 0
        for record in metrics.get("records", []):
            step_id = record.get("stepId", "")
            step_ms = record.get("duration", 0)
            if NATIVE_IMAGE_STEP in step_id:
                continue
            non_native_ms += step_ms
            step_durations[step_id].append(step_ms)
        row["quarkus_steps_cpu_secs_excl_native"] = round(non_native_ms / 1000, 1)

    return row


def write_csv(path, columns, rows):
    with path.open("w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=columns, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def markdown_table(title, rows, columns, limit=25):
    lines = [f"### {title}", "", "| " + " | ".join(columns) + " |", "|" + "---|" * len(columns)]
    for row in rows[:limit]:
        lines.append("| " + " | ".join("" if row.get(c) is None else str(row.get(c)) for c in columns) + " |")
    lines.append("")
    return "\n".join(lines)


def top(rows, key):
    return sorted((r for r in rows if r.get(key) is not None), key=lambda r: r[key], reverse=True)


def main():
    if len(sys.argv) != 3:
        print(__doc__ or "Usage: native-build-stats.py <stats-dir> <output-dir>", file=sys.stderr)
        sys.exit(1)

    stats_dir = Path(sys.argv[1])
    output_dir = Path(sys.argv[2])
    output_dir.mkdir(parents=True, exist_ok=True)

    rows = []
    step_durations = defaultdict(list)
    for category_dir in sorted(p for p in stats_dir.iterdir() if p.is_dir()):
        for module_dir in sorted(p for p in category_dir.iterdir() if p.is_dir()):
            rows.append(module_row(category_dir.name, module_dir, step_durations))

    write_csv(output_dir / "modules.csv", COLUMNS, rows)

    step_rows = []
    for step_id, durations in step_durations.items():
        step_rows.append({
            "step": step_id,
            "modules": len(durations),
            "total_secs": round(sum(durations) / 1000, 1),
            "median_ms": round(statistics.median(durations)),
            "max_ms": max(durations),
        })
    step_rows.sort(key=lambda r: r["total_secs"], reverse=True)
    write_csv(output_dir / "build-steps.csv", ["step", "modules", "total_secs", "median_ms", "max_ms"], step_rows)

    ok = [r for r in rows if r["status"] == "ok"]
    failed = [r for r in rows if r["status"] != "ok"]

    out = ["## Native Build Stats", ""]
    out.append(f"{len(ok)} native images built, {len(failed)} failed or missing stats.")
    if ok:
        def total(key):
            return round(sum(r.get(key) or 0 for r in ok), 1)
        out.append("")
        out.append(f"Total native-image time: {round(total('native_build_secs') / 60, 1)} min, "
                   f"total GC time: {round(total('gc_secs') / 60, 1)} min, "
                   f"total runner size: {round(total('runner_mb') / 1024, 2)} GB")
    out.append("")

    out.append(markdown_table("Largest binaries", top(ok, "runner_mb"),
                              ["module", "runner_mb", "code_area_mb", "image_heap_mb", "resources_mb", "reachable_methods"]))
    out.append(markdown_table("Slowest native-image builds", top(ok, "native_build_secs"),
                              ["module", "native_build_secs", "gc_pct", "peak_rss_mb", "reachable_methods"]))
    out.append(markdown_table("Highest GC share (memory pressure)", top(ok, "gc_pct"),
                              ["module", "gc_pct", "gc_secs", "native_build_secs", "peak_rss_mb"], limit=15))
    out.append(markdown_table("Most reflection registrations", top(ok, "reflection_methods"),
                              ["module", "reflection_types", "reflection_methods", "reflection_fields", "reachable_methods"], limit=15))
    out.append(markdown_table("Largest embedded resources", top(ok, "resources_mb"),
                              ["module", "resources_mb", "resources_count", "image_heap_mb"], limit=15))
    out.append(markdown_table("Slowest Quarkus build steps (excluding native-image, summed across modules)", step_rows,
                              ["step", "modules", "total_secs", "median_ms", "max_ms"], limit=20))
    if failed:
        out.append(markdown_table("Failed or missing stats", failed, ["category", "module"], limit=len(failed)))

    print("\n".join(out))


if __name__ == "__main__":
    main()
