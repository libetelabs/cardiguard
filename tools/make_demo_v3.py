"""시연 영상을 사건 유형별로 묶어 다시 조립한다.

구조를 바꾼 이유 — 전까지는 열다섯 장면이 자막 하나씩만 달고 줄줄이 이어져서,
처음 보는 사람은 지금이 어느 상황인지 알 수 없었다. 앞에 타이틀과 차례를 두고,
유형이 바뀌는 자리마다 구분 화면을 끼워 여섯 토막으로 끊는다.

정차 화면은 ParkedCard 에 「이번 주행」 요약이 붙으면서 전부 바뀌었다. 급제동
정차(원인 고르기)는 아예 없던 장면이다. 그 네 토막은 새로 찍고(record_case_scenes.sh),
바뀌지 않은 토막은 기존 완성본에서 프레임 단위로 떼어 쓴다. 기존 완성본은 자막이
박혀 있어 다시 자막을 얹지 않는다.

기존 완성본의 장면 경계는 자막 띠(아래 140px)의 프레임 간 차이로 찾았다.
  196 436 796 888 1218 1400 2247 2489 2673 2908 3164 3435 3750 4081  (전체 4411)

사용: REC=tools/out/case-… IDLE=tools/out/idle-… \
      python3 tools/make_demo_v3.py <기존완성본.mp4> <출력.mp4>
"""
import os
import subprocess
import sys
import tempfile

from PIL import Image, ImageDraw, ImageFont

BASE, OUT = sys.argv[1], sys.argv[2]
REC = os.environ["REC"]
W, H, FPS = 1920, 1080, 30
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONTS = os.path.expanduser("~/Library/Fonts")
tmp = tempfile.mkdtemp(prefix="cardiguard-v3-")

# 앱이 쓰는 색 그대로. 영상 안에서 색이 따로 놀면 다른 제품으로 보인다.
INK, PAPER, MUTED, RED, LINE = "#0F1012", "#ECEDEF", "#8E9196", "#D94A3D", "#2A2C30"


def font(name, size):
    return ImageFont.truetype(f"{FONTS}/Pretendard-{name}.otf", size)


def icon(size):
    """어댑티브 아이콘을 한 장으로 합치고 모서리를 깎는다. 런처가 하는 일을 흉내 낸다."""
    bg = Image.open(f"{ROOT}/app/src/main/res/mipmap-xxxhdpi/ic_launcher_background.png").convert("RGBA")
    fg = Image.open(f"{ROOT}/app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png").convert("RGBA")
    im = Image.alpha_composite(bg, fg)
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, im.size[0] - 1, im.size[1] - 1],
                                           radius=int(im.size[0] * 0.23), fill=255)
    im.putalpha(mask)
    return im.resize((size, size), Image.LANCZOS)


def slide(name, draw_fn):
    im = Image.new("RGB", (W, H), INK)
    draw_fn(im, ImageDraw.Draw(im))
    p = os.path.join(tmp, name + ".png")
    im.save(p)
    return p


def title_slide():
    def go(im, d):
        ic = icon(196)
        im.paste(ic, ((W - 196) // 2, 286), ic)
        f1, f2, f3 = font("Bold", 92), font("Medium", 44), font("Medium", 26)
        def mid(t, f, y, fill):
            d.text(((W - d.textlength(t, font=f)) / 2, y), t, font=f, fill=fill)
        mid("카디가드", f1, 528, PAPER)
        mid("상대가 선을 넘어도, 나는 선을 지킨다", f2, 654, MUTED)
        d.line([(W / 2 - 60, 738), (W / 2 + 60, 738)], fill=RED, width=3)
        mid("libetelabs · 차량용 앱", f3, 772, LINE)
    return slide("s_title", go)


INDEX = [
    ("①", "평상시", "늘 켜져 있고, 오늘 주행을 기록한다"),
    ("②", "급제동", "기록만 남기지 않는다. 원인을 묻는다"),
    ("③", "접촉 사고", "화면이 뒤에 있어도 감지하고, 정차 후 절차를 연다"),
    ("④", "위협", "반복 급제동을 되묻고, 문을 잠근다"),
    ("⑤", "고장", "차 안이 아니라 차 밖으로 안내한다"),
    ("⑥", "그 밖에", "말로 남기기, 주야간 전환"),
]


def index_slide():
    def go(im, d):
        d.text((228, 150), "이 영상에서 보는 것", font=font("Bold", 50), fill=MUTED)
        d.line([(228, 236), (W - 228, 236)], fill=LINE, width=2)
        fn, ft, fs = font("Bold", 42), font("Bold", 44), font("Medium", 36)
        y = 290
        for num, name, desc in INDEX:
            d.text((228, y), num, font=fn, fill=RED)
            d.text((300, y - 2), name, font=ft, fill=PAPER)
            d.text((560, y + 6), desc, font=fs, fill=MUTED)
            y += 108
    return slide("s_index", go)


def divider_slide(i):
    num, name, desc = INDEX[i]
    def go(im, d):
        d.rectangle([228, 404, 236, 676], fill=RED)
        d.text((296, 396), num, font=font("Bold", 120), fill=RED)
        d.text((296, 540), name, font=font("Bold", 88), fill=PAPER)
        d.text((300, 654), desc, font=font("Medium", 40), fill=MUTED)
    return slide(f"s_div{i}", go)


def caption(name, title, voice=None):
    """자막 모양은 make_demo_video.py 와 같아야 한다. 이어 붙으면 다르면 티가 난다."""
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    ft, fv = font("Bold", 36), font("Medium", 30)
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


ENC = ["-c:v", "libx264", "-preset", "medium", "-crf", "20", "-pix_fmt", "yuv420p", "-an"]
FIT = (f"scale={W}:{H}:force_original_aspect_ratio=decrease,"
       f"pad={W}:{H}:(ow-iw)/2:(oh-ih)/2")


def run(args):
    subprocess.run(args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


def still(idx, png, seconds):
    """구분 화면. 앞뒤로 살짝 흐려 들어오고 나간다. 끊어 붙인 티를 줄인다."""
    out = os.path.join(tmp, f"p{idx:02d}.mp4")
    fade = f"fade=t=in:st=0:d=0.35,fade=t=out:st={seconds - 0.35:.2f}:d=0.35"
    run(["ffmpeg", "-v", "error", "-loop", "1", "-t", str(seconds), "-i", png,
         "-vf", f"{FIT},{fade},fps={FPS}", *ENC, out, "-y"])
    return out


def keep(idx, first, last):
    """기존 완성본의 [first, last] 프레임을 그대로 떼어 온다. 자막은 이미 박혀 있다."""
    out = os.path.join(tmp, f"p{idx:02d}.mp4")
    run(["ffmpeg", "-v", "error", "-i", BASE,
         "-vf", f"select='between(n\\,{first}\\,{last})',setpts=N/FRAME_RATE/TB",
         "-vsync", "0", *ENC, out, "-y"])
    return out


def fresh(idx, src, start, seconds, title, voice=None, speed=1.0):
    """새로 찍은 녹화에서 구간을 떼어 자막을 얹는다."""
    out = os.path.join(tmp, f"p{idx:02d}.mp4")
    png = caption(f"c{idx:02d}", title, voice)
    vf = FIT + (f",setpts=PTS/{speed}" if speed != 1.0 else "")
    frames = round(seconds * FPS / speed) if speed != 1.0 else round(seconds * FPS)
    run(["ffmpeg", "-v", "error", "-ss", str(start), "-i", f"{REC}/{src}.mp4", "-i", png,
         "-filter_complex", f"[0:v]{vf},fps={FPS}[v];[v][1:v]overlay=0:0[o]",
         "-map", "[o]", "-frames:v", str(frames), *ENC, out, "-y"])
    return out


# ── 차례 ───────────────────────────────────────────────────────────────────
# keep 의 프레임 번호는 기존 완성본(4411프레임) 기준이다.
PARTS = []
def add(fn, *a, **kw): PARTS.append(fn(len(PARTS), *a, **kw))

add(still, title_slide(), 3.6)
add(still, index_slide(), 6.4)

add(still, divider_slide(0), 2.4)            # ① 평상시
add(keep, 0, 195)                            #   대기
add(keep, 196, 435)                          #   기어 D — 주행 감시 시작

add(still, divider_slide(1), 2.4)            # ② 급제동
add(keep, 436, 795)                          #   주행 중 급제동 감지
add(fresh, "brake-parked", 2.0, 10.5,
    "정차하면 원인을 묻는다 — 상대 때문 / 내 판단 / 그냥 감속")

add(still, divider_slide(2), 2.4)            # ③ 접촉 사고
add(keep, 796, 887)                          #   운전자는 내비를 본다
add(keep, 888, 1217)                         #   에어백 전개
add(keep, 1218, 1399)                        #   앱으로 돌아오니 사고가 올라와 있다
add(fresh, "collision", 1.0, 36.0,
    "정차 후 안내 — 한 화면에 한 단계. 옆에 이번 주행 요약을 둔다", speed=1.7)
add(fresh, "collision", 39.0, 7.5,
    "안내 완료 — 무엇을 처리했는지 남는다")
add(keep, 2489, 2672)                        #   기록이 대기 화면에 남는다

add(still, divider_slide(3), 2.4)            # ④ 위협
add(keep, 3164, 3434)                        #   반복 급제동 — 위협인지 되묻는다
add(keep, 3435, 3749)                        #   문을 잠근다. 112는 확인받고 건다

add(still, divider_slide(4), 2.4)            # ⑤ 고장
add(keep, 3750, 4080)                        #   차 안이 아니라 차 밖으로
add(fresh, "breakdown-parked", 1.5, 17.0,
    "고장도 정차하면 단계로 이어진다", speed=1.5)

add(still, divider_slide(5), 2.4)            # ⑥ 그 밖에
add(keep, 2908, 3163)                        #   운전대를 놓지 않고 말로 남긴다
add(keep, 2673, 2907)                        #   주야간은 차가 정한다

lst = os.path.join(tmp, "list.txt")
with open(lst, "w") as f:
    for p in PARTS:
        f.write(f"file '{os.path.abspath(p)}'\n")
run(["ffmpeg", "-v", "error", "-f", "concat", "-safe", "0", "-i", lst, "-c", "copy", OUT, "-y"])
print(OUT)
