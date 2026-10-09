"""Decode an .efkefc EDIT chunk (Effekseer binary XML) to .efkproj-style XML."""
import struct, sys, zlib
from xml.sax.saxutils import escape

def chunks(b):
    assert b[:4] == b'EFKE'
    i = 8
    while i < len(b):
        tag = b[i:i+4]; n = struct.unpack('<i', b[i+4:i+8])[0]
        yield tag, b[i+8:i+8+n]
        i += 8 + n

def decode(x):
    def table(i):
        n = struct.unpack('<H', x[i:i+2])[0]; i += 2; t = {}
        for _ in range(n):
            l = struct.unpack('<H', x[i:i+2])[0]; s = x[i+2:i+2+l].decode('utf-8'); i += 2 + l
            k = struct.unpack('<H', x[i:i+2])[0]; i += 2; t[k] = s
        return t, i
    names, i = table(0)
    vals, i = table(i)
    i += 2  # document element count
    out = []
    def el(p, d):
        # name, hasText, then textId (hasText) or hasChildren; a child count follows unless both are 0.
        name, has, val = struct.unpack('<HII', x[p:p+10]); p += 10
        cnt = 0
        if has or val:
            cnt = struct.unpack('<H', x[p:p+2])[0]; p += 2
        tag = names[name]; pad = '  ' * d
        if has and cnt == 0:
            out.append(f'{pad}<{tag}>{escape(vals[val])}</{tag}>')
        else:
            out.append(f'{pad}<{tag}>')
            for _ in range(cnt): p = el(p, d + 1)
            out.append(f'{pad}</{tag}>')
        return p
    p = el(i, 0)
    assert p == len(x), (p, len(x))
    return '<?xml version="1.0" encoding="utf-8"?>\n' + '\n'.join(out) + '\n'

if __name__ == '__main__':
    b = open(sys.argv[1], 'rb').read()
    for tag, d in chunks(b):
        if tag == b'EDIT':
            open(sys.argv[2], 'w', encoding='utf-8').write(decode(zlib.decompress(d)))
            print('wrote', sys.argv[2])
