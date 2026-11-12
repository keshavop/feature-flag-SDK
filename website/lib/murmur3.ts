/** MurmurHash3 x86 32-bit over UTF-8 bytes; a port of ff-core's Murmur3.kt (ADR-004). Returns an unsigned 32-bit int. */
export function murmur3(input: string, seed = 0): number {
  const data = new TextEncoder().encode(input);
  const c1 = 0xcc9e2d51;
  const c2 = 0x1b873593;
  let h1 = seed | 0;
  const blocks = data.length & ~3;
  for (let i = 0; i < blocks; i += 4) {
    let k1 = data[i] | (data[i + 1] << 8) | (data[i + 2] << 16) | (data[i + 3] << 24);
    k1 = Math.imul(k1, c1);
    k1 = (k1 << 15) | (k1 >>> 17);
    k1 = Math.imul(k1, c2);
    h1 ^= k1;
    h1 = (h1 << 13) | (h1 >>> 19);
    h1 = (Math.imul(h1, 5) + 0xe6546b64) | 0;
  }
  let k1 = 0;
  const rem = data.length & 3;
  if (rem >= 3) k1 ^= data[blocks + 2] << 16;
  if (rem >= 2) k1 ^= data[blocks + 1] << 8;
  if (rem >= 1) {
    k1 ^= data[blocks];
    k1 = Math.imul(k1, c1);
    k1 = (k1 << 15) | (k1 >>> 17);
    k1 = Math.imul(k1, c2);
    h1 ^= k1;
  }
  h1 ^= data.length;
  h1 ^= h1 >>> 16;
  h1 = Math.imul(h1, 0x85ebca6b);
  h1 ^= h1 >>> 13;
  h1 = Math.imul(h1, 0xc2b2ae35);
  h1 ^= h1 >>> 16;
  return h1 >>> 0;
}
