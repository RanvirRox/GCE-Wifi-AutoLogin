from PIL import Image, ImageDraw
import os

# We will render a clean 512x512 PNG bitmap icon to serve as legacy fallback bitmap for mipmaps
img = Image.new('RGBA', (512, 512), (215, 25, 33, 255))
draw = ImageDraw.Draw(img)

# We can generate PNG fallback icons across mipmap folders
sizes = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192
}

base_dir = '/home/kali/Desktop/GCE-Wifi-AutoLogin/app/src/main/res'

for folder, s in sizes.items():
    out_dir = os.path.join(base_dir, folder)
    os.makedirs(out_dir, exist_ok=True)
    # create solid icon PNG
    icon_img = Image.new('RGBA', (s, s), (215, 25, 33, 255))
    icon_img.save(os.path.join(out_dir, 'ic_launcher.png'))
    icon_img.save(os.path.join(out_dir, 'ic_launcher_round.png'))

print("Fallback mipmap PNGs generated successfully.")
