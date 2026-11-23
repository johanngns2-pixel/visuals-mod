# Shader-Test

`run.py` führt den Pfützen-Shader (`PuddleShader.java`) in einer künstlichen Szene (Boden, Säule, Blöcke) mit
Software-OpenGL aus und speichert ein Vergleichsbild (oben Eingabe, unten Ergebnis).

Benötigt: `pip install moderngl pillow numpy` und Mesa-EGL (`libegl1 libgl1-mesa-dri`).

    python3 run.py <Flächendeckung 0-1> <Spiegelstärke 0-1> <Nasser Boden 0-1> <Qualität 0|1|2> ausgabe.png
