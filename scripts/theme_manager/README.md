## What is This ?
Color theme editor for wasuramoti.

## How to Use

1. Run `./theme_editor.py` and open `http://localhost:5000` with browser.
2. Edit the theme and push "Save" button to create `./theme/<name>_new.yml`.
3. Overwrite `./theme/<name>.yml` with `./theme/<name>_new.yml`.
4. Run `./gen_theme.py` to create `src/main/res/values/styles-*theme-<name>.xml` and `src/main/res/drawable/*_theme_<name>.xml`.

## Adding New Theme
1. Copy any `./theme/<name>.yml` to new one and edit `name:` and `tag:`.
2. Edit following files and add new theme.
  - `src/main/res/values/ids.xml`
  - `src/main/res/values/strings.xm`
  - `src/main/res/values-ja/strings.xml`
  - `src/main/scala/ColorThemePref.scala`
3. Run `./gen_theme.py`.

## Deleting Theme
1. Edit following files and delete theme.
  - `src/main/res/values/ids.xml`
  - `src/main/res/values/strings.xm`
  - `src/main/res/values-ja/strings.xml`
  - `src/main/scala/ColorThemePref.scala`
2. Remove following files
  - `./theme/<name>yml`
  - `src/main/res/drawable/*_theme_<name>.xml`
  - `src/main/res/values/styles-*theme-<name>.xml`
