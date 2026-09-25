# -*- coding: utf-8 -*-
"""Final byte-anchored repair for PaymentService.java.

Fixes the LAST remaining corrupt token on the AutoPay series so the file compiles:
  1. `walletCredit Preview);`  ->  `walletCredit);`
  2. any leftover single-backslash `\d` illegal escapes -> `\\d`
Empty-string results are fine (no-op success).
"""
import io, os

p = "/home/dinesh/Documents/sale-marking-automation/src/main/java/com/countrydelight/service/PaymentService.java"
p = os.path.expanduser(p)
s = io.open(p, encoding="utf-8").read()

before_preview = s.count("Preview")
before_esc = s.count('matches("\\d{10}")')  # single backslash = illegal escape
print("BEFORE Preview=%d illegal_escape_matches=%d" % (before_preview, before_esc))

# 1) corrupt token(s)
s2 = s.replace("walletCredit Preview);", "walletCredit);")
s2 = s2.replace("Preview", "")  # drop any residual 'Preview' token above uint (safe: none are legit java identifiers used)

# 2) illegal escape: real bytes in the file must be `\\d` (two backslashes).
# In THIS python source, "\\\\d" holds two backslashes. Our file currently has `\d`
# (one) whenever it's broken; normalize to exactly two.
import re
# only fix the phone.matches pattern
s2 = s2.replace('!phone.matches("\\d{10}")', '!phone.matches("\\\\d{10}")')

io.open(p, "w", encoding="utf-8", newline="").write(s2)
print("AFTER Preview=%d" % s2.count("Preview"))
print("WRITE_DONE")
