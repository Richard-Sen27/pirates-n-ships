"""Generates the armed pirate brig BuildSpec (SHIP1): the brig with eight guns, open gun ports, shot lockers, a crow's
nest, ratline fittings and two swivel guns. Prints JSON."""
import json
import os
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import brig  # noqa: E402  (same folder)

print(json.dumps(brig.armed_spec("pns_pirate_brig_armed", "Pirate Brig (armed)")))
