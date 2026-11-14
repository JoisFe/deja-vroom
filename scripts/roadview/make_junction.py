#!/usr/bin/env python3
"""Build a reviewed FirstLook junction record from manually selected OSM ways.

Input coordinates and OSM geometry are [latitude, longitude]. This tool performs no
automatic branch classification: each way split index and outward direction must be
selected and reviewed by a person.
"""
from __future__ import annotations

import argparse
import json
import math
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

EARTH_RADIUS_M = 6_371_000.0
OSM_ATTRIBUTION = "© OpenStreetMap contributors"
OSM_LICENSE = "ODbL-1.0"


def _coordinate(value: Any, label: str = "coordinate") -> tuple[float, float]:
    if not isinstance(value, (list, tuple)) or len(value) != 2:
        raise ValueError(f"{label} must be [latitude, longitude]")
    lat, lon = float(value[0]), float(value[1])
    if not math.isfinite(lat) or not math.isfinite(lon) or not -90 <= lat <= 90 or not -180 <= lon <= 180:
        raise ValueError(f"{label} is outside latitude/longitude bounds")
    return lat, lon


def distance_m(a: Any, b: Any) -> float:
    lat1, lon1 = map(math.radians, _coordinate(a, "a"))
    lat2, lon2 = map(math.radians, _coordinate(b, "b"))
    dlat, dlon = lat2 - lat1, lon2 - lon1
    h = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 2 * EARTH_RADIUS_M * math.asin(math.sqrt(min(1.0, h)))


def bearing_deg(a: Any, b: Any) -> float:
    lat1, lon1 = map(math.radians, _coordinate(a, "a"))
    lat2, lon2 = map(math.radians, _coordinate(b, "b"))
    dlon = lon2 - lon1
    y = math.sin(dlon) * math.cos(lat2)
    x = math.cos(lat1) * math.sin(lat2) - math.sin(lat1) * math.cos(lat2) * math.cos(dlon)
    return (math.degrees(math.atan2(y, x)) + 360) % 360


def point_at(path: list[Any], distance: float) -> list[float]:
    points = [_coordinate(p, "path point") for p in path]
    if len(points) < 2:
        raise ValueError("path must contain at least two points")
    if not math.isfinite(distance) or distance < 0:
        raise ValueError("distance must be a finite non-negative number")
    remaining = distance
    for start, end in zip(points, points[1:]):
        span = distance_m(start, end)
        if span <= 1e-6:
            continue
        if remaining <= span:
            ratio = remaining / span
            return [round(start[0] + (end[0] - start[0]) * ratio, 7),
                    round(start[1] + (end[1] - start[1]) * ratio, 7)]
        remaining -= span
    if remaining > 0.01:
        raise ValueError(f"requested point is {remaining:.1f}m beyond the supplied path")
    return [round(points[-1][0], 7), round(points[-1][1], 7)]


def orient_way(way: dict[str, Any], selection: dict[str, Any], junction: Any) -> list[list[float]]:
    way_id = str(selection.get("wayId", ""))
    if str(way.get("id")) != way_id:
        raise ValueError(f"selected OSM way {way_id!r} is not present in input")
    geometry = way.get("geometry")
    if not isinstance(geometry, list) or len(geometry) < 2:
        raise ValueError(f"OSM way {way_id} has no usable geometry")
    coords = [_coordinate([p.get("lat"), p.get("lon")], f"way {way_id} point") for p in geometry]
    split = selection.get("splitIndex")
    if not isinstance(split, int) or not 0 <= split < len(coords):
        raise ValueError(f"way {way_id} splitIndex must select a geometry vertex")
    if distance_m(coords[split], junction) > 20:
        raise ValueError(f"way {way_id} split vertex is more than 20m from the junction")
    direction = selection.get("outwardDirection")
    if direction not in ("toward_start", "toward_end"):
        raise ValueError(f"way {way_id} outwardDirection must be toward_start or toward_end")
    indexes = range(split, -1, -1) if direction == "toward_start" else range(split, len(coords))
    path = [list(coords[i]) for i in indexes]
    # Use the common selected split coordinate as the path origin to make offsets deterministic.
    path[0] = list(_coordinate(junction, "junction"))
    if len(path) < 2:
        raise ValueError(f"way {way_id} has less than two points in the selected direction")
    return path


def detour_minutes(direct_seconds: Any, detour_seconds: Any) -> str | None:
    if direct_seconds is None or detour_seconds is None:
        return None
    direct, detour = float(direct_seconds), float(detour_seconds)
    if not math.isfinite(direct) or not math.isfinite(detour) or direct < 0 or detour < direct:
        return None
    minutes = round((detour - direct) / 60)
    return f"약 {minutes}분 돌아감" if minutes > 0 else None


def _osm_way_index(raw: dict[str, Any]) -> dict[str, dict[str, Any]]:
    elements = raw.get("elements")
    if not isinstance(elements, list):
        raise ValueError("Overpass response must contain an elements array")
    return {str(item["id"]): item for item in elements
            if isinstance(item, dict) and item.get("type") == "way" and "id" in item}


def build_record(raw: dict[str, Any], config: dict[str, Any]) -> dict[str, Any]:
    record_id = str(config.get("id", ""))
    if not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", record_id):
        raise ValueError("id must use lowercase ASCII letters, digits, and hyphens")
    route_id = str(config.get("routeId", "")).strip()
    if not route_id:
        raise ValueError("routeId must identify the exact verified route snapshot")
    junction = _coordinate(config.get("junction"), "junction")
    selected = config.get("ways")
    if not isinstance(selected, dict) or set(selected) != {"approach", "keep", "trap"}:
        raise ValueError("ways must select exactly approach, keep, and trap")
    ways = _osm_way_index(raw)
    paths = {key: orient_way(ways.get(str(selection.get("wayId")), {}), selection, junction)
             for key, selection in selected.items()}
    at = point_at(paths["approach"], float(config.get("backMeters", 150)))
    message = str(config.get("message", "")).strip()
    if not message or len(message) > 60:
        raise ValueError("message must contain 1 to 60 characters")
    lane_message = config.get("verifiedLaneMessage")
    lane_verified = bool(config.get("laneGuidanceVerified", False))
    if lane_message and not lane_verified:
        raise ValueError("verifiedLaneMessage requires laneGuidanceVerified=true")
    if lane_verified and (not isinstance(lane_message, str) or not lane_message.strip()):
        raise ValueError("laneGuidanceVerified requires a verifiedLaneMessage")
    if re.search(r"차로|차선", message) and not lane_verified:
        raise ValueError("lane-specific wording requires laneGuidanceVerified=true")
    cost = detour_minutes(config.get("directSeconds"), config.get("detourSeconds"))
    review = config.get("review", {})
    reviewed = bool(review.get("manuallyVerified"))
    coverage_verified = bool(review.get("roadviewCoverageVerified"))
    return {
        "schemaVersion": 1,
        "datasetType": "development",
        "id": record_id,
        "routeId": route_id,
        "junction": [round(junction[0], 7), round(junction[1], 7)],
        "at": at,
        "pan": round(bearing_deg(at, junction)),
        "tilt": 0,
        "zoom": 0,
        "caution": point_at(paths["trap"], 10),
        "message": message,
        "cost": cost,
        "verifiedLaneMessage": lane_message if config.get("laneGuidanceVerified", False) else None,
        "laneGuidanceVerified": lane_verified,
        "keepPath": [point_at(paths["keep"], d) for d in (20, 40, 60)],
        "trapPath": [point_at(paths["trap"], d) for d in (20, 40, 60)],
        "useRoadview": reviewed and coverage_verified,
        "review": {
            "manuallyVerified": reviewed,
            "roadviewCoverageVerified": coverage_verified,
            "reviewedAt": review.get("reviewedAt"),
            "notes": str(review.get("notes", "")),
        },
        "source": {
            "provider": "OpenStreetMap",
            "attribution": OSM_ATTRIBUTION,
            "license": OSM_LICENSE,
            "retrievedAt": config.get("retrievedAt"),
            "ways": {key: {"id": str(selection["wayId"]), "tags": ways[str(selection["wayId"])].get("tags", {})}
                     for key, selection in selected.items()},
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ways", required=True, type=Path, help="Overpass JSON saved with out tags geom")
    parser.add_argument("--config", required=True, type=Path, help="manually reviewed way selections and record metadata")
    parser.add_argument("--output", type=Path, help="output JSON file; stdout if omitted")
    args = parser.parse_args()
    try:
        record = build_record(json.loads(args.ways.read_text()), json.loads(args.config.read_text()))
        rendered = json.dumps(record, ensure_ascii=False, indent=2) + "\n"
        if args.output:
            args.output.write_text(rendered)
        else:
            sys.stdout.write(rendered)
    except (OSError, json.JSONDecodeError, ValueError, KeyError, TypeError) as error:
        parser.error(str(error))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
