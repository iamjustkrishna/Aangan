# Usage: python design/render_screens.py design out.html light|dark ScreenHome,ScreenPlayer
# Builds one HTML page showing the named screen mockups side by side (needs internet for React and Figtree).
# Open it in a browser, or screenshot it with headless Chrome: chrome --headless=new --screenshot=out.png --window-size=1650,820 file:///.../out.html
import json, os, re, sys
root = sys.argv[1]; out = sys.argv[2]; theme = sys.argv[3]
p = root  # folder holding tokens.json and components/
t = json.load(open(os.path.join(p, 'tokens.json'), encoding='utf-8'))
def val(v): 
    v = v if isinstance(v, str) else v.get(theme, v.get('light'))
    m = re.fullmatch(r'\{(.+)\}', v); return f'var(--{m.group(1)})' if m else v
css = ':root{' + ''.join(f'--{x["name"]}:{val(x["value"])};' for x in t['color']['tokens'])
for fam in ['spacing','radius','size']:
    css += ''.join(f'--{x["name"]}:{x["value"]};' for x in t[fam]['tokens'])
css += ''.join(f'--{x["name"]}:{val(x["value"])};' for x in t['shadow']['tokens'])
for k, v in t['type']['families'].items(): css += f'--font-{k}:{v};'
css += '}'
for g in t['type']['groups']:
    for s in g['styles']:
        css += f'.{s["name"]}{{font-family:var(--font-{g["family"]});font-size:{s["fontSize"]};line-height:{s["lineHeight"]};font-weight:{s["fontWeight"]};letter-spacing:{s.get("letterSpacing","normal")}}}'
bcss = open(os.path.join(p,'components','bundle.css'),encoding='utf-8').read()
bjs = open(os.path.join(p,'components','bundle.js'),encoding='utf-8').read()
screens = sys.argv[4].split(',')
scripts = []
for i, s in enumerate(screens):
    html = open(os.path.join(p,'components',s,'preview.html'),encoding='utf-8').read()
    js = re.search(r'<script>\n(.*?)\n</script>', html, re.S).group(1).replace("getElementById('root')", f"getElementById('r{i}')")
    scripts.append(js)
doc = f'''<!doctype html><html><head><meta charset="utf-8"><style>{bcss}</style><style>{css} body{{margin:0;background:var(--surface)}} .wrap{{display:flex;gap:24px;padding:24px}}</style>
<script src="https://cdn.jsdelivr.net/npm/react@18.3.1/umd/react.production.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/react-dom@18.3.1/umd/react-dom.production.min.js"></script>
<script>{bjs}</script></head><body><div class="wrap">{''.join(f'<div id="r{i}" class="fv-surface" style="width:376px;padding:0"></div>' for i in range(len(screens)))}</div>
{''.join('<script>'+s+'</script>' for s in scripts)}</body></html>'''
open(out,'w',encoding='utf-8').write(doc)
