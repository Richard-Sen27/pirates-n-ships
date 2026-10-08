"""Generates the armed navy sloop BuildSpec (WS4c): the starter sloop with four guns and a shot locker. Prints JSON."""
import json
import os
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import starter_sloop  # noqa: E402  (same folder)

print(json.dumps(starter_sloop.armed_spec("pns_navy_sloop_armed", "Navy Sloop (armed)")))
