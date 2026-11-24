"""Unit tests for the pure scheduling helpers in the agy-daemon overlay.

These run on a plain Python interpreter (no Android, no device):

    python3 -m unittest discover -s app/src/test/python -v

Cron parsing decides whether scheduled agent tasks fire at all, and the
duration parser feeds every `--delay`/`--interval` trigger, so both are worth
pinning down.
"""

import os
import sys
import unittest
from datetime import datetime

# Make the overlay importable without executing the daemon entry point.
_REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))
_OVERLAY_LIB = os.path.join(_REPO_ROOT, "app", "src", "main", "assets", "overlay", "lib")
sys.path.insert(0, _OVERLAY_LIB)

# scheduler.py imports triggers.py, which imports executor.py; none of those
# need Android at import time, but executor.py resolves paths from PREFIX/HOME.
os.environ.setdefault("PREFIX", "/tmp/agy-test/usr")
os.environ.setdefault("HOME", "/tmp/agy-test/home")

from daemon.scheduler import matches_cron, match_cron_field, parse_duration  # noqa: E402


class ParseDurationTests(unittest.TestCase):
    def test_seconds(self):
        self.assertEqual(parse_duration("10s"), 10)

    def test_minutes(self):
        self.assertEqual(parse_duration("15m"), 900)

    def test_hours(self):
        self.assertEqual(parse_duration("4h"), 14400)

    def test_fractional(self):
        self.assertEqual(parse_duration("1.5h"), 5400)
        self.assertEqual(parse_duration("0.5m"), 30)

    def test_bare_integer_is_seconds(self):
        self.assertEqual(parse_duration("42"), 42)

    def test_invalid_raises(self):
        with self.assertRaises(ValueError):
            parse_duration("not-a-duration")


class MatchCronFieldTests(unittest.TestCase):
    def test_wildcard(self):
        for value in (0, 30, 59):
            self.assertTrue(match_cron_field(value, "*", 0, 59))

    def test_exact(self):
        self.assertTrue(match_cron_field(5, "5", 0, 59))
        self.assertFalse(match_cron_field(6, "5", 0, 59))

    def test_list(self):
        self.assertTrue(match_cron_field(1, "1,15,30", 0, 59))
        self.assertTrue(match_cron_field(15, "1,15,30", 0, 59))
        self.assertFalse(match_cron_field(2, "1,15,30", 0, 59))

    def test_range(self):
        self.assertTrue(match_cron_field(9, "9-17", 0, 23))
        self.assertFalse(match_cron_field(18, "9-17", 0, 23))

    def test_step_from_wildcard(self):
        # */15 matches 0, 15, 30, 45
        for value in (0, 15, 30, 45):
            self.assertTrue(match_cron_field(value, "*/15", 0, 59))
        for value in (1, 14, 16, 59):
            self.assertFalse(match_cron_field(value, "*/15", 0, 59))

    def test_step_from_start(self):
        # 5/10 matches 5, 15, 25, ...
        self.assertTrue(match_cron_field(5, "5/10", 0, 59))
        self.assertTrue(match_cron_field(15, "5/10", 0, 59))
        self.assertFalse(match_cron_field(10, "5/10", 0, 59))


class MatchesCronTests(unittest.TestCase):
    def test_every_minute(self):
        self.assertTrue(matches_cron("* * * * *", datetime(2026, 3, 4, 5, 6)))

    def test_daily_at_three(self):
        self.assertTrue(matches_cron("0 3 * * *", datetime(2026, 3, 4, 3, 0)))
        self.assertFalse(matches_cron("0 3 * * *", datetime(2026, 3, 4, 3, 1)))
        self.assertFalse(matches_cron("0 3 * * *", datetime(2026, 3, 4, 4, 0)))

    def test_day_of_week_uses_sunday_zero(self):
        # 2026-03-08 is a Sunday.
        sunday = datetime(2026, 3, 8, 12, 0)
        self.assertEqual(sunday.weekday(), 6)  # Monday=0 in Python
        self.assertTrue(matches_cron("0 12 * * 0", sunday))
        # 2026-03-09 is a Monday -> cron dow 1
        monday = datetime(2026, 3, 9, 12, 0)
        self.assertTrue(matches_cron("0 12 * * 1", monday))
        self.assertFalse(matches_cron("0 12 * * 0", monday))

    def test_wrong_field_count_is_false(self):
        for expr in ("", "* * * *", "* * * * * *", "garbage"):
            self.assertFalse(matches_cron(expr, datetime(2026, 3, 4, 3, 0)))

    def test_month_bounds(self):
        self.assertTrue(matches_cron("0 0 1 1 *", datetime(2026, 1, 1, 0, 0)))
        self.assertFalse(matches_cron("0 0 1 1 *", datetime(2026, 2, 1, 0, 0)))


if __name__ == "__main__":
    unittest.main()
