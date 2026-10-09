import copy
import unittest

from gen_combat_v3_techniques import apply_choreography


class ChoreographyTest(unittest.TestCase):
    def setUp(self):
        self.entries = [{'id': 'xenopixelsmod:bt3_sample_001', 'sourceStartsMs': [15000],
                         'beats': [], 'durationTicks': 46, 'animationStatus': 'archetype_placeholder'}]

    def test_authored_timeline_survives_regeneration_without_mutating_baseline(self):
        before = copy.deepcopy(self.entries)
        authored = {'schema': 1, 'entries': [{
            'id': self.entries[0]['id'], 'sourceStartsMs': [15000],
            'animationStatus': 'reference_timed_unverified', 'durationTicks': 61,
            'beats': [{'kind': 'END', 'tick': 61, 'duration': 0, 'payload': '', 'value': 0}],
            'camera': []}]}
        result = apply_choreography(self.entries, authored)
        self.assertEqual(61, result[0]['durationTicks'])
        self.assertEqual('reference_timed_unverified', result[0]['animationStatus'])
        self.assertEqual(before, self.entries)

    def test_stale_or_unknown_occurrence_refused(self):
        for entry in ({'id': 'unknown', 'sourceStartsMs': [15000]},
                      {'id': self.entries[0]['id'], 'sourceStartsMs': [16000]}):
            with self.assertRaises(ValueError):
                apply_choreography(self.entries, {'schema': 1, 'entries': [entry]})

    def test_duplicate_and_gameplay_cost_override_refused(self):
        base = {'id': self.entries[0]['id'], 'sourceStartsMs': [15000]}
        for overrides in ([base, base], [dict(base, kiCost=0)]):
            with self.assertRaises(ValueError):
                apply_choreography(self.entries, {'schema': 1, 'entries': overrides})


if __name__ == '__main__':
    unittest.main()
