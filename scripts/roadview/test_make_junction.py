import math
import unittest

from make_junction import bearing_deg, build_record, detour_minutes, distance_m, point_at


def point_north(origin, meters):
    return [origin[0] + meters / 111_195, origin[1]]


def point_east(origin, meters):
    return [origin[0], origin[1] + meters / (111_195 * math.cos(math.radians(origin[0])))]


class JunctionBuilderTest(unittest.TestCase):
    def setUp(self):
        self.junction = [37.5, 127.0]
        # OSM input paths are intentionally synthetic and explicitly labeled fixture data.
        self.raw = {"elements": [
            {"type": "way", "id": 11, "tags": {"highway": "primary", "name": "fixture approach"},
             "geometry": [{"lat": lat, "lon": lon} for lat, lon in [self.junction, point_north(self.junction, 80), point_north(self.junction, 180)]]},
            {"type": "way", "id": 12, "tags": {"highway": "primary", "name": "fixture keep"},
             "geometry": [{"lat": lat, "lon": lon} for lat, lon in [self.junction, point_east(self.junction, 80), point_east(self.junction, 180)]]},
            {"type": "way", "id": 13, "tags": {"highway": "secondary", "name": "fixture trap"},
             "geometry": [{"lat": lat, "lon": lon} for lat, lon in [self.junction, point_north(self.junction, -80), point_north(self.junction, -180)]]},
        ]}
        self.config = {
            "id": "fixture-crossing", "routeId": "fixture-route-v1", "junction": self.junction, "backMeters": 150,
            "message": "분기점 표지를 미리 확인하세요.", "directSeconds": 300,
            "detourSeconds": 1020, "retrievedAt": "2026-11-13T00:00:00Z",
            "ways": {
                "approach": {"wayId": "11", "splitIndex": 0, "outwardDirection": "toward_end"},
                "keep": {"wayId": "12", "splitIndex": 0, "outwardDirection": "toward_end"},
                "trap": {"wayId": "13", "splitIndex": 0, "outwardDirection": "toward_end"},
            },
            "review": {"manuallyVerified": False, "roadviewCoverageVerified": False},
        }

    def test_point_interpolation_and_bearing(self):
        end = point_north(self.junction, 100)
        self.assertAlmostEqual(distance_m(self.junction, end), 100, delta=0.2)
        self.assertAlmostEqual(bearing_deg(self.junction, end), 0, delta=0.01)
        result = point_at([self.junction, end], 50)
        self.assertAlmostEqual(distance_m(self.junction, result), 50, delta=0.2)

    def test_record_uses_fixed_offsets_and_osm_provenance(self):
        record = build_record(self.raw, self.config)
        self.assertAlmostEqual(distance_m(record["junction"], record["at"]), 150, delta=0.5)
        self.assertAlmostEqual(distance_m(record["junction"], record["caution"]), 10, delta=0.5)
        self.assertEqual(len(record["keepPath"]), 3)
        self.assertAlmostEqual(distance_m(record["junction"], record["keepPath"][0]), 20, delta=0.5)
        self.assertEqual(record["cost"], "약 12분 돌아감")
        self.assertEqual(record["source"]["ways"]["keep"]["id"], "12")
        self.assertEqual(record["source"]["license"], "ODbL-1.0")
        self.assertFalse(record["useRoadview"])
        self.assertIsNone(record["verifiedLaneMessage"])

    def test_review_and_coverage_are_both_required_to_enable_roadview(self):
        self.config["review"] = {"manuallyVerified": True, "roadviewCoverageVerified": False}
        self.assertFalse(build_record(self.raw, self.config)["useRoadview"])
        self.config["review"]["roadviewCoverageVerified"] = True
        self.assertTrue(build_record(self.raw, self.config)["useRoadview"])

    def test_rejects_unverified_lane_message(self):
        self.config["verifiedLaneMessage"] = "2차로에서 우회전"
        with self.assertRaisesRegex(ValueError, "laneGuidanceVerified"):
            build_record(self.raw, self.config)
        self.config["verifiedLaneMessage"] = None
        self.config["message"] = "2차로에서 우회전하세요."
        with self.assertRaisesRegex(ValueError, "lane-specific wording"):
            build_record(self.raw, self.config)

    def test_invalid_detour_cost_is_omitted(self):
        self.assertIsNone(detour_minutes(100, 99))
        self.assertIsNone(detour_minutes(float("nan"), 120))
        self.assertIsNone(detour_minutes(None, 120))
        self.assertEqual(detour_minutes(60, 180), "약 2분 돌아감")

    def test_rejects_wrong_way_and_bad_split(self):
        self.config["ways"]["trap"]["wayId"] = "999"
        with self.assertRaisesRegex(ValueError, "not present"):
            build_record(self.raw, self.config)
        self.config["ways"]["trap"]["wayId"] = "13"
        self.config["ways"]["trap"]["splitIndex"] = 2
        with self.assertRaisesRegex(ValueError, "more than 20m"):
            build_record(self.raw, self.config)

    def test_point_at_rejects_short_path(self):
        with self.assertRaisesRegex(ValueError, "beyond"):
            point_at([self.junction, point_east(self.junction, 10)], 20)


if __name__ == "__main__":
    unittest.main()
