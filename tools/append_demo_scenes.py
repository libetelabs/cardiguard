"""시연 영상 뒤에 장면을 덧붙인다.

make_demo_video.py 는 녹화 하나를 통째로 자르지만, 그 원본 녹화는 남아 있지 않다.
완성본은 이미 자막이 박혀 있어 다시 자르면 자막이 두 겹이 된다. 그래서 완성본은
건드리지 않고, 새로 찍은 녹화에서 장면만 잘라 자막을 얹어 뒤에 잇는다.

사용: python3 tools/append_demo_scenes.py <기존완성본.mp4> <출력.mp4>
"""
import os
import subprocess
import sys
import tempfile

from PIL import Image, ImageDraw, ImageFont

BASE, OUT = sys.argv[1], sys.argv[2]
REC = os.environ["REC"]
W, H, FPS = 1920, 1080, 30
FONT_B = os.path.expanduser("~/Library/Fonts/Pretendard-Bold.otf")
FONT_M = os.path.expanduser("~/Library/Fonts/Pretendard-Medium.otf")
tmp = tempfile.mkdtemp(prefix="cardiguard-append-")


def font(size, bold=True):
    return ImageFont.truetype(FONT_B if bold else FONT_M, size)


def caption(name, title, voice=None):
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    ft, fv = font(36), font(30, bold=False)
    lines = [(title, ft, (255, 255, 255))]
    if voice:
        lines.append((f"(음성) {voice}", fv, (255, 205, 198)))
    pad_x, pad_y, gap = 34, 20, 10
    bw = max(d.textlength(t, font=f) for t, f, _ in lines) + pad_x * 2
    heights = [f.size + 6 for _, f, _ in lines]
    bh = sum(heights) + gap * (len(lines) - 1) + pad_y * 2
    bx, by = (W - bw) / 2, H - 104 - bh
    d.rounded_rectangle([bx, by, bx + bw, by + bh], radius=14, fill=(0, 0, 0, 196))
    y = by + pad_y
    for (t, f, color), hgt in zip(lines, heights):
        d.text(((W - d.textlength(t, font=f)) / 2, y), t, font=f, fill=color)
        y += hgt + gap
    p = os.path.join(tmp, name + ".png")
    im.save(p)
    return p


def run(args):
    subprocess.run(args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


def clip(idx, src, start, end, title, voice=None):
    out = os.path.join(tmp, f"c{idx:02d}.mp4")
    png = caption(f"cap{idx:02d}", title, voice)
    vf = f"scale={W}:{H}:force_original_aspect_ratio=decrease,pad={W}:{H}:(ow-iw)/2:(oh-ih)/2"
    run(["ffmpeg", "-v", "error", "-ss", str(start), "-to", str(end), "-i", src,
         "-i", png, "-filter_complex", f"[0:v]{vf}[v];[v][1:v]overlay=0:0,fps={FPS}[o]",
         "-map", "[o]", "-c:v", "libx264", "-preset", "medium", "-crf", "20",
         "-pix_fmt", "yuv420p", "-an", out, "-y"])
    return out


A, B = f"{REC}/segA.mp4", f"{REC}/segB.mp4"

# 기존 완성본은 사고 한 줄기만 보여 준다. 사고만 다루는 앱이 아니라는 것을 뒤에 붙인다.
SCENES = [
    (B, 17.5, 26, "운전대를 놓지 않고 말로 남긴다", "시각과 위치를 저장했습니다."),
    (A, 48, 57, "급제동이 반복되면 되묻는다 — 위협인지", "급제동이 반복됩니다. 위협받고 계신가요?"),
    (A, 57, 67.5, "위협이면 문을 잠근다. 112는 확인받고 건다",
     "문을 잠급니다. 내리지 말고 사람 많은 곳으로 이동하세요."),
    (B, 37, 48, "고장은 반대로 안내한다 — 차 안이 아니라 차 밖으로",
     "비상등을 켜세요. 고속도로라면 가드레일 밖으로 대피하세요."),
    (B, 48, 59, "고장도 정차하면 단계로 이어진다", None),
]

# 기존 완성본은 규격이 같으므로 다시 인코딩하지 않고 그대로 앞에 둔다.
parts = [BASE] + [clip(i, *s) for i, s in enumerate(SCENES)]
lst = os.path.join(tmp, "list.txt")
with open(lst, "w") as f:
    for p in parts:
        f.write(f"file '{os.path.abspath(p)}'\n")
run(["ffmpeg", "-v", "error", "-f", "concat", "-safe", "0", "-i", lst,
     "-c:v", "libx264", "-preset", "medium", "-crf", "20", "-pix_fmt", "yuv420p",
     "-an", OUT, "-y"])
print(OUT)
