#!/usr/bin/env python3
"""Build the modular neon XenoHUD atlas from the tracked transparent V3 element kit.

Runtime sprites use clean assets. The supplied orb illustrations are static icon artwork and are the
only orb files in the kit. Example rows, values, labels, multipliers, panels and navigation buttons
are reference-only and never enter the runtime atlas.

Usage: py -3.14 tools/gen_dmz_neon_atlas.py [--check]
"""
from __future__ import annotations
import argparse, hashlib, io, json, pathlib, sys
from PIL import Image, ImageDraw

ROOT=pathlib.Path(__file__).resolve().parent.parent
SOURCE=ROOT/'dragonmine_complete_ui_elements_master_bundle/04_dragonmine_status_redesign_v3/dragonmine_redesign_single_elements_v3'
ATLAS_PNG=ROOT/'src/main/resources/assets/xenopixelsmod/textures/gui/xeno_neon_character.png'
JAVA_OUT=ROOT/'src/main/java/net/bullettrain/xenopixelsmod/client/hud/XenoNeonAtlas.java'
GEN_DIR=ROOT/'tools/generated'; MANIFEST=GEN_DIR/'xeno_neon_character.json'
CONTACT=GEN_DIR/'xeno_neon_character_contact.png'; OVERLAY=GEN_DIR/'xeno_neon_character_anchors.png'
ATLAS_W=2048; ATLAS_H=2048; DENSITY=5; PAD=5
# Logical sizes are constrained by DMZ ScaledScreen's 320x240 minimum canvas. They preserve each
# supplied element's role while allowing 102 + 86 + 102 = 290 pixels across the screen.
SPECS={
 'INFO_PANEL':('01_panels/clean_information_panel.png',(102,153)),
 'STATS_PANEL':('01_panels/clean_statistics_panel.png',(102,150)),
 'INFO_HEADER':('02_headers/clean_information_header.png',(96,29)),
 'INFO_STATS_HEADER':('02_headers/clean_information_stats_header.png',(81,14)),
 'STATISTICS_HEADER':('02_headers/clean_statistics_header.png',(87,23)),
 'BASIC_ROW':('03_information_basic_rows/clean_level_row.png',(90,11)),
 'STAT_ROW':('04_information_stat_rows/clean_information_stat_row_generic.png',(104,13)),
 'STATISTIC_ROW':('05_statistics_rows/clean_statistics_row_generic.png',(104,13)),
 'NAMEPLATE':('06_nameplate/clean_nameplate.png',(124,32)),
 'NAV_BASE':('07_navigation_buttons/clean_navigation_button_base.png',(25,25)),
 'ORB_BLUE':('08_icons/example_blue_orb_icon.png',(10,18)),
 'ORB_GOLD':('08_icons/example_orange_orb_icon.png',(10,18)),
 'ORB_RED':('08_icons/example_red_orb_icon.png',(10,18)),
 'MULTIPLIER_FIELD':('09_micro_elements/clean_multiplier_field.png',(26,11)),
 'PLUS':('09_micro_elements/clean_plus_button.png',(9,9)),
 'DIVIDER':('10_overlays/clean_blue_orange_divider.png',(70,8)),
 'SCAN_RING':('10_overlays/clean_character_scan_ring.png',(64,64)),
}
# Shared screen geometry. Rows are composited independently over the clean panel shells.
INFO_ROWS=[34,45,56,67]
STAT_ROWS=[74,85,96,107,118,129,140]
STATISTIC_ROWS=[29,41,53,65,77,89,101]
SUMMARY_ROWS=[113,125,137]
COLORS={'LABEL':0xFFE9F8FF,'VALUE':0xFFFFFFFF,'HEADER':0xFF7CEBFF,'GOLD':0xFFFFC85A,
        'RED':0xFFFF5E70,'GREEN':0xFF72FF98,'MAGENTA':0xFFFF72DE,'CYAN':0xFF55EFFF,
        'ORANGE':0xFFFF9B52}
class BuildError(RuntimeError): pass

def load(rel,size):
 p=SOURCE/rel
 if not p.exists(): raise BuildError(f'missing tracked V3 asset: {rel}')
 im=Image.open(p)
 if im.mode!='RGBA': raise BuildError(f'{rel} must be RGBA, found {im.mode}')
 if im.getchannel('A').getbbox() is None: raise BuildError(f'{rel} is fully transparent')
 return im.resize((size[0]*DENSITY,size[1]*DENSITY),Image.Resampling.LANCZOS)

def build_sprites(): return {n:load(*spec) for n,spec in SPECS.items()}
def pack(sprites):
 out={}; x=y=shelf=0
 for n in sorted(sprites,key=lambda n:(-sprites[n].height,n)):
  im=sprites[n]
  if x+im.width>ATLAS_W: x=0; y+=shelf+PAD; shelf=0
  if y+im.height>ATLAS_H: raise BuildError('atlas overflow at '+n)
  out[n]=(x,y,im.width,im.height); x+=im.width+PAD; shelf=max(shelf,im.height)
 return out

def png(im):
 b=io.BytesIO(); im.save(b,'PNG'); return b.getvalue()
def sha(b): return hashlib.sha256(b).hexdigest()
def make_atlas(sprites,placed):
 out=Image.new('RGBA',(ATLAS_W,ATLAS_H))
 for n,(x,y,_,_) in placed.items(): out.alpha_composite(sprites[n],(x,y))
 return out

def contact(sprites):
 names=sorted(sprites); cols=4; cw=150; ch=190
 out=Image.new('RGBA',(cols*cw,((len(names)+cols-1)//cols)*ch),(13,17,25,255)); d=ImageDraw.Draw(out)
 for i,n in enumerate(names):
  im=sprites[n].resize(SPECS[n][1],Image.Resampling.LANCZOS); x=i%cols*cw+8; y=i//cols*ch+24
  out.alpha_composite(im,(x,y)); d.text((x,6+i//cols*ch),f'{n} {im.width}x{im.height}',fill='white')
 return out

def overlay(sprites):
 out=Image.new('RGBA',(320,240),(13,17,25,255)); d=ImageDraw.Draw(out)
 ix,sy=15,48; sx=203
 out.alpha_composite(sprites['INFO_PANEL'].resize(SPECS['INFO_PANEL'][1]),(ix,sy))
 out.alpha_composite(sprites['STATS_PANEL'].resize(SPECS['STATS_PANEL'][1]),(sx,sy))
 for y in INFO_ROWS: d.rectangle((ix+6,sy+y,ix+96,sy+y+10),outline='#45ff90')
 for y in STAT_ROWS: d.rectangle((ix-1,sy+y,ix+103,sy+y+12),outline='#ffd75a')
 for y in STATISTIC_ROWS+SUMMARY_ROWS: d.rectangle((sx-1,sy+y,sx+103,sy+y+12),outline='#59dfff')
 d.rectangle((128,68,192,132),outline='#ff67de'); d.text((8,8),'V3 modular logical anchors',fill='white')
 return out

def java(placed):
 lines=['package net.bullettrain.xenopixelsmod.client.hud;','','import net.minecraft.resources.ResourceLocation;','','/** Generated by {@code tools/gen_dmz_neon_atlas.py}; do not edit. */','public final class XenoNeonAtlas {','    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(','            "xenopixelsmod", "textures/gui/xeno_neon_character.png");',f'    public static final int ATLAS_WIDTH = {ATLAS_W};',f'    public static final int ATLAS_HEIGHT = {ATLAS_H};','    public record Sprite(int u, int v, int sourceWidth, int sourceHeight, int width, int height) {}','']
 for n in sorted(placed):
  x,y,w,h=placed[n]; lw,lh=SPECS[n][1]
  lines.append(f'    public static final Sprite {n} = new Sprite({x}, {y}, {w}, {h}, {lw}, {lh});')
 def arr(name,ys): lines.extend(['',f'    public static final int[][] {name} = {{'+', '.join(f'{{{y}, 11}}' for y in ys)+'};'])
 arr('INFO_ROWS',INFO_ROWS); arr('STAT_ROWS',STAT_ROWS); arr('STATISTIC_ROWS',STATISTIC_ROWS); arr('SUMMARY_ROWS',SUMMARY_ROWS)
 lines += ['','    public static final int INFO_LABEL_X = 9;','    public static final int INFO_VALUE_X = 57;','    public static final int INFO_RIGHT_X = 96;','    public static final int STAT_LABEL_X = 13;','    public static final int STAT_RIGHT_X = 99;','    public static final int PLUS_X = 2;','    public static final int PLUS_Y = 2;','    public static final int PLUS_WIDTH = 9;','    public static final int PLUS_HEIGHT = 9;','    public static final int STATISTIC_LABEL_X = 8;','    public static final int STATISTIC_RIGHT_X = 97;','    public static final int SUMMARY_LABEL_X = 8;','    public static final int SUMMARY_RIGHT_X = 97;','']
 for n,v in COLORS.items(): lines.append(f'    public static final int {n} = 0x{v:08X};')
 lines += ['','    private XenoNeonAtlas() {}','}','']
 return '\n'.join(lines)

def build():
 sprites=build_sprites(); placed=pack(sprites); atlas=png(make_atlas(sprites,placed)); source=java(placed)
 manifest={'source':str(SOURCE.relative_to(ROOT)).replace('\\','/'),'density':DENSITY,'atlas':{'width':ATLAS_W,'height':ATLAS_H,'sha256':sha(atlas)},'sprites':{n:list(r) for n,r in sorted(placed.items())},'logical_sizes':{n:list(SPECS[n][1]) for n in sorted(SPECS)},'anchors':{'info_rows':INFO_ROWS,'stat_rows':STAT_ROWS,'statistic_rows':STATISTIC_ROWS,'summary_rows':SUMMARY_ROWS},'java_sha256':sha(source.encode())}
 return atlas,source,png(contact(sprites)),png(overlay(sprites)),manifest

def write(result):
 atlas,source,contact_png,overlay_png,manifest=result; GEN_DIR.mkdir(parents=True,exist_ok=True); ATLAS_PNG.parent.mkdir(parents=True,exist_ok=True)
 ATLAS_PNG.write_bytes(atlas); JAVA_OUT.write_text(source,encoding='utf-8'); CONTACT.write_bytes(contact_png); OVERLAY.write_bytes(overlay_png); MANIFEST.write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
def check(result):
 atlas,source,_,_,manifest=result; bad=[]
 if not ATLAS_PNG.exists() or ATLAS_PNG.read_bytes()!=atlas: bad.append(str(ATLAS_PNG))
 if not JAVA_OUT.exists() or JAVA_OUT.read_text(encoding='utf-8')!=source: bad.append(str(JAVA_OUT))
 if not MANIFEST.exists() or json.loads(MANIFEST.read_text())!=manifest: bad.append(str(MANIFEST))
 if bad:
  print('FAIL stale: '+', '.join(bad),file=sys.stderr); return 1
 print('OK  V3 modular neon atlas, Java and manifest are current'); return 0
def main():
 a=argparse.ArgumentParser(); a.add_argument('--check',action='store_true'); args=a.parse_args()
 try: result=build()
 except BuildError as e: print('FAIL '+str(e),file=sys.stderr); return 1
 if args.check:return check(result)
 write(result); print(f'wrote V3 modular neon atlas ({len(result[4]["sprites"])} sprites)'); return 0
if __name__=='__main__': raise SystemExit(main())
