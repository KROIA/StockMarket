"""Bounded, read-only NBT reader (gzip or raw). Based on earlier investigation scripts.

World files are untrusted input: decompressed size, nesting depth and list lengths are capped,
and every malformed input raises NBTError instead of hanging or exhausting memory.
"""
import gzip, io, struct

MAX_BYTES = 256 * 1024 * 1024   # decompressed size cap
MAX_DEPTH = 512                 # same nesting limit Minecraft uses


class NBTError(ValueError):
    pass


def _rd(b, i, t, depth):
    if depth > MAX_DEPTH:
        raise NBTError('NBT nesting deeper than %d' % MAX_DEPTH)
    if t == 1: return struct.unpack_from('>b', b, i)[0], i + 1
    if t == 2: return struct.unpack_from('>h', b, i)[0], i + 2
    if t == 3: return struct.unpack_from('>i', b, i)[0], i + 4
    if t == 4: return struct.unpack_from('>q', b, i)[0], i + 8
    if t == 5: return struct.unpack_from('>f', b, i)[0], i + 4
    if t == 6: return struct.unpack_from('>d', b, i)[0], i + 8
    if t in (7, 11, 12):
        n = struct.unpack_from('>i', b, i)[0]; i += 4
        w = {7: 1, 11: 4, 12: 8}[t]
        if n < 0 or i + n * w > len(b):
            raise NBTError('array length %d out of bounds' % n)
        if t == 7: return list(b[i:i + n]), i + n
        return list(struct.unpack_from('>%d%s' % (n, 'i' if t == 11 else 'q'), b, i)), i + n * w
    if t == 8:
        n = struct.unpack_from('>H', b, i)[0]
        if i + 2 + n > len(b): raise NBTError('string out of bounds')
        return b[i + 2:i + 2 + n].decode('utf-8', 'replace'), i + 2 + n
    if t == 9:
        et = b[i]; n = struct.unpack_from('>i', b, i + 1)[0]; i += 5
        # every element takes at least one byte (except empty-typed lists), so n is bounded by the input
        if n < 0 or (et != 0 and n > len(b) - i):
            raise NBTError('list length %d out of bounds' % n)
        out = []
        for _ in range(n if et else 0):
            v, i = _rd(b, i, et, depth + 1); out.append(v)
        return out, i
    if t == 10:
        d = {}
        while True:
            tt = b[i]; i += 1
            if tt == 0: return d, i
            k, i = _rd(b, i, 8, depth)
            d[k], i = _rd(b, i, tt, depth + 1)
    raise NBTError('unknown tag type %r' % t)


def loads(data):
    """Parse NBT bytes (optionally gzip-compressed). Returns the root compound as dicts/lists."""
    if data[:2] == b'\x1f\x8b':
        with gzip.GzipFile(fileobj=io.BytesIO(data)) as g:
            data = g.read(MAX_BYTES + 1)
        if len(data) > MAX_BYTES:
            raise NBTError('decompressed NBT larger than %d bytes' % MAX_BYTES)
    try:
        t = data[0]
        _, i = _rd(data, 1, 8, 0)          # root name
        return _rd(data, i, t, 0)[0]
    except (IndexError, struct.error, EOFError) as e:
        raise NBTError('truncated or malformed NBT: %s' % e)
    except RecursionError:
        raise NBTError('NBT nesting too deep')


def load(path):
    with open(path, 'rb') as f:
        data = f.read(MAX_BYTES + 1)
    if len(data) > MAX_BYTES:
        raise NBTError('NBT file larger than %d bytes' % MAX_BYTES)
    try:
        return loads(data)
    except (OSError, gzip.BadGzipFile) as e:
        raise NBTError('bad gzip: %s' % e)


def dumps(root, name=''):
    """Minimal writer (dict/list/int/str/float/bytes) used by selftest to build fixtures."""
    def tag(v):
        if isinstance(v, int): return 3 if -2**31 <= v < 2**31 else 4
        if isinstance(v, float): return 6
        if isinstance(v, str): return 8
        if isinstance(v, list): return 9
        if isinstance(v, dict): return 10
        raise TypeError(type(v))

    def s(x):
        e = x.encode('utf-8'); return struct.pack('>H', len(e)) + e

    def pay(v):
        t = tag(v)
        if t == 3: return struct.pack('>i', v)
        if t == 4: return struct.pack('>q', v)
        if t == 6: return struct.pack('>d', v)
        if t == 8: return s(v)
        if t == 9:
            et = tag(v[0]) if v else 0
            return bytes([et]) + struct.pack('>i', len(v)) + b''.join(pay(x) for x in v)
        return b''.join(bytes([tag(x)]) + s(k) + pay(x) for k, x in v.items()) + b'\x00'

    return gzip.compress(b'\x0a' + s(name) + pay(root))
