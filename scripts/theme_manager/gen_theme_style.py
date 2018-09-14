#!/usr/bin/env python3

from pathlib import Path
from subprocess import check_output
from string import Template
from copy import copy
import yaml

ROOT_DIR = str(check_output("git rev-parse --show-toplevel", shell=True), 'utf-8').rstrip()

def gen_style(fn, theme):
  with open(fn) as f:
    isLight = theme['isLight']
    template = Template(f.read())
    data = copy(theme['colors'])
    data['name'] = theme['name']
    data['key'] = theme['key']
    data['parentTheme'] = 'Theme.AppCompat.Light' if isLight else 'Theme.AppCompat'
    data['parentPrefTheme'] = 'PreferenceFixTheme.Light' if isLight else 'PreferenceFixTheme'
    data['actionSettingsIcon'] = 'light_ic_action_settings' if isLight else 'ic_action_settings'
    data['parentActionBar'] = 'Widget.AppCompat.Light.ActionBar.Solid' if isLight else 'Widget.AppCompat.ActionBar.Solid'
  outfile = Path(fn).stem + '-theme-' + theme['key'] + '.xml'
  outpath = Path(ROOT_DIR) / 'src/main/res/values' / outfile
  with open(outpath, 'w') as fout:
    fout.write(template.substitute(data))

def gen_theme(theme):
  for fn in Path('./template/').glob('*.xml'):
    gen_style(fn, theme)

def main():
  for fn in Path('./theme/').glob('*.yml'):
    with open(fn) as f:
      theme = yaml.load(f)
      gen_theme(theme)

if __name__ == '__main__':
  main()
