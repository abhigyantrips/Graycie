# Third-party notices

Graycie's first-party source code and assets are licensed under
`GPL-3.0-or-later`. The following bundled third-party assets are exceptions and
remain under their respective licenses.

## Atkinson Hyperlegible — OFL-1.1

Files:

- `app/src/main/res/font/atkinson_hyperlegible_regular.ttf`
- `app/src/main/res/font/atkinson_hyperlegible_bold.ttf`

These files were verified byte-for-byte against the corresponding files in the
[official Atkinson Hyperlegible repository](https://github.com/googlefonts/atkinson-hyperlegible).
Their SHA-256 digests are:

- Regular: `7fb917c89019896d0b52ee84b7cbb3304c18cb90b19a62f5e32712bd23e97669`
- Bold: `5a3b0c8cc8ca545155150b4512a1fa248298df121c50d6557e651e61fbdab92f`

The complete OFL-1.1 notice is bundled at
`app/src/main/assets/licenses/ATKINSON_HYPERLEGIBLE_LICENSE.txt`.

## Tabler Filled icons — MIT

Files:

- `app/src/main/res/drawable/ic_tabler_*.xml`

The vectors originate from the Filled icon set in the
[official Tabler Icons project](https://github.com/tabler/tabler-icons). They
were converted to Android vector XML and may have been adjusted for Android
resource conventions; modification and format conversion are permitted by the
MIT license.

The complete MIT notice is bundled at
`app/src/main/assets/licenses/TABLER_ICONS_LICENSE.txt`.

## Chat Favour — bundled non-free terms

Files:

- `app/src/main/res/font/chat_favour_regular.otf`
- `app/src/main/res/drawable/ic_graycie_a.xml` (a vector derived from the
  lowercase “a” glyph)

Chat Favour is used unchanged as a font in Graycie's Home wordmark. The
launcher vector is derived from its lowercase “a” glyph. These assets are
explicitly excluded from Graycie's GPL-covered first-party assets. Chat Favour
is not claimed to be open source.

The bundled terms permit personal and commercial use but prohibit modifying
the font software. They do not expressly grant redistribution of the raw font.
Consequently both assets are declared under F-Droid's `NonFreeAssets`
anti-feature. F-Droid review may require explicit redistribution permission or
replacement of both assets.

The terms received with the font are preserved verbatim at
`app/src/main/assets/licenses/CHAT_FAVOUR_READ_ME.txt`.
