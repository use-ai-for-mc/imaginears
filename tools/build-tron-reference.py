#!/usr/bin/env python3
"""Build the bundled TRON reference path from collected ride profile JSON files."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def profile_to_reference(path: Path) -> dict | None:
    data = json.loads(path.read_text())
    if data.get("rideKey") != "tron-lightcycle" or "path" not in data:
        return None

    evidence = data.get("rideEvidence") or {}
    points = [
        {
            "t": round(float(point["t"]), 1),
            "x": round(float(point["x"]), 3),
            "y": round(float(point["y"]), 3),
            "z": round(float(point["z"]), 3),
        }
        for point in data.get("path", [])
    ]
    if len(points) < 10:
        return None

    return {
        "sourceFile": path.name,
        "startedAt": data.get("startedAt", ""),
        "markerDamage": int(evidence.get("damage", -1)),
        "vehicleVariant": evidence.get("vehicleVariant", ""),
        "durationSeconds": round(float(data.get("durationMs", 0)) / 1000.0, 1),
        "sampleIntervalSeconds": 2,
        "points": points,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "profile_dir",
        type=Path,
        help="Directory containing logs/imears-ride-sessions JSON files.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("src/main/resources/assets/imears/rides/tron-lightcycle-reference.json"),
    )
    parser.add_argument("--source-label", default="logs/imears-ride-sessions")
    args = parser.parse_args()

    profiles = [
        profile
        for path in sorted(args.profile_dir.glob("tron-lightcycle-*.json"))
        if (profile := profile_to_reference(path)) is not None
    ]
    root = {
        "schemaVersion": 1,
        "rideKey": "tron-lightcycle",
        "rideName": "TRON Lightcycle",
        "generatedFrom": args.source_label,
        "profiles": profiles,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(root, indent=2) + "\n")
    print(f"Wrote {args.output} from {len(profiles)} profile(s)")


if __name__ == "__main__":
    main()
