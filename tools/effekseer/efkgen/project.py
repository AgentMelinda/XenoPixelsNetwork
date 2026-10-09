"""Effekseer 1.80 project XML builders.

Names follow the 1.80 layout the editor saves (learned by decoding what it writes back):
colours live under DrawingValues/ColorAll/{Type, Fixed | Random | Easing}, spawn timing under
CommonValues/Generation/{GenerationTime, GenerationTimeOffset}. Enum values, confirmed against the
editor's own samples: Billboard 1 = YAxisFixed, 2 = Fixed (default = Billboard); AlphaBlend
1 = Blend, 2 = Add; DrawingValues Type 0 = None, 3 = Ribbon, 4 = Ring, 5 = Model, 6 = Track (default = Sprite); Scaling Type
0 Fixed, 2 Easing, 3 SinglePVA, 4 SingleEasing; spawn shape 1 Sphere, 3 Circle.
"""
import xml.etree.ElementTree as ET

T = 'texture/'


def rng(center, lo=None, hi=None):
    lo = center if lo is None else lo
    hi = center if hi is None else hi
    return {'Center': center, 'Max': hi, 'Min': lo}


def span(lo, hi):
    if isinstance(lo, int) and isinstance(hi, int):
        return rng((lo + hi) // 2, lo, hi)  # frames and colours are whole numbers
    return rng((lo + hi) / 2.0, lo, hi)


def rgba(c, a=255):
    return {'R': c[0], 'G': c[1], 'B': c[2], 'A': a}


def color_range(c, a=255, jitter=0):
    return {k: span(max(0, v - jitter), min(255, v + jitter)) if jitter else rng(v)
            for k, v in zip('RGBA', (c[0], c[1], c[2], a))}


def easing_color(start, end):
    return {'Type': 2, 'Easing': {'Start': start, 'End': end}}


def fixed_color(c, a=255):
    return {'Type': 0, 'Fixed': rgba(c, a)}


def random_color(c, a=255, jitter=0):
    return {'Type': 1, 'Random': color_range(c, a, jitter)}


def fade(fade_in, fade_out):
    d = {}
    if fade_in:
        d.update({'FadeInType': 1, 'FadeIn': {'Frame': fade_in}})
    if fade_out:
        d.update({'FadeOutType': 1, 'FadeOut': {'Frame': fade_out}})
    return d


def node(name, children=(), **sections):
    d = {}
    for key, value in sections.items():
        d[key] = value
    d['Name'] = name
    d['Children'] = list(children)
    return d


def common(life, count=None, every=None, delay=None):
    c = {'Life': life if isinstance(life, dict) else rng(life)}
    if count == 'inf':
        c['MaxGeneration'] = {'Infinite': 'True'}
    elif count is not None:
        c['MaxGeneration'] = {'Value': count}
    generation = {}
    if every is not None:
        generation['GenerationTime'] = every if isinstance(every, dict) else rng(every)
    if delay is not None:
        generation['GenerationTimeOffset'] = delay if isinstance(delay, dict) else rng(delay)
    if generation:
        c['Generation'] = generation
    return c


def pva_location(location=None, velocity=None, accel=None):
    p = {}
    if location: p['Location'] = location
    if velocity: p['Velocity'] = velocity
    if accel: p['Acceleration'] = accel
    return {'Type': 1, 'PVA': p}


def spin(z_start=(-180, 180), z_speed=(-4, 4)):
    return {'Type': 1, 'PVA': {'Rotation': {'Z': span(*z_start)}, 'Velocity': {'Z': span(*z_speed)}}}


def scale_ease(start, end):
    return {'Type': 4, 'SingleEasing': {'Start': start, 'End': end}}


def scale_single(value):
    return {'Type': 3, 'SinglePVA': {'Scale': value if isinstance(value, dict) else rng(value)}}


def scale_xyz_ease(start, end):
    return {'Type': 2, 'Easing': {'Start': start, 'End': end}}


def scale_fixed(x, y, z):
    return {'Type': 0, 'Fixed': {'Scale': {'X': x, 'Y': y, 'Z': z}}}


def sprite(texture, blend=2, billboard=None, color=None, fade_in=0, fade_out=0, extra=None, emissive=None):
    renderer = {'ColorTexture': texture, 'AlphaBlend': blend}
    if emissive:
        # HDR brightness (the 1.6 samples' *_HDR effects): the hot core of an energy attack.
        renderer['EmissiveScaling'] = emissive
    renderer.update(fade(fade_in, fade_out))
    if extra: renderer.update(extra)
    drawing = {}
    if color:
        drawing['ColorAll'] = color
    if billboard is not None:
        drawing['Sprite'] = {'Billboard': billboard}
    return renderer, drawing


def ring_shape(texture, inner, outer, edge, middle, blend=2, fade_in=0, fade_out=0, vertices=48, emissive=None):
    """A flat ring drawn by the Ring renderer (Simple_Ring_Shape, Simple_Distortion samples): it
    lies in the node's XY plane, so its normal is local +Z. edge and middle are (r, g, b, a): the
    colour at both rims and through the middle of the band.
    """
    renderer = {'AlphaBlend': blend}
    if texture:
        renderer['ColorTexture'] = texture
    if emissive:
        renderer['EmissiveScaling'] = emissive
    renderer.update(fade(fade_in, fade_out))
    drawing = {'Type': 4, 'Ring': {
        'VertexCount': vertices,
        'Outer_Fixed': {'Location': {'X': outer}}, 'Inner_Fixed': {'Location': {'X': inner}},
        'OuterColor_Fixed': rgba(edge[:3], edge[3]), 'CenterColor_Fixed': rgba(middle[:3], middle[3]),
        'InnerColor_Fixed': rgba(edge[:3], edge[3])}}
    return renderer, drawing


def turbulence(power, scale=6, seed=1):
    return {'LocalForceField1': {'Type': 1, 'Power': power,
                                 'Turbulence': {'Seed': seed, 'FieldScale': scale}}}


def circle(r_lo, r_hi, division=48, axis=1):
    """A ring spawn around an axis: 0 X, 1 Y (a standing body), 2 Z (the editor's default, which it
    does not store - confirmed by the editor dropping it; a flying body along the look)."""
    ring = {'Division': division, 'Radius': span(r_lo, r_hi)}
    if axis != 2:
        ring = {'AxisDirection': axis, **ring}
    return {'Type': 3, 'Circle': ring}


def sphere(r_lo, r_hi):
    return {'Type': 1, 'Sphere': {'Radius': span(r_lo, r_hi),
                                  'RotationX': span(0, 360), 'RotationY': span(0, 360)}}


def lathe(name, points, mesh_type=0, divisions=48, ribbon_count=2, twist=1, axis=1, fade_ends=True):
    """Procedural body of revolution from four (radius, height) control points.

    axis: 0 X, 1 Y, 2 Z (the axis the profile is revolved around and runs along).
    """
    white = (255, 255, 255)
    end_alpha = 0 if fade_ends else 255
    p = {
        'Name': name, 'Type': mesh_type,
        'Mesh': {'AngleBeginEnd': {'X': 0, 'Y': 360}, 'Divisions': {'X': divisions, 'Y': divisions},
                 'Rotate': 1},
        'Ribbon': {'CrossSection': 1, 'Rotate': twist, 'Vertices': 24, 'RibbonScales': {'X': 0.12, 'Y': 0.12},
                   'RibbonAngles': {'X': 0, 'Y': 0}, 'RibbonNoises': {'X': 0, 'Y': 0},
                   'Count': ribbon_count},
        'Shape': {'PrimitiveType': 3, 'Radius': 1, 'Radius2': 1, 'Depth': 1, 'DepthMin': -1, 'DepthMax': 1,
                  'AxisType': axis},
        'ShapeNoise': {'CurlNoiseFrequency': {'X': 1, 'Y': 1, 'Z': 1},
                       'CurlNoisePower': {'X': 0.02, 'Y': 0.02, 'Z': 0.02}},
        'VertexColor': {
            # Transparent at both rims, solid through the middle.
            'ColorUpperLeft': rgba(white, end_alpha), 'ColorUpperCenter': rgba(white, end_alpha),
            'ColorUpperRight': rgba(white, end_alpha),
            'ColorMiddleLeft': rgba(white), 'ColorMiddleCenter': rgba(white),
            'ColorMiddleRight': rgba(white),
            'ColorLowerLeft': rgba(white, end_alpha), 'ColorLowerCenter': rgba(white, end_alpha),
            'ColorLowerRight': rgba(white, end_alpha),
            'ColorCenterPosition': {'X': 0.5, 'Y': 0.45}, 'ColorCenterArea': {'X': 0, 'Y': 0.5},
        },
    }
    for i, (r, y) in enumerate(points, 1):
        p['Shape'][f'Point{i}'] = {'X': r, 'Y': y}
    return p


def model(reference, color, blend, texture, fade_in, fade_out, scroll, alpha_tex=None,
          distort=None, cutoff=None, emissive=None):
    renderer = {'ColorTexture': texture, 'AlphaBlend': blend, 'UV': 3,
                'UVScroll': {'Size': {'X': rng(scroll[0]), 'Y': rng(scroll[1])},
                             'Speed': {'X': rng(scroll[2]), 'Y': rng(scroll[3])}}}
    renderer.update(fade(fade_in, fade_out))
    if emissive:
        renderer['EmissiveScaling'] = emissive
    drawing = {'Type': 5, 'ColorAll': fixed_color(color[:3], color[3] if len(color) > 3 else 255),
               'Model': {'ModelReference': 1, 'Reference': reference, 'Culling': 2}}
    advanced = {}
    if alpha_tex:
        advanced['AlphaTextureParam'] = {'Enabled': 'True', 'Texture': alpha_tex}
    if distort:
        advanced['UVDistortionTextureParam'] = {'Enabled': 'True', 'Texture': distort[0],
                                                'UVDistortionIntensity': distort[1]}
    if cutoff:
        advanced['AlphaCutoffParam'] = {'Enabled': 'True', 'Fixed': {'Threshold': cutoff[0]},
                                        'EdgeParam': {'EdgeThreshold': cutoff[1],
                                                      'EdgeColor': rgba(cutoff[2])}}
    out = {'RendererCommonValues': renderer, 'DrawingValues': drawing}
    if advanced:
        out['AdvancedRendererCommonValuesValues'] = advanced
    return out


def to_xml(parent, key, value):
    if isinstance(value, dict):
        el = ET.SubElement(parent, key)
        for k, v in value.items():
            to_xml(el, k, v)
    elif isinstance(value, list):
        el = ET.SubElement(parent, key)
        for item in value:
            to_xml(el, 'Node', item)
    else:
        el = ET.SubElement(parent, key)
        el.text = fmt(value)
    return el


def fmt(v):
    if isinstance(v, float):
        return ('%.6f' % v).rstrip('0').rstrip('.') or '0'
    return str(v)


def project(root_node, procedural, frames=160):
    doc = ET.Element('EffekseerProject')
    root = ET.SubElement(doc, 'Root')
    ET.SubElement(root, 'Name').text = 'Root'
    children = ET.SubElement(root, 'Children')
    to_xml(children, 'Node', root_node)
    if procedural:
        pm = ET.SubElement(ET.SubElement(doc, 'ProceduralModel'), 'ProceduralModels')
        for p in procedural:
            to_xml(pm, 'ProceduralModelParameter', p)
    for k, v in (('ToolVersion', '1.80.6'), ('Version', '3'), ('StartFrame', '0'),
                 ('EndFrame', str(frames)), ('IsLoop', 'True')):
        ET.SubElement(doc, k).text = v
    ET.indent(doc)
    return '<?xml version="1.0" encoding="utf-8"?>\n' + ET.tostring(doc, encoding='unicode') + '\n'
