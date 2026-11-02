"""Generates conformance/bucketing.json from the reference MurmurHash3 implementation (pip install mmh3).

Every SDK must reproduce these hashes and buckets exactly (ADR-004). Run from the repo root:
    python3 conformance/tools/gen_bucketing.py
"""
import json
import random

import mmh3

BUCKETS = 100_000


def vector(key_or_seed: str, salt: str, value: str) -> dict:
    text = f"{key_or_seed}.{salt}.{value}"
    h = mmh3.hash(text, 0, signed=False)
    return {"keyOrSeed": key_or_seed, "salt": salt, "value": value, "input": text, "hash": h, "bucket": h % BUCKETS}


def main() -> None:
    rng = random.Random(20261102)
    flags = ["new-checkout", "dark-mode", "search-v2", "pricing.experiment", "ünïcødé-flag", "a"]
    salts = ["", "s1", "3f9c2a", "salt with spaces"]
    values = ["u1", "user-42", "", "12345", "जय", "😀emoji", "o" * 37, "x.y.z"]
    vectors = [vector(f, s, v) for f in flags for s in salts[:2] for v in values]
    for _ in range(140):
        key = "".join(rng.choice("abcdefghijklmnopqrstuvwxyz-0123456789") for _ in range(rng.randint(1, 24)))
        vectors.append(vector(rng.choice(flags), rng.choice(salts), key))
    # experiment seeds use an empty salt: "<seed>..<value>"
    vectors += [vector(str(seed), "", v) for seed in (0, 61, 2147483647) for v in values]
    with open("conformance/bucketing.json", "w", encoding="utf-8") as out:
        json.dump({"buckets": BUCKETS, "vectors": vectors}, out, ensure_ascii=False, indent=1)
        out.write("\n")
    print(f"wrote {len(vectors)} vectors")


if __name__ == "__main__":
    main()
