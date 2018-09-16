#!/usr/bin/env python

from pathlib import Path
import yaml
from flask import Flask, render_template, request

THEME_DIR = './theme/'
app = Flask(__name__, template_folder='assets')

@app.route('/')
def get_index():
  html = '<!doctype html><title>Wasuramoti Theme Editor</title>'
  for yml in Path(THEME_DIR).glob('*.yml'):
    tag = yml.stem
    html += '<div><a href="/theme/{}">[{}]</a></div>'.format(tag, tag)
  return html

@app.route('/theme/<tag>')
def get_theme(tag):
  config = load_config(tag)
  return render_template('theme_editor_index.html', **config)

@app.route('/theme/<tag>/save', methods=['POST'])
def save_theme(tag):
  config = load_config(tag)
  for name, color in request.form.items():
    config['colors'][name] = color
  with open(Path(THEME_DIR) / '{}_new.yml'.format(tag), 'w') as fout:
    yaml.dump(config, fout, default_flow_style=False)
  return 'ok'


def load_config(tag):
  with open(Path(THEME_DIR) / '{}.yml'.format(tag)) as fin:
    return yaml.load(fin)

if __name__ == '__main__':
  app.run()
