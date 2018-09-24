#!/usr/bin/env python

from subprocess import check_output
from pathlib import Path
import yaml
from flask import Flask, render_template, request, send_file

ROOT_DIR = str(check_output("git rev-parse --show-toplevel", shell=True), 'utf-8').rstrip()
CUR_DIR = Path(__file__).parent
THEME_DIR = CUR_DIR / 'theme'
TEMPLATE_FOLDER = (CUR_DIR/'assets').resolve()
IMPORTANT_COLORS = [
    'actionBarDividerColor',
    'generalBackgroundColor',
    'kimarijiSecondaryColor',
    'mainActivityBorderColor',
    'poemTextFuriganaColor',
    'poemTextMainColor',
    'torifudaEdgeColor'
]

app = Flask(__name__, template_folder=TEMPLATE_FOLDER)

@app.route('/')
def get_index():
  html = '<!doctype html><title>Wasuramoti Theme Editor</title>'
  for yml in THEME_DIR.glob('*.yml'):
    tag = yml.stem
    html += '<div><a href="/theme/{}">[{}]</a></div>'.format(tag, tag)
  return html

@app.route('/theme/<tag>')
def get_theme(tag):
  config = load_config(tag)
  config['important_colors'] = IMPORTANT_COLORS
  return render_template('theme_editor_index.html', **config)

@app.route('/theme/<tag>/save', methods=['POST'])
def save_theme(tag):
  config = load_config(tag)
  for name, color in request.form.items():
    config['colors'][name] = color
  with open(THEME_DIR / '{}_new.yml'.format(tag), 'w') as fout:
    yaml.dump(config, fout, default_flow_style=False)
  return 'ok'


@app.route('/image/<name>.png')
def get_image(name):
  path = Path(ROOT_DIR)/'src'/'main'/'res'/'drawable-hdpi'/('{}.png'.format(name))
  return send_file(str(path), mimetype='image/png')

def load_config(tag):
  with open(THEME_DIR / '{}.yml'.format(tag)) as fin:
    return yaml.load(fin)

if __name__ == '__main__':
  app.run()
