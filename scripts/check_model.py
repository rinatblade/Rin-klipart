#!/usr/bin/env python3
"""Проверка модели ИИ (RMBG-1.4) перед сборкой: формат входа/выхода и что маска осмысленная."""
import sys, numpy as np, onnxruntime as ort
path = sys.argv[1]
s = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
i, o = s.get_inputs()[0], s.get_outputs()[0]
print("input :", i.name, i.shape, i.type)
print("output:", o.name, o.shape, o.type)
assert i.type == "tensor(float)", "вход не float32"
N = 1024
# тестовая картинка: красный круг с синим квадратом на светлом фоне (ожидаем, что маска покрывает объект)
img = np.full((N, N, 3), 235, np.float32)
yy, xx = np.mgrid[0:N, 0:N]
img[(yy-512)**2 + (xx-512)**2 < 300**2] = (220, 40, 60)
img[430:600, 430:600] = (30, 90, 200)
x = (img/255.0 - 0.5).transpose(2, 0, 1)[None].astype(np.float32)
out = s.run(None, {i.name: x})[0]
print("out shape:", out.shape, "min/max:", float(out.min()), float(out.max()))
assert out.size >= N*N, "неожиданный размер выхода"
m = out.reshape(-1)[:N*N].reshape(N, N)
m = (m - m.min()) / max(1e-6, (m.max() - m.min()))
inside = float(m[(yy-512)**2 + (xx-512)**2 < 250**2].mean())
outside = float(m[(yy-512)**2 + (xx-512)**2 > 400**2].mean())
print("маска внутри объекта: %.3f, снаружи: %.3f" % (inside, outside))
assert inside > outside + 0.3, "маска не отличает объект от фона — проверьте предобработку"
print("МОДЕЛЬ OK")
