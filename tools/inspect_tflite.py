"""Inspect a .tflite detector and dump a reference run for Kotlin decoder tests.

Usage: python tools/inspect_tflite.py <model.tflite> [out.json]
The input is a deterministic synthetic image (no real-world photo, no plates), so the dump only
verifies tensor I/O and decoding, not detection accuracy.
"""
import hashlib
import json
import sys

import numpy as np
from ai_edge_litert.interpreter import Interpreter

path = sys.argv[1]
it = Interpreter(model_path=path)
it.allocate_tensors()
inp = it.get_input_details()[0]
h, w = int(inp["shape"][1]), int(inp["shape"][2])
yy, xx = np.mgrid[0:h, 0:w]
img = np.stack([(xx * 255 // (w - 1)), (yy * 255 // (h - 1)), ((xx + yy) * 255 // (w + h - 2))], -1).astype(np.uint8)
it.set_tensor(inp["index"], img[None])
it.invoke()
outs = {}
for d in it.get_output_details():
    v = it.get_tensor(d["index"])
    outs[d["name"]] = v.reshape(-1).tolist()
    looks_int = bool(np.all(np.abs(v - np.round(v)) < 1e-6))
    print("OUT", d["name"], list(d["shape"]), "min=%.3f max=%.3f integer_like=%s" % (v.min(), v.max(), looks_int))
print("sha256", hashlib.sha256(open(path, "rb").read()).hexdigest())
if len(sys.argv) > 2:
    json.dump({"input": {"width": w, "height": h}, "outputs": outs}, open(sys.argv[2], "w"))
