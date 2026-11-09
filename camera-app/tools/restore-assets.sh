#!/bin/sh
# Restaure les images (filigrane + logos) dans app/src/main/res/drawable-nodpi/
# Elles sont stockées en base64 dans ce dépôt car l'envoi GitHub de Muse
# ne transfère que des fichiers texte. À lancer une fois après le clonage,
# depuis la racine du projet camera-app.
set -e
cd "$(dirname "$0")/.."
for f in tools/assets-base64/*.b64; do
  name=$(basename "$f" .b64)
  base64 -d "$f" > "app/src/main/res/drawable-nodpi/$name"
  echo "restauré : app/src/main/res/drawable-nodpi/$name"
done
